# Permissions

The manifest requests:

- `INTERNET` and `ACCESS_NETWORK_STATE` for the configured TCP connection.
- `CAMERA` for local CameraX preview, photo capture, flash, and silent video capture. URCS asks at the point of use. Denial leaves the rest of the app available.
- `ACCESS_COARSE_LOCATION` and `ACCESS_FINE_LOCATION` for foreground phone coordinates. URCS requests them only when the user taps Get coordinates, and stops location updates when leaving Phone Controls.

URCS does not request microphone, Bluetooth, or USB permissions because those integrations are not implemented. It does not request background location, mobile-data control, system-clock control, or global display-write access. The app stores logs and captured images in app-specific storage; it does not request broad shared-storage access.

The Connections screen does not claim Bluetooth or USB readiness. Future implementations must request the correct runtime permissions for each Android version and handle denial without crashing.
