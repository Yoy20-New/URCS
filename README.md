# 🤖 URCS — Universal Robotics Control System

[![Android CI](https://img.shields.io/badge/Android-API%2026%2B-brightgreen.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.22-blue.svg)](https://kotlinlang.org)
[![Python](https://img.shields.io/badge/Python-3.8%2B-yellow.svg)](https://python.org)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

**URCS** transforms any Android smartphone into an autonomous robotics sensor hub, camera streamer, and control station. It bridges Android hardware (IMU sensors, GPS, CameraX, battery status) with microcontrollers (ESP32, Arduino Uno/Mega) and desktop ground stations over Wi-Fi and TCP.

---

## 🌟 Key Features

* 📹 **Built-In Inbuilt Camera Streamer (`:8081`)**: Direct HTTP MJPEG video streaming (`/video`) and on-demand snapshots (`/snapshot`). Compatible with OpenCV (`cv2.VideoCapture`), VLC, and standard web browsers without needing 3rd-party apps like IP Webcam or DroidCam.
* ⚡ **Unbroken Background Execution**: Powered by an Android Foreground Service with partial CPU WakeLocks and Wi-Fi Locks. Sensor polling, video streaming, and TCP telemetry remain active when the phone screen turns off or the app is minimized.
* 🧭 **Real-Time Sensor & Attitude Telemetry**: Broadcasts Accelerometer, Gyroscope, Magnetometer, Pitch, Roll, GPS Coordinates, and Battery capacity up to 30 Hz.
* 🛡️ **Embedded Safety Watchdog**: Arduino and ESP32 firmware implement a 600 ms failsafe timeout that automatically halts all motors if communication drops.
* 🖥️ **Modern Python Desktop Ground Station**: Includes `tools/urcs_phone_monitor.py` featuring live sensor sparklines, 3D attitude readouts, Google Maps GPS link, quick dual-motor controls, and MJPEG video streaming.
* 🔌 **Bidirectional JSON Protocol**: Human-readable, newline-delimited JSON commands for driving DC motors, servos, emergency stop, and sensor streaming.

---

## 📱 Quickstart for Testers & Consumers

If you want to test the app without compiling code:

1. **Download the APK**:
   * Grab the latest `URCS-debug.apk` from the [Releases](https://github.com/YOUR_USERNAME/URCS/releases) tab.
2. **Install on Phone**:
   * Open the `.apk` on your Android device (Android 8.0+ supported).
   * Allow *"Install unknown apps"* if prompted.
   * If Google Play Protect displays a warning, select **More details ➔ Install anyway**.
3. **Connect & Stream**:
   * Ensure your phone and laptop/robot are on the same Wi-Fi network (or connect to ESP32 AP `URCS-Robot`).
   * Note the IP displayed on the URCS Camera card (e.g. `192.168.1.5`).
   * View live stream in any browser: `http://<PHONE_IP>:8081/video`
   * Open the Python Ground Station to monitor telemetry in real time!

---

## 💻 Quickstart for Developers

### 1. Run the Python Desktop Ground Station
```bash
# Install dependencies
pip install opencv-python pillow numpy

# Run the Ground Station GUI
python tools/urcs_phone_monitor.py
```
* Telemetry server listens on port `8080`.
* In URCS Phone App ➔ **Connections**, enter your laptop's IP address and Port `8080`, then toggle **Connect**.

### 2. Microcontroller Setup (ESP32 or Arduino)
* **ESP32 Wi-Fi Gateway**:
  * Open `arduino/urcs_esp32_wifi/urcs_esp32_wifi.ino` in Arduino IDE.
  * Flashes a Wi-Fi Access Point (`URCS-Robot`, IP `192.168.4.1:8080`) with built-in motor driver pins and safety watchdog.
* **Arduino Uno / Nano (USB Serial)**:
  * Open `arduino/urcs_uno_serial/urcs_uno_serial.ino` in Arduino IDE.
  * Connect via the USB serial-to-TCP bridge in `tools/urcs_laptop_bridge.py`.

### 3. Building the Android App from Source
Requires JDK 17 and Android SDK (API 35):
```bash
# Clone the repository
git clone https://github.com/YOUR_USERNAME/URCS.git
cd URCS

# Run unit tests
./gradlew test

# Build debug APK
./gradlew assembleDebug

# Output will be located at:
# app/build/outputs/apk/debug/app-debug.apk
```

---

## 📂 Repository Structure

```
URCS/
├── app/                       # Android application (Jetpack Compose, CameraX, Foreground Service)
│   └── src/main/java/com/robotics/urcs/
│       ├── camera/            # Inbuilt MJPEG HTTP streaming server (:8081)
│       ├── service/           # UrcsForegroundService (WakeLock + WifiLock)
│       ├── communication/     # Asynchronous TcpTransport client
│       ├── device/            # Sensor, Battery & GPS repositories
│       └── ui/                # Modern Material 3 UI screens
├── core/                      # Kotlin Multiplatform / JVM core library
│   └── src/main/java/com/robotics/urcs/
│       └── communication/     # RobotCommand, RobotMessage, ProtocolCodec
├── arduino/                   # Embedded microcontroller firmware
│   ├── urcs_esp32_wifi/       # ESP32 Wi-Fi AP + TCP controller + 600ms watchdog
│   └── urcs_uno_serial/       # Arduino Uno/Nano USB serial controller
├── tools/                     # Desktop tooling & Ground Station
│   ├── urcs_phone_monitor.py  # Python Tkinter + OpenCV Ground Station GUI
│   └── urcs_laptop_bridge.py  # TCP-to-Serial forwarding bridge
├── output/                    # Precompiled release/debug APKs
└── docs/                      # Technical specifications & architecture guides
```

---

## 📡 Hardware Communication Protocol

URCS communicates using standard newline-delimited JSON (`\n`):

### Motor Control
```json
{"type":"motor","id":1,"speed":120}
{"type":"motor","id":2,"speed":-120}
```

### Servo Control
```json
{"type":"servo","id":1,"angle":90}
```

### Emergency Stop
```json
{"type":"stop"}
```

### Live Telemetry (Phone ➔ Robot / Laptop)
```json
{
  "type": "phone_telemetry",
  "battery": {"level": 85, "charging": false},
  "gps": {"latitude": 37.7749, "longitude": -122.4194, "accuracy": 4.5},
  "sensors": {
    "accelerometer": [0.12, 9.81, 0.05],
    "gyroscope": [0.01, -0.02, 0.00]
  }
}
```

---

## 🤝 Feedback & Community Testing

We welcome bug reports, hardware integrations, and feature suggestions!

* **Found a bug?** Open an issue using our [Bug Report Template](.github/ISSUE_TEMPLATE/bug_report.md).
* **Want a feature?** Submit an idea with our [Feature Request Template](.github/ISSUE_TEMPLATE/feature_request.md).
* **Questions / Showcases?** Join our [GitHub Discussions](https://github.com/YOUR_USERNAME/URCS/discussions).

---

## 📄 License
This project is licensed under the MIT License.
