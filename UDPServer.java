import java.io.*;
import java.net.*;
import java.util.Base64;
import java.util.Random;

public class UDPServer {
    private static final int MAX_PACKET_SIZE = 65535;
    private static final int DATA_PORT_MIN = 50000;
    private static final int DATA_PORT_MAX = 51000;
    private static final Random random = new Random();

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java UDPServer <port>");
            System.exit(1);
        }

        int port = Integer.parseInt(args[1]);

        try (DatagramSocket welcomeSocket = new DatagramSocket(port)) {
            System.out.println("Server started on port: " + port);

            byte[] receiveData = new byte[MAX_PACKET_SIZE];

            while (true) {
                // Receive request
                DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                welcomeSocket.receive(receivePacket);
                String request = new String(receivePacket.getData(), 0, receivePacket.getLength());

                InetAddress clientAddress = receivePacket.getAddress();
                int clientPort = receivePacket.getPort();

                // Parse request
                String[] parts = request.split(" ");
                if (parts.length >= 2 && parts[0].equals("DOWNLOAD")) {
                    // Create new thread to handle download request
                    new Thread(() -> handleDownloadRequest(welcomeSocket, request, clientAddress, clientPort)).start();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void handleDownloadRequest(DatagramSocket welcomeSocket, String request, InetAddress clientAddress, int clientPort) {
        String[] parts = request.split(" ");
        String filename = parts[1];
        File file = new File(filename);

        try {
            if (file.exists()) {
                // Select random data port
                int dataPort = DATA_PORT_MIN + random.nextInt(DATA_PORT_MAX - DATA_PORT_MIN + 1);
                
                // Send OK response
                String response = "OK " + filename + " SIZE " + file.length() + " PORT " + dataPort;
                byte[] sendData = response.getBytes();
                DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, clientAddress, clientPort);
                welcomeSocket.send(sendPacket);
                System.out.println("Sent OK response for: " + filename + " on port " + dataPort);

                // Create new thread to handle data transmission
                new Thread(() -> handleFileTransmission(filename, clientAddress, clientPort, dataPort)).start();
            } else {
                String response = "ERR " + filename + " NOT_FOUND";
                byte[] sendData = response.getBytes();
                DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, clientAddress, clientPort);
                welcomeSocket.send(sendPacket);
                System.out.println("Sent ERR response for: " + filename);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void handleFileTransmission(String filename, InetAddress clientAddress, int clientPort, int dataPort) {
        try (DatagramSocket dataSocket = new DatagramSocket(dataPort)) {
            System.out.println("Data thread started on port: " + dataPort + " for file: " + filename);

            byte[] receiveData = new byte[MAX_PACKET_SIZE];

            while (true) {
                // Receive data request
                DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                dataSocket.receive(receivePacket);
                String request = new String(receivePacket.getData(), 0, receivePacket.getLength());

                // Parse request
                String[] parts = request.split(" ");
                if (parts.length >= 7 && parts[0].equals("FILE") && parts[1].equals(filename) && parts[2].equals("GET")) {
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
                        dataSocket.send(sendPacket);

                        System.out.println("Sent data block: " + filename + " [" + start + "-" + end + "]");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}