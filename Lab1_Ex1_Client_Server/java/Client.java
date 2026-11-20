/*
 * 23CSE312 - Distributed Systems | Lab Exercise 1 - Program 1
 * Client-Server Communication (bidirectional message exchange) - CLIENT
 *
 * Roll No : CH.SC.U4CSE24035
 *
 * Compile : javac Client.java
 * Run     : java Client <server_ip> [port]    (default port 5000)
 * Stop    : type "bye" (either side can end the chat)
 */
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Scanner;

public class Client {
    static final String ROLL_NO = "CH.SC.U4CSE24035";
    static final int DEFAULT_PORT = 5000;

    static String now() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
    }

    /** LAN IP of this machine (UDP "connect" sends no packet). */
    static String getLocalIp() {
        try (DatagramSocket s = new DatagramSocket()) {
            s.connect(InetAddress.getByName("8.8.8.8"), 80);
            return s.getLocalAddress().getHostAddress();
        } catch (Exception e) {
            return "127.0.0.1";
        }
    }

    static void banner(String role, int port) throws UnknownHostException {
        System.out.println("============================================================");
        System.out.println(" 23CSE312 Lab 1.1 - Client-Server Communication (" + role + ")");
        System.out.println(" Roll No  : " + ROLL_NO);
        System.out.println(" Hostname : " + InetAddress.getLocalHost().getHostName());
        System.out.println(" Local IP : " + getLocalIp() + "   Port: " + port);
        System.out.println(" Started  : " + LocalDateTime.now().format(
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        System.out.println("============================================================");
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.out.println("Usage: java Client <server_ip> [port]");
            return;
        }
        String serverIp = args[0];
        int port = args.length > 1 ? Integer.parseInt(args[1]) : DEFAULT_PORT;
        banner("CLIENT", port);

        System.out.println("[" + now() + "] Connecting to server " + serverIp + ":" + port + " ...");
        Socket sock = new Socket(serverIp, port);
        String peer = "Server (" + serverIp + ":" + port + ")";
        System.out.println("[" + now() + "] Connected! (this client is "
                + sock.getLocalAddress().getHostAddress() + ":" + sock.getLocalPort() + ")");
        System.out.println("Type messages and press Enter. Type 'bye' to quit.\n");

        BufferedReader in = new BufferedReader(
                new InputStreamReader(sock.getInputStream(), StandardCharsets.UTF_8));
        PrintWriter out = new PrintWriter(
                new OutputStreamWriter(sock.getOutputStream(), StandardCharsets.UTF_8), true);

        // Receiver thread: prints every incoming message
        Thread receiver = new Thread(() -> {
            try {
                String msg;
                while ((msg = in.readLine()) != null) {
                    System.out.println("\n[" + now() + "] " + peer + ": " + msg);
                    if (msg.trim().equalsIgnoreCase("bye")) {
                        System.out.println("[" + now() + "] Server ended the chat.");
                        break;
                    }
                    System.out.print("You (Client) > ");
                    System.out.flush();
                }
            } catch (IOException ignored) { }
            System.out.println("[" + now() + "] Connection closed.");
            System.exit(0);
        });
        receiver.setDaemon(true);
        receiver.start();

        // Sender (main thread): reads keyboard input and sends it
        Scanner sc = new Scanner(System.in);
        while (true) {
            System.out.print("You (Client) > ");
            if (!sc.hasNextLine()) break;
            String msg = sc.nextLine();
            out.println(msg);
            if (msg.trim().equalsIgnoreCase("bye")) break;
        }
        sock.close();
        System.out.println("[" + now() + "] Client shut down.");
        System.exit(0);
    }
}
