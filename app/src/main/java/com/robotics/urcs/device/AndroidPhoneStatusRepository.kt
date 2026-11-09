package com.robotics.urcs.device

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.os.BatteryManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.core.content.ContextCompat
import com.robotics.urcs.device.NetworkTransport

data class BatteryReading(val levelPercent: Int?, val charging: Boolean?)

data class NetworkReading(val transport: NetworkTransport, val hasInternet: Boolean)

data class PhoneLocationReading(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val timestampMillis: Long
)

class AndroidPhoneStatusRepository(context: Context) {
    private val appContext = context.applicationContext
    private val locationManager = appContext.getSystemService(LocationManager::class.java)
    private val connectivityManager = appContext.getSystemService(ConnectivityManager::class.java)
    private val batteryManager = appContext.getSystemService(BatteryManager::class.java)

    fun readLastKnownLocation(): PhoneLocationReading? {
        if (!hasLocationPermission()) return null
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
        var freshest: Location? = null
        for (provider in providers) {
            val loc = runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull() ?: continue
            if (freshest == null || loc.time > freshest.time) {
                freshest = loc
            }
        }
        return freshest?.let { PhoneLocationReading(it.latitude, it.longitude, it.accuracy, it.time) }
    }

    fun readBattery(): BatteryReading {
        if (batteryManager != null) {
            val level = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            val isCharging = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val status = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS)
                status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
            } else {
                batteryManager.isCharging
            }
            if (level in 0..100) {
                return BatteryReading(level, isCharging)
            }
        }
        val intent = ContextCompat.registerReceiver(
            appContext,
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_EXPORTED
        ) ?: return BatteryReading(null, null)
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val percent = if (level >= 0 && scale > 0) (level * 100) / scale else null
        val charging = when (intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)) {
            BatteryManager.BATTERY_STATUS_CHARGING, BatteryManager.BATTERY_STATUS_FULL -> true
            BatteryManager.BATTERY_STATUS_DISCHARGING, BatteryManager.BATTERY_STATUS_NOT_CHARGING -> false
            else -> null
        }
        return BatteryReading(percent, charging)
    }

    fun readNetwork(): NetworkReading {
        val active = connectivityManager.activeNetwork ?: return NetworkReading(NetworkTransport.NONE, false)
        val capabilities = connectivityManager.getNetworkCapabilities(active)
            ?: return NetworkReading(NetworkTransport.NONE, false)
        val transport = when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkTransport.WIFI
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkTransport.CELLULAR
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkTransport.ETHERNET
            else -> NetworkTransport.OTHER
        }
        return NetworkReading(transport, capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))
    }

    fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun isLocationEnabled(): Boolean = runCatching {
        locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }.getOrDefault(false)

    fun startLocationUpdates(intervalMillis: Long = 2_000L, onLocation: (PhoneLocationReading) -> Unit): LocationListener {
        val hasFine = ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        check(hasFine || hasCoarse) { "Location permission is not granted" }
        val provider = listOfNotNull(
            if (hasFine) LocationManager.GPS_PROVIDER else null,
            LocationManager.NETWORK_PROVIDER
        ).firstOrNull { runCatching { locationManager.isProviderEnabled(it) }.getOrDefault(false) }
            ?: error("Turn on Location in Android Settings before requesting coordinates")

        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                onLocation(PhoneLocationReading(location.latitude, location.longitude, location.accuracy, location.time))
            }

            @Suppress("OVERRIDE_DEPRECATION")
            @Deprecated("Deprecated by Android")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

            override fun onProviderEnabled(provider: String) = Unit
            override fun onProviderDisabled(provider: String) = Unit
        }
        locationManager.requestLocationUpdates(provider, intervalMillis.coerceIn(1_000L, 60_000L), 5f, listener, Looper.getMainLooper())
        return listener
    }

    fun stopLocationUpdates(listener: LocationListener) {
        runCatching { locationManager.removeUpdates(listener) }
    }
}
