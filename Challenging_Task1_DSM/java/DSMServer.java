/*
 * 23CSE312 - Distributed Systems | Challenging Task 1
 * Distributed Shared Memory (DSM) - SHARED MEMORY MANAGER (server)
 *
 * Roll No : CH.SC.U4CSE24035
 *
 * Holds the shared memory (a table of named integer variables) and lets any node
 * on the network CREATE / READ / WRITE them over TCP. Every access is serialised
 * (synchronized), and every variable carries a version number, so a node doing
 * read -> modify -> write back can detect if someone else wrote in between.
 *
 * Compile : javac DSMServer.java
 * Run     : java DSMServer [port]        (default port 7000)
 *
 * Same text protocol as the Python version (see dsm_server.py), so Java and
 * Python nodes can share the same memory.
 */
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class DSMServer {
    static final String ROLL_NO = "CH.SC.U4CSE24035";
    static final int DEFAULT_PORT = 7000;

    /** One shared variable. */
    static class Var {
        int value, version;
        String writer, time;
        Var(int value, String writer) { this.value = value; this.version = 1; this.writer = writer; this.time = now(); }
    }

    /** The shared memory. TreeMap keeps LIST output sorted. */
    static final Map<String, Var> sharedMemory = new TreeMap<>();

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

    static String valueLine(String name) {
        Var v = sharedMemory.get(name);
        return "VALUE " + name + " " + v.value + " " + v.version + " " + v.writer + " " + v.time;
    }

    /** Run one DSM command atomically and return the reply line(s). */
    static synchronized String execute(String request, String clientIp) {
        String[] p = request.trim().split("\\s+");
        if (p.length == 0 || p[0].isEmpty()) return "ERR BADCMD";
        String cmd = p[0].toUpperCase();
        try {
            if (cmd.equals("CREATE") && p.length == 3) {
                String name = p[1];
                int value = Integer.parseInt(p[2]);
                if (sharedMemory.containsKey(name)) {
                    Var v = sharedMemory.get(name);
                    return "ERR EXISTS " + name + " " + v.value + " " + v.version;
                }
                sharedMemory.put(name, new Var(value, clientIp));
                return "OK CREATED " + name + " " + value + " 1";
            }
            if (cmd.equals("READ") && p.length == 2) {
                if (!sharedMemory.containsKey(p[1])) return "ERR NOTFOUND " + p[1];
                return valueLine(p[1]);
            }
            if (cmd.equals("WRITE") && (p.length == 3 || p.length == 4)) {
                String name = p[1];
                int value = Integer.parseInt(p[2]);
                Var v = sharedMemory.get(name);
                if (v == null) return "ERR NOTFOUND " + name;
                if (p.length == 4 && Integer.parseInt(p[3]) != v.version)
                    return "ERR CONFLICT " + name + " " + v.value + " " + v.version;
                v.value = value;
                v.version++;
                v.writer = clientIp;
                v.time = now();
                return "OK WRITTEN " + name + " " + value + " " + v.version;
            }
            if (cmd.equals("LIST") && p.length == 1) {
                StringBuilder sb = new StringBuilder("COUNT " + sharedMemory.size());
                for (String name : sharedMemory.keySet()) sb.append('\n').append(valueLine(name));
                return sb.append("\nEND").toString();
            }
        } catch (NumberFormatException e) {
            return "ERR BADVALUE (values must be integers)";
        }
        return "ERR BADCMD";
    }

    static void handleNode(Socket conn) {
        String ip = conn.getInetAddress().getHostAddress();
        System.out.println("[" + now() + "] Node connected   : " + ip + ":" + conn.getPort());
        try (conn) {
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
            PrintWriter out = new PrintWriter(
                    new OutputStreamWriter(conn.getOutputStream(), StandardCharsets.UTF_8), true);
            String line;
            while ((line = in.readLine()) != null) {
                if (line.isBlank()) continue;
                String reply = execute(line, ip);
                System.out.printf("[%s] %-15s %-28s -> %s%n", now(), ip, line.trim(), reply.split("\n")[0]);
                out.println(reply);
            }
        } catch (IOException ignored) { }
        System.out.println("[" + now() + "] Node disconnected: " + ip);
    }

    public static void main(String[] args) throws IOException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_PORT;
        System.out.println("================================================================");
        System.out.println(" 23CSE312 Challenging Task 1 - Distributed Shared Memory");
        System.out.println(" Role     : SHARED MEMORY MANAGER (server)");
        System.out.println(" Roll No  : " + ROLL_NO);
        System.out.println(" Hostname : " + InetAddress.getLocalHost().getHostName());
        System.out.println(" Local IP : " + getLocalIp() + "   Port: " + port);
        System.out.println(" Started  : " + LocalDateTime.now().format(
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        System.out.println("================================================================");

        try (ServerSocket srv = new ServerSocket(port)) {
            System.out.println("[" + now() + "] Shared memory ready. Waiting for nodes ...  (Ctrl+C to stop)");
            while (true) {
                Socket conn = srv.accept();
                new Thread(() -> handleNode(conn)).start();
            }
        }
    }
}
