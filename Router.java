/*
 * File name: Router.java
 *
 *
 * This is the main class which should be executed on the server. It initializes all the routers
 * and takes input of various neighbours.
 *
 */

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Scanner;

public class Router {

    //adding the ip address of all the different routers
    static String QUEEG = "129.21.30.37";
    static String COMET = "129.21.34.80";
    static String RHEA = "129.21.37.49";
    static String GLADOS = "129.21.22.196";

    /**
     * This is the main method which initializes the routers mentioned in the above strings.
     * @param args None
     * @throws Exception
     */
    public static void main(String[] args) throws Exception{
        Scanner sc = new Scanner(System.in);

        //creating a hashmap with all the host names
        HashMap<Integer,String> host_names = new HashMap<>();
        host_names.put(1,QUEEG);
        host_names.put(2,COMET);
        host_names.put(3,RHEA);
        host_names.put(4,GLADOS);

        //System.out.println("Enter the number of routers you want to turn on: ");

        //need to add validation
        int no_of_routers = sc.nextInt();

        //hashmap keeping track of all the active routers
        HashMap<Integer,VirtualRouters> active_routers = new HashMap<>();


        //System.out.println("Select the routers to turn on: ");
        for (int i = 1; i <= host_names.size(); i++) {
            InetAddress address = InetAddress.getByName(host_names.get(i));
            //System.out.println(i + ". " + address.getHostName() + " : " +host_names.get(i));
        }

        // need to add validation
        for (int i = 0; i < no_of_routers; i++) {
            int n = sc.nextInt();
            //System.out.println("Enter the sending port for this router :");
            active_routers.put(n, new VirtualRouters(host_names.get(n), sc.nextInt()));
        }

        //sending UDP packets to switch on the routers
        DatagramSocket ds = new DatagramSocket();

        byte[] message = new byte[1024];
        String active_hosts = "";

        //creating the list of active hosts to send as a packet to the router
        for (Integer i: active_routers.keySet()) {
            active_hosts += active_routers.get(i).name + ";";
        }


        for (Integer i: active_routers.keySet()) {
            InetAddress ia = InetAddress.getByName(active_routers.get(i).name);
            message = active_hosts.getBytes();
            DatagramPacket outgoing_packet = new DatagramPacket(message, message.length, ia, active_routers.get(i).listening_port);

            ds.send(outgoing_packet);
        }

        //adding neighbours
        for (Integer i: active_routers.keySet()) {
            //System.out.println("Please enter the number of neighbours of :" + active_routers.get(i).name);
            int no_of_neighbours = sc.nextInt();
            //System.out.println("Please enter the neighbour router number and costs for :" + active_routers.get(i).name);
            for (int j = 0; j < no_of_neighbours; j++) {
                active_routers.get(i).add_neighbours(active_routers.get(sc.nextInt()), sc.nextInt());
            }
        }

        for (Integer i: active_routers.keySet()){
            byte[] neighbour_list = active_routers.get(i).createPacket().getBytes();
            InetAddress ia = InetAddress.getByName(active_routers.get(i).name);
            DatagramPacket outgoing = new DatagramPacket(neighbour_list, neighbour_list.length, ia, active_routers.get(i).listening_port);
            ds.send(outgoing);
        }
    }
}

/**
 * VirtualRouters.java
 *
 * This class is used to create routers keeping track of all neighbours.
 *
 */
class VirtualRouters{
    String name;
    HashMap<VirtualRouters, Integer> neighbours;
    int listening_port;

    /**
     * Constructor which initializes values.
     * @param name Name of the router
     * @param listening_port port on which this router will receive messages.
     */
    public VirtualRouters(String name, int listening_port){
        this.name = name;
        this.listening_port = listening_port;
        this.neighbours = new HashMap<>();
    }

    /**
     * Add neighbours.
     * @param router Neighbour
     * @param cost cost to reach the neighbour.
     */
    public void add_neighbours(VirtualRouters router, int cost){
        neighbours.put(router, cost);
    }


    /**
     * Creates a packet to transmit.
     * @return packet
     */
    public String createPacket(){
        String packet = "";

        for (VirtualRouters v: neighbours.keySet()) {
            packet += v.name + " :"+neighbours.get(v) + ":" +v.listening_port+ ",";
        }
        return packet;
    }

    /**
     * Returns the string representation of object.,
     * @return
     */
    public String toString(){
        return "Router Name: " + this.name + " Status: " ;
    }
}