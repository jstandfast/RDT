import java.io.File;
import java.io.FileWriter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.DatagramPacket;
import java.util.Scanner;
import java.net.InetAddress;
import java.lang.Thread;
import java.lang.StringBuilder;

public class Client {

  public static DatagramSocket clientSocket = null;
  public static Thread receiveData;
  public static Thread sendACKs;
  public static boolean ack = true;
  public static int window = 0;
  public static int numchunks = 0;

  public static void main(String[] args) {

    String inputfilename = "";
    InetAddress server_ip = null;
    int client_port = 0;
    int server_port = 0;
    String filename = "";
    DatagramPacket packet;
    DatagramPacket ackdgp;
    DataPacket datap;
    StringBuilder strbuild = new StringBuilder();

    try {

      inputfilename = "client.in";
      File input = new File(inputfilename);
      Scanner inputreader = new Scanner(input);
      String server_ip_tmp = inputreader.nextLine();

      try {
        server_ip = InetAddress.getByName(server_ip_tmp);
        System.out.println(server_ip);
      } catch (IOException e) {
        e.printStackTrace();
      }
      
      String client_port_tmp = inputreader.nextLine();
      client_port = Integer.parseInt(client_port_tmp);
      System.out.println(client_port);
      String server_port_tmp = inputreader.nextLine();
      server_port = Integer.parseInt(server_port_tmp);
      System.out.println(server_port);
      filename = inputreader.nextLine();
      System.out.println(filename);
      String window_tmp = inputreader.nextLine();
      window = Integer.parseInt(window_tmp);
      System.out.println(window);

    } catch (FileNotFoundException e) {
      System.out.println("File " + inputfilename + " not found.\nFull error details:");
      e.printStackTrace();
      System.out.println("Shutting down the client.");
      exit();
    }

    try {

      clientSocket = new DatagramSocket(client_port);
      byte[] buf = filename.getBytes();
      System.out.println("Requesting file " + new String(buf));
      packet = new DatagramPacket(buf, buf.length, server_ip, server_port);
      ackdgp = new DatagramPacket(buf, buf.length, server_ip, server_port);
      clientSocket.send(packet);

      receiveData = new Thread(new Runnable() {
          
        @Override
        public void run() {

          int count = -1;

          while (ack) {
            
            try {
              byte[] buf = new byte[1000];
              DatagramPacket receivepacket = new DatagramPacket(buf,buf.length);
              clientSocket.receive(receivepacket);
              byte[] packetdata = receivepacket.getData();
              DataPacket receiveddp = new DataPacket(packetdata);
              System.out.println(receiveddp.toString());
              String actualdata = new String(receiveddp.data);

              if(count == -1 && !actualdata.equals("End of file.")) {
                String expectedchunks = actualdata.substring(0,16);
                if(expectedchunks.equals("Expected_chunks:")) {
                  String chunkstr = actualdata.substring(16,actualdata.length());
                  numchunks = Integer.parseInt(chunkstr);
                  System.out.println("Expecting " + numchunks + " chunks");
                }
              } else {
                strbuild.append(actualdata);
              }

              if(receiveddp.seqno == count) {
                AckPacket ackpacket = new AckPacket(count);
                byte[] ackpacketbytes = ackpacket.toBytes();
                ackdgp.setData(ackpacketbytes);
                ackdgp.setLength(ackdgp.getData().length);
                clientSocket.send(ackdgp);
                count = count + window;
              }

              if(actualdata.equals("End of file.") || count > numchunks)
                ack = false;

              if(!ack) {
                writeFile(strbuild);
              }

            } catch (IOException e) {
              e.printStackTrace();
            }
            
          }
        }
      });
      receiveData.start();

    } catch (IOException e) {
      System.out.println("Error 2: Client could not connect to server.");
      exit();
    }

  }

  public static void exit() {
    System.out.println("Closing connection.");

    if(clientSocket != null || !clientSocket.isClosed())
      clientSocket.close();
    else
      System.out.println("No sockets initiated.");

    if(receiveData != null)
      receiveData.interrupt();
    else 
      System.out.println("No threads initiated.");

    System.out.println("Client shutting down.");

    System.exit(0);

  }

  public static void writeFile(StringBuilder strbuild) {

    try {
        String newfile = "result.txt";
        FileWriter fwriter = new FileWriter(newfile);
        fwriter.write(strbuild.toString());
        fwriter.close();
        System.out.println("File retrieved from server and written to " + newfile);
      } catch (IOException e) {
        System.out.println("Error 6: New file could not be written.");
        e.printStackTrace();
      }

    exit();

  }
}
