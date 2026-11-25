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
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.location.LocationListenerCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.location.LocationRequestCompat
import java.util.Locale
import kotlin.math.max

/** Keep real coordinates: reject stale/poor fixes and confirm implausible jumps, without smoothing lag. */
internal class MapLocationFixes {
    var last: Location? = null
        private set
    private var jump: Location? = null

    fun age(location: Location): Long = if (location.elapsedRealtimeNanos > 0) {
        (SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos) / 1_000_000
    } else System.currentTimeMillis() - location.time

    fun fresh(): Boolean = last?.let { age(it) in 0..FRESH_MS } == true

    fun accept(input: Location): Boolean {
        if (!input.latitude.isFinite() || input.latitude !in -90.0..90.0 ||
            !input.longitude.isFinite() || input.longitude !in -180.0..180.0 ||
            !input.hasAccuracy() || !input.accuracy.isFinite() || input.accuracy <= 0f ||
            input.accuracy > 50f || age(input) !in 0..10_000L) return false
        val network = input.provider == LocationManager.NETWORK_PROVIDER
        if (network && input.accuracy > 20f) return false
        val previous = last
        if (previous != null) {
            val delta = age(previous) - age(input)
            if (delta <= 0) return false
            // A network estimate must never displace a recent satellite/fused fix.
            if (network && fresh() && previous.provider != LocationManager.NETWORK_PROVIDER) return false
            if (fresh() && input.accuracy > max(20f, previous.accuracy * 2.5f)) return false
            if (previous.distanceTo(input) > allowance(previous, input, delta)) {
                val candidate = jump
                jump = Location(input)
                if (candidate == null) return false
                val confirmationDelta = age(candidate) - age(input)
                if (confirmationDelta !in 500..5_000L ||
                    candidate.distanceTo(input) > allowance(candidate, input, confirmationDelta)) return false
            }
        }
        last = Location(input)
        jump = null
        return true
    }

    private fun allowance(a: Location, b: Location, millis: Long) =
        35f * (millis / 1000f) + max(15f, a.accuracy + b.accuracy)

    companion object { const val FRESH_MS = 8_000L }
}

/** Foreground only. Continuous GNSS stays subscribed while one-shot recovery runs alongside it. */
internal class MapLocationTracker(
    context: Context,
    private val updated: (Location?, Boolean, String) -> Unit
) {
    private val app = context.applicationContext
    private val manager = app.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val handler = Handler(Looper.getMainLooper())
    private val executor = ContextCompat.getMainExecutor(app)
    private val fixes = MapLocationFixes()
    private val listeners = mutableMapOf<String, LocationListenerCompat>()
    private var active = false
    private var generation = 0
    private var receiverRegistered = false
    private var current: CancellationSignal? = null
    private var lastRecovery = 0L
    private val providers = listOf(LocationManager.GPS_PROVIDER, "fused", LocationManager.NETWORK_PROVIDER)
    private val providerChanges = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (active) { subscribe(); recover(force = true); publish() }
        }
    }
    private val tick = object : Runnable {
        override fun run() {
            if (!active) return
            subscribe()
            if (!fixes.fresh()) recover()
            publish()
            handler.postDelayed(this, 2_000L)
        }
    }

    fun start() {
        if (active) { if (!fixes.fresh()) recover(force = true); publish(); return }
        active = true
        generation++
        if (!receiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(LocationManager.PROVIDERS_CHANGED_ACTION)
                addAction(LocationManager.MODE_CHANGED_ACTION)
            }
            if (Build.VERSION.SDK_INT >= 33) app.registerReceiver(providerChanges, filter, Context.RECEIVER_NOT_EXPORTED)
            else {
                // Both actions are protected Android system broadcasts; no custom permission needed.
                @Suppress("UnspecifiedRegisterReceiverFlag")
                app.registerReceiver(providerChanges, filter)
            }
            receiverRegistered = true
        }
        subscribe()
        // Only recent, precise cached positions pass the same live-fix checks.
        if (permitted()) for (provider in providers) {
            try { manager.getLastKnownLocation(provider)?.let { fixes.accept(it) } }
            catch (_: SecurityException) { /* Permission may change from Android settings. */ }
            catch (_: IllegalArgumentException) { /* Provider absent on this phone. */ }
        }
        recover(force = true)
        publish()
        handler.postDelayed(tick, 2_000L)
    }

    fun stop() {
        active = false
        generation++
        handler.removeCallbacks(tick)
        current?.cancel(); current = null
        listeners.values.forEach { try { manager.removeUpdates(it) } catch (_: SecurityException) {} }
        listeners.clear()
        if (receiverRegistered) {
            app.unregisterReceiver(providerChanges)
            receiverRegistered = false
        }
    }

    private fun permitted() = ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    private fun enabled(provider: String): Boolean = try { manager.isProviderEnabled(provider) } catch (_: IllegalArgumentException) { false }

    private fun subscribe() {
        if (!active || !permitted()) return
        for (provider in providers) {
            if (!enabled(provider) || listeners.containsKey(provider)) continue
            val session = generation
            val listener = object : LocationListenerCompat {
                override fun onLocationChanged(location: Location) {
                    if (active && session == generation && fixes.accept(location)) publish()
                }
                override fun onProviderEnabled(provider: String) { if (active) { recover(force = true); publish() } }
                override fun onProviderDisabled(provider: String) { if (active) publish() }
            }
            try {
                LocationManagerCompat.requestLocationUpdates(manager, provider,
                    LocationRequestCompat.Builder(1_000L)
                        .setMinUpdateIntervalMillis(500L).setMinUpdateDistanceMeters(0f)
                        .setQuality(LocationRequestCompat.QUALITY_HIGH_ACCURACY).build(), executor, listener)
                listeners[provider] = listener
            } catch (_: SecurityException) { /* publish() explains the missing permission. */ }
            catch (_: IllegalArgumentException) { /* Unsupported provider; GNSS still works. */ }
        }
    }

    private fun recover(force: Boolean = false) {
        if (!active || !permitted()) return
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastRecovery < 15_000L) return
        lastRecovery = now
        current?.cancel()
        val provider = providers.firstOrNull { enabled(it) } ?: return
        val session = generation
        val signal = CancellationSignal()
        current = signal
        try {
            LocationManagerCompat.getCurrentLocation(manager, provider, signal, executor) { location ->
                if (active && session == generation && location != null && fixes.accept(location)) publish()
            }
        } catch (_: SecurityException) { current = null }
        catch (_: IllegalArgumentException) { current = null }
    }

    private fun publish() {
        if (!active) return
        val enabled = permitted() && LocationManagerCompat.isLocationEnabled(manager) &&
            (enabled(LocationManager.GPS_PROVIDER) || enabled("fused"))
        val fresh = enabled && fixes.fresh()
        val last = fixes.last
        val message = when {
            !permitted() -> "Permita a localização precisa do aplicativo"
            !enabled -> "Ative o GPS do celular"
            last == null -> "Buscando localização precisa…"
            !fresh -> "Sinal GPS fraco • última posição • buscando…"
            else -> String.format(Locale("pt", "BR"), "GPS ±%.0f m • %.5f, %.5f", last.accuracy, last.latitude, last.longitude)
        }
        updated(last?.let(::Location), fresh, message)
    }
}
