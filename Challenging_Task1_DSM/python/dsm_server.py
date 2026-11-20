"""
23CSE312 - Distributed Systems | Challenging Task 1
Distributed Shared Memory (DSM) - SHARED MEMORY MANAGER (server)

Roll No : CH.SC.U4CSE24035

Holds the shared memory (a table of named integer variables) and lets any node
on the network CREATE / READ / WRITE them over TCP. Every access is serialised
by a lock, and every variable carries a version number, so a node that does
read -> modify -> write back can detect if someone else wrote in between.

Run : python dsm_server.py [port]        (default port 7000)

Protocol (one text line per request/response, UTF-8):
  CREATE <name> <int>                -> OK CREATED <name> <value> <version>
                                        | ERR EXISTS <name> <value> <version>
  READ <name>                        -> VALUE <name> <value> <version> <last_writer_ip> <HH:MM:SS>
                                        | ERR NOTFOUND <name>
  WRITE <name> <int> [<expected_ver>] -> OK WRITTEN <name> <value> <new_version>
                                        | ERR CONFLICT <name> <value> <version>
                                        | ERR NOTFOUND <name>
  LIST                               -> COUNT <n>, n VALUE lines, END
"""
import datetime
import socket
import sys
import threading

ROLL_NO = "CH.SC.U4CSE24035"
DEFAULT_PORT = 7000

# The shared memory: name -> {"value", "version", "writer", "time"}
shared_memory = {}
lock = threading.Lock()


def get_local_ip():
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(("8.8.8.8", 80))
        return s.getsockname()[0]
    except OSError:
        return "127.0.0.1"
    finally:
        s.close()


def now():
    return datetime.datetime.now().strftime("%H:%M:%S")


def value_line(name):
    v = shared_memory[name]
    return f"VALUE {name} {v['value']} {v['version']} {v['writer']} {v['time']}"


def execute(request, client_ip):
    """Run one DSM command atomically and return the reply line(s)."""
    parts = request.split()
    if not parts:
        return "ERR BADCMD"
    cmd = parts[0].upper()
    try:
        with lock:
            if cmd == "CREATE" and len(parts) == 3:
                name, value = parts[1], int(parts[2])
                if name in shared_memory:
                    v = shared_memory[name]
                    return f"ERR EXISTS {name} {v['value']} {v['version']}"
                shared_memory[name] = {"value": value, "version": 1, "writer": client_ip, "time": now()}
                return f"OK CREATED {name} {value} 1"

            if cmd == "READ" and len(parts) == 2:
                name = parts[1]
                if name not in shared_memory:
                    return f"ERR NOTFOUND {name}"
                return value_line(name)

            if cmd == "WRITE" and len(parts) in (3, 4):
                name, value = parts[1], int(parts[2])
                if name not in shared_memory:
                    return f"ERR NOTFOUND {name}"
                v = shared_memory[name]
                if len(parts) == 4 and int(parts[3]) != v["version"]:
                    return f"ERR CONFLICT {name} {v['value']} {v['version']}"
                v.update(value=value, version=v["version"] + 1, writer=client_ip, time=now())
                return f"OK WRITTEN {name} {value} {v['version']}"

            if cmd == "LIST" and len(parts) == 1:
                lines = [f"COUNT {len(shared_memory)}"]
                lines += [value_line(n) for n in sorted(shared_memory)]
                lines.append("END")
                return "\n".join(lines)
    except ValueError:
        return "ERR BADVALUE (values must be integers)"
    return "ERR BADCMD"


def handle_node(conn, addr):
    ip = addr[0]
    print(f"[{now()}] Node connected   : {ip}:{addr[1]}")
    with conn:
        reader = conn.makefile("r", encoding="utf-8")
        try:
            for line in reader:
                request = line.strip()
                if not request:
                    continue
                reply = execute(request, ip)
                print(f"[{now()}] {ip:<15} {request:<28} -> {reply.splitlines()[0]}")
                conn.sendall((reply + "\n").encode("utf-8"))
        except OSError:
            pass
    print(f"[{now()}] Node disconnected: {ip}:{addr[1]}")


def main():
    port = int(sys.argv[1]) if len(sys.argv) > 1 else DEFAULT_PORT
    print("=" * 64)
    print(" 23CSE312 Challenging Task 1 - Distributed Shared Memory")
    print(" Role     : SHARED MEMORY MANAGER (server)")
    print(f" Roll No  : {ROLL_NO}")
    print(f" Hostname : {socket.gethostname()}")
    print(f" Local IP : {get_local_ip()}   Port: {port}")
    print(f" Started  : {datetime.datetime.now():%Y-%m-%d %H:%M:%S}")
    print("=" * 64)

    srv = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    srv.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    srv.bind(("0.0.0.0", port))
    srv.listen(10)
    print(f"[{now()}] Shared memory ready. Waiting for nodes ...  (Ctrl+C to stop)")
    try:
        while True:
            conn, addr = srv.accept()
            threading.Thread(target=handle_node, args=(conn, addr), daemon=True).start()
    except KeyboardInterrupt:
        print("\nShutting down shared memory manager.")


if __name__ == "__main__":
    main()
