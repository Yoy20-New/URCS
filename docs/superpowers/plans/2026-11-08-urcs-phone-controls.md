# URCS phone controls and custom buttons implementation plan

> **For agentic workers:** Follow the repository's TDD workflow. Keep each task independently reviewable and update its checkbox after verification.

**Goal:** Add practical phone status/settings controls, useful local camera controls, and configurable on-screen robot buttons without claiming privileged Android capabilities.

**Architecture:** Add a lifecycle-bound Android phone-status repository for battery, network, clock, and foreground location; keep validation and custom-button serialization in `core`; extend Compose destinations and settings persistence in `app`. Use Android system Settings intents for privileged controls.

**Tech stack:** Kotlin, Jetpack Compose, Android APIs, CameraX, DataStore, Gson, JUnit.

**Spec:** `docs/superpowers/specs/2026-11-08-urcs-phone-controls.md`.

## Files and responsibilities

- `core/.../device/PhoneStatus.kt`: pure formatting/state labels for battery, time, coordinates, and active network transport.
- `core/.../robotics/CustomRobotButton.kt`: custom button model, command validation/codec.
- `app/.../device/AndroidPhoneStatusRepository.kt`: Android battery/network/location observers and lifecycle cleanup.
- `app/.../ui/DeviceControlsScreen.kt`: phone state, permission/settings affordances, brightness control.
- `app/.../ui/CameraScreen.kt`: camera facing, torch, local video capture.
- `app/.../settings/AppSettingsRepository.kt`: durable custom robot button list.
- `app/.../ui/PrimaryScreens.kt`: custom robot button editor and connected send actions.
- `app/.../ui/UrcsApp.kt`, `core/.../ui/AppSection.kt`: navigation/dashboard integration.
- `app/src/main/AndroidManifest.xml`, `PERMISSIONS.md`: minimum permissions and user-facing rationale.
- `README.md`, `HARDWARE_PROTOCOL.md`, `PROGRESS.md`: feature boundaries and verification status.

## Tasks

### 1. Core status labels and custom-button format

- [x] Add failing tests for active-network labels, battery/charging labels, timestamp formatting, and custom-button input validation/round-trip.
- [x] Run `:core:test`; new tests failed on the missing phone status/custom button APIs.
- [x] Implement small pure Kotlin models/formatters and validated JSON encoding for motor/servo/stop button actions.
- [x] Run `:core:test`; new formatter/codec tests pass with the core suite.

### 2. Phone status and controls

- [x] Add focused pure-state tests for coordinates, unavailable battery, active-network labels, and clock formatting.
- [x] Add on-demand location permission flow, foreground-only location updates, and actual battery/active-network/time collection. Do not request phone-state permission for radio generation because the public API requires privileged/carrier access.
- [x] Add Device Controls UI with location copy, Settings shortcuts, app-window brightness slider, and clear access-denied/unavailable labels.
- [x] Integrate the screen into navigation and show a compact actual-state summary on Dashboard.
- [x] Update manifest and permission documentation; verify no background location or unnecessary global write permission is requested.

### 3. Camera controls

- [x] Add CameraX binding for available front/back camera, flash/torch capability state, image capture, and video recording lifecycle.
- [x] Add controls and visible status/errors; save images/videos only to app-specific storage.
- [x] Manually inspect camera lifecycle paths in code and ensure unbind/record-stop on leaving the screen.
- [x] Cover torch pending, failed, and successful completion state; ignore stale callbacks after lens change or screen disposal.
- [x] Check that camera status labels retain valid Unicode text.

### 4. Configurable robot buttons

- [x] Add failing tests for custom button validation, command bounds, JSON round-trip, and malformed stored values.
- [x] Implement DataStore persistence for the button list and add/rename/remove editor.
- [x] Add buttons to Controller; gate sends on live TCP state and emergency-stop latch; record failures visibly.
- [x] Verify command JSON matches `HARDWARE_PROTOCOL.md`.

### 5. Verification and handoff

- [x] Run `test`, `:app:lintDebug`, and `:app:assembleDebug`; fix actionable failures.
- [x] Refresh `output/URCS-debug.apk`, verify package metadata/hash, and update `PROGRESS.md`.
- [x] Verify README, permissions, camera, and robot telemetry wording keeps phone state distinct from remote hardware.

## Review focus

- Permission denial, approximate-only location, location services disabled, and no first fix must remain understandable and non-crashing.
- Location and telephony callbacks must be removed when the screen stops.
- Active network transport does not prove 4G/5G generation; never infer a radio generation from generic cellular connectivity.
- Camera lens or flash may be absent; unsupported controls must disable or explain themselves.
- Video stop, camera unbind, and app disposal must release hardware resources.
- Stored custom-button JSON may be stale or malformed; ignore invalid entries safely.
- Buttons must not send if disconnected or stop-latched; arbitrary user text must not bypass command validation.
