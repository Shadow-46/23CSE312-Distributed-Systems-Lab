"""
23CSE312 - Distributed Systems | Lab Exercise 1 - Program 1
Client-Server Communication (bidirectional message exchange) - CLIENT

Roll No : CH.SC.U4CSE24035

Run   : python client.py <server_ip> [port]   (default port 5000)
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


def receive_loop(sock, peer_label):
    """Runs in a background thread: prints every message from the server."""
    reader = sock.makefile("r", encoding="utf-8")
    try:
        for line in reader:
            msg = line.rstrip("\n")
            print(f"\n[{now()}] {peer_label}: {msg}")
            if msg.strip().lower() == "bye":
                print(f"[{now()}] Server ended the chat.")
                break
            print("You (Client) > ", end="", flush=True)
    except OSError:
        pass
    print(f"[{now()}] Connection closed.")
    os._exit(0)


def main():
    if len(sys.argv) < 2:
        print("Usage: python client.py <server_ip> [port]")
        sys.exit(1)
    server_ip = sys.argv[1]
    port = int(sys.argv[2]) if len(sys.argv) > 2 else DEFAULT_PORT
    banner("CLIENT", port)

    sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    print(f"[{now()}] Connecting to server {server_ip}:{port} ...")
    sock.settimeout(10)
    try:
        sock.connect((server_ip, port))
    except OSError as e:
        print(f"[{now()}] Could not reach the server at {server_ip}:{port} ({e}).")
        print("  * Use the exact 'Local IP' printed in the server banner.")
        print("  * Make sure the server is running and port 5000 is allowed through its firewall.")
        sys.exit(1)
    sock.settimeout(None)
    local = sock.getsockname()
    peer_label = f"Server ({server_ip}:{port})"
    print(f"[{now()}] Connected! (this client is {local[0]}:{local[1]})")
    print("Type messages and press Enter. Type 'bye' to quit.\n")

    threading.Thread(target=receive_loop, args=(sock, peer_label), daemon=True).start()

    try:
        while True:
            msg = input("You (Client) > ")
            sock.sendall((msg + "\n").encode("utf-8"))
            if msg.strip().lower() == "bye":
                break
    except (EOFError, KeyboardInterrupt, OSError):
        pass
    finally:
        sock.close()
        print(f"[{now()}] Client shut down.")


if __name__ == "__main__":
    main()
