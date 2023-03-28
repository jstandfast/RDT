//package stop_wait;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Random;

/*
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
*/

public class Pipe {

	InetAddress client_ip;
	InetAddress server_ip;
	int client_port;
	int server_port;
	String filename;
	int port_no;
	double prob;
	long seed;

	DatagramSocket client_socket;
	DatagramSocket server_socket;

	Pipe() throws FileNotFoundException, UnknownHostException {

		/*

		**NOTE: I couldn't get JSON to work here. Getting error: package javax.json does not exist.
		Resources online all talk about classpath, but I am not sure where exactly to put which jar
		files and what to put as the classpath in order to get it to work.

		JsonReader jsonReader = Json.createReader(new FileReader("pipe.json"));
		JsonObject obj = jsonReader.readObject();

		String client_ip_tmp = obj.getString("client_ip");
		String server_ip_tmp = obj.getString("server_ip");
		client_port = obj.getInt("client_port");
		server_port = obj.getInt("server_port");
		port_no = obj.getInt("port_no");

		prob = obj.getJsonNumber("prob").doubleValue();
		seed = obj.getJsonNumber("seed").longValue();

		jsonReader.close();
		client_ip = InetAddress.getByName(client_ip_tmp);
		server_ip = InetAddress.getByName(server_ip_tmp);

		*/

		String client_ip_tmp = "127.0.0.1";
		String server_ip_tmp = "127.0.0.1";
		client_port = 5000;
		server_port = 6000;
		port_no = 8000;

		prob = 0.4;
		seed = 100;

		client_ip = InetAddress.getByName(client_ip_tmp);
		server_ip = InetAddress.getByName(server_ip_tmp);

		System.out.println(client_ip + "\n"
							+ client_port + "\n"
							+ server_ip + "\n"
							+ server_port + "\n"
							+ port_no + "\n"
							+ prob + "\n"
							+ seed + "\n"
							+ "Pipe is ready.");
	}

	void run() throws IOException {

		DatagramSocket socket = new DatagramSocket(port_no);
		Random r = new Random(seed);

		byte[] buf = new byte[1000];
		for (;;) {
			DatagramPacket p = new DatagramPacket(buf, buf.length);
			socket.receive(p);
			System.out.println("Received a packet");

			InetAddress dst = p.getAddress();
			int port = p.getPort();

			if (dst.equals(client_ip) && port == client_port) {

				// forward the packet to the server
				p.setAddress(server_ip);
				p.setPort(server_port);

				dst = p.getAddress();
				port = p.getPort();

				System.out.println("received from client," + dst + "  " + port + "  " + "   forward to server");
				socket.send(p);
			} else if (dst.equals(server_ip) && port == server_port) {

				double pr = r.nextDouble();
				System.out.println(">>>" + pr + "  " + prob);
				// forward the packet to the server
				if (pr > prob) {
					p.setAddress(client_ip);
					p.setPort(client_port);
				
					dst = p.getAddress();
					port = p.getPort();
					// System.out.println(dst + " " + port);

					System.out.println("received from server," + dst + "  " + port + "  " + "   forward to client");
					socket.send(p);
				} else {
					System.out.println("received from server, dropped");

				}
			} else {
				System.out.println("--------------");
			}
		}

	}

	public static void main(String[] args) throws IOException {
		/*
		if (args.length != 0) {
			System.out.println("Usage: java Pipe\n The program using configuration file pipe.json");

			return;
		}
		*/

		Pipe p = new Pipe();
		p.run();

	}
}
