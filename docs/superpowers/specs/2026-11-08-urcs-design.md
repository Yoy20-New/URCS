# URCS Design

## Intent

Build URCS as a reusable Android robotics workstation for a phone-sized device. The empty repository and lack of a detected Android SDK/JDK mean the first deliverable must establish both a usable project and a reproducible build path before expanding hardware support.

## Goals

- Kotlin, Jetpack Compose, Material 3, package `com.robotics.urcs`, minimum API 26.
- A cohesive navigation shell for dashboard, sensors, controller, connections, camera, automation, logging, terminal, and settings.
- Show real Android sensor inventory and readings; never imply a robot is connected without a real connection.
- Provide usable protocol, motion, automation, profile, and logging primitives with tests.
- Document build, protocol, permissions, limitations, and the exact verification state.
- Produce `output/URCS-debug.apk` when an Android toolchain can be used.

## Non-goals and safety boundaries

- Do not claim compatibility with physical robots that have not been tested.
- A phone emergency-stop is best-effort and is not machinery safety protection.
- No remote camera streaming or speech-driven motion without a complete, permission-aware implementation.
- Avoid presenting unsupported sensors or connections as available.

## Constraints

- Windows 11, target phone Redmi 9 Prime, app min SDK 26.
- Workspace starts empty except for Git metadata; no Android SDK, Gradle, Android Studio, or usable Java 17+ runtime was found on PATH/common Android Studio paths.
- Keep project artifacts in this repository. Avoid changing system-wide software settings.
- The newest official Android 17 API 37 platform is preview-only in this command-line toolchain, so stable Compose and lifecycle artifacts that build on API 36 are pinned with AGP 8.13.2 and Gradle 8.13.
- The user explicitly requested implementation, tests, lint, APK verification, and progress tracking; routine technical decisions are delegated to the implementer.

## Approaches considered

1. **Full breadth up front:** build Bluetooth Classic, BLE, Wi-Fi, USB, CameraX, voice, automation, profiles, and all UI before the first build. Rejected because it delays the first working build and makes hardware and permission failures difficult to isolate.
2. **Vertical slices (selected):** establish the Gradle/Compose app, then implement and verify real sensors, communication primitives, control/protocol logic, and local logging/settings in increments. Add optional platform-dependent features after a stable core. This best matches the requested incremental milestones and makes unsupported hardware explicit.
3. **UI-only mock:** rejected because it would violate the requirement for real system data and real communication.

## Architecture

A single Android app module with focused Kotlin packages: `core`, `sensors`, `communication`, `robotics`, `automation`, `logging`, `profiles`, and `ui`. Compose screens observe lifecycle-aware state. Android platform APIs stay at the boundary; pure protocol, motion mapping, rule validation, and serialization logic remain unit-testable. Persist lightweight preferences and profiles locally. Start with TCP and Bluetooth capability discovery; add BLE/USB support where APIs and permission handling can be verified.

## Builder decisions

- Begin with the most complete reliable vertical slice the available environment permits; continue toward the requested breadth rather than treating a screen as a finished feature.
- Use a pinned Gradle wrapper and Android plugin/Compose BOM versions verified against official documentation at implementation time.
- Keep protocol JSON explicit and bounded; malformed or unsupported messages must fail with a readable error.
- Implement only real device/hardware states; clearly label configuration-only or unavailable capabilities.

## Success criteria

- Android Studio-importable project with the requested package and minimum SDK.
- Unit tests cover core parsing, control mapping, and automation validation.
- Build, tests, lint, and APK existence are reported only when actually verified.
- Required docs and a concise progress ledger reflect completed, partial, untested, and blocked work.
