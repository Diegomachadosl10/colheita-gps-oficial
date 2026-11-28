package com.colheitagps.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import android.net.ConnectivityManager
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList

/** Real Android LocationManager with synthetic GNSS. Never uses a real user's position or account. */
@RunWith(AndroidJUnit4::class)
class OfflineGpsNativeTest {
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private val context get()=instrumentation.targetContext
    private fun shell(command:String) {
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).use {it.readBytes()}
    }
    private fun waitUntil(check:()->Boolean) {
        val deadline=SystemClock.elapsedRealtime()+8_000
        while(!check()&&SystemClock.elapsedRealtime()<deadline) SystemClock.sleep(100)
        assertTrue("A condição esperada não foi recebida pelo Android",check())
    }
    @Suppress("DEPRECATION")
    @Test fun satelliteTrackingAndRecoveryWorkWithWifiAndMobileDataDisabled() {
        shell("svc wifi disable");shell("svc data disable")
        val connectivity=context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        waitUntil {connectivity.activeNetwork==null}
        shell("pm grant ${context.packageName} ${Manifest.permission.ACCESS_COARSE_LOCATION}")
        shell("pm grant ${context.packageName} ${Manifest.permission.ACCESS_FINE_LOCATION}")
        shell("appops set ${context.packageName} android:mock_location allow")
        shell("cmd location set-location-enabled true")
        // A visible existing activity makes this a genuine foreground-location test.
        // No account is signed in; radios are already off before the welcome screen opens.
        val activity=instrumentation.startActivitySync(Intent(context,LoginActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val manager=context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val updates=CopyOnWriteArrayList<Triple<Location?,Boolean,String>>()
        val tracker=MapLocationTracker(context) {l,f,s->updates.add(Triple(l,f,s))}
        fun emit(north:Double,accuracy:Float) {
            manager.setTestProviderLocation(LocationManager.GPS_PROVIDER,Location(LocationManager.GPS_PROVIDER).apply {
                latitude=-22.0+north/111_000.0;longitude=-52.0;this.accuracy=accuracy
                time=System.currentTimeMillis();elapsedRealtimeNanos=SystemClock.elapsedRealtimeNanos()
            })
        }
        try {
            manager.addTestProvider(LocationManager.GPS_PROVIDER,false,true,false,false,true,true,true,Criteria.POWER_HIGH,Criteria.ACCURACY_FINE)
            manager.setTestProviderEnabled(LocationManager.GPS_PROVIDER,true)
            instrumentation.runOnMainSync {tracker.start()}
            emit(0.0,65f)
            waitUntil {updates.lastOrNull()?.let {it.second&&it.first?.accuracy==65f}==true}
            assertTrue(updates.last().third.contains("aproximado"))
            SystemClock.sleep(1_100);emit(3.0,6f)
            waitUntil {updates.lastOrNull()?.first?.latitude== -22.0+3.0/111_000.0}
            assertTrue(updates.last().second);assertEquals(LocationManager.GPS_PROVIDER,updates.last().first!!.provider)
            manager.setTestProviderEnabled(LocationManager.GPS_PROVIDER,false)
            waitUntil {updates.lastOrNull()?.second==false}
            manager.setTestProviderEnabled(LocationManager.GPS_PROVIDER,true)
            SystemClock.sleep(1_100);emit(6.0,6f)
            waitUntil {updates.lastOrNull()?.let {it.second&&it.first?.latitude== -22.0+6.0/111_000.0}==true}
            assertNull(connectivity.activeNetwork)
            instrumentation.runOnMainSync {tracker.stop()}
            val count=updates.size
            SystemClock.sleep(1_100);emit(9.0,6f);SystemClock.sleep(1_100)
            assertEquals(count,updates.size)
        } finally {
            instrumentation.runOnMainSync {tracker.stop();activity.finish()}
            try {manager.removeTestProvider(LocationManager.GPS_PROVIDER)} catch(_:Exception) {}
            shell("appops set ${context.packageName} android:mock_location default")
        }
    }
}
