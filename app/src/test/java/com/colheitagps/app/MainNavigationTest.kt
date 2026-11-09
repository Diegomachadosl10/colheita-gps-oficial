package com.colheitagps.app

import android.app.Application
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = Application::class)
class MainNavigationTest {
    private fun texts(view: View): List<String> = (if (view is TextView) listOf(view.text.toString()) else emptyList()) +
        (if (view is ViewGroup) (0 until view.childCount).flatMap { texts(view.getChildAt(it)) } else emptyList())
    private fun equipment(activity: MainActivity, history: Boolean = false) {
        activity.javaClass.getDeclaredMethod("pendenciasEquipamento", String::class.java, Boolean::class.javaPrimitiveType)
            .apply { isAccessible = true }.invoke(activity, "21569", history)
    }
    @Test fun deviceBackFromAnEquipmentReturnsToMaintenanceInsteadOfHome() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).get()
        equipment(activity)
        activity.onBackPressed()
        assertTrue(texts(activity.window.decorView).contains("Pendências de manutenção"))
        assertFalse(texts(activity.window.decorView).contains("Colheita GPS 2.0"))
    }
    @Test fun deviceBackFromEquipmentHistoryReturnsToThatEquipment() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).get()
        equipment(activity, true)
        activity.onBackPressed()
        assertTrue(texts(activity.window.decorView).contains("Equipamento 21569"))
    }
    @Test fun homeShowsTheConnectedPersonsName() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).get()
        val access = TeamAccess("operador-teste", mapOf("nome" to "João da Silva", "papel" to "operador", "status" to "aprovado", "frenteId" to "santo_inacio", "turno" to "A"))
        ReflectionHelpers.setField(activity, "teamAccess", access)
        ReflectionHelpers.setField(activity, "teamStore", TeamStore(activity.applicationContext, "santo_inacio_A"))
        ReflectionHelpers.setField(activity, "papelAtual", "operador")
        activity.javaClass.getDeclaredMethod("inicio").apply { isAccessible = true }.invoke(activity)
        assertTrue(texts(activity.window.decorView).contains("Olá, João da Silva"))
    }
}
