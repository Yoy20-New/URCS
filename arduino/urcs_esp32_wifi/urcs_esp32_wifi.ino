/*
 * URCS ESP32 / ESP8266 Robot Controller & Telemetry Gateway
 *
 * This sketch runs on an ESP32 or ESP8266 board programmed via Arduino IDE.
 * It provides:
 * 1. Wi-Fi Access Point (SSID: URCS-Robot, Password: none or configurable)
 *    IP Address: 192.168.4.1, Port: 8080 (Matches URCS default settings!)
 * 2. TCP Server accepting incoming URCS app / Ground Station connections.
 * 3. Bidirectional communication:
 *    - Receives URCS commands (motor, servo, stop) and drives actuators.
 *    - Safety watchdog halts all motors if no command arrives within 600ms.
 *    - Periodically sends robot telemetry to URCS app terminal.
 *    - Allows typing in Serial Monitor to send telemetry/messages to phone.
 */

#if defined(ESP32)
  #include <WiFi.h>
#elif defined(ESP8266)
  #include <ESP8266WiFi.h>
#else
  #error "This sketch requires an ESP32 or ESP8266 board. For Arduino Uno/Nano, use urcs_uno_serial.ino."
#endif

// --- Network Configuration ---
const char* AP_SSID = "URCS-Robot";
const char* AP_PASS = "";       // Open network (or set password e.g. "12345678")
const uint16_t TCP_PORT = 8080;

WiFiServer server(TCP_PORT);
WiFiClient client;

// Pin configurations (Built-in LED and optional Motor Driver pins)
#define LED_PIN 2   // Built-in LED on most ESP32/ESP8266 boards

// Optional Motor Driver Pins (e.g. L298N / DRV8833)
#define PIN_M1_IN1 18
#define PIN_M1_IN2 19
#define PIN_M2_IN1 22
#define PIN_M2_IN2 23

// Watchdog & Telemetry Timing
const unsigned long COMMAND_WATCHDOG_TIMEOUT_MS = 600;
unsigned long lastCommandMillis = 0;
unsigned long lastTelemetryMillis = 0;
bool motorsActive = false;
float simulatedDistanceCm = 25.0;

void setupMotors() {
  pinMode(PIN_M1_IN1, OUTPUT);
  pinMode(PIN_M1_IN2, OUTPUT);
  pinMode(PIN_M2_IN1, OUTPUT);
  pinMode(PIN_M2_IN2, OUTPUT);
  stopAllMotors();
}

void stopAllMotors() {
  digitalWrite(PIN_M1_IN1, LOW);
  digitalWrite(PIN_M1_IN2, LOW);
  digitalWrite(PIN_M2_IN1, LOW);
  digitalWrite(PIN_M2_IN2, LOW);
  motorsActive = false;
}

void setMotor(int id, int speed) {
  // speed: -255 to +255
  int in1 = (id == 1) ? PIN_M1_IN1 : PIN_M2_IN1;
  int in2 = (id == 1) ? PIN_M1_IN2 : PIN_M2_IN2;

  if (speed > 0) {
    digitalWrite(in1, HIGH);
    digitalWrite(in2, LOW);
    motorsActive = true;
  } else if (speed < 0) {
    digitalWrite(in1, LOW);
    digitalWrite(in2, HIGH);
    motorsActive = true;
  } else {
    digitalWrite(in1, LOW);
    digitalWrite(in2, LOW);
  }
}

void setup() {
  Serial.begin(115200);
  delay(1000);
  pinMode(LED_PIN, OUTPUT);
  digitalWrite(LED_PIN, LOW);
  setupMotors();

  Serial.println("\n==========================================");
  Serial.println("  URCS ESP32/ESP8266 Robot TCP Controller ");
  Serial.println("  Failsafe Watchdog: 600 ms Timeout       ");
  Serial.println("==========================================");

  // Set up Wi-Fi Access Point
  WiFi.mode(WIFI_AP);
  WiFi.softAP(AP_SSID, AP_PASS);

  IPAddress myIP = WiFi.softAPIP();
  Serial.print("1. Connect your phone/laptop to Wi-Fi: ");
  Serial.println(AP_SSID);
  Serial.print("2. In URCS app (Connections tab), Host: ");
  Serial.print(myIP);
  Serial.print("  Port: ");
  Serial.println(TCP_PORT);

  server.begin();
  server.setNoDelay(true);
  Serial.println("3. Server started! Waiting for connection...\n");
}

void loop() {
  // Check for incoming client connection (superseding stale connections)
  if (server.hasClient()) {
    WiFiClient newClient = server.available();
    if (client && client.connected()) {
      Serial.println("\n[INFO] Superseding existing client connection.");
      client.stop();
    }
    client = newClient;
    client.setNoDelay(true);
    Serial.println("\n>>> [CONNECTED] URCS Client connected! <<<");
    digitalWrite(LED_PIN, HIGH);
    client.println("{\"type\":\"ack\",\"message\":\"Connected to URCS ESP32 Robot\"}");
    lastCommandMillis = millis();
  }

  // Failsafe Watchdog: Halt all motors if no command received within timeout
  if (motorsActive && (millis() - lastCommandMillis > COMMAND_WATCHDOG_TIMEOUT_MS)) {
    Serial.println("[WATCHDOG] Command timeout (>600ms). Halting all motors for safety!");
    stopAllMotors();
  }

  // Handle incoming data from client
  if (client && client.connected()) {
    while (client.available()) {
      String line = client.readStringUntil('\n');
      line.trim();
      if (line.length() > 0) {
        lastCommandMillis = millis();
        processIncomingCommand(line);
      }
    }

    // Send periodic telemetry every 2 seconds
    if (millis() - lastTelemetryMillis > 2000) {
      lastTelemetryMillis = millis();
      
      // Simulate changing ultrasonic sensor range
      simulatedDistanceCm += (random(-20, 20) / 10.0);
      if (simulatedDistanceCm < 5.0) simulatedDistanceCm = 5.0;
      if (simulatedDistanceCm > 80.0) simulatedDistanceCm = 80.0;

      String telemetryMsg = "{\"type\":\"telemetry\",\"sensor\":\"ultrasonic\",\"value\":" + String(simulatedDistanceCm, 1) + "}";
      client.println(telemetryMsg);
      Serial.print("[TX -> Client] ");
      Serial.println(telemetryMsg);
    }
  } else {
    digitalWrite(LED_PIN, LOW);
    if (motorsActive) {
      stopAllMotors();
    }
  }

  // Handle manual input typed into Serial Monitor (send to Client)
  if (Serial.available()) {
    String input = Serial.readStringUntil('\n');
    input.trim();
    if (input.length() > 0) {
      if (client && client.connected()) {
        client.println(input);
        Serial.print("[TX -> Client] ");
        Serial.println(input);
      } else {
        Serial.println("[!] No client connected. Cannot send data.");
      }
    }
  }
}

// Parse and execute newline-delimited JSON commands from URCS
void processIncomingCommand(String json) {
  Serial.print("[RX <- Client] ");
  Serial.println(json);

  // 1. Motor Command: {"type":"motor","id":1,"speed":120}
  if (json.indexOf("\"type\":\"motor\"") >= 0) {
    int idIndex = json.indexOf("\"id\":");
    int speedIndex = json.indexOf("\"speed\":");
    if (idIndex > 0 && speedIndex > 0) {
      int motorId = json.substring(idIndex + 5).toInt();
      int speed = json.substring(speedIndex + 8).toInt();
      Serial.print("   -> Motor Command: Motor ");
      Serial.print(motorId);
      Serial.print(" @ Speed: ");
      Serial.println(speed);
      
      setMotor(motorId, speed);
      digitalWrite(LED_PIN, !digitalRead(LED_PIN));
    }
  }
  // 2. Servo Command: {"type":"servo","id":1,"angle":90}
  else if (json.indexOf("\"type\":\"servo\"") >= 0) {
    int idIndex = json.indexOf("\"id\":");
    int angleIndex = json.indexOf("\"angle\":");
    if (idIndex > 0 && angleIndex > 0) {
      int servoId = json.substring(idIndex + 5).toInt();
      int angle = json.substring(angleIndex + 8).toInt();
      Serial.print("   -> Servo Command: Servo ");
      Serial.print(servoId);
      Serial.print(" -> Angle: ");
      Serial.print(angle);
      Serial.println(" deg");
    }
  }
  // 3. Stop Command: {"type":"stop"}
  else if (json.indexOf("\"type\":\"stop\"") >= 0) {
    Serial.println("   -> [!!! EMERGENCY STOP !!!] All Motors Stopped.");
    stopAllMotors();
    digitalWrite(LED_PIN, LOW);
    if (client && client.connected()) {
      client.println("{\"type\":\"ack\",\"message\":\"Emergency stop executed\"}");
    }
  }
}
