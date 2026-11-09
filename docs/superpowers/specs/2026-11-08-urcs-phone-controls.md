# URCS phone controls and custom robot buttons

## Context

The user tested the current APK successfully and identified missing camera, GPS/location, 4G, battery, time, display, and button controls. Existing app behavior is a phone camera preview/photo capture, Android sensor monitoring, a TCP robot client, directional motor controls, and basic settings.

## Product decision

Add a **Phone & Controls** experience that presents real phone state and offers only actions available to a regular Android app. Add camera controls to the existing local Camera screen and configurable on-screen robot action buttons to Controller. Clearly distinguish phone state from robot telemetry and phone camera from a remote robot camera.

## Scope

- Phone location: request foreground coarse/fine location at point of use, display actual latitude, longitude, accuracy, and update time, and provide a copy action. If permission or location services are unavailable, display that state and offer the Android Location Settings screen.
- Cellular/network: show actual active connectivity and the active transport; provide a shortcut to network settings. Do not infer 4G/5G from generic cellular connectivity. Android's radio-generation API requires privileged phone-state access or carrier privileges, and a regular app cannot toggle mobile data.
- Battery and clock: show current battery level/charging state and local time/date/timezone. Provide a shortcut to Date & time settings. The app does not claim it can set the system clock.
- Display: provide an in-app screen brightness slider and a shortcut to Android Display settings. Do not request global system-write access.
- Camera: add front/back selection, flash/torch control when supported, and local video record/stop with an honest recording status. Keep media app-private. This remains the phone camera; remote camera transport is outside this scope.
- Robot buttons: allow the user to add, rename, and remove labeled on-screen buttons mapped to validated motor, servo, or stop commands. Persist the list locally and send only while TCP is connected and manual control is not emergency-stopped.
- Dashboard: add a compact summary and navigation to the phone control panel.

## Out of scope / platform limits

- Directly enabling/disabling cellular data, silently turning GPS on, setting Android system time, globally changing hardware key mappings, and globally changing display settings are not available to an ordinary third-party app. Use clear status text and system-settings shortcuts where appropriate.
- GPS coordinates for a robot (rather than this phone) require real robot telemetry and a hardware/protocol decision; never label phone coordinates as robot coordinates.
- Remote robot camera streaming, voice commands, and new Bluetooth/USB transports are separate work.

## Permissions and privacy

- Ask for location permission only when the user opens location controls.
- Location updates run only while the screen is visible, use a modest update interval, and stop when leaving the screen.
- Do not upload or log location automatically. Copy/share happens only from an explicit user action.
- Camera/video stay local in app-specific storage; camera permission is requested on the Camera screen.

## Acceptance criteria

- UI never presents an unknown or denied value as a real reading.
- Location can show fresh coordinates, accuracy, and timestamp when granted and enabled; denied/off states include a clear settings action.
- Network and battery values reflect Android APIs; cellular generation is never guessed from transport type.
- Brightness changes only the URCS window; settings shortcuts open system-owned screens.
- Camera selector, flash, and video actions reflect actual camera capabilities and report failures.
- Custom robot buttons are validated, persisted, connected-only, and covered by core unit tests.
- Unit tests, debug APK build, and lint pass; device-specific functions remain identified as requiring phone verification.

## Platform references

- Radio-generation API permission requirement: https://developer.android.com/reference/android/telephony/TelephonyManager#getDataNetworkType()
- Mobile-data toggle permission requirement: https://developer.android.com/reference/android/telephony/TelephonyManager#setDataEnabled(boolean)
- Location permissions and update flow: https://developer.android.com/develop/sensors-and-location/location/request-updates
- User-mediated location settings: https://developer.android.com/develop/sensors-and-location/location/change-location-settings
- User-mediated system brightness access: https://developer.android.com/reference/android/provider/Settings.System#canWrite(android.content.Context)
- CameraX video capture: https://developer.android.com/media/camera/camerax/video-capture
