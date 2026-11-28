package com.colheitagps.app

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import android.os.SystemClock
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35], manifest = Config.NONE, application = Application::class)
class MapLocationTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private val manager get() = app.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private fun advance(millis: Long) { ShadowSystemClock.advanceBy(Duration.ofMillis(millis)) }
    private fun fix(metersNorth: Double = 0.0, accuracy: Float = 5f, age: Long = 0,
                    provider: String = LocationManager.GPS_PROVIDER) = Location(provider).apply {
        latitude = -22.0 + metersNorth / 111_000.0; longitude = -52.0
        this.accuracy = accuracy
        time = System.currentTimeMillis() - age
        elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos() - age * 1_000_000
    }
    @Before fun prepare() {
        advance(60_000)
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        shadowOf(manager).setProviderEnabled(LocationManager.GPS_PROVIDER, true)
        shadowOf(manager).setProviderEnabled(LocationManager.NETWORK_PROVIDER, true)
    }

    @Test fun staleInvalidAndInaccuratePositionsNeverReplaceTheActualPosition() {
        val fixes = MapLocationFixes()
        assertFalse(fixes.accept(fix(age = 20_000)))
        assertFalse(fixes.accept(fix(accuracy = 120f)))
        assertFalse(fixes.accept(fix().apply { latitude = Double.NaN }))
        assertFalse(fixes.accept(fix().apply { latitude = 100.0 }))
        assertFalse(fixes.accept(fix().apply { accuracy = Float.NaN }))
        assertFalse(fixes.accept(fix().apply { removeAccuracy() }))
        assertFalse(fixes.accept(fix(age = -5_000)))
        assertTrue(fixes.accept(fix()))
        val previous = fixes.last!!
        advance(1_000)
        assertFalse(fixes.accept(fix(500.0, accuracy = 100f)))
        assertFalse(fixes.accept(fix(age = 5_000)))
        assertEquals(previous.latitude, fixes.last!!.latitude, 0.0)
    }

    @Test fun neitherNetworkNorUnverifiableFusedPositionsCanDisplaceOrStartTheMapMarker() {
        val fixes = MapLocationFixes()
        assertFalse(fixes.accept(fix(accuracy = 200f, provider = LocationManager.NETWORK_PROVIDER)))
        assertFalse(fixes.accept(fix(accuracy = 15f, provider = LocationManager.NETWORK_PROVIDER)))
        assertFalse(fixes.accept(fix(accuracy = 5f, provider = "fused")))
        advance(1_000)
        assertTrue(fixes.accept(fix(3.0)))
        advance(1_000)
        assertFalse(fixes.accept(fix(25.0, accuracy = 10f, provider = LocationManager.NETWORK_PROVIDER)))
        assertTrue(fixes.accept(fix(25.0, accuracy = 45f)))
        assertEquals(LocationManager.GPS_PROVIDER, fixes.last!!.provider)
        advance(9_000)
        assertFalse(fixes.accept(fix(10.0, accuracy = 10f, provider = LocationManager.NETWORK_PROVIDER)))
    }

    @Test fun aSingleImpossibleJumpIsIgnoredButNormalMovementIsNeverDelayed() {
        val fixes = MapLocationFixes()
        assertTrue(fixes.accept(fix()))
        advance(1_000); assertFalse(fixes.accept(fix(2_000.0)))
        advance(1_000); assertTrue(fixes.accept(fix(4.0)))
        advance(1_000); assertTrue(fixes.accept(fix(8.0)))
        assertEquals(fix(8.0).latitude, fixes.last!!.latitude, 0.0)
        advance(1_000); assertFalse(fixes.accept(fix(2_000.0)))
        advance(1_000); assertTrue(fixes.accept(fix(2_002.0)))
        assertEquals(fix(2_002.0).latitude, fixes.last!!.latitude, 0.0)
    }

    @Test fun degradedSatelliteAccuracyStillTracksMovementAndNeverBorrowsAPreciseNetworkPoint() {
        val fixes=MapLocationFixes()
        assertTrue(fixes.accept(fix()))
        advance(1_000);assertTrue(fixes.accept(fix(4.0,accuracy=65f)))
        advance(1_000);assertTrue(fixes.accept(fix(8.0,accuracy=60f)))
        assertEquals(fix(8.0).latitude,fixes.last!!.latitude,0.0)
        assertTrue(fixes.fresh());assertEquals(60f,fixes.last!!.accuracy,0f)
        advance(1_000);assertFalse(fixes.accept(fix(500.0,accuracy=5f,provider=LocationManager.NETWORK_PROVIDER)))
        advance(1_000);assertTrue(fixes.accept(fix(12.0,accuracy=6f)))
        assertEquals(6f,fixes.last!!.accuracy,0f)
    }

    @Test fun sparseSatelliteFixesCanConfirmRecoveryWithoutBeingRejectedForever() {
        val fixes=MapLocationFixes();assertTrue(fixes.accept(fix()))
        advance(1_000);assertFalse(fixes.accept(fix(2_000.0)))
        advance(8_000);assertTrue(fixes.accept(fix(2_002.0)))
        assertEquals(fix(2_002.0).latitude,fixes.last!!.latitude,0.0)
        advance(10_000);assertFalse(fixes.fresh())
        advance(6_000);assertFalse(fixes.fresh())
        assertTrue(fixes.accept(fix(2_004.0)))
    }

    @Test fun startupAndRadioChangesUseOnlyTheSatelliteSubscription() {
        shadowOf(manager).setLastKnownLocation(LocationManager.NETWORK_PROVIDER,fix(500.0,accuracy=5f,provider=LocationManager.NETWORK_PROVIDER))
        shadowOf(manager).setLastKnownLocation(LocationManager.GPS_PROVIDER,fix(accuracy=65f))
        val updates=mutableListOf<Triple<Location?,Boolean,String>>()
        val tracker=MapLocationTracker(app) {l,f,s->updates.add(Triple(l,f,s))}
        try {
            tracker.start();shadowOf(Looper.getMainLooper()).idle()
            assertTrue(updates.last().second);assertTrue(updates.last().third.contains("aproximado ±65"))
            assertEquals(LocationManager.GPS_PROVIDER,updates.last().first!!.provider)
            assertEquals(1,shadowOf(manager).getLocationRequests(LocationManager.GPS_PROVIDER).size)
            assertTrue(shadowOf(manager).getLocationRequests(LocationManager.NETWORK_PROVIDER).isEmpty())
            repeat(5) {app.sendBroadcast(Intent(LocationManager.PROVIDERS_CHANGED_ACTION));shadowOf(Looper.getMainLooper()).idle()}
            assertEquals(1,shadowOf(manager).getLocationRequests(LocationManager.GPS_PROVIDER).size)
            advance(1_000);shadowOf(manager).simulateLocation(fix(12.0));shadowOf(Looper.getMainLooper()).idle()
            assertEquals(fix(12.0).latitude,updates.last().first!!.latitude,0.0)
        } finally {tracker.stop()}
        assertTrue(shadowOf(manager).getLocationRequests(LocationManager.GPS_PROVIDER).isEmpty())
    }

    @Test fun afterASignalGapTheFirstValidReachablePositionIsAcceptedImmediately() {
        val fixes = MapLocationFixes(); assertTrue(fixes.accept(fix()))
        advance(16_000); assertFalse(fixes.fresh())
        assertFalse(fixes.accept(fix(40.0, age = 20_000)))
        assertTrue(fixes.accept(fix(40.0))); assertTrue(fixes.fresh())
    }

    @Test fun theTrackerUsesRecentCacheKeepsUpdatesWhileStationaryAndStopsOnPause() {
        shadowOf(manager).setLastKnownLocation(LocationManager.GPS_PROVIDER, fix())
        val updates = mutableListOf<Triple<Location?, Boolean, String>>()
        val tracker = MapLocationTracker(app) { location, fresh, message -> updates.add(Triple(location, fresh, message)) }
        try {
            tracker.start(); shadowOf(Looper.getMainLooper()).idle()
            assertTrue(updates.last().second); assertTrue(updates.last().third.startsWith("GPS ±5 m"))
            advance(1_000)
            shadowOf(manager).simulateLocation(fix())
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(SystemClock.elapsedRealtimeNanos(), updates.last().first!!.elapsedRealtimeNanos)
            tracker.stop(); val count = updates.size
            advance(1_000); shadowOf(manager).simulateLocation(fix(10.0)); shadowOf(Looper.getMainLooper()).idle()
            assertEquals(count, updates.size)
            tracker.start(); shadowOf(Looper.getMainLooper()).idle()
            assertTrue(updates.last().second)
            assertEquals(fix(10.0).latitude, updates.last().first!!.latitude, 0.0)
        } finally { tracker.stop() }
    }

    @Test fun staleFixIsFlaggedAndGpsReturnsAfterTheProviderIsSwitchedBackOn() {
        val updates = mutableListOf<Triple<Location?, Boolean, String>>()
        val tracker = MapLocationTracker(app) { location, fresh, message -> updates.add(Triple(location, fresh, message)) }
        try {
            tracker.start(); shadowOf(Looper.getMainLooper()).idle()
            shadowOf(manager).simulateLocation(fix()); shadowOf(Looper.getMainLooper()).idle()
            assertTrue(updates.last().second)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(16))
            assertFalse(updates.last().second); assertTrue(updates.last().third.contains("última posição"))
            shadowOf(manager).setProviderEnabled(LocationManager.GPS_PROVIDER, false)
            shadowOf(Looper.getMainLooper()).idle()
            assertFalse(updates.last().second)
            shadowOf(manager).setProviderEnabled(LocationManager.GPS_PROVIDER, true)
            shadowOf(Looper.getMainLooper()).idle()
            shadowOf(manager).simulateLocation(fix(20.0)); shadowOf(Looper.getMainLooper()).idle()
            assertTrue(updates.last().second); assertEquals(fix(20.0).latitude, updates.last().first!!.latitude, 0.0)
        } finally { tracker.stop() }
    }

    @Test fun missingPermissionDoesNotCrashOrRegisterLocationRequests() {
        shadowOf(app).denyPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        var message = ""
        val tracker = MapLocationTracker(app) { _, fresh, text -> assertFalse(fresh); message = text }
        try { tracker.start(); assertTrue(message.contains("localização precisa")) }
        finally { tracker.stop() }
    }
}
