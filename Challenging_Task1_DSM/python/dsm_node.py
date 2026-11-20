"""
23CSE312 - Distributed Systems | Challenging Task 1
Distributed Shared Memory (DSM) - NODE (client)

Roll No : CH.SC.U4CSE24035

A node attaches to the shared memory manager and works on shared variables as
if they were local memory: create, read, read-modify-write (increment), write.

Run : python dsm_node.py <memory_manager_ip> [port]     (default port 7000)
"""
import datetime
import socket
import sys

ROLL_NO = "CH.SC.U4CSE24035"
DEFAULT_PORT = 7000


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


class SharedMemory:
    """Thin client stub: turns method calls into DSM protocol messages."""

    def __init__(self, ip, port):
        self.sock = socket.create_connection((ip, port), timeout=10)
        self.reader = self.sock.makefile("r", encoding="utf-8")

    def _call(self, request):
        self.sock.sendall((request + "\n").encode("utf-8"))
        reply = self.reader.readline()
        if not reply:
            raise OSError("memory manager closed the connection")
        return reply.strip()

    def create(self, name, value):
        return self._call(f"CREATE {name} {value}")

    def read(self, name):
        """Returns (value, version, last_writer, time) or None if not found."""
        reply = self._call(f"READ {name}").split()
        if reply[0] != "VALUE":
            return None
        return int(reply[2]), int(reply[3]), reply[4], reply[5]

    def write(self, name, value, expected_version=None):
        req = f"WRITE {name} {value}" + (f" {expected_version}" if expected_version else "")
        return self._call(req)

    def list_all(self):
        self.sock.sendall(b"LIST\n")
        count = int(self.reader.readline().split()[1])
        rows = [self.reader.readline().split() for _ in range(count)]
        self.reader.readline()  # END
        return rows

    def close(self):
        self.sock.close()


def show(name, data):
    value, version, writer, t = data
    print(f"  >> {name} = {value}   (version {version}, last written by {writer} at {t})")


def main():
    if len(sys.argv) < 2:
        print("Usage: python dsm_node.py <memory_manager_ip> [port]")
        sys.exit(1)
    ip = sys.argv[1]
    port = int(sys.argv[2]) if len(sys.argv) > 2 else DEFAULT_PORT

    print("=" * 64)
    print(" 23CSE312 Challenging Task 1 - Distributed Shared Memory")
    print(" Role     : NODE (client)")
    print(f" Roll No  : {ROLL_NO}")
    print(f" Hostname : {socket.gethostname()}")
    print(f" Local IP : {get_local_ip()}")
    print(f" Memory   : {ip}:{port}")
    print(f" Started  : {datetime.datetime.now():%Y-%m-%d %H:%M:%S}")
    print("=" * 64)

    try:
        mem = SharedMemory(ip, port)
    except OSError as e:
        print(f"\n[{now()}] Could not reach the memory manager at {ip}:{port} ({e}).")
        print("  * Use the exact 'Local IP' printed in the dsm_server banner.")
        print("  * Make sure dsm_server is running and both machines are on the same network.")
        print("  * Allow TCP port 7000 through the firewall on the server machine.")
        sys.exit(1)
    print(f"[{now()}] Attached to shared memory at {ip}:{port}")

    menu = ("\n1. Create shared variable\n2. Read shared variable\n"
            "3. Increment (read -> modify -> write back)\n4. Write a new value\n"
            "5. List all shared variables\n6. Exit")
    while True:
        print(menu)
        choice = input("Choice > ").strip()
        try:
            if choice == "1":
                name = input("  Variable name > ").strip()
                value = int(input("  Initial value > ").strip())
                print(f"  [{now()}] {mem.create(name, value)}")

            elif choice == "2":
                name = input("  Variable name > ").strip()
                data = mem.read(name)
                print(f"  [{now()}] READ {name}")
                if data:
                    show(name, data)
                else:
                    print(f"  '{name}' does not exist in shared memory")

            elif choice == "3":
                name = input("  Variable name > ").strip()
                step = input("  Increment by [1] > ").strip()
                step = int(step) if step else 1
                while True:
                    data = mem.read(name)
                    if not data:
                        print(f"  '{name}' does not exist in shared memory")
                        break
                    old, ver = data[0], data[1]
                    print(f"  [{now()}] Step 1 READ   : {name} = {old} (version {ver})")
                    new = old + step
                    print(f"  [{now()}] Step 2 MODIFY : {old} + {step} = {new}")
                    reply = mem.write(name, new, ver)
                    print(f"  [{now()}] Step 3 WRITE  : {reply}")
                    if reply.startswith("OK"):
                        break
                    print("  Another node wrote first - retrying with the latest value ...")

            elif choice == "4":
                name = input("  Variable name > ").strip()
                value = int(input("  New value > ").strip())
                print(f"  [{now()}] {mem.write(name, value)}")

            elif choice == "5":
                rows = mem.list_all()
                print(f"\n  {'NAME':<12} {'VALUE':>8} {'VER':>4}  {'LAST WRITER':<15} TIME")
                print("  " + "-" * 52)
                for _, n, v, ver, w, t in rows:
                    print(f"  {n:<12} {v:>8} {ver:>4}  {w:<15} {t}")
                print(f"  Total: {len(rows)} variable(s)")

            elif choice == "6":
                mem.close()
                print("Detached from shared memory. Bye!")
                break
        except ValueError:
            print("  Please enter an integer value.")
        except OSError as e:
            print(f"  Lost connection to shared memory: {e}")
            break


if __name__ == "__main__":
    main()
