package com.robotics.urcs.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicBoolean

data class SensorReading(
    val sensor: Sensor,
    val values: List<Float>,
    val accuracy: Int,
    val timestampNanos: Long,
    val updatedAtMillis: Long,
    val history: List<Float> = emptyList()
)

class AndroidSensorRepository(context: Context) : SensorEventListener {
    private val sensorManager = context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    val availableSensors: List<Sensor> = sensorManager.getSensorList(Sensor.TYPE_ALL)

    private val mutableReadings = MutableStateFlow<Map<Int, SensorReading>>(emptyMap())
    val readings: StateFlow<Map<Int, SensorReading>> = mutableReadings.asStateFlow()

    private val isMonitoring = AtomicBoolean(false)

    fun start(
        samplePeriodMicros: Int = SensorManager.SENSOR_DELAY_UI,
        selectedSensorTypes: Set<Int>? = null
    ) {
        if (isMonitoring.getAndSet(true)) {
            sensorManager.unregisterListener(this)
        }
        availableSensors
            .filter { selectedSensorTypes == null || it.type in selectedSensorTypes }
            .forEach { sensor ->
            sensorManager.registerListener(this, sensor, samplePeriodMicros.coerceAtLeast(SensorManager.SENSOR_DELAY_NORMAL))
        }
    }

    fun stop() {
        if (isMonitoring.getAndSet(false)) {
            sensorManager.unregisterListener(this)
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        val history = (mutableReadings.value[event.sensor.type]?.history.orEmpty() + event.values.firstOrNull().orZero()).takeLast(64)
        val reading = SensorReading(
            sensor = event.sensor,
            values = event.values.toList(),
            accuracy = event.accuracy,
            timestampNanos = event.timestamp,
            updatedAtMillis = System.currentTimeMillis(),
            history = history
        )
        mutableReadings.update { current -> current + (event.sensor.type to reading) }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    fun close() = stop()
}

private fun Float?.orZero(): Float = this?.takeIf(Float::isFinite) ?: 0f

fun sensorUnit(type: Int): String = when (type) {
    Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_GRAVITY, Sensor.TYPE_LINEAR_ACCELERATION -> "m/s²"
    Sensor.TYPE_GYROSCOPE, Sensor.TYPE_GYROSCOPE_UNCALIBRATED -> "rad/s"
    Sensor.TYPE_MAGNETIC_FIELD, Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED -> "µT"
    Sensor.TYPE_PRESSURE -> "hPa"
    Sensor.TYPE_PROXIMITY -> "cm"
    Sensor.TYPE_LIGHT -> "lx"
    Sensor.TYPE_RELATIVE_HUMIDITY -> "%"
    Sensor.TYPE_AMBIENT_TEMPERATURE -> "°C"
    Sensor.TYPE_STEP_COUNTER, Sensor.TYPE_STEP_DETECTOR -> "steps"
    else -> ""
}

fun sensorAccuracyLabel(accuracy: Int): String = when (accuracy) {
    SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> "High"
    SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> "Medium"
    SensorManager.SENSOR_STATUS_ACCURACY_LOW -> "Low"
    SensorManager.SENSOR_STATUS_UNRELIABLE -> "Unreliable"
    else -> "Unknown"
}
