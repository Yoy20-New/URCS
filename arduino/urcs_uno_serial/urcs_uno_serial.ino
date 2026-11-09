/*
 * URCS Arduino Uno / Nano / Mega USB Serial Controller
 *
 * This sketch runs on any standard Arduino board (Uno, Nano, Mega, Leonardo).
 * It listens on Serial (115200 baud) for URCS commands forwarded from the laptop
 * via the Python TCP-to-Serial bridge (tools/urcs_laptop_bridge.py).
 *
 * It parses newline-delimited JSON commands, controls motor pins, implements a
 * 600ms safety watchdog, and sends periodic JSON telemetry back.
 */

#define LED_PIN 13

// Optional Motor Driver Pins (e.g. L298N on Arduino Uno)
#define PIN_M1_IN1 5
#define PIN_M1_IN2 6
#define PIN_M2_IN1 9
#define PIN_M2_IN2 10

const unsigned long COMMAND_WATCHDOG_TIMEOUT_MS = 600;
unsigned long lastCommandMillis = 0;
unsigned long lastTelemetryMillis = 0;
bool motorsActive = false;
float simulatedBatteryVoltage = 12.4;

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
  pinMode(LED_PIN, OUTPUT);
  digitalWrite(LED_PIN, LOW);
  setupMotors();

  // Send ready JSON message (clean JSON for bridge)
  Serial.println(F("{\"type\":\"ack\",\"message\":\"URCS Arduino Serial Controller Ready\"}"));
}

void loop() {
  // Read incoming newline-delimited command from Serial (from phone/laptop bridge)
  if (Serial.available()) {
    String line = Serial.readStringUntil('\n');
    line.trim();
    if (line.length() > 0) {
      lastCommandMillis = millis();
      processCommand(line);
    }
  }

  // Failsafe Watchdog: Halt all motors if no command received within timeout
  if (motorsActive && (millis() - lastCommandMillis > COMMAND_WATCHDOG_TIMEOUT_MS)) {
    stopAllMotors();
    digitalWrite(LED_PIN, LOW);
  }

  // Send periodic telemetry every 3 seconds to phone/client
  if (millis() - lastTelemetryMillis > 3000) {
    lastTelemetryMillis = millis();
    simulatedBatteryVoltage -= 0.01;
    if (simulatedBatteryVoltage < 10.5) simulatedBatteryVoltage = 12.6;

    // Send clean JSON telemetry line
    Serial.print(F("{\"type\":\"telemetry\",\"sensor\":\"battery_voltage\",\"value\":"));
    Serial.print(simulatedBatteryVoltage, 2);
    Serial.println(F("}"));
  }
}

void processCommand(String json) {
  // 1. Motor Command: {"type":"motor","id":1,"speed":120}
  if (json.indexOf("\"type\":\"motor\"") >= 0) {
    int idIndex = json.indexOf("\"id\":");
    int speedIndex = json.indexOf("\"speed\":");
    if (idIndex > 0 && speedIndex > 0) {
      int motorId = json.substring(idIndex + 5).toInt();
      int speed = json.substring(speedIndex + 8).toInt();
      setMotor(motorId, speed);
      digitalWrite(LED_PIN, HIGH);
    }
  }
  // 2. Servo Command: {"type":"servo","id":1,"angle":90}
  else if (json.indexOf("\"type\":\"servo\"") >= 0) {
    int idIndex = json.indexOf("\"id\":");
    int angleIndex = json.indexOf("\"angle\":");
    if (idIndex > 0 && angleIndex > 0) {
      int servoId = json.substring(idIndex + 5).toInt();
      int angle = json.substring(angleIndex + 8).toInt();
      // Handle servo angle
      digitalWrite(LED_PIN, !digitalRead(LED_PIN));
    }
  }
  // 3. Stop Command: {"type":"stop"}
  else if (json.indexOf("\"type\":\"stop\"") >= 0) {
    stopAllMotors();
    digitalWrite(LED_PIN, LOW);
    Serial.println(F("{\"type\":\"ack\",\"message\":\"Robot stopped successfully\"}"));
  }
}
