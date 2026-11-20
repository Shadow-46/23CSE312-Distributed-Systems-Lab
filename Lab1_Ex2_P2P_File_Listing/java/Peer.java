/*
 * 23CSE312 - Distributed Systems | Lab Exercise 1 - Program 2
 * Peer-to-Peer File Listing
 *
 * Roll No : CH.SC.U4CSE24035
 *
 * Every peer is BOTH a server and a client:
 *   - A background thread serves the list of files in this peer's shared folder.
 *   - The foreground menu lets the user ask any other peer (by IP) for its list.
 * The list is built live from the real folder contents (File.listFiles) on every request.
 *
 * Compile : javac Peer.java
 * Run     : java Peer [port] [shared_folder]
 *           default port = 6000, default folder = ../shared_folder
 *
 * Protocol is identical to the Python version, so a Java peer and a Python peer
 * can list each other's files.
 */
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class Peer {
    static final String ROLL_NO = "CH.SC.U4CSE24035";
    static final int DEFAULT_PORT = 6000;

    static String now() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
    }

    static String getLocalIp() {
        try (DatagramSocket s = new DatagramSocket()) {
            s.connect(InetAddress.getByName("8.8.8.8"), 80);
            return s.getLocalAddress().getHostAddress();
        } catch (Exception e) {
            return "127.0.0.1";
        }
    }

    static String hostname() {
        try { return InetAddress.getLocalHost().getHostName(); }
        catch (UnknownHostException e) { return "unknown"; }
    }

    /** One row of the listing: type, size, modified, name. */
    record Entry(String type, long size, String modified, String name) { }

    /** Read the REAL contents of the folder - nothing is hard coded. */
    static List<Entry> scanFolder(File folder) {
        List<Entry> list = new ArrayList<>();
        File[] files = folder.listFiles();
        if (files == null) return list;
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        for (File f : files) {
            list.add(new Entry(f.isDirectory() ? "DIR" : "FILE",
                    f.isDirectory() ? 0 : f.length(),
                    fmt.format(new Date(f.lastModified())),
                    f.getName()));
        }
        list.sort(Comparator.comparing((Entry e) -> !e.type().equals("DIR"))
                .thenComparing(e -> e.name().toLowerCase()));
        return list;
    }

    static void printTable(List<Entry> entries) {
        System.out.printf("  %3s  %-4s  %12s  %-16s  %s%n", "#", "TYPE", "SIZE (bytes)", "MODIFIED", "NAME");
        System.out.println("  " + "-".repeat(64));
        int i = 1;
        for (Entry e : entries) {
            String size = e.type().equals("DIR") ? "-" : String.valueOf(e.size());
            System.out.printf("  %3d  %-4s  %12s  %-16s  %s%n", i++, e.type(), size, e.modified(), e.name());
        }
        System.out.println("  Total: " + entries.size() + " item(s)");
    }

    // ------------------------------------------------------------ server side
    static void handlePeer(Socket conn, File folder) {
        try (conn) {
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
            PrintWriter out = new PrintWriter(
                    new OutputStreamWriter(conn.getOutputStream(), StandardCharsets.UTF_8), true);
            String addr = conn.getInetAddress().getHostAddress() + ":" + conn.getPort();
            String request = in.readLine();
            request = request == null ? "" : request.trim();
            System.out.println("\n[" + now() + "] Request '" + request + "' from peer " + addr);
            if (!request.equalsIgnoreCase("LIST")) {
                out.println("ERROR unknown command");
                return;
            }
            List<Entry> entries = scanFolder(folder);
            StringBuilder sb = new StringBuilder();
            sb.append("PEER ").append(hostname()).append(' ').append(folder.getAbsolutePath()).append('\n');
            sb.append("COUNT ").append(entries.size()).append('\n');
            for (Entry e : entries) {
                sb.append(e.type()).append('\t').append(e.size()).append('\t')
                  .append(e.modified()).append('\t').append(e.name()).append('\n');
            }
            sb.append("END");
            out.println(sb);
            System.out.println("[" + now() + "] Sent " + entries.size() + " entries to "
                    + conn.getInetAddress().getHostAddress());
            System.out.print("Choice > ");
            System.out.flush();
        } catch (IOException e) {
            System.out.println("[" + now() + "] Error serving peer: " + e.getMessage());
        }
    }

    static void serve(int port, File folder) {
        try (ServerSocket srv = new ServerSocket(port)) {
            while (true) {
                Socket conn = srv.accept();
                new Thread(() -> handlePeer(conn, folder)).start();
            }
        } catch (IOException e) {
            System.out.println("Server stopped: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------ client side
    static void requestList(String ip, int port) throws IOException {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(ip, port), 10000);
            PrintWriter out = new PrintWriter(
                    new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8), true);
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
            out.println("LIST");
            String[] header = in.readLine().trim().split(" ", 3);
            if (!header[0].equals("PEER")) {
                System.out.println("  Peer replied with an error: " + String.join(" ", header));
                return;
            }
            int count = Integer.parseInt(in.readLine().split(" ")[1]);
            List<Entry> entries = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                String[] p = in.readLine().split("\t", 4);
                entries.add(new Entry(p[0], Long.parseLong(p[1]), p[2], p[3]));
            }
            in.readLine(); // END
            System.out.println("\n  Files on peer " + ip + ":" + port + "  (host: " + header[1] + ")");
            System.out.println("  Shared folder: " + header[2]);
            printTable(entries);
        }
    }

    public static void main(String[] args) throws IOException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_PORT;
        File folder = new File(args.length > 1 ? args[1] : "../shared_folder").getCanonicalFile();
        folder.mkdirs();

        System.out.println("================================================================");
        System.out.println(" 23CSE312 Lab 1.2 - Peer-to-Peer File Listing (PEER)");
        System.out.println(" Roll No       : " + ROLL_NO);
        System.out.println(" Hostname      : " + hostname());
        System.out.println(" Local IP      : " + getLocalIp() + "   Port: " + port);
        System.out.println(" Shared folder : " + folder.getAbsolutePath());
        System.out.println(" Started       : " + LocalDateTime.now().format(
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        System.out.println("================================================================");

        Thread server = new Thread(() -> serve(port, folder));
        server.setDaemon(true);
        server.start();
        System.out.println("[" + now() + "] Listening for peers on port " + port + " ...");

        Scanner sc = new Scanner(System.in);
        while (true) {
            System.out.println("\n1. Show MY shared files\n2. List files on ANOTHER peer\n3. Exit");
            System.out.print("Choice > ");
            if (!sc.hasNextLine()) break;
            String choice = sc.nextLine().trim();
            if (choice.equals("1")) {
                System.out.println("\n  My shared folder: " + folder.getAbsolutePath());
                printTable(scanFolder(folder));
            } else if (choice.equals("2")) {
                System.out.print("Peer IP (ip or ip:port) > ");
                String target = sc.nextLine().trim();
                String[] parts = target.split(":");
                try {
                    requestList(parts[0], parts.length > 1 ? Integer.parseInt(parts[1]) : DEFAULT_PORT);
                } catch (Exception e) {
                    System.out.println("  Could not reach peer " + target + ": " + e.getMessage());
                }
            } else if (choice.equals("3")) {
                System.out.println("Bye!");
                break;
            }
        }
        System.exit(0);
    }
}
