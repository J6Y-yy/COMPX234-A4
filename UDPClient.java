import java.io.*;
import java.net.*;

public class UDPClient {
    public static void main(String[] args) {
        if (args.length != 3) {
            System.err.println("Usage: java UDPClient <hostname> <port> <file_list>");
            System.exit(1);
        }

        String hostname = args[0];
        int port = Integer.parseInt(args[1]);
        String fileListPath = args[2];

        System.out.println("Client started with arguments: " + hostname + ", " + port + ", " + fileListPath);
        
    }
}