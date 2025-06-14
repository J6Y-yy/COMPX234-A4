import java.io.*;
import java.net.*;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;

public class UDPClient {
    private static final int MAX_RETRIES = 5;
    private static final int INITIAL_TIMEOUT = 1000; // Initial timeout in milliseconds
    private static final int MAX_PACKET_SIZE = 65535; // Maximum UDP packet size

    public static void main(String[] args) {
        if (args.length != 3) {
            System.err.println("Usage: java UDPClient <hostname> <port> <file_list>");
            System.exit(1);
        }

        String hostname = args[0];
        int serverPort = Integer.parseInt(args[1]);
        String fileListPath = args[2];

        try (DatagramSocket socket = new DatagramSocket()) {
            InetAddress serverAddress = InetAddress.getByName(hostname);
            File fileList = new File(fileListPath);
            BufferedReader reader = new BufferedReader(new FileReader(fileList));
            String filename;

            while ((filename = reader.readLine()) != null) {
                if (filename.trim().isEmpty()) continue;
                downloadFile(socket, serverAddress, serverPort, filename.trim());
            }

            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void downloadFile(DatagramSocket socket, InetAddress serverAddress, int serverPort, String filename) {
        try {
            System.out.println("Starting download of file: " + filename);

            // Send DOWNLOAD request
            String downloadMsg = "DOWNLOAD " + filename;
            byte[] sendData = downloadMsg.getBytes();
            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, serverAddress, serverPort);
            DatagramPacket receivePacket = new DatagramPacket(new byte[MAX_PACKET_SIZE], MAX_PACKET_SIZE);

            String response = sendAndReceive(socket, sendPacket, receivePacket, INITIAL_TIMEOUT);
            if (response == null) {
                System.err.println("Download failed: No response from server");
                return;
            }

            // Process response
            String[] parts = response.split(" ");
            if (parts[0].equals("ERR")) {
                System.err.println("File not found: " + filename);
                return;
            } else if (!parts[0].equals("OK") || parts.length < 6) {
                System.err.println("Invalid response: " + response);
                return;
            }

            // Parse file size and data port
            long fileSize = Long.parseLong(parts[3]);
            int dataPort = Integer.parseInt(parts[5]);
            System.out.println("File size: " + fileSize + " bytes, Data port: " + dataPort);

            // Create file for writing
            FileOutputStream fos = new FileOutputStream(filename);
            BufferedOutputStream bos = new BufferedOutputStream(fos);
            AtomicInteger progress = new AtomicInteger(0);

            // Download file in chunks
            long start = 0;
            long end = Math.min(999, fileSize - 1);
            long totalReceived = 0;

            while (totalReceived < fileSize) {
                String fileRequest = "FILE " + filename + " GET START " + start + " END " + end;
                sendData = fileRequest.getBytes();
                sendPacket = new DatagramPacket(sendData, sendData.length, serverAddress, dataPort);
                receivePacket = new DatagramPacket(new byte[MAX_PACKET_SIZE], MAX_PACKET_SIZE);

                response = sendAndReceive(socket, sendPacket, receivePacket, INITIAL_TIMEOUT);
                if (response == null) {
                    System.err.println("Download failed: Could not retrieve data chunk");
                    bos.close();
                    new File(filename).delete();
                    return;
                }

                parts = response.split(" ");
                if (!parts[0].equals("FILE") || !parts[1].equals(filename) || !parts[2].equals("OK")) {
                    System.err.println("Invalid data response: " + response);
                    continue;
                }

                // Parse data chunk
                int dataIndex = 0;
                for (int i = 0; i < parts.length; i++) {
                    if (parts[i].equals("DATA")) {
                        dataIndex = i + 1;
                        break;
                    }
                }

                if (dataIndex == 0) {
                    System.err.println("Missing DATA field in data chunk: " + response);
                    continue;
                }

                String base64Data = String.join(" ", parts, dataIndex, parts.length);
                byte[] fileData = Base64.getDecoder().decode(base64Data);
                bos.write(fileData);
                bos.flush();

                totalReceived += fileData.length;
                start = end + 1;
                end = Math.min(start + 999, fileSize - 1);

                // Show progress
                int currentProgress = (int) (totalReceived * 100.0 / fileSize);
                if (currentProgress > progress.getAndSet(currentProgress)) {
                    System.out.print(".");
                }
            }

            System.out.println("\nFile download completed: " + filename);

            // Send close request
            String closeMsg = "FILE " + filename + " CLOSE";
            sendData = closeMsg.getBytes();
            sendPacket = new DatagramPacket(sendData, sendData.length, serverAddress, dataPort);
            receivePacket = new DatagramPacket(new byte[MAX_PACKET_SIZE], MAX_PACKET_SIZE);

            response = sendAndReceive(socket, sendPacket, receivePacket, INITIAL_TIMEOUT);
            if (response != null && response.startsWith("FILE " + filename + " CLOSE_OK")) {
                System.out.println("Close connection confirmation received");
            } else {
                System.err.println("Close connection confirmation failed");
            }

            bos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static String sendAndReceive(DatagramSocket socket, DatagramPacket sendPacket, DatagramPacket receivePacket, int initialTimeout) throws SocketException {
        int timeout = initialTimeout;
        int retries = 0;

        while (retries < MAX_RETRIES) {
            try {
                socket.setSoTimeout(timeout);
                socket.send(sendPacket);
                socket.receive(receivePacket);
                return new String(receivePacket.getData(), 0, receivePacket.getLength()).trim();
            } catch (SocketTimeoutException e) {
                retries++;
                System.out.println("Timeout, retrying " + retries + "/" + MAX_RETRIES + ", timeout: " + timeout + "ms");
                timeout *= 2; // Double timeout
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        }

        System.out.println("Max retries reached, giving up");
        return null;
    }
}    