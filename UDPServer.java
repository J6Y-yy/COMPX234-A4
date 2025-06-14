import java.io.*;
import java.net.*;
import java.util.Base64;
import java.util.Random;

public class UDPServer {
    private static final int DATA_PORT_MIN = 50000;
    private static final int DATA_PORT_MAX = 51000;
    private static final int MAX_PACKET_SIZE = 65535; // Maximum UDP packet size
    private static final Random random = new Random();

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java UDPServer <port>");
            System.exit(1);
        }

        int serverPort = Integer.parseInt(args[0]);

        try (DatagramSocket welcomeSocket = new DatagramSocket(serverPort)) {
            System.out.println("Server started, listening on port: " + serverPort);

            while (true) {
                byte[] receiveData = new byte[MAX_PACKET_SIZE];
                DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                welcomeSocket.receive(receivePacket);

                String clientRequest = new String(receivePacket.getData(), 0, receivePacket.getLength()).trim();
                System.out.println("Received request: " + clientRequest);

                InetAddress clientAddress = receivePacket.getAddress();
                int clientPort = receivePacket.getPort();

                // Handle download request
                new Thread(() -> handleDownloadRequest(welcomeSocket, clientRequest, clientAddress, clientPort)).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void handleDownloadRequest(DatagramSocket welcomeSocket, String request, InetAddress clientAddress, int clientPort) {
        String[] parts = request.split(" ");
        if (!parts[0].equals("DOWNLOAD") || parts.length < 2) {
            System.out.println("Invalid request: " + request);
            return;
        }

        String filename = parts[1];
        File file = new File(filename);

        if (!file.exists()) {
            // File not found, send error response
            String errorResponse = "ERR " + filename + " NOT_FOUND";
            sendResponse(welcomeSocket, errorResponse, clientAddress, clientPort);
            return;
        }

        // File exists, create new thread to handle data transmission
        new Thread(() -> handleFileTransmission(filename, clientAddress, clientPort)).start();

        // Send OK response
        long fileSize = file.length();
        int dataPort = getRandomDataPort();
        String okResponse = "OK " + filename + " SIZE " + fileSize + " PORT " + dataPort;
        sendResponse(welcomeSocket, okResponse, clientAddress, clientPort);

        System.out.println("Sent OK response, file: " + filename + ", size: " + fileSize + ", data port: " + dataPort);
    }

    private static void handleFileTransmission(String filename, InetAddress clientAddress, int clientPort) {
        int dataPort = getRandomDataPort();
        try (DatagramSocket dataSocket = new DatagramSocket(dataPort)) {
            System.out.println("Data thread started, port: " + dataPort + ", file: " + filename);

            File file = new File(filename);
            RandomAccessFile raf = new RandomAccessFile(file, "r");
            long fileSize = file.length();

            byte[] receiveData = new byte[MAX_PACKET_SIZE];
            DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);

            while (true) {
                try {
                    dataSocket.receive(receivePacket);
                    String clientRequest = new String(receivePacket.getData(), 0, receivePacket.getLength()).trim();
                    System.out.println("Data thread received request: " + clientRequest);

                    String[] parts = clientRequest.split(" ");
                    if (!parts[0].equals("FILE") || !parts[1].equals(filename)) {
                        System.out.println("Invalid file request: " + clientRequest);
                        continue;
                    }

                    if (parts[2].equals("CLOSE")) {
                        // Close request
                        String closeResponse = "FILE " + filename + " CLOSE_OK";
                        byte[] sendData = closeResponse.getBytes();
                        DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, clientAddress, clientPort);
                        dataSocket.send(sendPacket);
                        System.out.println("Sent close confirmation");
                        break;
                    } else if (parts[2].equals("GET")) {
                        // Data request
                        long start = Long.parseLong(parts[4]);
                        long end = Long.parseLong(parts[6]);
                        int length = (int) (end - start + 1);

                        if (start < 0 || end >= fileSize || start > end) {
                            System.out.println("Invalid byte range: " + start + " - " + end);
                            continue;
                        }

                        // Read file data
                        raf.seek(start);
                        byte[] fileData = new byte[length];
                        int bytesRead = raf.read(fileData);

                        if (bytesRead != length) {
                            System.out.println("Error reading data, expected: " + length + ", actual: " + bytesRead);
                            continue;
                        }

                        // Encode to Base64
                        String base64Data = Base64.getEncoder().encodeToString(fileData);
                        String response = "FILE " + filename + " OK START " + start + " END " + end + " DATA " + base64Data;
                        byte[] sendData = response.getBytes();
                        DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, clientAddress, clientPort);
                        dataSocket.send(sendPacket);

                        System.out.println("Sent data block: " + start + " - " + end);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    break;
                }
            }

            raf.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void sendResponse(DatagramSocket socket, String response, InetAddress address, int port) {
        try {
            byte[] sendData = response.getBytes();
            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, address, port);
            socket.send(sendPacket);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static int getRandomDataPort() {
        return DATA_PORT_MIN + random.nextInt(DATA_PORT_MAX - DATA_PORT_MIN + 1);
    }
}    