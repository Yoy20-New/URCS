# Architecture

URCS is a single Android application module plus a JVM-only `core` module.

- `core/communication`: strict JSON command/telemetry codec.
- `core/robotics`: bounded joystick mapping and dead-zone/sensitivity behavior.
- `core/automation`: validation and safe threshold evaluation with a hardware-connected guard.
- `core/profiles`: validated robot profile JSON import/export.
- `core/logging`: CSV field escaping.
- `app/sensors`: Android `SensorManager` discovery and listener lifecycle.
- `app/device`: battery and active-network status plus foreground-only `LocationManager` updates.
- `app/communication`: coroutine-backed TCP client and connection state.
- `app/settings`: DataStore preferences and profile persistence.
- `app/logging`: app-private session files and export.
- `app/ui`: Compose navigation and destination screens; CameraX is used for local preview, photo capture, and video; device controls use system settings intents and app-window brightness.

The UI passes Android-specific state to pure core logic. Sensor and location listeners are registered only while their screens are active. TCP input uses a background coroutine and line framing. Profiles, preferences, and validated custom robot buttons are persisted locally; automation rules remain in memory for the current app process.

## Runtime data flow

```text
Android SensorManager -> SensorRepository -> StateFlow -> Sensor UI / logger / threshold rules
Android LocationManager -> foreground Phone Controls -> coordinate display/copy
ConnectivityManager + BatteryManager -> Phone Controls status
Compose controls -> ProtocolCodec -> TCP client -> newline-delimited JSON -> robot firmware
robot firmware -> newline-delimited JSON -> TCP client -> terminal / logger / telemetry map
```

The phone does not expose a hardware-level emergency-stop guarantee. The firmware must independently enforce motor limits, timeouts, and emergency-stop protection. Phone location is separate from robot telemetry. Android restricts mobile-data, radio-generation, system-clock, global brightness, and hardware-key control for ordinary third-party apps.
