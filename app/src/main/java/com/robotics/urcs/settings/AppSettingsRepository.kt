package com.robotics.urcs.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.JsonArray
import com.google.gson.JsonParser
import com.robotics.urcs.profiles.ProfileCodec
import com.robotics.urcs.profiles.ProfileDecodeResult
import com.robotics.urcs.profiles.RobotProfile
import com.robotics.urcs.robotics.CustomRobotButton
import com.robotics.urcs.robotics.CustomRobotButtonCodec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "urcs_settings")

data class AppSettings(
    val theme: String = "System",
    val samplePeriodMillis: Int = 50,
    val sensitivity: Float = 0.7f,
    val profiles: List<RobotProfile> = emptyList(),
    val activeProfileName: String = "",
    val customButtons: List<CustomRobotButton> = emptyList(),
    val selectedSensorTypes: Set<Int>? = null,
    val cameraEnabled: Boolean = true,
    val cameraQuality: String = "HD",
    val dashboardControlsEnabled: Boolean = true,
    val mirrorCamera: Boolean = false,
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false,
    val cameraOrientation: String = "Portrait",
    val gpsEnabled: Boolean = false,
    val gpsUpdateIntervalSeconds: Int = 2,
    val coordinateFormat: String = "Decimal",
    val timeFormat24Hours: Boolean = true,
    val keepScreenAwake: Boolean = false,
    val appBrightness: Float = 1f,
    val useSystemBrightness: Boolean = true,
    val maxMotorPercent: Float = 100f
)

class AppSettingsRepository(context: Context) {
    private val store = context.applicationContext.settingsDataStore
    private object Keys {
        val theme = stringPreferencesKey("theme")
        val samplePeriod = intPreferencesKey("sample_period_ms")
        val sensitivity = floatPreferencesKey("control_sensitivity")
        val profiles = stringPreferencesKey("profiles_json")
        val activeProfile = stringPreferencesKey("active_profile")
        val customButtons = stringPreferencesKey("custom_robot_buttons")
        val selectedSensorTypes = stringPreferencesKey("selected_sensor_types")
        val cameraEnabled = booleanPreferencesKey("camera_enabled")
        val cameraQuality = stringPreferencesKey("camera_quality")
        val dashboardControlsEnabled = booleanPreferencesKey("dashboard_controls_enabled")
        val mirrorCamera = booleanPreferencesKey("mirror_camera")
        val flipHorizontal = booleanPreferencesKey("flip_horizontal")
        val flipVertical = booleanPreferencesKey("flip_vertical")
        val cameraOrientation = stringPreferencesKey("camera_orientation")
        val gpsEnabled = booleanPreferencesKey("gps_enabled")
        val gpsUpdateIntervalSeconds = intPreferencesKey("gps_update_interval_seconds")
        val coordinateFormat = stringPreferencesKey("coordinate_format")
        val timeFormat24Hours = booleanPreferencesKey("time_format_24_hours")
        val keepScreenAwake = booleanPreferencesKey("keep_screen_awake")
        val appBrightness = floatPreferencesKey("app_brightness")
        val useSystemBrightness = booleanPreferencesKey("use_system_brightness")
        val maxMotorPercent = floatPreferencesKey("max_motor_percent")
    }

    val settings: Flow<AppSettings> = store.data.map { preferences ->
        AppSettings(
            theme = preferences[Keys.theme] ?: "System",
            samplePeriodMillis = preferences[Keys.samplePeriod] ?: 50,
            sensitivity = preferences[Keys.sensitivity] ?: 0.7f,
            profiles = decodeProfiles(preferences[Keys.profiles].orEmpty()),
            activeProfileName = preferences[Keys.activeProfile].orEmpty(),
            customButtons = decodeCustomButtons(preferences[Keys.customButtons].orEmpty()),
            selectedSensorTypes = decodeSelectedSensorTypes(preferences[Keys.selectedSensorTypes]),
            cameraEnabled = preferences[Keys.cameraEnabled] ?: true,
            cameraQuality = preferences[Keys.cameraQuality]?.takeIf { it in CAMERA_QUALITIES } ?: "HD",
            dashboardControlsEnabled = preferences[Keys.dashboardControlsEnabled] ?: true,
            mirrorCamera = preferences[Keys.mirrorCamera] ?: false,
            flipHorizontal = preferences[Keys.flipHorizontal] ?: false,
            flipVertical = preferences[Keys.flipVertical] ?: false,
            cameraOrientation = preferences[Keys.cameraOrientation]?.takeIf { it in CAMERA_ORIENTATIONS } ?: "Portrait",
            gpsEnabled = preferences[Keys.gpsEnabled] ?: false,
            gpsUpdateIntervalSeconds = (preferences[Keys.gpsUpdateIntervalSeconds] ?: 2).coerceIn(1, 60),
            coordinateFormat = preferences[Keys.coordinateFormat]?.takeIf { it in COORDINATE_FORMATS } ?: "Decimal",
            timeFormat24Hours = preferences[Keys.timeFormat24Hours] ?: true,
            keepScreenAwake = preferences[Keys.keepScreenAwake] ?: false,
            appBrightness = (preferences[Keys.appBrightness] ?: 1f).coerceIn(0.1f, 1f),
            useSystemBrightness = preferences[Keys.useSystemBrightness] ?: true,
            maxMotorPercent = (preferences[Keys.maxMotorPercent] ?: 100f).coerceIn(1f, 100f)
        )
    }

    suspend fun setTheme(theme: String) {
        require(theme in setOf("System", "Light", "Dark")) { "Theme must be one of System, Light, or Dark: $theme" }
        store.edit { it[Keys.theme] = theme }
    }
    suspend fun setSamplePeriod(millis: Int) = store.edit { it[Keys.samplePeriod] = millis.coerceIn(20, 500) }
    suspend fun setSensitivity(value: Float) = store.edit { it[Keys.sensitivity] = value.coerceIn(0f, 1f) }
    suspend fun setSelectedSensorTypes(types: Set<Int>?) = store.edit { preferences ->
        if (types == null) preferences.remove(Keys.selectedSensorTypes)
        else preferences[Keys.selectedSensorTypes] = types.sorted().joinToString(",")
    }
    suspend fun setCameraEnabled(enabled: Boolean) = store.edit { it[Keys.cameraEnabled] = enabled }
    suspend fun setCameraQuality(quality: String) = store.edit {
        it[Keys.cameraQuality] = quality.uppercase().takeIf { value -> value in CAMERA_QUALITIES } ?: "HD"
    }
    suspend fun setDashboardControlsEnabled(enabled: Boolean) = store.edit { it[Keys.dashboardControlsEnabled] = enabled }
    suspend fun setMirrorCamera(enabled: Boolean) = store.edit { it[Keys.mirrorCamera] = enabled }
    suspend fun setFlipHorizontal(enabled: Boolean) = store.edit { it[Keys.flipHorizontal] = enabled }
    suspend fun setFlipVertical(enabled: Boolean) = store.edit { it[Keys.flipVertical] = enabled }
    suspend fun setCameraOrientation(orientation: String) = store.edit {
        it[Keys.cameraOrientation] = orientation.takeIf { value -> value in CAMERA_ORIENTATIONS } ?: "Portrait"
    }
    suspend fun setGpsEnabled(enabled: Boolean) = store.edit { it[Keys.gpsEnabled] = enabled }
    suspend fun setGpsUpdateIntervalSeconds(seconds: Int) = store.edit { it[Keys.gpsUpdateIntervalSeconds] = seconds.coerceIn(1, 60) }
    suspend fun setCoordinateFormat(format: String) = store.edit {
        it[Keys.coordinateFormat] = format.takeIf { value -> value in COORDINATE_FORMATS } ?: "Decimal"
    }
    suspend fun setTimeFormat24Hours(enabled: Boolean) = store.edit { it[Keys.timeFormat24Hours] = enabled }
    suspend fun setKeepScreenAwake(enabled: Boolean) = store.edit { it[Keys.keepScreenAwake] = enabled }
    suspend fun setAppBrightness(value: Float) = store.edit { it[Keys.appBrightness] = value.coerceIn(0.1f, 1f) }
    suspend fun setUseSystemBrightness(enabled: Boolean) = store.edit { it[Keys.useSystemBrightness] = enabled }
    suspend fun setMaxMotorPercent(value: Float) = store.edit { it[Keys.maxMotorPercent] = value.coerceIn(1f, 100f) }

    suspend fun saveProfile(profile: RobotProfile) = store.edit { preferences ->
        val profiles = decodeProfiles(preferences[Keys.profiles].orEmpty()).toMutableList()
        profiles.removeAll { it.name.equals(profile.name, ignoreCase = true) }
        profiles += profile
        preferences[Keys.profiles] = encodeProfiles(profiles)
    }

    suspend fun deleteProfile(name: String) = store.edit { preferences ->
        val profiles = decodeProfiles(preferences[Keys.profiles].orEmpty()).filterNot { it.name.equals(name, ignoreCase = true) }
        preferences[Keys.profiles] = encodeProfiles(profiles)
        if (preferences[Keys.activeProfile]?.equals(name, ignoreCase = true) == true) preferences[Keys.activeProfile] = ""
    }

    suspend fun setActiveProfile(name: String) = store.edit { preferences -> preferences[Keys.activeProfile] = name }
    suspend fun setCustomButtons(buttons: List<CustomRobotButton>) = store.edit { preferences ->
        preferences[Keys.customButtons] = CustomRobotButtonCodec.encode(buttons)
    }

    private fun decodeProfiles(json: String): List<RobotProfile> = runCatching {
        JsonParser.parseString(json).asJsonArray.mapNotNull { item ->
            when (val result = ProfileCodec.decode(item.toString())) {
                is ProfileDecodeResult.Success -> result.profile
                is ProfileDecodeResult.Error -> null
            }
        }
    }.getOrDefault(emptyList())

    private fun encodeProfiles(profiles: List<RobotProfile>): String = JsonArray().apply {
        profiles.forEach { add(JsonParser.parseString(ProfileCodec.encode(it))) }
    }.toString()

    private fun decodeCustomButtons(json: String): List<CustomRobotButton> =
        if (json.isBlank()) emptyList() else CustomRobotButtonCodec.decode(json).getOrDefault(emptyList())

    private fun decodeSelectedSensorTypes(value: String?): Set<Int>? = value?.split(',')
        ?.mapNotNull { it.toIntOrNull() }
        ?.toSet()

    private companion object {
        val CAMERA_QUALITIES = setOf("SD", "HD", "FHD")
        val CAMERA_ORIENTATIONS = setOf("Portrait", "Landscape")
        val COORDINATE_FORMATS = setOf("Decimal", "DMS")
    }
}
