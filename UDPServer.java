import java.io.*;
import java.net.*;

public class UDPServer {
    private static final int MAX_PACKET_SIZE = 65535;

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java UDPServer <port>");
            System.exit(1);
        }

        int port = Integer.parseInt(args[1]);

        try (DatagramSocket socket = new DatagramSocket(port)) {
            System.out.println("Server started on port: " + port);

            byte[] receiveData = new byte[MAX_PACKET_SIZE];

            while (true) {
                // Receive request
                DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                socket.receive(receivePacket);
                String request = new String(receivePacket.getData(), 0, receivePacket.getLength());
                System.out.println("Received request: " + request);

                InetAddress clientAddress = receivePacket.getAddress();
                int clientPort = receivePacket.getPort();

                // Parse request
                String[] parts = request.split(" ");
                if (parts.length >= 2 && parts[0].equals("DOWNLOAD")) {
                    String filename = parts[1];
                    File file = new File(filename);

                    // Check if file exists
                    if (file.exists()) {
                        String response = "OK " + filename + " SIZE " + file.length();
                        byte[] sendData = response.getBytes();
                        DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, clientAddress, clientPort);
                        socket.send(sendPacket);
                    } else {
                        String response = "ERR " + filename + " NOT_FOUND";
                        byte[] sendData = response.getBytes();
                        DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, clientAddress, clientPort);
                        socket.send(sendPacket);
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}