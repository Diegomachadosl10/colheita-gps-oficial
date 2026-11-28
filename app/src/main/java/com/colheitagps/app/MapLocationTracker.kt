package com.colheitagps.app

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.location.LocationListenerCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.location.LocationRequestCompat
import androidx.core.location.GnssStatusCompat
import java.util.Locale
import kotlin.math.max

/** Map coordinates come from GNSS, never a Wi-Fi/cell estimate reported as precise GPS. */
internal class MapLocationFixes {
    var last: Location? = null
        private set
    private var jump: Location? = null

    fun age(location: Location): Long = if (location.elapsedRealtimeNanos > 0) {
        (SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos) / 1_000_000
    } else System.currentTimeMillis() - location.time

    fun fresh(): Boolean = last?.let { age(it) in 0..FRESH_MS } == true

    fun accept(input: Location): Boolean {
        if (input.provider != LocationManager.GPS_PROVIDER ||
            !input.latitude.isFinite() || input.latitude !in -90.0..90.0 ||
            !input.longitude.isFinite() || input.longitude !in -180.0..180.0 ||
            !input.hasAccuracy() || !input.accuracy.isFinite() || input.accuracy <= 0f ||
            input.accuracy > MAX_ACCURACY_M || age(input) !in 0..MAX_INPUT_AGE_MS) return false
        val previous = last
        if (previous != null) {
            val delta = age(previous) - age(input)
            if (delta <= 0) return false
            // Degraded GNSS is still current: display its actual uncertainty rather
            // than freezing the previous precise point while the vehicle moves.
            if (previous.distanceTo(input) > allowance(previous, input, delta)) {
                val candidate = jump
                jump = Location(input)
                if (candidate == null) return false
                val confirmationDelta = age(candidate) - age(input)
                if (confirmationDelta !in 500..MAX_INPUT_AGE_MS ||
                    candidate.distanceTo(input) > allowance(candidate, input, confirmationDelta)) return false
            }
        }
        last = Location(input)
        jump = null
        return true
    }

    private fun allowance(a: Location, b: Location, millis: Long) =
        35f * (millis / 1000f) + max(15f, a.accuracy + b.accuracy)

    companion object {
        const val FRESH_MS = 8_000L
        const val MAX_INPUT_AGE_MS = 15_000L
        const val PRECISE_ACCURACY_M = 25f
        const val MAX_ACCURACY_M = 100f
    }
}

/** One continuous satellite subscription, independent of internet and Firebase. */
internal class MapLocationTracker(
    context: Context,
    private val updated: (Location?, Boolean, String) -> Unit
) {
    private val app = context.applicationContext
    private val manager = app.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val handler = Handler(Looper.getMainLooper())
    private val executor = ContextCompat.getMainExecutor(app)
    private val fixes = MapLocationFixes()
    private var listener: LocationListenerCompat? = null
    private var active = false
    private var receiverRegistered = false
    private var satellitesRegistered = false
    private var gpsWasEnabled = false
    private var satellitesSeen: Int? = null
    private var satellitesUsed = 0
    private val satellites = object : GnssStatusCompat.Callback() {
        override fun onSatelliteStatusChanged(status: GnssStatusCompat) {
            if (!active) return
            satellitesSeen = status.satelliteCount
            satellitesUsed = (0 until status.satelliteCount).count { status.usedInFix(it) }
            publish()
        }
        override fun onStopped() {
            if (!active) return
            satellitesSeen = null; satellitesUsed = 0; publish()
        }
    }
    private val providerChanges = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { if (active) checkProvider() }
    }
    private val tick = object : Runnable {
        override fun run() {
            if (!active) return
            checkProvider()
            handler.postDelayed(this, 2_000L)
        }
    }

    fun start() {
        if (active) { checkProvider(); return }
        active = true
        if (!receiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(LocationManager.PROVIDERS_CHANGED_ACTION)
                addAction(LocationManager.MODE_CHANGED_ACTION)
            }
            if (Build.VERSION.SDK_INT >= 33) app.registerReceiver(providerChanges, filter, Context.RECEIVER_NOT_EXPORTED)
            else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                app.registerReceiver(providerChanges, filter)
            }
            receiverRegistered = true
        }
        checkProvider()
        if (permitted() && enabled()) try {
            manager.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let { fixes.accept(it) }
        } catch (_: SecurityException) { } catch (_: IllegalArgumentException) { }
        publish()
        handler.postDelayed(tick, 2_000L)
    }

    fun stop() {
        active = false
        handler.removeCallbacks(tick)
        unsubscribe()
        clearSatelliteCallback()
        satellitesSeen = null; satellitesUsed = 0; gpsWasEnabled = false
        if (receiverRegistered) { app.unregisterReceiver(providerChanges); receiverRegistered = false }
    }

    private fun permitted() = ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    private fun enabled(): Boolean = try {
        LocationManagerCompat.isLocationEnabled(manager) && manager.isProviderEnabled(LocationManager.GPS_PROVIDER)
    } catch (_: IllegalArgumentException) { false }
    private fun unsubscribe() {
        val previous = listener
        listener = null // Ignore callbacks already queued for a stopped subscription.
        if (previous != null) try { LocationManagerCompat.removeUpdates(manager, previous) } catch (_: SecurityException) { }
    }
    private fun clearSatelliteCallback() {
        if (satellitesRegistered) try { LocationManagerCompat.unregisterGnssStatusCallback(manager, satellites) }
        catch (_: SecurityException) { }
        satellitesRegistered = false
    }
    private fun checkProvider() {
        val gps = permitted() && enabled()
        if (!permitted()) {
            unsubscribe()
            clearSatelliteCallback()
        } else if (gps) {
            if (!gpsWasEnabled) unsubscribe() // Rebind after GPS off/on, not after a Wi-Fi/mobile transition.
            subscribe()
        }
        gpsWasEnabled = gps
        publish()
    }
    private fun subscribe() {
        if (!active || !permitted() || !enabled()) return
        if (!satellitesRegistered) try {
            satellitesRegistered = LocationManagerCompat.registerGnssStatusCallback(manager, executor, satellites)
        } catch (_: SecurityException) { } catch (_: IllegalArgumentException) { }
        if (listener != null) return
        val subscription = object : LocationListenerCompat {
            override fun onLocationChanged(location: Location) {
                if (active && listener === this && fixes.accept(location)) publish()
            }
            override fun onProviderEnabled(provider: String) { if (active && listener === this) checkProvider() }
            override fun onProviderDisabled(provider: String) { if (active && listener === this) checkProvider() }
        }
        listener = subscription
        try {
            LocationManagerCompat.requestLocationUpdates(manager, LocationManager.GPS_PROVIDER,
                LocationRequestCompat.Builder(1_000L)
                    .setMinUpdateIntervalMillis(500L).setMinUpdateDistanceMeters(0f)
                    .setQuality(LocationRequestCompat.QUALITY_HIGH_ACCURACY).build(), executor, subscription)
        } catch (_: SecurityException) { listener = null }
        catch (_: IllegalArgumentException) { listener = null }
        // No repeated getCurrentLocation/cancellation loop while GNSS is acquiring a fix.
    }
    private fun publish() {
        if (!active) return
        val gps = permitted() && enabled()
        val fresh = gps && fixes.fresh()
        val last = fixes.last
        val satelliteText = satellitesSeen?.let { " • $satellitesUsed de $it satélites" }.orEmpty()
        val message = when {
            !permitted() -> "Permita a localização precisa do aplicativo"
            !gps -> "Ative o GPS do celular"
            last == null -> "Buscando GPS por satélite$satelliteText"
            !fresh -> "Sinal GPS fraco • última posição • buscando$satelliteText"
            last.accuracy > MapLocationFixes.PRECISE_ACCURACY_M -> String.format(Locale("pt", "BR"), "GPS aproximado ±%.0f m%s", last.accuracy, satelliteText)
            else -> String.format(Locale("pt", "BR"), "GPS ±%.0f m • %.5f, %.5f", last.accuracy, last.latitude, last.longitude)
        }
        updated(last?.let(::Location), fresh, message)
    }
}
