import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.*;
import java.util.Scanner;
import java.net.InetAddress;
import java.lang.Thread;

public class Server {

  //Utilizing public variables so all threads and methods can access them as needed.
  //I did not utilize synchronize because all threads sleep when not in use.

  public static DatagramSocket serverSocket = null;
  public static FileHandler filehandler;
  public static Thread receiveRequest;
  public static Thread sendData;
  public static Thread receiveACKs;
  public static int window = 0;
  public static int count = -1;
  public static int chunks = 0;
  public static boolean ack = true;
  public static boolean pendingnewfile = true;

  public static int client_port = 0;
  public static InetAddress client_ip = null;

  public static void main(String[] args) {

    String inputfilename = "";
    int server_port = 0;
    
    //Reading server.in and establishing all parameters.
    try {

      DataPacket datap;
      inputfilename = "server.in";
      File input = new File(inputfilename);
      Scanner inputreader = new Scanner(input);
      String server_port_tmp = inputreader.nextLine();
      server_port = Integer.parseInt(server_port_tmp);
      System.out.println(server_port);
      String window_tmp = inputreader.nextLine();
      window = Integer.parseInt(window_tmp);
      System.out.println(window);

    } catch (FileNotFoundException e) {
      System.out.println("Error 0: File " + inputfilename + " not found.\nFull error details:");
      e.printStackTrace();
      System.out.println("Shutting down the server.");
      exit();
    }

    //Nested try/catch doing all the work of the DatagramSocket and threads.
    try {
      serverSocket = new DatagramSocket(server_port);
      System.out.println("The server has been initiated and is ready.");

      //Initial thread set up to receive a request for a file from a client. It sleeps while file is being sent but operates in between.
      receiveRequest = new Thread(new Runnable() {
        
        @Override
        public void run() {

          while(true) {

            if(pendingnewfile) {

              try {
                serverSocket.setSoTimeout(0); //Setting timeout to 0 so it is effectively infinity while we wait for a client to send initial request.
              } catch (SocketException e) {
                System.out.println("Error 13: Attempt to set Socket Timeout time to infinity failed.");
                e.printStackTrace();
              }
              
              byte[] buf = new byte[1000];
              DatagramPacket receivepacket = new DatagramPacket(buf, buf.length);

              try {
                serverSocket.receive(receivepacket);
                client_ip = receivepacket.getAddress();
                client_port = receivepacket.getPort();
              } catch (IOException e) {
                System.out.println("Error 10: Attempt to receive initial packet failed.");
                e.printStackTrace();
              }              

              //First obtaining the file name.
              String filename = new String(receivepacket.getData());

              filename = filename.substring(0,receivepacket.getLength());

              System.out.println("Client is requesting file (" + filename + ").");

              //Initiating the FileHandler
              try {
                filehandler = new FileHandler(filename);
                chunks = filehandler.no_of_chunks;
                System.out.println("Number of chunks to send: " + chunks);
              } catch (IOException e) {
                System.out.println("Error 3: Attempt to read file failed.");
                e.printStackTrace();
              }

              if(chunks > 0) {
                try {
                  serverSocket.setSoTimeout(5000);
                } catch (SocketException e) {
                  System.out.println("Error 14: Attempt to set Socket Timeout time to 5 seconds failed.");
                  e.printStackTrace();
                }
                count = -1;
                ack = true;
                pendingnewfile = false;
              }
            
            } else {
              try {
                java.lang.Thread.sleep(500);
              } catch (Exception e) {
                System.out.println("Error 9: Attempt to sleep failed.");
                System.out.println(e);
              }
            }

          }
            
        }
      });
      receiveRequest.start();

      sendData = new Thread(new Runnable() {
        
        @Override
        public void run() {
          
          while (true) {

            if(!pendingnewfile) {
              short seqno = (short) count;

              byte[] tosend = new byte[1000];
              DatagramPacket sendpacket = new DatagramPacket(tosend, tosend.length);
              sendpacket.setAddress(client_ip);
              sendpacket.setPort(client_port);
                        
              if(ack) {

                if(count == -1) {
                  String chunkstr = "Expected_chunks:" + chunks;
                  tosend = chunkstr.getBytes();
                } else {
                  tosend = filehandler.readChunk(count); 
                }

                DataPacket datatosend = new DataPacket(seqno, tosend);
                System.out.println("Sending packet: " + datatosend.toString());
                           
                try {
                  byte[] datatosendbytes = datatosend.toBytes();
                  sendpacket.setData(datatosendbytes);
                  sendpacket.setLength(datatosendbytes.length);
                  serverSocket.send(sendpacket);
                } catch (IOException e) {
                  e.printStackTrace();
                }

                ack = false;

              } else {
                try {
                  java.lang.Thread.sleep(500);
                } catch (Exception e) {
                  System.out.println("Error 2: Attempt to sleep failed.");
                  System.out.println(e);
                }
              }

              if(count > chunks) {
                String end = "End of file.";
                byte[] endbytes = end.getBytes();
                DataPacket enddp = new DataPacket(++seqno,endbytes);
                System.out.println(enddp.toString());
                try {
                  sendpacket.setData(enddp.toBytes());
                  sendpacket.setLength(sendpacket.getData().length);
                  serverSocket.send(sendpacket);
                } catch (IOException e) {
                  e.printStackTrace();
                }

                pendingnewfile = true;
                System.out.println("Server ready to receive new file request.");
              }
            } else {
              try {
                java.lang.Thread.sleep(500);
              } catch (Exception e) {
                System.out.println("Error 11: Attempt to sleep failed.");
                System.out.println(e);
              }
            }
          }

        }
      });
      sendData.start();

      receiveACKs = new Thread(new Runnable() {
        
        @Override
        public void run() {

          while (true) {

            if(!pendingnewfile) {

              if(!ack) {

                try {
                  byte[] buf = new byte[1000];
                  DatagramPacket receiveACK = new DatagramPacket(buf,buf.length);
                  try {
                    serverSocket.receive(receiveACK);
                    byte[] ackdata = receiveACK.getData();
                    AckPacket receivedACK = new AckPacket(ackdata);
                    System.out.println("Received ACK: " + receivedACK.toString());
                    if(receivedACK.ackno == count) {
                      count = count + window;
                    }
                  } catch (SocketTimeoutException e) {
                    e.printStackTrace();
                  }
                  
                  ack = true;                  

                } catch (IOException e) {
                  e.printStackTrace();
                }
              } else {
                try {
                  java.lang.Thread.sleep(500);
                } catch (Exception e) {
                  System.out.println("Error 4: Attempt to sleep failed.");
                  System.out.println(e);
                }
              }
            } else {
              try {
                  java.lang.Thread.sleep(500);
                } catch (Exception e) {
                  System.out.println("Error 12: Attempt to sleep failed.");
                  System.out.println(e);
                }
            }
            
          }
        }
      });
      receiveACKs.start();
      
    } catch (IOException e) {
      System.out.println("Error 1: Attempt to establish DatagramSocket failed.");
      e.printStackTrace();
    }
  }

  public static void exit() {

    System.out.println("Closing connection.");

    if(serverSocket != null)
      serverSocket.close();
    else
      System.out.println("No sockets initiated.");

    if(sendData != null)
      sendData.interrupt();
    else 
      System.out.println("Thread sendData not initiated.");

    if(receiveACKs != null)
      receiveACKs.interrupt();
    else 
      System.out.println("Thread receiveACKs not initiated.");

    if(receiveRequest != null)
      receiveRequest.interrupt();
    else 
      System.out.println("Thread receiveRequest not initiated.");

    System.out.println("Server shutting down.");

    System.exit(0);
  }
}