import java.io.*;
import java.net.*;
import java.util.Base64;

public class UDPClient {
    private static final int MAX_RETRIES = 5;
    private static final int INITIAL_TIMEOUT = 1000; // 1 second
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

            // Read file list
            File fileList = new File(fileListPath);
            try (BufferedReader reader = new BufferedReader(new FileReader(fileList))) {
                String filename;
                while ((filename = reader.readLine()) != null) {
                    if (filename.trim().isEmpty()) continue;
                    downloadFile(socket, serverAddress, port, filename);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void downloadFile(DatagramSocket socket, InetAddress serverAddress, int port, String filename) {
        try {
            // Send DOWNLOAD request
            String request = "DOWNLOAD " + filename;
            String response = sendAndReceive(socket, request, serverAddress, port, INITIAL_TIMEOUT);
            if (response == null) {
                System.err.println("Failed to receive response for DOWNLOAD request");
                return;
            }

            // Parse response
            String[] parts = response.split(" ");
            if (parts[0].equals("ERR")) {
                System.err.println("Error: " + response);
                return;
            } else if (!parts[0].equals("OK") || parts.length < 6) {
                System.err.println("Invalid response: " + response);
                return;
            }

            long fileSize = Long.parseLong(parts[3]);
            int dataPort = Integer.parseInt(parts[5]);
            System.out.println("Downloading file: " + filename + ", size: " + fileSize + " bytes, data port: " + dataPort);

            // Create file for writing
            try (RandomAccessFile file = new RandomAccessFile(filename, "rw")) {
                long start = 0;
                long end = Math.min(999, fileSize - 1);

                while (start <= end) {
                    // Request data chunk
                    String blockRequest = "FILE " + filename + " GET START " + start + " END " + end;
                    String blockResponse = sendAndReceive(socket, blockRequest, serverAddress, dataPort, INITIAL_TIMEOUT);
                    if (blockResponse == null) {
                        System.err.println("Failed to receive response for data block request");
                        continue;
                    }

                    // Parse data block response
                    String[] blockParts = blockResponse.split(" ");
                    if (blockParts[0].equals("FILE") && blockParts[1].equals(filename) && blockParts[2].equals("OK")) {
                        int dataIndex = 0;
                        for (int i = 0; i < blockParts.length; i++) {
                            if (blockParts[i].equals("DATA")) {
                                dataIndex = i + 1;
                                break;
                            }
                        }

                        if (dataIndex > 0) {
                            StringBuilder dataBuilder = new StringBuilder();
                            for (int i = dataIndex; i < blockParts.length; i++) {
                                dataBuilder.append(blockParts[i]);
                                if (i < blockParts.length - 1) {
                                    dataBuilder.append(" ");
                                }
                            }

                            String base64Data = dataBuilder.toString();
                            byte[] fileData = Base64.getDecoder().decode(base64Data);

                            // Write to file
                            file.seek(start);
                            file.write(fileData);

                            System.out.print(".");
                        }
                    }

                    start = end + 1;
                    end = Math.min(start + 999, fileSize - 1);
                }
            }

            System.out.println("\nDownload completed: " + filename);

            // Send close request
            String closeRequest = "FILE " + filename + " CLOSE";
            String closeResponse = sendAndReceive(socket, closeRequest, serverAddress, dataPort, INITIAL_TIMEOUT);
            if (closeResponse != null && closeResponse.startsWith("FILE " + filename + " CLOSE_OK")) {
                System.out.println("Connection closed successfully");
            } else {
                System.err.println("Failed to close connection properly");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static String sendAndReceive(DatagramSocket socket, String message, InetAddress address, int port, int initialTimeout) throws IOException {
        int timeout = initialTimeout;
        byte[] sendData = message.getBytes();
        byte[] receiveData = new byte[MAX_PACKET_SIZE];

        for (int i = 0; i < MAX_RETRIES; i++) {
            try {
                // Send a message
                DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, address, port);
                socket.send(sendPacket);

                // Set the timeout and receive the response
                socket.setSoTimeout(timeout);
                DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                socket.receive(receivePacket);
                return new String(receivePacket.getData(), 0, receivePacket.getLength());
            } catch (SocketTimeoutException e) {
                System.out.println("Timeout, retrying (" + (i + 1) + "/" + MAX_RETRIES + ")");
                timeout *= 2; // Exponential retreat
            }
        }

        return null; // All retries failed
    }
}