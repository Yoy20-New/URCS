# Troubleshooting

## Gradle reports Java 8 or an unsupported Java version

AGP 8.13 requires JDK 17. Set `JAVA_HOME` to Android Studio's bundled `jbr` folder or install JDK 17, then open a new PowerShell session.

## Gradle cannot find Android SDK

In Android Studio, install Android SDK Platform 36 and Build-Tools 36.0.0. Set `ANDROID_HOME` to the SDK directory and let Gradle generate `local.properties`, or set `sdk.dir` there to the local SDK path. Do not commit machine-specific `local.properties`.

## Gradle cannot write temporary files

Set `TEMP` and `TMP` to writable directories. In restricted environments, point `JAVA_TOOL_OPTIONS` to a writable `-Djava.io.tmpdir=...` directory and `GRADLE_USER_HOME` to a writable Gradle cache directory.

## Camera preview is blank

Confirm the device reports camera hardware and grant Camera permission from the Camera screen. CameraX uses the rear camera when available. Still images are saved under app-specific picture storage and may not appear in the public gallery.

## TCP connection fails

Confirm the phone and robot are on the same reachable network, the host and port are correct, the robot is listening, and its firewall allows the connection. The app uses a five-second connection timeout and displays the socket error. The transport is line-framed JSON over TCP.

## Sensor list or readings are empty

The sensor list comes directly from Android `SensorManager`; devices differ. Start monitoring and check whether that sensor reports live values. Unsupported sensors are not emulated.

## Install fails on a device

The minimum supported Android version is API 26. Check the device API level and available storage. For USB installation, enable USB debugging and authorize the connected computer.
