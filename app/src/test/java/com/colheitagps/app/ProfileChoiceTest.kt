package com.colheitagps.app

import android.app.Activity
import android.app.Application
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.RadioButton
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],manifest=Config.NONE,application=Application::class)
class ProfileChoiceTest {
    private fun views(view:View):List<View> = listOf(view)+(if(view is ViewGroup) (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList())
    @Test fun switchingCardsKeepsExactlyOneRequestedProfileAndAccessibleCheckedState() {
        val activity=Robolectric.buildActivity(Activity::class.java).get()
        val choice=ProfileChoice(activity)
        val radios=views(choice).filterIsInstance<RadioButton>()
        assertEquals("operador",choice.selectedProfile.requested); assertEquals(1,radios.count { it.isChecked })
        choice.getChildAt(1).performClick()
        assertEquals("supervisor",choice.selectedProfile.requested); assertEquals("Supervisor",choice.selectedProfile.label)
        assertFalse(radios[0].isChecked); assertTrue(radios[1].isChecked)
        val info=AccessibilityNodeInfo.obtain(); choice.getChildAt(1).onInitializeAccessibilityNodeInfo(info)
        assertTrue(info.isCheckable); assertTrue(info.isChecked)
        choice.getChildAt(0).performClick()
        assertEquals("operador",choice.selectedProfile.requested); assertEquals(1,radios.count { it.isChecked })
    }
    @Test fun pendingSubmissionLocksTheRequestedProfileUntilTheUserCanRetry() {
        val activity=Robolectric.buildActivity(Activity::class.java).get()
        val choice=ProfileChoice(activity)
        choice.setSelectable(false); choice.getChildAt(1).performClick()
        assertEquals(ProfileChoice.Profile.OPERATOR,choice.selectedProfile)
        choice.setSelectable(true); choice.getChildAt(1).performClick()
        assertEquals(ProfileChoice.Profile.SUPERVISOR,choice.selectedProfile)
    }
}
