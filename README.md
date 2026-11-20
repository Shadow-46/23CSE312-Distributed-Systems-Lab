# 23CSE312 – Distributed Systems Lab

**Roll No:** CH.SC.U4CSE24035
**Name:** _<your name>_

Source code for the 23CSE312 Distributed Systems lab exercises, in **Python** and **Java**.
Every program prints a banner with the roll number, hostname, local IP and start time, so each screenshot shows which machine it ran on.

The Python and Java versions of each program use the **same text protocol**, so they can talk to each other (e.g. a Java client can chat with a Python server).

| # | Exercise | Folder | Default port |
|---|----------|--------|--------------|
| 1 | Client-Server Communication (bidirectional chat) | [`Lab1_Ex1_Client_Server`](Lab1_Ex1_Client_Server) | 5000 |
| 2 | Peer-to-Peer File Listing | [`Lab1_Ex2_P2P_File_Listing`](Lab1_Ex2_P2P_File_Listing) | 6000 |
| 3 | Challenging Task 1 – Distributed Shared Memory | [`Challenging_Task1_DSM`](Challenging_Task1_DSM) | 7000 |

---

## 0. Before you start (do this on EVERY computer)

1. **Requirements:** Python 3.8+ and/or JDK 17+ (`python --version`, `javac -version`).
2. **Same network:** all computers must be on the same Wi-Fi / LAN.
3. **Find each computer's IP:**
   ```powershell
   ipconfig
   ```
   Use the `IPv4 Address` of the Wi-Fi/Ethernet adapter (e.g. `192.168.1.10`). The program banners also print it.
4. **Check connectivity:** from computer B, run `ping <IP of A>`.
5. **Allow the ports through Windows Firewall** on the computer that *listens* (run PowerShell **as Administrator**):
   ```powershell
   New-NetFirewallRule -DisplayName "DS Lab" -Direction Inbound -Protocol TCP -LocalPort 5000,6000,7000 -Action Allow
   ```
   (Or click **Allow access** when Windows pops up the firewall prompt the first time a server starts.)
   Remove it afterwards with `Remove-NetFirewallRule -DisplayName "DS Lab"`.

> Throughout this file, **A** = first computer (e.g. `192.168.1.10`), **B** = second computer (e.g. `192.168.1.20`). Replace them with your real IPs.

---

## 1. Client-Server Communication

A server and a client exchange messages **in both directions at the same time**: each side has a receiver thread, and the main thread sends whatever you type. Type `bye` on either side to end the chat.

### Python
| Computer A (server) | Computer B (client) |
|---|---|
| `cd Lab1_Ex1_Client_Server\python` | `cd Lab1_Ex1_Client_Server\python` |
| `python server.py` | `python client.py 192.168.1.10` |

### Java
| Computer A (server) | Computer B (client) |
|---|---|
| `cd Lab1_Ex1_Client_Server\java` | `cd Lab1_Ex1_Client_Server\java` |
| `javac Server.java` | `javac Client.java` |
| `java Server` | `java Client 192.168.1.10` |

Optional port argument: `python server.py 5050` / `python client.py <ip> 5050`.

**Screenshots to take:** both terminals showing the banner (two different IPs), the "Connected to Client (192.168.1.20:…)" line, messages going both ways, and `bye`.

---

## 2. Peer-to-Peer File Listing

Each **peer is both a server and a client**: a background thread serves the files in its shared folder, and the menu lets you ask another peer for its list. The list is read **live from the real folder** (`os.scandir` / `File.listFiles`) on every request, so there is no dummy data. Add or delete a file and list again to show this.

Default shared folder: `Lab1_Ex2_P2P_File_Listing/shared_folder` (you can pass any folder path instead).

### Python
Run on **both** A and B:
```powershell
cd Lab1_Ex2_P2P_File_Listing\python
python peer.py
```
To use a different folder: `python peer.py 6000 "C:\Users\you\Documents"`

### Java
Run on **both** A and B:
```powershell
cd Lab1_Ex2_P2P_File_Listing\java
javac Peer.java
java Peer
```
To use a different folder: `java Peer 6000 "C:\Users\you\Documents"`

### Demo steps
1. On B choose `2`, enter A's IP → B shows A's files. A's terminal logs `Request 'LIST' from peer 192.168.1.20`.
2. On A choose `2`, enter B's IP → A shows B's files.
3. Create a new file in A's shared folder and repeat step 1 → the new file appears (live listing).
4. Choose `1` to show your own files.

---

## 3. Challenging Task 1 – Distributed Shared Memory (DSM)

**Design:**
- `dsm_server` / `DSMServer` = the **shared memory manager**. It holds a table of named integer variables in memory.
- `dsm_node` / `DSMNode` = a **node** that reads and writes shared variables over the network.
- Every operation runs under a lock, so it is atomic. Every variable stores a **version number** and the **IP of the last writer**.
- "Increment" is a real **read → modify → write back**. The write carries the version that was read, and if another node wrote in between, the server rejects it (`ERR CONFLICT`) and the node retries. This keeps updates consistent.

### Step-by-step (2 computers)

**Computer A** – terminal 1 (memory manager):
```powershell
cd Challenging_Task1_DSM\python
python dsm_server.py
```
**Computer A** – terminal 2 (node A). Use A's **LAN IP**, not 127.0.0.1, so the "last writer" column shows the real IP:
```powershell
cd Challenging_Task1_DSM\python
python dsm_node.py 192.168.1.10
```
- Choose `1` → name `counter`, value `10` → `OK CREATED counter 10 1`

**Computer B** (node B):
```powershell
cd Challenging_Task1_DSM\python
python dsm_node.py 192.168.1.10
```
- Choose `2` → `counter` → `counter = 10 (version 1, last written by 192.168.1.10)`
- Choose `3` → `counter` → press Enter (increment by 1):
  ```
  Step 1 READ   : counter = 10 (version 1)
  Step 2 MODIFY : 10 + 1 = 11
  Step 3 WRITE  : OK WRITTEN counter 11 2
  ```

**Back on Computer A** (node A):
- Choose `2` → `counter` → `counter = 11 (version 2, last written by 192.168.1.20)` ✅ B's change is visible on A.
- Choose `5` to list all shared variables.

The server terminal logs every CREATE/READ/WRITE together with the IP of the node that sent it, which is useful as a screenshot.

### Java version
```powershell
cd Challenging_Task1_DSM\java
javac DSMServer.java DSMNode.java
java DSMServer                 # on A
java DSMNode 192.168.1.10      # on A (2nd terminal) and on B
```
Java and Python nodes can share the same memory manager.

---

## Pushing this repo to GitHub (proof of work)

Commits are made under **your own** name and GitHub account:
```powershell
git config --global user.name "Your Name"
git config --global user.email "you@example.com"
git add .
git commit -m "Add DS lab programs (CH.SC.U4CSE24035)"
gh repo create 23CSE312-Distributed-Systems-Lab --public --source . --push
```
(Or create an empty public repo on github.com and use `git remote add origin <url>` + `git push -u origin main`.)
Put screenshots in a `screenshots/` folder and commit them too.
