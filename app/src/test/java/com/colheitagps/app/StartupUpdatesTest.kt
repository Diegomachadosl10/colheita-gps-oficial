package com.colheitagps.app

import android.app.Activity
import android.app.AlertDialog
import android.app.Application
import android.os.Looper
import android.os.SystemClock
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.util.ReflectionHelpers
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],manifest=Config.NONE,application=Application::class)
class StartupUpdatesTest {
    @After fun reset() { ReflectionHelpers.setStaticField(StartupUpdates::class.java,"controller",null);ReflectionHelpers.setStaticField(StartupUpdates::class.java,"promptedVersion",0L) }
    private fun prepare(a:Activity,state:UpdateState) {
        val controller=UpdateController(a.applicationContext);ReflectionHelpers.setField(controller,"state",state)
        ReflectionHelpers.setStaticField(StartupUpdates::class.java,"controller",controller)
        ReflectionHelpers.setStaticField(StartupUpdates::class.java,"lastCheck",SystemClock.elapsedRealtime())
        ReflectionHelpers.setStaticField(StartupUpdates::class.java,"promptedVersion",0L)
    }
    @Test fun anAvailableReleaseOpensTheExistingInstallerOnlyAfterThePersonChoosesUpdate() {
        val a=Robolectric.buildActivity(Activity::class.java).setup().visible().windowFocusChanged(true).get()
        val info=AppUpdateInfo(99,"9.0.0",26,"https://github.com/${AppUpdatePolicy.REPOSITORY}/releases/download/v9.0.0/DM-AGRO-9.0.0.apk","a".repeat(64),100,"Melhorias")
        prepare(a,UpdateState("available","",info));StartupUpdates.resume(a)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))
        val dialog=ShadowAlertDialog.getLatestAlertDialog();assertNotNull(dialog)
        assertNull(shadowOf(a).peekNextStartedActivity())
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(UpdateActivity::class.java.name,shadowOf(a).nextStartedActivity.component?.className)
        StartupUpdates.focused(a);shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))
        assertFalse(ShadowAlertDialog.getLatestAlertDialog().isShowing)
    }
    @Test fun anOfflineLookupStaysQuietAndDoesNotInterruptTheCurrentScreen() {
        val a=Robolectric.buildActivity(Activity::class.java).setup().visible().windowFocusChanged(true).get()
        prepare(a,UpdateState("error","Sem conexão"));StartupUpdates.resume(a)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))
        assertNull(ShadowAlertDialog.getLatestAlertDialog());assertNull(shadowOf(a).peekNextStartedActivity())
    }
}
