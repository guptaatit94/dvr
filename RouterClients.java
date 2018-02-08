/*
 * File name: RouterClients.java
 *
 *
 *
 * This code should be executed at the client side. It inputs the port on which it listens.
 */

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class RouterClients {

    //Variables
    static String SUBNET = "255.255.255.0";
    static String FIRST_ITERATION = "--->FIRST ITERATION";
    static String NEXT_ITERATION = "--->NEXT ITERATION";
    static int count = 0;
    static String HEADER = "Destination \t Subnet \t Next Hop \t Cost \t";
    static int INFINITE = 10000;


    /**
     * The main method.
     *
     * @param args none
     * @throws Exception
     */
    public static void main(String[] args) throws Exception {
        Scanner sc = new Scanner(System.in);
        /*System.out.println("Enter the listening port : ");
        int listening_port = sc.nextInt();
        */

        int listening_port = Integer.parseInt(args[0]);

        InetAddress self_address = InetAddress.getLocalHost();

        System.out.println(self_address + " is now active listening on " + listening_port);
        System.out.println("Waiting for connection from the main program... ");

        //Creating Socket
        DatagramSocket ds = new DatagramSocket(listening_port);

        byte[] message = new byte[1024];
        DatagramPacket incoming = new DatagramPacket(message, message.length);
        ds.receive(incoming);

        System.out.println("Connection established --> " + self_address + " is now active!");

        String active_hosts = new String(incoming.getData());

        //keeping track of the active hosts.
        ArrayList<String> active_routers = new ArrayList<>();
        String[] routers = active_hosts.split(";");
        for (int i = 0; i < routers.length - 1; i++) {
            InetAddress ia = InetAddress.getByName(routers[i]);
            if (!ia.equals(self_address)) {
                active_routers.add(routers[i]);
            }
        }

        byte[] message1 = new byte[1024];
        incoming = new DatagramPacket(message1, message1.length);
        ds.receive(incoming);
        String status = new String(incoming.getData());

        //need to split at , to get ia address
        HashMap<String, NeighboursDetails> neighbours = new HashMap<>();

        //need to split at : to build hashmap
        String[] input = status.split(",");
        for (int i = 0; i < input.length - 1; i++) {
            String[] parts = input[i].split(":");
            neighbours.put(parts[0].trim(), new NeighboursDetails(parts[0].trim(), Integer.parseInt(parts[1]), Integer.parseInt(parts[2])));
        }

        //This hashmap is used to store best path possible to reach all active routers.
        ConcurrentHashMap<String, TableEntry> entries = new ConcurrentHashMap<>();

        //This hashmap is used to store non optimal paths. Since no of neighbours is assumed to be 2, it will work.
        ConcurrentHashMap<String, TableEntry> non_optimal = new ConcurrentHashMap<>();

        //printing the first iteration, creating its table entry object and adding to the hashmap.
        System.out.println(FIRST_ITERATION);
        System.out.println(HEADER);
        for (String s : neighbours.keySet()) {
            TableEntry entry = new TableEntry(neighbours.get(s).name, SUBNET, neighbours.get(s).name, neighbours.get(s).cost);
            entries.put(s, entry);
        }

        //creating packet to send as distance vector to neighbours.
        String packet = self_address.getHostAddress() + ":";
        for (String s : entries.keySet()) {
            System.out.println(entries.get(s));
            packet += entries.get(s).return_row();
        }
        System.out.println();

        //This hashmap keeps track of timestamp of all the active neighbouring routers.
        ConcurrentHashMap<String, Long> alive = new ConcurrentHashMap<>();


        //Running and infinite loop to send/ receive/ update distance vectors.
        while (true) {
            //Sending part
            //Sends self distance vector to all the neighbours.
            for (String s : neighbours.keySet()) {
                byte[] dv = packet.getBytes();
                DatagramPacket distance_vector = new DatagramPacket(dv, dv.length, neighbours.get(s).get_inetaddress(), neighbours.get(s).listening_port);
                ds.send(distance_vector);
            }

            //Receiving part
            //Waits for timeout seconds before deciding if all the neighbours of a router are dead and exiting.
            byte[] received_packet = new byte[1024];
            DatagramPacket received_vector = new DatagramPacket(received_packet, received_packet.length);
            ds.setSoTimeout(3000);
            try {
                ds.receive(received_vector);
            } catch (SocketTimeoutException e) {
                System.out.println("All my friends are dead :( ");
                System.out.println("R.I.P.");
                System.exit(0);
            }

            String received_packet_string = new String(received_vector.getData());

            //Splitting at : to get the sender.
            String[] parts = received_packet_string.split(":");
            String nexthop = parts[0];

            //Populating alive with the timestamp
            alive.put(nexthop, System.currentTimeMillis());

            //Working with the distance vectors of neighbours.
            String[] temp = parts[1].split(";");

            for (int i = 0; i < temp.length - 1; i++) {
                String[] temp1 = temp[i].split(",");
                String nexthop_sender = temp1[2];

                //Using the distance vector to update if there is a better path
                if (!temp1[0].equals(self_address.getHostAddress())) {
                    int cost = Integer.parseInt(temp1[1]) + neighbours.get(nexthop).cost;
                    TableEntry entry = new TableEntry(temp1[0], SUBNET, nexthop, cost);

                    if (nexthop_sender.equals(self_address.getHostAddress())) {
                        entry.cost = INFINITE;
                    }

                    if (!entries.containsKey(entry.destination)) {
                        entries.put(entry.destination, entry);
                    }


                    if (cost < entries.get(entry.destination).cost) {
                        non_optimal.put(entry.destination, entries.get(entry.destination));
                        entries.put(entry.destination, entry);
                    } else if (!non_optimal.containsKey(entry.destination)) {
                        non_optimal.put(entry.destination, entry);
                    }

                    if (entries.containsKey(entry.destination)) {
                        if ((entry.destination).equals(entries.get(entry.destination).destination)) {
                            if ((entry.nexthop).equals(entries.get(entry.destination).nexthop)) {
                                entries.put(entry.destination, entry);
                            }
                        }
                    }

                }

            }

            //checking if a router failed by checking the timestamp. A router is assumed to fail when we

            for (String s : alive.keySet()) {
                long difference = System.currentTimeMillis() - alive.get(s);
                if (difference > 6000) {
                    System.out.println("Router " + s + " has failed");
                    alive.remove(s);
                    neighbours.remove(s);

                    for (String t : entries.keySet()) {
                        if (entries.get(t).destination.equals(s)) {
                            entries.get(t).cost = INFINITE;
                        }
                        if (entries.get(t).nexthop.equals(s)) {
                            entries.get(t).cost = INFINITE;
                        }

                    }
                }
            }


            for (String s : entries.keySet()) {
                if (non_optimal.containsKey(s)) {
                    if (entries.get(s).cost > non_optimal.get(s).cost) {
                        entries.put(s, non_optimal.get(s));
                        non_optimal.remove(s);
                    }

                }
            }

            //Setting the timeout between different iterations.
            TimeUnit.SECONDS.sleep(1);

            //Printing the next iterations with a counter of number of iterations.
            System.out.println(NEXT_ITERATION + " " + (++count));
            System.out.println(HEADER);


            //Creating the distance vector for next iteration.
            packet = self_address.getHostAddress() + ":";
            for (String t : entries.keySet()) {
                System.out.println(entries.get(t));
                packet += entries.get(t).return_row();
            }
            System.out.println();
        }
    }
}

/*
 * File name: TableEntry.java
 *
 * Author: Atit Gupta (ag3654@rit.edu)
 *
 *
 * Foundation of Computer Networks
 * Project 2: Routing Information Protocol.
 *
 *
 * This code is used to create Entries for the routing table.
 *
 */
class TableEntry {
    String destination, nexthop, subnetmask;
    int cost;

    /**
     * Constructor initializes the table entry values.
     *
     * @param destination dest
     * @param subnetmask  subnet mask
     * @param nexthop     next hop
     * @param cost        cost
     */
    TableEntry(String destination, String subnetmask, String nexthop, int cost) {
        this.destination = destination;
        this.subnetmask = subnetmask;
        this.nexthop = nexthop;
        this.cost = cost;
    }

    /**
     * Used to get the CIDR value of an IP.
     *
     * @param ip Ip
     * @return CIDR
     */

    public String CIDR(String ip) {
        String cidr = "";
        String[] ip1 = ip.split("\\.");
        String[] subnet1 = this.subnetmask.split("\\.");
        for (int i = 0; i < ip1.length; i++) {
            int a = (Integer.parseInt(ip1[i]) & Integer.parseInt(subnet1[i]));
            cidr += String.valueOf(a) + ".";
        }
        return cidr.substring(0, cidr.length() - 1);
    }

    /**
     * Helps in creation of next packet
     *
     * @return string
     */
    public String return_row() {
        return this.destination + "," + this.cost + "," + this.nexthop + ";";
    }

    /**
     * Returns the string representation of the object.
     *
     * @return String
     */

    @Override
    public String toString() {
        return CIDR(this.destination) + " \t " + this.subnetmask + " \t " + this.nexthop + " \t " + this.cost;
    }
}


/*
 * File name: NeighboursDetails.java
 *
 * Author: Atit Gupta (ag3654@rit.edu)
 *
 * Foundation of Computer Networks
 * Project 2: Routing Information Protocol.
 *
 *
 * This code is used to keep track of neighbours.
 *
 */

class NeighboursDetails {
    String name;
    int cost, listening_port;

    /**
     * Consturctor initializes the Neighbour with listening port and cost.
     *
     * @param name           name of the neighbour
     * @param cost           cost to reach
     * @param listening_port port on which it listens
     */
    public NeighboursDetails(String name, int cost, int listening_port) {
        this.name = name;
        this.cost = cost;
        this.listening_port = listening_port;
    }

    /**
     * Returns the InetAddress
     *
     * @return InetAddr
     * @throws Exception
     */
    public InetAddress get_inetaddress() throws Exception {
        return InetAddress.getByName(this.name);
    }

    /**
     * Returns the string representation of the object.
     *
     * @return string
     */
    @Override
    public String toString() {
        return "Neighbour: " + this.name + " cost: " + this.cost + " listens on: " + this.listening_port;
    }
}