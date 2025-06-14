import java.io.*;
import java.net.*;

public class UDPServer {
    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java UDPServer <port>");
            System.exit(1);
        }

        int port = Integer.parseInt(args[1]);
        System.out.println("Server started on port: " + port);
        
    }
}