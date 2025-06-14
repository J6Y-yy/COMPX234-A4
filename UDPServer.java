import java.io.*;
import java.net.*;
import java.util.Base64;

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

                InetAddress clientAddress = receivePacket.getAddress();
                int clientPort = receivePacket.getPort();

                // Parse request
                String[] parts = request.split(" ");
                if (parts.length >= 2 && parts[0].equals("DOWNLOAD")) {
                    handleDownloadRequest(socket, request, clientAddress, clientPort);
                } else if (parts.length >= 5 && parts[0].equals("FILE") && parts[2].equals("GET")) {
                    handleFileRequest(socket, request, clientAddress, clientPort);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void handleDownloadRequest(DatagramSocket socket, String request, InetAddress clientAddress, int clientPort) {
        String[] parts = request.split(" ");
        String filename = parts[1];
        File file = new File(filename);

        try {
            if (file.exists()) {
                String response = "OK " + filename + " SIZE " + file.length();
                byte[] sendData = response.getBytes();
                DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, clientAddress, clientPort);
                socket.send(sendPacket);
                System.out.println("Sent OK response for: " + filename);
            } else {
                String response = "ERR " + filename + " NOT_FOUND";
                byte[] sendData = response.getBytes();
                DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, clientAddress, clientPort);
                socket.send(sendPacket);
                System.out.println("Sent ERR response for: " + filename);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void handleFileRequest(DatagramSocket socket, String request, InetAddress clientAddress, int clientPort) {
        String[] parts = request.split(" ");
        String filename = parts[1];
        long start = Long.parseLong(parts[4]);
        long end = Long.parseLong(parts[6]);

        try (RandomAccessFile file = new RandomAccessFile(filename, "r")) {
            int length = (int) (end - start + 1);
            byte[] data = new byte[length];
            file.seek(start);
            file.readFully(data);

            // Encode to Base64
            String base64Data = Base64.getEncoder().encodeToString(data);
            String response = "FILE " + filename + " OK START " + start + " END " + end + " DATA " + base64Data;
            byte[] sendData = response.getBytes();
            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, clientAddress, clientPort);
            socket.send(sendPacket);

            System.out.println("Sent data block: " + filename + " [" + start + "-" + end + "]");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}