# URCS dashboard and settings decisions

The single-page Settings direction is approved and has been connected to the app.

## Dashboard

The Dashboard contains exactly three feature groups:

- **Sensors:** one monitoring switch, the saved sample interval, and selected sensor readings with the value on the left and a compact graph on the right.
- **Camera:** shared enabled state and quality preset.
- **Robot controls:** forward, backward, left, right, and saved sensitivity.

Other app areas remain accessible from the left slide-out navigation drawer.

## Settings

Settings stays on one scrollable page. It includes per-sensor selection, sample interval, camera enabled state and quality, dashboard robot-controls enabled state and sensitivity, appearance, and robot profiles. Sensor selection, camera options, robot controls, and sensitivity save through Android DataStore.

The existing Kotlin draft in [SettingsUiTemplates.kt](SettingsUiTemplates.kt) is an unconnected reference. The app implementation is in `app/src/main/java/com/robotics/urcs/ui/DataScreens.kt` and `app/src/main/java/com/robotics/urcs/settings/AppSettingsRepository.kt`.

## Platform constraints

- Mobile-data and radio-generation controls remain Android-owned; the app does not promise to switch 4G.
- Location stays foreground-only and is requested only when the user asks for coordinates.
- Brightness affects the URCS window; global brightness opens Android settings.
- Hardware safety remains on the robot controller; app controls are not a substitute for a hardware emergency stop.
