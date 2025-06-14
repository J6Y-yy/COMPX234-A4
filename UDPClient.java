import java.io.*;
import java.net.*;
import java.util.Base64;

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
            byte[] sendData = request.getBytes();
            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, serverAddress, port);
            socket.send(sendPacket);

            // Receive response
            byte[] receiveData = new byte[MAX_PACKET_SIZE];
            DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
            socket.receive(receivePacket);
            String response = new String(receivePacket.getData(), 0, receivePacket.getLength());

            // Parse response
            String[] parts = response.split(" ");
            if (parts[0].equals("ERR")) {
                System.err.println("Error: " + response);
                return;
            } else if (!parts[0].equals("OK")) {
                System.err.println("Invalid response: " + response);
                return;
            }

            long fileSize = Long.parseLong(parts[3]);
            System.out.println("Downloading file: " + filename + ", size: " + fileSize + " bytes");

            // Create file for writing
            try (RandomAccessFile file = new RandomAccessFile(filename, "rw")) {
                long start = 0;
                long end = Math.min(999, fileSize - 1);

                while (start <= end) {
                    // Request data chunk
                    String blockRequest = "FILE " + filename + " GET START " + start + " END " + end;
                    sendData = blockRequest.getBytes();
                    sendPacket = new DatagramPacket(sendData, sendData.length, serverAddress, port);
                    socket.send(sendPacket);

                    // Receive data chunk
                    socket.receive(receivePacket);
                    String blockResponse = new String(receivePacket.getData(), 0, receivePacket.getLength());

                    // Parse data chunk response
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
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}