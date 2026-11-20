"""
23CSE312 - Distributed Systems | Lab Exercise 1 - Program 2
Peer-to-Peer File Listing

Roll No : CH.SC.U4CSE24035

Every peer is BOTH a server and a client:
  * A background thread serves the list of files in this peer's shared folder.
  * The foreground menu lets the user ask any other peer (by IP) for its list.
The list is built live from the real folder contents (os.scandir) on every request.

Run : python peer.py [port] [shared_folder]
      default port = 6000, default folder = ../shared_folder

Protocol (text, one line per message, UTF-8):
  request : LIST
  reply   : PEER <hostname> <folder_path>
            COUNT <n>
            <type>\t<size_bytes>\t<modified YYYY-MM-DD HH:MM>\t<name>   (n lines, type = FILE|DIR)
            END
"""
import datetime
import os
import socket
import sys
import threading

ROLL_NO = "CH.SC.U4CSE24035"
DEFAULT_PORT = 6000
DEFAULT_FOLDER = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "shared_folder")


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


def scan_folder(folder):
    """Read the REAL contents of the folder - nothing is hard coded."""
    entries = []
    with os.scandir(folder) as it:
        for e in it:
            st = e.stat()
            entries.append((
                "DIR" if e.is_dir() else "FILE",
                0 if e.is_dir() else st.st_size,
                datetime.datetime.fromtimestamp(st.st_mtime).strftime("%Y-%m-%d %H:%M"),
                e.name,
            ))
    entries.sort(key=lambda x: (x[0] != "DIR", x[3].lower()))
    return entries


def print_table(entries):
    print(f"  {'#':>3}  {'TYPE':<4}  {'SIZE (bytes)':>12}  {'MODIFIED':<16}  NAME")
    print("  " + "-" * 64)
    for i, (typ, size, mod, name) in enumerate(entries, 1):
        size_s = "-" if typ == "DIR" else str(size)
        print(f"  {i:>3}  {typ:<4}  {size_s:>12}  {mod:<16}  {name}")
    print(f"  Total: {len(entries)} item(s)")


# ---------------------------------------------------------------- server side
def handle_peer(conn, addr, folder):
    with conn:
        reader = conn.makefile("r", encoding="utf-8")
        request = reader.readline().strip()
        print(f"\n[{now()}] Request '{request}' from peer {addr[0]}:{addr[1]}")
        if request.upper() != "LIST":
            conn.sendall(b"ERROR unknown command\n")
            return
        entries = scan_folder(folder)
        lines = [f"PEER {socket.gethostname()} {os.path.abspath(folder)}", f"COUNT {len(entries)}"]
        lines += [f"{t}\t{s}\t{m}\t{n}" for t, s, m, n in entries]
        lines.append("END")
        conn.sendall(("\n".join(lines) + "\n").encode("utf-8"))
        print(f"[{now()}] Sent {len(entries)} entries to {addr[0]}")
        print("Choice > ", end="", flush=True)


def serve(port, folder):
    srv = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    srv.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    srv.bind(("0.0.0.0", port))
    srv.listen(5)
    while True:
        conn, addr = srv.accept()
        threading.Thread(target=handle_peer, args=(conn, addr, folder), daemon=True).start()


# ---------------------------------------------------------------- client side
def request_list(ip, port):
    with socket.create_connection((ip, port), timeout=10) as s:
        s.sendall(b"LIST\n")
        reader = s.makefile("r", encoding="utf-8")
        header = reader.readline().strip().split(" ", 2)
        if header[0] != "PEER":
            print("  Peer replied with an error:", " ".join(header))
            return
        count = int(reader.readline().split()[1])
        entries = []
        for _ in range(count):
            t, size, mod, name = reader.readline().rstrip("\n").split("\t", 3)
            entries.append((t, int(size), mod, name))
        reader.readline()  # END
    print(f"\n  Files on peer {ip}:{port}  (host: {header[1]})")
    print(f"  Shared folder: {header[2]}")
    print_table(entries)


def main():
    port = int(sys.argv[1]) if len(sys.argv) > 1 else DEFAULT_PORT
    folder = os.path.abspath(sys.argv[2] if len(sys.argv) > 2 else DEFAULT_FOLDER)
    os.makedirs(folder, exist_ok=True)

    print("=" * 64)
    print(" 23CSE312 Lab 1.2 - Peer-to-Peer File Listing (PEER)")
    print(f" Roll No       : {ROLL_NO}")
    print(f" Hostname      : {socket.gethostname()}")
    print(f" Local IP      : {get_local_ip()}   Port: {port}")
    print(f" Shared folder : {folder}")
    print(f" Started       : {datetime.datetime.now():%Y-%m-%d %H:%M:%S}")
    print("=" * 64)

    threading.Thread(target=serve, args=(port, folder), daemon=True).start()
    print(f"[{now()}] Listening for peers on port {port} ...")

    while True:
        print("\n1. Show MY shared files\n2. List files on ANOTHER peer\n3. Exit")
        choice = input("Choice > ").strip()
        if choice == "1":
            print(f"\n  My shared folder: {folder}")
            print_table(scan_folder(folder))
        elif choice == "2":
            target = input("Peer IP (ip or ip:port) > ").strip()
            ip, _, p = target.partition(":")
            try:
                request_list(ip, int(p) if p else DEFAULT_PORT)
            except (OSError, ValueError) as e:
                print(f"  Could not reach peer {target}: {e}")
        elif choice == "3":
            print("Bye!")
            break


if __name__ == "__main__":
    main()
