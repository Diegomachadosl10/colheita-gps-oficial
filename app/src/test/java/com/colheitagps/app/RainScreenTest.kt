package com.colheitagps.app

import android.app.AlertDialog
import android.app.Application
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = Application::class)
class RainScreenTest {
    private fun views(view: View): List<View> = listOf(view) +
        (if (view is ViewGroup) (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList())
    private fun activity(): MainActivity {
        val activity = Robolectric.buildActivity(MainActivity::class.java).get()
        val access = TeamAccess("opA", mapOf("nome" to "João", "papel" to "operador", "status" to "aprovado",
            "frenteId" to "santo_inacio", "turno" to "A"))
        ReflectionHelpers.setField(activity, "teamAccess", access)
        ReflectionHelpers.setField(activity, "teamStore", TeamStore(activity.applicationContext, "santo_inacio_A"))
        ReflectionHelpers.setField(activity, "papelAtual", "operador")
        return activity
    }
    private fun form(activity: MainActivity): AlertDialog {
        activity.javaClass.getDeclaredMethod("formularioChuva", String::class.java).apply { isAccessible = true }
            .invoke(activity, "09/11/2026")
        shadowOf(Looper.getMainLooper()).idle()
        return ShadowAlertDialog.getLatestAlertDialog()
    }
    private fun stored(activity: MainActivity) = JSONArray(activity.getSharedPreferences("registros_v3", 0)
        .getString(RainRecords.GROUP, "[]"))
    @Test fun formRejectsBlankAndSavesZeroThenCorrectsTheSameTurnWithoutLosingOtherRecords() {
        val activity = activity()
        val original = JSONObject().put("id", "buraco").put("tipo", "Buraco").put("fotos", JSONArray().put("/foto-local.jpg"))
        activity.getSharedPreferences("registros_v3", 0).edit().putString(RainRecords.GROUP,
            JSONArray().put(original).toString()).commit()
        val dialog = form(activity)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(dialog.isShowing)
        assertEquals(1, stored(activity).length())
        val field = views(dialog.window!!.decorView).filterIsInstance<EditText>().first()
        field.setText("0")
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(dialog.isShowing)
        assertEquals(2, stored(activity).length())
        val correction = form(activity)
        views(correction.window!!.decorView).filterIsInstance<EditText>().first().setText("12,5")
        correction.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        val all = stored(activity)
        assertEquals(2, all.length())
        assertEquals("12,5 mm", RainRecords.label(RainRecords.readings(all).single()))
        assertEquals("/foto-local.jpg", all.getJSONObject(0).getJSONArray("fotos").getString(0))
        activity.javaClass.getDeclaredMethod("ocorrenciasCampo", Boolean::class.javaPrimitiveType)
            .apply { isAccessible = true }.invoke(activity, false)
        val text = views(activity.window.decorView).filterIsInstance<TextView>().map { it.text.toString() }
        assertTrue(text.contains("Buraco"))
        assertFalse(text.any { it.contains("12,5 mm") })
    }
    @Test fun anotherOperatorCanReadButCannotCorrectTheTeamsReading() {
        val activity = activity()
        activity.getSharedPreferences("registros_v3", 0).edit().putString(RainRecords.GROUP,
            JSONArray().put(RainRecords.create("09/11/2026", "8", "", "santo_inacio_A", "outro", "Maria")).toString()).commit()
        val dialog = form(activity)
        assertTrue(views(dialog.window!!.decorView).filterIsInstance<EditText>().isEmpty())
        assertTrue(views(dialog.window!!.decorView).filterIsInstance<TextView>().any { it.text.contains("8 mm") })
        assertFalse(dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.visibility == View.VISIBLE)
    }
    @Test fun supervisorCorrectsTheReadingAndKeepsTheOriginalAuthor() {
        val activity = activity()
        val access = TeamAccess("supA", mapOf("nome" to "Supervisor", "papel" to "supervisor", "status" to "aprovado",
            "frenteId" to "santo_inacio", "turno" to "A"))
        ReflectionHelpers.setField(activity, "teamAccess", access)
        activity.getSharedPreferences("registros_v3", 0).edit().putString(RainRecords.GROUP,
            JSONArray().put(RainRecords.create("09/11/2026", "8", "", "santo_inacio_A", "opA", "João")).toString()).commit()
        val dialog = form(activity)
        views(dialog.window!!.decorView).filterIsInstance<EditText>().first().setText("10,5")
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        val reading = RainRecords.readings(stored(activity)).single()
        assertEquals("10,5 mm", RainRecords.label(reading))
        assertEquals("opA", reading.getString("registradoPorUid"))
        assertEquals("João", reading.getString("registradoPorNome"))
    }
}
