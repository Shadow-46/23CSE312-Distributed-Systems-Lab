/*
 * 23CSE312 - Distributed Systems | Challenging Task 1
 * Distributed Shared Memory (DSM) - NODE (client)
 *
 * Roll No : CH.SC.U4CSE24035
 *
 * A node attaches to the shared memory manager and works on shared variables as
 * if they were local memory: create, read, read-modify-write (increment), write.
 *
 * Compile : javac DSMNode.java
 * Run     : java DSMNode <memory_manager_ip> [port]     (default port 7000)
 */
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class DSMNode {
    static final String ROLL_NO = "CH.SC.U4CSE24035";
    static final int DEFAULT_PORT = 7000;

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

    /** Thin client stub: turns method calls into DSM protocol messages. */
    static class SharedMemory implements Closeable {
        private final Socket sock;
        private final BufferedReader in;
        private final PrintWriter out;

        SharedMemory(String ip, int port) throws IOException {
            sock = new Socket();
            sock.connect(new InetSocketAddress(ip, port), 10000);
            in = new BufferedReader(new InputStreamReader(sock.getInputStream(), StandardCharsets.UTF_8));
            out = new PrintWriter(new OutputStreamWriter(sock.getOutputStream(), StandardCharsets.UTF_8), true);
        }

        String call(String request) throws IOException {
            out.println(request);
            String reply = in.readLine();
            if (reply == null) throw new IOException("memory manager closed the connection");
            return reply.trim();
        }

        String create(String name, int value) throws IOException { return call("CREATE " + name + " " + value); }

        /** Returns [value, version, writer, time] or null if not found. */
        String[] read(String name) throws IOException {
            String[] r = call("READ " + name).split(" ");
            return r[0].equals("VALUE") ? new String[]{r[2], r[3], r[4], r[5]} : null;
        }

        String write(String name, int value, Integer expectedVersion) throws IOException {
            return call("WRITE " + name + " " + value + (expectedVersion != null ? " " + expectedVersion : ""));
        }

        List<String[]> listAll() throws IOException {
            out.println("LIST");
            int count = Integer.parseInt(in.readLine().split(" ")[1]);
            List<String[]> rows = new ArrayList<>();
            for (int i = 0; i < count; i++) rows.add(in.readLine().split(" "));
            in.readLine(); // END
            return rows;
        }

        public void close() throws IOException { sock.close(); }
    }

    static void show(String name, String[] d) {
        System.out.println("  >> " + name + " = " + d[0] + "   (version " + d[1]
                + ", last written by " + d[2] + " at " + d[3] + ")");
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.out.println("Usage: java DSMNode <memory_manager_ip> [port]");
            return;
        }
        String ip = args[0];
        int port = args.length > 1 ? Integer.parseInt(args[1]) : DEFAULT_PORT;

        System.out.println("================================================================");
        System.out.println(" 23CSE312 Challenging Task 1 - Distributed Shared Memory");
        System.out.println(" Role     : NODE (client)");
        System.out.println(" Roll No  : " + ROLL_NO);
        System.out.println(" Hostname : " + InetAddress.getLocalHost().getHostName());
        System.out.println(" Local IP : " + getLocalIp());
        System.out.println(" Memory   : " + ip + ":" + port);
        System.out.println(" Started  : " + LocalDateTime.now().format(
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        System.out.println("================================================================");

        SharedMemory mem;
        try {
            mem = new SharedMemory(ip, port);
        } catch (IOException e) {
            System.out.println("\n[" + now() + "] Could not reach the memory manager at " + ip + ":" + port
                    + " (" + e.getMessage() + ").");
            System.out.println("  * Use the exact 'Local IP' printed in the DSMServer banner.");
            System.out.println("  * Make sure DSMServer is running and both machines are on the same network.");
            System.out.println("  * Allow TCP port 7000 through the firewall on the server machine.");
            return;
        }
        System.out.println("[" + now() + "] Attached to shared memory at " + ip + ":" + port);

        Scanner sc = new Scanner(System.in);
        String menu = "\n1. Create shared variable\n2. Read shared variable\n"
                + "3. Increment (read -> modify -> write back)\n4. Write a new value\n"
                + "5. List all shared variables\n6. Exit";
        while (true) {
            System.out.println(menu);
            System.out.print("Choice > ");
            if (!sc.hasNextLine()) break;
            String choice = sc.nextLine().trim();
            try {
                switch (choice) {
                    case "1" -> {
                        System.out.print("  Variable name > ");
                        String name = sc.nextLine().trim();
                        System.out.print("  Initial value > ");
                        int value = Integer.parseInt(sc.nextLine().trim());
                        System.out.println("  [" + now() + "] " + mem.create(name, value));
                    }
                    case "2" -> {
                        System.out.print("  Variable name > ");
                        String name = sc.nextLine().trim();
                        String[] d = mem.read(name);
                        System.out.println("  [" + now() + "] READ " + name);
                        if (d != null) show(name, d);
                        else System.out.println("  '" + name + "' does not exist in shared memory");
                    }
                    case "3" -> {
                        System.out.print("  Variable name > ");
                        String name = sc.nextLine().trim();
                        System.out.print("  Increment by [1] > ");
                        String s = sc.nextLine().trim();
                        int step = s.isEmpty() ? 1 : Integer.parseInt(s);
                        while (true) {
                            String[] d = mem.read(name);
                            if (d == null) {
                                System.out.println("  '" + name + "' does not exist in shared memory");
                                break;
                            }
                            int old = Integer.parseInt(d[0]), ver = Integer.parseInt(d[1]);
                            System.out.println("  [" + now() + "] Step 1 READ   : " + name + " = " + old + " (version " + ver + ")");
                            int nv = old + step;
                            System.out.println("  [" + now() + "] Step 2 MODIFY : " + old + " + " + step + " = " + nv);
                            String reply = mem.write(name, nv, ver);
                            System.out.println("  [" + now() + "] Step 3 WRITE  : " + reply);
                            if (reply.startsWith("OK")) break;
                            System.out.println("  Another node wrote first - retrying with the latest value ...");
                        }
                    }
                    case "4" -> {
                        System.out.print("  Variable name > ");
                        String name = sc.nextLine().trim();
                        System.out.print("  New value > ");
                        int value = Integer.parseInt(sc.nextLine().trim());
                        System.out.println("  [" + now() + "] " + mem.write(name, value, null));
                    }
                    case "5" -> {
                        List<String[]> rows = mem.listAll();
                        System.out.printf("%n  %-12s %8s %4s  %-15s %s%n", "NAME", "VALUE", "VER", "LAST WRITER", "TIME");
                        System.out.println("  " + "-".repeat(52));
                        for (String[] r : rows)
                            System.out.printf("  %-12s %8s %4s  %-15s %s%n", r[1], r[2], r[3], r[4], r[5]);
                        System.out.println("  Total: " + rows.size() + " variable(s)");
                    }
                    case "6" -> {
                        mem.close();
                        System.out.println("Detached from shared memory. Bye!");
                        return;
                    }
                    default -> { }
                }
            } catch (NumberFormatException e) {
                System.out.println("  Please enter an integer value.");
            } catch (IOException e) {
                System.out.println("  Lost connection to shared memory: " + e.getMessage());
                return;
            }
        }
    }
}
