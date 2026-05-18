# Challenge #1: Modbus FC4 Mirroring Verification Guide

This guide provides a step-by-step procedure to verify the implementation of Challenge #1, which enables mirroring of Modbus Function Code 4 traffic to an observer host via AAS control.

## Prerequisites
- Kathara installed and configured.
- Docker running.
- The lab must be started: `kathara lstart --noterminals`

---

## Step-by-Step Test Procedure

### 1. Verify the Observer Host
**Command:**
```bash
kathara exec observer "tcpdump --version"
```
*   **What it does:** Checks if the observer node is using the correct image (`loriringhio97/p4`) and has the necessary tools installed.
*   **Expected Result:** Displays `tcpdump version 4.99.3`.
*   **Without Implementation:** The node would use `python:3.12-slim`, and the command would fail with `tcpdump: command not found`.

### 2. Verify the AAS Infrastructure
**Command:**
```bash
# Check if the AAS server is listening
Test-NetConnection -ComputerName localhost -Port 6001
```
*   **What it does:** Confirms the Java AAS application started successfully.
*   **Expected Result:** `TcpTestSucceeded : True`.
*   **Without Implementation:** The AAS JAR (compiled for Java 21) would crash on the Kathara node (Java 17) with a `LinkageError`, and the port would be closed.

### 3. Enable Mirroring via AAS
**Command (PowerShell):**
```powershell
Invoke-RestMethod -Uri "http://localhost:6001/aas/submodels/NetworkTopology/submodel/submodelElements/SetMirroring/invoke" -Method Post -ContentType "application/json" -Body '[1, true]'
```
*   **What it does:** Uses the Network Infrastructure AAS to tell Switch 1 to start mirroring FC4 traffic.
*   **Expected Result:** JSON response containing `"Adding entry to exact match table mirror_table"`.
*   **Without Implementation:** The `SetMirroring` operation would not exist, or the P4 compilation error would have prevented the switch from starting its CLI.

### 4. Capture and Trigger Traffic
**Command (Terminal 1 - Observer):**
```bash
kathara exec observer "tcpdump -n -i eth0 tcp port 502 -c 1 -w /shared/observer.pcap"
```
**Command (Terminal 2 - Client):**
```bash
kathara exec modbusclient "python modbus_ops.py fc4 --address 0 --count 1 --host 200.1.1.7 --port 502"
```
*   **What it does:** Starts a capture on the observer and sends a "Read Input Registers" (FC4) request from the client.
*   **Expected Result:** The client command finishes, and `tcpdump` exits after capturing 1 packet.

### 5. Inspect Mirrored Traffic
**Command:**
```bash
kathara exec observer "tcpdump -nn -r /shared/observer.pcap"
```
*   **What it does:** Reads the captured packet to verify it is the mirrored Modbus traffic.
*   **Expected Result:** Shows a packet from `195.11.14.5.XXXXX > 200.1.1.7.502`.
*   **Without Implementation:** The `observer.pcap` file would be empty or never created because the P4 code lacked the `clone` logic.

### 6. Disable Mirroring
**Command:**
```powershell
Invoke-RestMethod -Uri "http://localhost:6001/aas/submodels/NetworkTopology/submodel/submodelElements/SetMirroring/invoke" -Method Post -ContentType "application/json" -Body '[1, false]'
```
*   **What it does:** Tells the switch to clear the mirror table.
*   **Expected Result:** JSON response confirming `table_clear mirror_table`.

---

## Comparison Summary

| Feature | With Implementation (Current) | Without Implementation |
| :--- | :--- | :--- |
| **P4 Dataplane** | Compiles successfully; supports `clone(I2E)` and mirror sessions. | Fails to compile due to `clone_ingress_pkt_to_egress` error. |
| **AAS Support** | Full lifecycle control of mirroring via `SetMirroring` operation. | No mirroring control; AAS submodel is static or missing. |
| **Java Compatibility** | JAR runs on Java 17 (Kathara node). | JAR crashes due to Java 21/17 version mismatch. |
| **Observer Node** | Equipped with `tcpdump` and connected to `s1` port 3. | Standard Python image; no networking tools for analysis. |
| **Visibility** | FC4 traffic is visible to the observer for security monitoring. | Modbus traffic remains private between client and server. |
