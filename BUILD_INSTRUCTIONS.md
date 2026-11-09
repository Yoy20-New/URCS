# Build and install

## Requirements

- Windows 11 with Android Studio and its bundled JBR, or a standalone JDK 17.
- Android SDK Platform 36 and Android SDK Build-Tools 36.0.0.
- Network access the first time Gradle resolves dependencies.
- This project pins Gradle 8.13, Android Gradle Plugin 8.13.2, Kotlin 2.2.21, Compose BOM 2026.06.00, and minimum Android API 26.

## Build from PowerShell

From the project root:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
.\gradlew.bat test
.\gradlew.bat :app:lintDebug
.\gradlew.bat :app:assembleDebug
New-Item -ItemType Directory -Force output | Out-Null
Copy-Item app\build\outputs\apk\debug\app-debug.apk output\URCS-debug.apk
Get-Item output\URCS-debug.apk | Select-Object FullName,Length
```

If Android Studio is installed elsewhere, set `JAVA_HOME` to its `jbr` folder. Set `ANDROID_HOME` to the SDK path shown in Android Studio's SDK Manager. The build uses `local.properties` for the SDK path; this machine-specific file is ignored by Git.

The first wrapper run downloads Gradle 8.13 into the Gradle user cache. Gradle then downloads the pinned Maven dependencies. Android Studio can also open the root project and run the same Gradle tasks.

## Install on Redmi 9 Prime

1. Enable Developer options and USB debugging on the phone.
2. Connect the phone and accept its debugging prompt.
3. Install with `adb install -r output\URCS-debug.apk`, or copy the APK to the phone and open it.
4. Grant camera permission only when opening the Camera screen.

The APK is a debug build signed with the generated debug key. No release signing configuration is included.
