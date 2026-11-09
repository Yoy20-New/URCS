#!/usr/bin/env python3
"""
URCS Laptop Test Server & Arduino Serial Bridge

This utility allows testing the URCS Android app directly with:
1. Pure Laptop Mock Mode: Acts as a TCP robot server on your Wi-Fi network.
   Shows received motor, servo, stop commands, and sends telemetry/acks back to phone.
2. Arduino Serial Bridge Mode: Forwards TCP commands over USB Serial to an Arduino Uno/Nano
   and streams Arduino Serial output back to the URCS phone app.
"""

import sys
import socket
import threading
import time
import json
import argparse

def get_local_ip():
    """Get this laptop's local IP address on the Wi-Fi network."""
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        # doesn't have to be reachable
        s.connect(('10.255.255.255', 1))
        ip = s.getsockname()[0]
    except Exception:
        ip = '127.0.0.1'
    finally:
        s.close()
    return ip

def run_server(port=8080, serial_port=None, baudrate=115200):
    ser = None
    if serial_port:
        try:
            import serial
            ser = serial.Serial(serial_port, baudrate, timeout=0.1)
            print(f"[SERIAL] Connected to Arduino on {serial_port} at {baudrate} baud.")
        except Exception as e:
            print(f"[!] Could not open serial port {serial_port}: {e}")
            print("    Running in Pure Laptop Mock Server mode instead.\n")

    local_ip = get_local_ip()
    server_sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server_sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    server_sock.bind(('0.0.0.0', port))
    server_sock.listen(1)

    print("=======================================================")
    print("      URCS Robotics Test Server (Laptop Gateway)       ")
    print("=======================================================")
    print(f" -> Local Wi-Fi IP:  {local_ip}")
    print(f" -> Listening Port:   {port}")
    print("\n[INSTRUCTIONS FOR MOBILE APP]:")
    print(" 1. Make sure your phone and laptop are on the SAME Wi-Fi network.")
    print(" 2. In URCS Android App, open 'Connections' from the drawer menu.")
    print(f" 3. Enter Host: {local_ip}")
    print(f" 4. Enter Port: {port}")
    print(" 5. Tap 'Connect'.")
    print("=======================================================\n")
    print("[*] Waiting for incoming connection from URCS phone app...")

    while True:
        try:
            conn, addr = server_sock.accept()
            print(f"\n[+] Connected by phone at {addr[0]}:{addr[1]}")
            
            # Send initial greeting acknowledgment
            greeting = json.dumps({"type": "ack", "message": "Connected to Laptop URCS Test Server"}) + "\n"
            conn.sendall(greeting.encode('utf-8'))

            stop_threads = threading.Event()

            # Thread to send serial data from Arduino to Phone
            def serial_to_tcp():
                if not ser:
                    return
                while not stop_threads.is_set():
                    try:
                        line = ser.readline().decode('utf-8', errors='replace').strip()
                        if line:
                            print(f"[Arduino -> Phone] {line}")
                            conn.sendall((line + "\n").encode('utf-8'))
                    except Exception:
                        break

            # Thread to send periodic simulated telemetry if no Arduino is attached
            def telemetry_loop():
                dist = 25.0
                while not stop_threads.is_set():
                    time.sleep(2.0)
                    if stop_threads.is_set():
                        break
                    dist += (hash(time.time()) % 10 - 5) * 0.2
                    dist = max(5.0, min(80.0, dist))
                    msg = json.dumps({"type": "telemetry", "sensor": "ultrasonic", "value": round(dist, 1)}) + "\n"
                    try:
                        conn.sendall(msg.encode('utf-8'))
                    except Exception:
                        break

            if ser:
                t_ser = threading.Thread(target=serial_to_tcp, daemon=True)
                t_ser.start()
            else:
                t_telemetry = threading.Thread(target=telemetry_loop, daemon=True)
                t_telemetry.start()

            # Read incoming commands from Phone
            buffer = ""
            while True:
                data = conn.recv(1024)
                if not data:
                    print("[-] Phone disconnected.")
                    break
                buffer += data.decode('utf-8', errors='replace')
                while '\n' in buffer:
                    line, buffer = buffer.split('\n', 1)
                    line = line.strip()
                    if not line:
                        continue
                    
                    print(f"\n[Phone -> Robot] {line}")
                    try:
                        parsed = json.loads(line)
                        cmd_type = parsed.get("type")
                        if cmd_type == "motor":
                            print(f"   >>> MOTOR #{parsed.get('id')} => Speed: {parsed.get('speed')}")
                        elif cmd_type == "servo":
                            print(f"   >>> SERVO #{parsed.get('id')} => Angle: {parsed.get('angle')} deg")
                        elif cmd_type == "stop":
                            print("   >>> [EMERGENCY STOP] HALT ALL ACTUATORS!")
                            ack = json.dumps({"type": "ack", "message": "Emergency Stop Acknowledged"}) + "\n"
                            conn.sendall(ack.encode('utf-8'))
                    except Exception:
                        pass

                    # Forward to Arduino if connected
                    if ser:
                        ser.write((line + "\n").encode('utf-8'))

            stop_threads.set()
            conn.close()

        except KeyboardInterrupt:
            print("\nShutting down server.")
            break
        except Exception as e:
            print(f"[!] Server error: {e}")

    server_sock.close()
    if ser:
        ser.close()

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="URCS Robot Server & Serial Bridge")
    parser.add_argument("--port", type=int, default=8080, help="TCP port (default: 8080)")
    parser.add_argument("--serial", type=str, default=None, help="COM port for Arduino (e.g. COM3, COM7)")
    parser.add_argument("--baud", type=int, default=115200, help="Baud rate (default: 115200)")
    args = parser.parse_args()

    run_server(port=args.port, serial_port=args.serial, baudrate=args.baud)
