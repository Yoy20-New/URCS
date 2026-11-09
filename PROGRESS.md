# URCS development progress

Last updated: 2026-11-08

## Delivered

- Android Studio project with Kotlin/Compose app module, JVM core module, Gradle wrapper, and package `com.robotics.urcs`.
- Ten navigation destinations covering dashboard, sensor lab, controller, connections, phone controls, camera, automation, data logger, terminal, and settings.
- App branding: supplied URCS mark is used in the in-app header and as a density-aware legacy/adaptive launcher icon.
- Phone Controls: real battery and active-network status, local date/time/time zone, foreground phone GPS coordinates with permission handling and copy action, Android settings shortcuts, and an in-app brightness slider.
- Camera: local preview, front/back selection, flash/torch where the device supports it, photo capture, and silent video record/stop. Media stays in app-specific storage.
- Camera bind callbacks are guarded against stale/disposed screens, and a front-only camera is selected when no back lens exists.
- Flash state follows CameraX's async result; failures keep the last known state, and stale lens/screen callbacks are ignored.
- Controller: saved custom on-screen buttons for validated motor, servo, and stop JSON commands; add, rename, remove; DataStore persistence; connection and stop-latch gating.
- Existing sensor monitoring, TCP client, motor controls, terminal, local logs/CSV/JSON export, TCP profiles, preferences, and threshold-to-stop automation retained.
- Platform constraints are explicit in UI/docs: no silent mobile-data, GPS-service, system-time, global-brightness, or physical phone-button remapping.

## Verification

- `test :app:lintDebug :app:assembleDebug`: passed.
- Unit tests: 38 executions across core and Android debug/release variants; 0 failures, 0 errors, 0 skipped (from the preceding feature build; not rerun for this logo-only update).
- Android Lint: 0 errors, 8 warnings, 5 hints (from the preceding feature build; not rerun for this logo-only update).
- Logo update build: `:app:assembleDebug` passed.
- Debug APK: `output/URCS-debug.apk`, 17,500,673 bytes, SHA-256 `EF2BCE02886ACA9D9D7E0E124D10B8F5474ED40E08D36505806126868EDDF2D1`.
- APK metadata checked: package `com.robotics.urcs`, version `1.0.0`, min API 26, target API 35, compile API 36.
- No Android device was connected for this expansion, so the new location, video, torch, brightness, and custom-button flows still need a phone smoke test.

## Scope limits

Bluetooth Classic, BLE, UDP, TCP server, USB serial, voice commands, and remote robot-camera streaming are not implemented. Cellular connectivity is shown without guessing 4G/5G radio generation; Android owns mobile-data toggles and system time. Phone GPS is not robot GPS. The brightness slider affects URCS only, and robot custom buttons do not remap the phone's physical buttons. Physical robot compatibility remains untested; a phone stop command is not an independent machinery safety device.
