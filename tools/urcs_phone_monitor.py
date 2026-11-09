#!/usr/bin/env python3
"""
URCS Phone Monitor & Robotics Ground Station
============================================

Monitors your smartphone's:
- Live Sensors (Accelerometer, Gyroscope, Magnetometer, Pitch & Roll)
- Real-time GPS Coordinates (Latitude, Longitude, Accuracy + Google Maps link)
- Battery & Network status
- Built-in Inbuilt Camera Stream (Direct HTTP/MJPEG on port 8081 - NO 3rd party apps required!)
- High-Resolution Snapshot Capture (downloads single JPEG on demand)
- Continuous Background Execution & Telemetry Streaming
- Bidirectional Robot Control Commands (Motor, Servo, Emergency Stop)

Requirements:
    pip install opencv-python pillow numpy
"""

import os
import sys
import time
import math
import json
import socket
import threading
import urllib.request
import webbrowser
import tkinter as tk
from tkinter import ttk, messagebox
import cv2
from PIL import Image, ImageTk

# --- Color Theme ---
BG_DARK = "#121212"
CARD_BG = "#1E1E1E"
ACCENT_GREEN = "#00E676"
ACCENT_BLUE = "#2979FF"
ACCENT_ORANGE = "#FF9100"
ACCENT_RED = "#FF1744"
TEXT_MAIN = "#FFFFFF"
TEXT_MUTED = "#9E9E9E"
BORDER_COLOR = "#333333"

class PhoneTelemetryData:
    """Thread-safe data holder for phone telemetry."""
    def __init__(self):
        self.lock = threading.Lock()
        self.connected = False
        self.client_ip = "None"
        self.last_update = 0
        self.packet_count = 0
        self.packet_rate_hz = 0.0
        self._last_rate_time = time.time()
        self._rate_packet_count = 0
        
        # Battery & System
        self.battery_level = 0
        self.charging = False
        self.network = "Unknown"
        
        # GPS
        self.gps_lat = None
        self.gps_lon = None
        self.gps_acc = None
        self.gps_time = 0
        
        # Sensors
        self.accel = [0.0, 0.0, 0.0]
        self.gyro = [0.0, 0.0, 0.0]
        self.mag = [0.0, 0.0, 0.0]
        self.pitch = 0.0
        self.roll = 0.0
        
        # Recent raw commands
        self.recent_commands = []

    def update_from_json(self, data: dict, client_ip: str):
        now = time.time()
        with self.lock:
            self.connected = True
            self.client_ip = client_ip
            self.last_update = now
            self.packet_count += 1
            self._rate_packet_count += 1

            # Recalculate packet rate every second
            dt = now - self._last_rate_time
            if dt >= 1.0:
                self.packet_rate_hz = self._rate_packet_count / dt
                self._rate_packet_count = 0
                self._last_rate_time = now
            
            # Check for phone_telemetry type
            if data.get("type") == "phone_telemetry":
                # Battery
                batt = data.get("battery", {})
                if isinstance(batt, dict):
                    self.battery_level = batt.get("level", self.battery_level)
                    self.charging = batt.get("charging", self.charging)
                elif isinstance(batt, (int, float)):
                    self.battery_level = int(batt)

                # GPS
                gps = data.get("gps", {})
                if gps and isinstance(gps, dict):
                    self.gps_lat = gps.get("latitude")
                    self.gps_lon = gps.get("longitude")
                    self.gps_acc = gps.get("accuracy")
                    self.gps_time = gps.get("timestamp", now)

                # Sensors
                sensors = data.get("sensors", {})
                if "accelerometer" in sensors:
                    self.accel = [float(v) for v in sensors["accelerometer"][:3]]
                    ax, ay, az = self.accel
                    # Calculate Pitch & Roll from accelerometer
                    self.pitch = math.degrees(math.atan2(ay, math.sqrt(ax*ax + az*az))) if (ax*ax + az*az) > 0 else 0
                    self.roll = math.degrees(math.atan2(-ax, az)) if az != 0 else 0
                if "gyroscope" in sensors:
                    self.gyro = [float(v) for v in sensors["gyroscope"][:3]]
                if "magnetic" in sensors:
                    self.mag = [float(v) for v in sensors["magnetic"][:3]]

                # Log periodic telemetry status to console (every 2.5s)
                if not hasattr(self, '_last_print_time') or now - self._last_print_time >= 2.5:
                    self._last_print_time = now
                    gps_str = f"({self.gps_lat:.4f}, {self.gps_lon:.4f})" if self.gps_lat is not None else "No GPS fix"
                    print(f"[Telemetry RX] Rate: {self.packet_rate_hz:.1f}Hz | Batt: {self.battery_level}% | Accel: [{self.accel[0]:.1f}, {self.accel[1]:.1f}, {self.accel[2]:.1f}] | GPS: {gps_str}")

            # Standard URCS commands
            cmd_type = data.get("type")
            if cmd_type in ("motor", "servo", "stop", "telemetry"):
                print(f"[Command RX] {json.dumps(data)}")
                self.recent_commands.append(f"{time.strftime('%H:%M:%S')} - {json.dumps(data)}")
                if len(self.recent_commands) > 100:
                    self.recent_commands.pop(0)


class TelemetryServer(threading.Thread):
    """Background TCP server listening for URCS app connections."""
    def __init__(self, data_store: PhoneTelemetryData, host="0.0.0.0", port=8080):
        super().__init__(daemon=True)
        self.data_store = data_store
        self.host = host
        self.port = port
        self.running = True
        self.active_client_sock = None

    def run(self):
        sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        sock.bind((self.host, self.port))
        sock.listen(2)
        sock.settimeout(1.0)
        
        while self.running:
            try:
                conn, addr = sock.accept()
                with self.data_store.lock:
                    self.data_store.connected = True
                    self.data_store.client_ip = addr[0]
                self.active_client_sock = conn
                
                # Send welcome ack
                try:
                    conn.sendall(b'{"type":"ack","message":"URCS Python GroundStation Ready"}\n')
                except Exception:
                    pass
                
                buf = ""
                conn.settimeout(2.0)
                while self.running:
                    try:
                        raw = conn.recv(2048)
                        if not raw:
                            break
                        buf += raw.decode("utf-8", errors="replace")
                        while "\n" in buf:
                            line, buf = buf.split("\n", 1)
                            line = line.strip()
                            if not line:
                                continue
                            try:
                                parsed = json.loads(line)
                                self.data_store.update_from_json(parsed, addr[0])
                            except json.JSONDecodeError:
                                # Raw text / line
                                with self.data_store.lock:
                                    self.data_store.recent_commands.append(f"{time.strftime('%H:%M:%S')} [RAW] {line}")
                    except socket.timeout:
                        continue
                    except Exception:
                        break
                        
                conn.close()
                self.active_client_sock = None
                with self.data_store.lock:
                    self.data_store.connected = False
            except socket.timeout:
                continue
            except Exception:
                if self.running:
                    time.sleep(1.0)
        sock.close()

    def send_to_phone(self, text: str):
        if self.active_client_sock:
            try:
                if not text.endswith("\n"):
                    text += "\n"
                self.active_client_sock.sendall(text.encode("utf-8"))
                return True
            except Exception:
                return False
        return False

    def stop(self):
        self.running = False


class CameraStreamThread(threading.Thread):
    """Thread for grabbing video frames from URCS built-in MJPEG stream or OpenCV source."""
    def __init__(self, stream_url):
        super().__init__(daemon=True)
        self.stream_url = stream_url
        self.running = True
        self.current_frame = None
        self.lock = threading.Lock()
        self.connected = False
        self.error_msg = ""
        self.fps = 0.0
        self._fps_count = 0
        self._fps_time = time.time()

    def run(self):
        source = int(self.stream_url) if str(self.stream_url).isdigit() else self.stream_url
        cap = None
        
        while self.running:
            if cap is None or not cap.isOpened():
                cap = cv2.VideoCapture(source)
                if not cap.isOpened():
                    self.connected = False
                    self.error_msg = f"Connecting to: {self.stream_url}...\nEnsure URCS camera is open on phone."
                    time.sleep(1.5)
                    continue

            self.connected = True
            ret, frame = cap.read()
            if ret:
                now = time.time()
                self._fps_count += 1
                if now - self._fps_time >= 1.0:
                    self.fps = self._fps_count / (now - self._fps_time)
                    self._fps_count = 0
                    self._fps_time = now

                with self.lock:
                    self.current_frame = frame
                    self.error_msg = ""
            else:
                time.sleep(0.05)
                # Try reconnecting if network stream
                if isinstance(source, str) and ("http" in source or "rtsp" in source):
                    if cap is not None:
                        cap.release()
                    cap = None
                    time.sleep(1.0)
                    
        if cap is not None:
            cap.release()

    def get_frame(self):
        with self.lock:
            return self.current_frame.copy() if self.current_frame is not None else None

    def stop(self):
        self.running = False


class UrcsMonitorGUI:
    """Modern Desktop GUI for URCS Phone Monitoring."""
    def __init__(self, root, telemetry: PhoneTelemetryData, server: TelemetryServer):
        self.root = root
        self.telemetry = telemetry
        self.server = server
        self.camera_thread = None
        self.last_connected_ip = ""
        
        self.root.title("URCS Ground Control & Phone Monitor (Inbuilt Stream & Background Live)")
        self.root.geometry("1150x760")
        self.root.minsize(980, 680)
        self.root.configure(bg=BG_DARK)
        
        self.local_ip = self.get_local_ip()
        self.snapshots_dir = os.path.join(os.path.dirname(os.path.abspath(__file__)), "snapshots")
        os.makedirs(self.snapshots_dir, exist_ok=True)
        
        self.setup_ui()
        self.update_telemetry_loop()

    def get_local_ip(self):
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        try:
            s.connect(("10.255.255.255", 1))
            ip = s.getsockname()[0]
        except Exception:
            ip = "127.0.0.1"
        finally:
            s.close()
        return ip

    def setup_ui(self):
        # Header Bar
        header = tk.Frame(self.root, bg=CARD_BG, height=54, padx=16, pady=8)
        header.pack(fill=tk.X, side=tk.TOP)
        
        title_lbl = tk.Label(header, text="URCS GROUND CONTROL", font=("Helvetica", 14, "bold"), fg=TEXT_MAIN, bg=CARD_BG)
        title_lbl.pack(side=tk.LEFT)

        self.ip_lbl = tk.Label(header, text=f"Telemetry Server: {self.local_ip}:8080", font=("Consolas", 10), fg=TEXT_MUTED, bg=CARD_BG)
        self.ip_lbl.pack(side=tk.LEFT, padx=16)

        self.rate_lbl = tk.Label(header, text="0.0 Hz", font=("Consolas", 9), fg=ACCENT_GREEN, bg=CARD_BG)
        self.rate_lbl.pack(side=tk.LEFT, padx=8)

        self.status_pill = tk.Label(header, text="WAITING FOR PHONE", font=("Helvetica", 9, "bold"), bg="#37474F", fg=TEXT_MAIN, padx=10, pady=3)
        self.status_pill.pack(side=tk.RIGHT)

        # Main Layout (Panels)
        main_pane = tk.Frame(self.root, bg=BG_DARK, padx=12, pady=10)
        main_pane.pack(fill=tk.BOTH, expand=True)

        # Left Column: Sensors, GPS, Battery, Quick Controls
        left_col = tk.Frame(main_pane, bg=BG_DARK, width=420)
        left_col.pack(side=tk.LEFT, fill=tk.BOTH, expand=False, padx=(0, 10))

        # Right Column: Camera Preview & Command Terminal
        right_col = tk.Frame(main_pane, bg=BG_DARK)
        right_col.pack(side=tk.RIGHT, fill=tk.BOTH, expand=True)

        # --- Card 1: Phone Status & GPS ---
        gps_card = tk.LabelFrame(left_col, text=" PHONE TELEMETRY & GPS ", font=("Helvetica", 9, "bold"),
                                 bg=CARD_BG, fg=ACCENT_BLUE, padx=12, pady=8, relief=tk.FLAT, highlightbackground=BORDER_COLOR, highlightthickness=1)
        gps_card.pack(fill=tk.X, pady=(0, 8))

        # Battery Bar
        tk.Label(gps_card, text="Battery Status:", font=("Helvetica", 9), fg=TEXT_MUTED, bg=CARD_BG).grid(row=0, column=0, sticky="w", pady=2)
        self.batt_lbl = tk.Label(gps_card, text="--%", font=("Helvetica", 10, "bold"), fg=ACCENT_GREEN, bg=CARD_BG)
        self.batt_lbl.grid(row=0, column=1, sticky="e", pady=2)

        # GPS Coordinates
        tk.Label(gps_card, text="GPS Latitude:", font=("Helvetica", 9), fg=TEXT_MUTED, bg=CARD_BG).grid(row=1, column=0, sticky="w", pady=2)
        self.lat_lbl = tk.Label(gps_card, text="Not available", font=("Consolas", 10), fg=TEXT_MAIN, bg=CARD_BG)
        self.lat_lbl.grid(row=1, column=1, sticky="e", pady=2)

        tk.Label(gps_card, text="GPS Longitude:", font=("Helvetica", 9), fg=TEXT_MUTED, bg=CARD_BG).grid(row=2, column=0, sticky="w", pady=2)
        self.lon_lbl = tk.Label(gps_card, text="Not available", font=("Consolas", 10), fg=TEXT_MAIN, bg=CARD_BG)
        self.lon_lbl.grid(row=2, column=1, sticky="e", pady=2)

        tk.Label(gps_card, text="GPS Accuracy:", font=("Helvetica", 9), fg=TEXT_MUTED, bg=CARD_BG).grid(row=3, column=0, sticky="w", pady=2)
        self.acc_lbl = tk.Label(gps_card, text="--", font=("Consolas", 10), fg=TEXT_MAIN, bg=CARD_BG)
        self.acc_lbl.grid(row=3, column=1, sticky="e", pady=2)

        self.maps_btn = tk.Button(gps_card, text="📍 Open in Google Maps", font=("Helvetica", 8, "bold"),
                                  bg="#263238", fg=ACCENT_BLUE, relief=tk.FLAT, cursor="hand2", command=self.open_maps)
        self.maps_btn.grid(row=4, column=0, columnspan=2, sticky="ew", pady=(6, 2))

        # --- Card 2: Live IMU Sensors ---
        sensor_card = tk.LabelFrame(left_col, text=" ONBOARD SENSORS (IMU) ", font=("Helvetica", 9, "bold"),
                                    bg=CARD_BG, fg=ACCENT_GREEN, padx=12, pady=8, relief=tk.FLAT, highlightbackground=BORDER_COLOR, highlightthickness=1)
        sensor_card.pack(fill=tk.BOTH, expand=True, pady=(0, 8))

        # Accelerometer
        tk.Label(sensor_card, text="Accelerometer (m/s²)", font=("Helvetica", 9, "bold"), fg=TEXT_MAIN, bg=CARD_BG).pack(anchor="w", pady=(1, 1))
        self.accel_x_bar = self.create_bar(sensor_card, "X:")
        self.accel_y_bar = self.create_bar(sensor_card, "Y:")
        self.accel_z_bar = self.create_bar(sensor_card, "Z:")

        # Gyroscope
        tk.Label(sensor_card, text="Gyroscope (rad/s)", font=("Helvetica", 9, "bold"), fg=TEXT_MAIN, bg=CARD_BG).pack(anchor="w", pady=(6, 1))
        self.gyro_x_bar = self.create_bar(sensor_card, "Roll:")
        self.gyro_y_bar = self.create_bar(sensor_card, "Pitch:")
        self.gyro_z_bar = self.create_bar(sensor_card, "Yaw:")

        # Attitude Angle Readout
        attitude_frame = tk.Frame(sensor_card, bg="#181818", pady=4, padx=8)
        attitude_frame.pack(fill=tk.X, pady=(6, 2))
        self.pitch_lbl = tk.Label(attitude_frame, text="Pitch: 0.0°", font=("Consolas", 9, "bold"), fg=ACCENT_ORANGE, bg="#181818")
        self.pitch_lbl.pack(side=tk.LEFT, expand=True)
        self.roll_lbl = tk.Label(attitude_frame, text="Roll: 0.0°", font=("Consolas", 9, "bold"), fg=ACCENT_BLUE, bg="#181818")
        self.roll_lbl.pack(side=tk.RIGHT, expand=True)

        # --- Card 2.5: Quick Robot Control Pad ---
        ctrl_card = tk.LabelFrame(left_col, text=" QUICK ROBOT CONTROLS ", font=("Helvetica", 9, "bold"),
                                  bg=CARD_BG, fg=ACCENT_ORANGE, padx=8, pady=6, relief=tk.FLAT, highlightbackground=BORDER_COLOR, highlightthickness=1)
        ctrl_card.pack(fill=tk.X)

        btn_row1 = tk.Frame(ctrl_card, bg=CARD_BG)
        btn_row1.pack(fill=tk.X, pady=2)
        tk.Button(btn_row1, text="▲ Forward", font=("Helvetica", 8, "bold"), bg="#2E7D32", fg=TEXT_MAIN, relief=tk.FLAT,
                  command=lambda: self.send_motors(80, 80)).pack(side=tk.LEFT, expand=True, fill=tk.X, padx=2)
        tk.Button(btn_row1, text="▼ Backward", font=("Helvetica", 8, "bold"), bg="#37474F", fg=TEXT_MAIN, relief=tk.FLAT,
                  command=lambda: self.send_motors(-80, -80)).pack(side=tk.LEFT, expand=True, fill=tk.X, padx=2)

        btn_row2 = tk.Frame(ctrl_card, bg=CARD_BG)
        btn_row2.pack(fill=tk.X, pady=2)
        tk.Button(btn_row2, text="◄ Left", font=("Helvetica", 8, "bold"), bg="#37474F", fg=TEXT_MAIN, relief=tk.FLAT,
                  command=lambda: self.send_motors(-60, 60)).pack(side=tk.LEFT, expand=True, fill=tk.X, padx=2)
        tk.Button(btn_row2, text="► Right", font=("Helvetica", 8, "bold"), bg="#37474F", fg=TEXT_MAIN, relief=tk.FLAT,
                  command=lambda: self.send_motors(60, -60)).pack(side=tk.LEFT, expand=True, fill=tk.X, padx=2)

        btn_row3 = tk.Frame(ctrl_card, bg=CARD_BG)
        btn_row3.pack(fill=tk.X, pady=(3, 1))
        tk.Button(btn_row3, text="🛑 EMERGENCY STOP", font=("Helvetica", 9, "bold"), bg=ACCENT_RED, fg=TEXT_MAIN, relief=tk.FLAT,
                  command=lambda: self.send_raw('{"type":"stop"}')).pack(side=tk.LEFT, expand=True, fill=tk.X, padx=2)
        tk.Button(btn_row3, text="💡 Torch", font=("Helvetica", 8, "bold"), bg="#455A64", fg=TEXT_MAIN, relief=tk.FLAT,
                  command=lambda: self.send_raw('{"type":"device","action":"torch"}')).pack(side=tk.LEFT, padx=2)

        # --- Card 3: Inbuilt Camera Monitor ---
        cam_card = tk.LabelFrame(right_col, text=" URCS INBUILT CAMERA STREAM (PORT 8081) ", font=("Helvetica", 9, "bold"),
                                 bg=CARD_BG, fg=ACCENT_ORANGE, padx=10, pady=6, relief=tk.FLAT, highlightbackground=BORDER_COLOR, highlightthickness=1)
        cam_card.pack(fill=tk.BOTH, expand=True, pady=(0, 8))

        # Camera URL Input toolbar
        cam_bar = tk.Frame(cam_card, bg=CARD_BG)
        cam_bar.pack(fill=tk.X, pady=(0, 4))

        tk.Label(cam_bar, text="Stream URL:", font=("Helvetica", 8, "bold"), fg=TEXT_MUTED, bg=CARD_BG).pack(side=tk.LEFT)
        self.cam_url_entry = tk.Entry(cam_bar, font=("Consolas", 9), bg="#262626", fg=TEXT_MAIN, insertbackground=TEXT_MAIN, relief=tk.FLAT, width=32)
        self.cam_url_entry.insert(0, "http://127.0.0.1:8081/video")
        self.cam_url_entry.pack(side=tk.LEFT, padx=6)

        self.cam_toggle_btn = tk.Button(cam_bar, text="Connect Stream", font=("Helvetica", 8, "bold"),
                                        bg=ACCENT_BLUE, fg="#000000", relief=tk.FLAT, cursor="hand2", command=self.toggle_camera)
        self.cam_toggle_btn.pack(side=tk.LEFT, padx=3)

        self.snapshot_btn = tk.Button(cam_bar, text="📷 Save Snapshot", font=("Helvetica", 8, "bold"),
                                      bg="#37474F", fg=TEXT_MAIN, relief=tk.FLAT, cursor="hand2", command=self.save_snapshot)
        self.snapshot_btn.pack(side=tk.LEFT, padx=3)

        self.auto_connect_cam = tk.BooleanVar(value=True)
        tk.Checkbutton(cam_bar, text="Auto-Connect", variable=self.auto_connect_cam, font=("Helvetica", 8),
                       bg=CARD_BG, fg=TEXT_MUTED, selectcolor=BG_DARK, activebackground=CARD_BG, activeforeground=TEXT_MAIN).pack(side=tk.RIGHT)

        # Video Display Canvas
        self.video_canvas = tk.Label(cam_card, bg="#000000",
                                     text="URCS Inbuilt Camera Stream\n\nWhen phone connects, live video on port 8081 streams automatically.\nNo external apps like IP Webcam or DroidCam needed!",
                                     font=("Helvetica", 10), fg=TEXT_MUTED)
        self.video_canvas.pack(fill=tk.BOTH, expand=True)

        # --- Card 4: Terminal & Robot Commands ---
        cmd_card = tk.LabelFrame(right_col, text=" ROBOT COMMAND CONSOLE & BACKGROUND LOG ", font=("Helvetica", 9, "bold"),
                                 bg=CARD_BG, fg=TEXT_MUTED, padx=10, pady=6, relief=tk.FLAT, highlightbackground=BORDER_COLOR, highlightthickness=1)
        cmd_card.pack(fill=tk.BOTH, expand=True)

        self.cmd_listbox = tk.Listbox(cmd_card, font=("Consolas", 9), bg="#141414", fg=ACCENT_GREEN, selectbackground="#333", relief=tk.FLAT, height=5)
        self.cmd_listbox.pack(fill=tk.BOTH, expand=True, side=tk.LEFT)

        scrollbar = tk.Scrollbar(cmd_card, orient=tk.VERTICAL, command=self.cmd_listbox.yview)
        scrollbar.pack(side=tk.RIGHT, fill=tk.Y)
        self.cmd_listbox.config(yscrollcommand=scrollbar.set)

        # Send command bar
        send_bar = tk.Frame(cmd_card, bg=CARD_BG)
        send_bar.pack(fill=tk.X, pady=(4, 0))
        self.send_entry = tk.Entry(send_bar, font=("Consolas", 9), bg="#262626", fg=TEXT_MAIN, insertbackground=TEXT_MAIN, relief=tk.FLAT)
        self.send_entry.pack(side=tk.LEFT, fill=tk.X, expand=True, padx=(0, 6))
        self.send_entry.bind("<Return>", lambda e: self.send_command())

        send_btn = tk.Button(send_bar, text="Send to Phone", font=("Helvetica", 8, "bold"), bg="#37474F", fg=TEXT_MAIN, relief=tk.FLAT, command=self.send_command)
        send_btn.pack(side=tk.RIGHT)

    def create_bar(self, parent, label_text):
        frame = tk.Frame(parent, bg=CARD_BG)
        frame.pack(fill=tk.X, pady=1)
        tk.Label(frame, text=label_text, font=("Consolas", 8, "bold"), fg=TEXT_MUTED, bg=CARD_BG, width=6, anchor="w").pack(side=tk.LEFT)
        val_lbl = tk.Label(frame, text="0.00", font=("Consolas", 8), fg=TEXT_MAIN, bg=CARD_BG, width=8, anchor="e")
        val_lbl.pack(side=tk.RIGHT)
        pbar = ttk.Progressbar(frame, orient=tk.HORIZONTAL, length=180, mode="determinate", maximum=100)
        pbar.pack(side=tk.RIGHT, fill=tk.X, expand=True, padx=4)
        return (val_lbl, pbar)

    def open_maps(self):
        with self.telemetry.lock:
            lat = self.telemetry.gps_lat
            lon = self.telemetry.gps_lon
        if lat is not None and lon is not None:
            url = f"https://www.google.com/maps?q={lat},{lon}"
            webbrowser.open(url)
        else:
            messagebox.showinfo("GPS Location", "No GPS fix received from phone yet.\nEnsure GPS permission is granted in URCS Settings.")

    def toggle_camera(self):
        if self.camera_thread and self.camera_thread.running:
            self.camera_thread.stop()
            self.camera_thread = None
            self.cam_toggle_btn.config(text="Connect Stream", bg=ACCENT_BLUE)
            self.video_canvas.config(image="", text="Camera stream disconnected.\nClick 'Connect Stream' to reconnect.")
        else:
            url = self.cam_url_entry.get().strip()
            if not url:
                return
            self.cam_toggle_btn.config(text="Disconnect", bg=ACCENT_RED)
            self.camera_thread = CameraStreamThread(url)
            self.camera_thread.start()

    def save_snapshot(self):
        """Dispatches snapshot grabbing to a background thread to prevent UI freezing."""
        threading.Thread(target=self._async_save_snapshot, daemon=True).start()

    def _async_save_snapshot(self):
        """Grabs a JPEG snapshot from camera stream or phone /snapshot endpoint."""
        # 1. First try grabbing from active stream
        if self.camera_thread and self.camera_thread.running:
            frame = self.camera_thread.get_frame()
            if frame is not None:
                fname = f"snapshot_{time.strftime('%Y%m%d_%H%M%S')}.jpg"
                path = os.path.join(self.snapshots_dir, fname)
                cv2.imwrite(path, frame)
                self.root.after(0, lambda: self._on_snapshot_success(path))
                return

        # 2. If stream not running, try downloading directly from phone's /snapshot endpoint
        client_ip = self.telemetry.client_ip
        if client_ip and client_ip != "None":
            snapshot_url = f"http://{client_ip}:8081/snapshot"
            try:
                req = urllib.request.Request(snapshot_url, headers={"User-Agent": "URCS-Python"})
                with urllib.request.urlopen(req, timeout=3.0) as resp:
                    data = resp.read()
                    fname = f"snapshot_{time.strftime('%Y%m%d_%H%M%S')}.jpg"
                    path = os.path.join(self.snapshots_dir, fname)
                    with open(path, "wb") as f:
                        f.write(data)
                    self.root.after(0, lambda: self._on_snapshot_success(path))
                    return
            except Exception as e:
                self.root.after(0, lambda: messagebox.showerror("Snapshot Error", f"Could not download snapshot from {snapshot_url}:\n{e}"))
                return

        self.root.after(0, lambda: messagebox.showwarning("No Camera", "Please connect camera stream or wait for phone connection to take snapshots."))

    def _on_snapshot_success(self, path):
        self.cmd_listbox.insert(tk.END, f"{time.strftime('%H:%M:%S')} [Snapshot] Saved to {path}")
        self.cmd_listbox.yview_moveto(1)
        messagebox.showinfo("Snapshot Saved", f"Captured high-res snapshot:\n{path}")

    def send_motors(self, left: int, right: int):
        """Sends standardized URCS dual-motor commands (ID 1 = Left, ID 2 = Right)."""
        self.send_raw(json.dumps({"type": "motor", "id": 1, "speed": left}))
        self.send_raw(json.dumps({"type": "motor", "id": 2, "speed": right}))

    def send_raw(self, cmd_json_str: str):
        success = self.server.send_to_phone(cmd_json_str)
        if success:
            self.cmd_listbox.insert(tk.END, f"{time.strftime('%H:%M:%S')} [TX -> Phone] {cmd_json_str}")
            self.cmd_listbox.yview_moveto(1)
        else:
            self.cmd_listbox.insert(tk.END, f"{time.strftime('%H:%M:%S')} [TX FAIL] No phone connected.")
            self.cmd_listbox.yview_moveto(1)

    def send_command(self):
        txt = self.send_entry.get().strip()
        if txt:
            self.send_raw(txt)
            self.send_entry.delete(0, tk.END)

    def update_telemetry_loop(self):
        # 1. Update Connection Status Pill
        with self.telemetry.lock:
            conn = self.telemetry.connected
            client_ip = self.telemetry.client_ip
            rate_hz = self.telemetry.packet_rate_hz
            batt = self.telemetry.battery_level
            charging = self.telemetry.charging
            lat = self.telemetry.gps_lat
            lon = self.telemetry.gps_lon
            acc = self.telemetry.gps_acc
            ax, ay, az = self.telemetry.accel
            gx, gy, gz = self.telemetry.gyro
            pitch = self.telemetry.pitch
            roll = self.telemetry.roll
            cmds = list(self.telemetry.recent_commands)
            self.telemetry.recent_commands.clear()

        if conn:
            self.status_pill.config(text=f"CONNECTED ({client_ip})", bg="#1B5E20", fg=ACCENT_GREEN)
            self.rate_lbl.config(text=f"{rate_hz:.1f} Hz (Background Telemetry)")
            
            # Auto populate camera URL if phone IP changed
            if client_ip != "None" and client_ip != self.last_connected_ip:
                self.last_connected_ip = client_ip
                inbuilt_url = f"http://{client_ip}:8081/video"
                curr = self.cam_url_entry.get().strip()
                if "127.0.0.1" in curr or "192.168.1.16" in curr or not curr:
                    self.cam_url_entry.delete(0, tk.END)
                    self.cam_url_entry.insert(0, inbuilt_url)
                
                # Auto start camera if enabled
                if self.auto_connect_cam.get() and (not self.camera_thread or not self.camera_thread.running):
                    self.toggle_camera()
        else:
            self.status_pill.config(text="WAITING FOR PHONE", bg="#37474F", fg="#B0BEC5")
            self.rate_lbl.config(text="0.0 Hz")

        # 2. Battery
        chg_str = " (⚡ Charging)" if charging else ""
        self.batt_lbl.config(text=f"{batt}%{chg_str}")

        # 3. GPS
        if lat is not None and lon is not None:
            self.lat_lbl.config(text=f"{lat:.6f}°")
            self.lon_lbl.config(text=f"{lon:.6f}°")
            self.acc_lbl.config(text=f"±{acc:.1f} m" if acc else "Unknown")
            self.maps_btn.config(state=tk.NORMAL)
        else:
            self.lat_lbl.config(text="Waiting for fix...")
            self.lon_lbl.config(text="Waiting for fix...")
            self.acc_lbl.config(text="--")

        # 4. Accelerometer Bars (normalized to [-20, 20] -> [0, 100])
        def norm_val(v, span=20.0):
            return int(min(100, max(0, ((v + span) / (2 * span)) * 100)))

        self.accel_x_bar[0].config(text=f"{ax:.2f}")
        self.accel_x_bar[1]["value"] = norm_val(ax)
        self.accel_y_bar[0].config(text=f"{ay:.2f}")
        self.accel_y_bar[1]["value"] = norm_val(ay)
        self.accel_z_bar[0].config(text=f"{az:.2f}")
        self.accel_z_bar[1]["value"] = norm_val(az)

        # 5. Gyroscope Bars (normalized to [-10, 10] -> [0, 100])
        self.gyro_x_bar[0].config(text=f"{gx:.2f}")
        self.gyro_x_bar[1]["value"] = norm_val(gx, 10.0)
        self.gyro_y_bar[0].config(text=f"{gy:.2f}")
        self.gyro_y_bar[1]["value"] = norm_val(gy, 10.0)
        self.gyro_z_bar[0].config(text=f"{gz:.2f}")
        self.gyro_z_bar[1]["value"] = norm_val(gz, 10.0)

        # 6. Attitude
        self.pitch_lbl.config(text=f"Pitch: {pitch:.1f}°")
        self.roll_lbl.config(text=f"Roll: {roll:.1f}°")

        # 7. Recent Commands in Listbox
        for cmd in cmds:
            self.cmd_listbox.insert(tk.END, cmd)
            self.cmd_listbox.yview_moveto(1)

        # 8. Video Frame Update
        if self.camera_thread and self.camera_thread.running:
            frame = self.camera_thread.get_frame()
            if frame is not None:
                h, w, _ = frame.shape
                target_w = max(320, self.video_canvas.winfo_width())
                target_h = max(240, self.video_canvas.winfo_height())
                scale = min(target_w / w, target_h / h)
                nw, nh = max(1, int(w * scale)), max(1, int(h * scale))
                resized = cv2.resize(frame, (nw, nh))
                rgb = cv2.cvtColor(resized, cv2.COLOR_BGR2RGB)
                img = Image.fromarray(rgb)
                imgtk = ImageTk.PhotoImage(image=img)
                self.video_canvas.imgtk = imgtk
                self.video_canvas.config(image=imgtk, text="")
            elif self.camera_thread.error_msg:
                self.video_canvas.config(image="", text=self.camera_thread.error_msg)

        # Loop every 100ms
        self.root.after(100, self.update_telemetry_loop)


def main():
    print("==========================================================")
    print("   URCS Python Ground Station & Inbuilt Phone Monitor     ")
    print("==========================================================")
    print("Features:")
    print(" - Inbuilt Camera Stream: Direct HTTP/MJPEG on port 8081")
    print(" - High-Res Snapshot Download (/snapshot endpoint)")
    print(" - Continuous Background Telemetry (Sensors, GPS, Battery)")
    print(" - Quick Controls & Bidirectional Command Console")
    print("----------------------------------------------------------")
    
    data_store = PhoneTelemetryData()
    server = TelemetryServer(data_store, host="0.0.0.0", port=8080)
    server.start()

    root = tk.Tk()
    app = UrcsMonitorGUI(root, data_store, server)

    def on_closing():
        server.stop()
        if app.camera_thread:
            app.camera_thread.stop()
        root.destroy()
        sys.exit(0)

    root.protocol("WM_DELETE_WINDOW", on_closing)
    root.mainloop()

if __name__ == "__main__":
    main()
