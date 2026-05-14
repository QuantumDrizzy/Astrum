package com.astrum.app.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import androidx.core.content.ContextCompat

data class AstroLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float?,
    val source: String = "GPS"
)

class LocationHelper(private val context: Context) {

    private val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private var listener: LocationListener? = null
    var onLocation: ((AstroLocation) -> Unit)? = null

    fun start() {
        if (!hasPermission()) return
        stop() // remove any previously registered listener before creating a new one

        val l = object : LocationListener {
            override fun onLocationChanged(loc: Location) {
                onLocation?.invoke(loc.toAstro())
            }
            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        }
        listener = l

        // GPS_PROVIDER: 10 s interval, 0 m min-distance so every fix is delivered.
        // On GrapheneOS (network location disabled) GPS_PROVIDER is the only reliable source;
        // NETWORK_PROVIDER is attempted opportunistically and silently skipped if unavailable.
        try {
            manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 10_000L, 0f, l)
        } catch (_: Exception) {}
        try {
            manager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 10_000L, 0f, l)
        } catch (_: Exception) {}

        // Return last known location immediately so UI doesn't wait for first fix
        lastKnown()?.let { onLocation?.invoke(it) }
    }

    fun stop() {
        listener?.let {
            try { manager.removeUpdates(it) } catch (_: Exception) {}
        }
        listener = null
    }

    fun lastKnown(): AstroLocation? {
        if (!hasPermission()) return null
        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        )
        return providers
            .mapNotNull { provider ->
                runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
            }
            .filterNotNull()
            .maxByOrNull { it.time }
            ?.toAstro()
    }

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun Location.toAstro() = AstroLocation(
        latitude  = latitude,
        longitude = longitude,
        accuracy  = if (hasAccuracy()) accuracy else null,
        source    = provider ?: "GPS"
    )
}
