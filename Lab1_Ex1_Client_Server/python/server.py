"""
23CSE312 - Distributed Systems | Lab Exercise 1 - Program 1
Client-Server Communication (bidirectional message exchange) - SERVER

Roll No : CH.SC.U4CSE24035

Run   : python server.py [port]          (default port 5000)
Stop  : type "bye" (either side can end the chat)
"""
import datetime
import os
import socket
import sys
import threading

ROLL_NO = "CH.SC.U4CSE24035"
DEFAULT_PORT = 5000


def get_local_ip():
    """Return the LAN IP of this machine (no packet is actually sent)."""
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


def banner(role, port):
    print("=" * 60)
    print(f" 23CSE312 Lab 1.1 - Client-Server Communication ({role})")
    print(f" Roll No  : {ROLL_NO}")
    print(f" Hostname : {socket.gethostname()}")
    print(f" Local IP : {get_local_ip()}   Port: {port}")
    print(f" Started  : {datetime.datetime.now():%Y-%m-%d %H:%M:%S}")
    print("=" * 60)


def receive_loop(conn, peer_label):
    """Runs in a background thread: prints every message from the client."""
    reader = conn.makefile("r", encoding="utf-8")
    try:
        for line in reader:
            msg = line.rstrip("\n")
            print(f"\n[{now()}] {peer_label}: {msg}")
            if msg.strip().lower() == "bye":
                print(f"[{now()}] Client ended the chat.")
                break
            print("You (Server) > ", end="", flush=True)
    except OSError:
        pass
    print(f"[{now()}] Connection closed.")
    os._exit(0)


def main():
    port = int(sys.argv[1]) if len(sys.argv) > 1 else DEFAULT_PORT
    banner("SERVER", port)

    server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    server.bind(("0.0.0.0", port))          # listen on all network interfaces
    server.listen(1)
    print(f"[{now()}] Waiting for a client on port {port} ...")

    conn, addr = server.accept()
    peer_label = f"Client ({addr[0]}:{addr[1]})"
    print(f"[{now()}] Connected to {peer_label}")
    print("Type messages and press Enter. Type 'bye' to quit.\n")

    threading.Thread(target=receive_loop, args=(conn, peer_label), daemon=True).start()

    try:
        while True:
            msg = input("You (Server) > ")
            conn.sendall((msg + "\n").encode("utf-8"))
            if msg.strip().lower() == "bye":
                break
    except (EOFError, KeyboardInterrupt, OSError):
        pass
    finally:
        conn.close()
        server.close()
        print(f"[{now()}] Server shut down.")


if __name__ == "__main__":
    main()
