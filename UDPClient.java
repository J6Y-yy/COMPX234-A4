import java.io.*;
import java.net.*;

public class UDPClient {
    private static final int MAX_PACKET_SIZE = 65535;

    public static void main(String[] args) {
        if (args.length != 3) {
            System.err.println("Usage: java UDPClient <hostname> <port> <file_list>");
            System.exit(1);
        }

        String hostname = args[0];
        int port = Integer.parseInt(args[1]);
        String fileListPath = args[2];

        try (DatagramSocket socket = new DatagramSocket()) {
            InetAddress serverAddress = InetAddress.getByName(hostname);
            System.out.println("Connected to server: " + serverAddress);

            // Read file list
            File fileList = new File(fileListPath);
            try (BufferedReader reader = new BufferedReader(new FileReader(fileList))) {
                String filename;
                while ((filename = reader.readLine()) != null) {
                    if (filename.trim().isEmpty()) continue;
                    System.out.println("Requesting file: " + filename);

                    // Send DOWNLOAD request
                    String request = "DOWNLOAD " + filename;
                    byte[] sendData = request.getBytes();
                    DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, serverAddress, port);
                    socket.send(sendPacket);

                    // Receive response
                    byte[] receiveData = new byte[MAX_PACKET_SIZE];
                    DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                    socket.receive(receivePacket);
                    String response = new String(receivePacket.getData(), 0, receivePacket.getLength());
                    System.out.println("Received response: " + response);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}