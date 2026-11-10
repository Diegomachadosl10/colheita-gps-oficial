package com.colheitagps.app

import android.app.AlertDialog
import android.app.Application
import android.os.Looper
import android.view.View
import android.view.ViewGroup
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
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = Application::class)
class DeletionScreenTest {
    private fun views(view: View): List<View> = listOf(view) +
        (if (view is ViewGroup) (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList())
    private fun activity(role: String = "supervisor"): MainActivity {
        val activity = Robolectric.buildActivity(MainActivity::class.java).get()
        ReflectionHelpers.setField(activity, "teamAccess", TeamAccess("teste", mapOf("nome" to "Pessoa", "papel" to role,
            "status" to "aprovado", "frenteId" to "santo_inacio", "turno" to "A")))
        ReflectionHelpers.setField(activity, "teamStore", TeamStore(activity.applicationContext, "santo_inacio_A"))
        ReflectionHelpers.setField(activity, "papelAtual", role)
        ReflectionHelpers.setField(activity, "acessoConfirmado", true)
        return activity
    }
    private fun call(activity: MainActivity, name: String, value: String? = null) {
        val method = if (value == null) activity.javaClass.getDeclaredMethod(name)
            else activity.javaClass.getDeclaredMethod(name, String::class.java)
        method.apply { isAccessible = true }.invoke(activity, *if (value == null) emptyArray() else arrayOf(value))
        shadowOf(Looper.getMainLooper()).idle()
    }
    private fun seed(activity: MainActivity) {
        val issues = JSONArray().put(JSONObject().put("id", "p1").put("equipamento", "21569").put("descricao", "Pneu furado")
            .put("fotos", JSONArray().put("/foto-local.jpg")))
            .put(JSONObject().put("id", "p2").put("equipamento", "33000").put("descricao", "Outro equipamento"))
        activity.getSharedPreferences("registros_v3", 0).edit()
            .putString("pendencias", MaintenanceRecords.merge(issues, JSONArray()).toString()).commit()
    }
    private fun records(activity: MainActivity) = JSONArray(activity.getSharedPreferences("registros_v3", 0).getString("pendencias", "[]"))
    private fun items(dialog: AlertDialog) = (0 until dialog.listView.adapter.count).map { dialog.listView.adapter.getItem(it).toString() }
    private fun select(dialog: AlertDialog, label: String) {
        val index = items(dialog).indexOf(label)
        assertTrue(label, index >= 0)
        dialog.listView.performItemClick(null, index, index.toLong())
        shadowOf(Looper.getMainLooper()).idle()
    }
    private fun issueOptions(activity: MainActivity) {
        activity.javaClass.getDeclaredMethod("opcoesPendencia", Int::class.javaPrimitiveType).apply { isAccessible = true }.invoke(activity, 0)
        shadowOf(Looper.getMainLooper()).idle()
    }
    @Test fun operatorCanUseEquipmentAndIssuesButHasNoDeleteAction() {
        val activity = activity("operador")
        seed(activity)
        issueOptions(activity)
        assertFalse(items(ShadowAlertDialog.getLatestAlertDialog()).any { it.contains("Excluir") })
        call(activity, "pendencias")
        val title = views(activity.window.decorView).filterIsInstance<TextView>().single { it.text.toString() == "Equipamento 21569" }
        (title.parent as View).performClick()
        assertFalse(items(ShadowAlertDialog.getLatestAlertDialog()).any { it.contains("Excluir") })
        call(activity, "excluirEquipamento", "21569")
        assertTrue(MaintenanceRecords.archivedEquipment(records(activity)).isEmpty())
    }
    @Test fun supervisorDeletesOnlyTheSelectedIssueAndItRemainsInHistoryWithItsPhotos() {
        val activity = activity()
        seed(activity)
        issueOptions(activity)
        select(ShadowAlertDialog.getLatestAlertDialog(), "Excluir pendência")
        ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        val all = records(activity)
        assertEquals(4, all.length())
        val issue = MaintenanceRecords.issues(all).getJSONObject(0)
        assertTrue(MaintenanceRecords.isArchived(issue))
        assertEquals("/foto-local.jpg", issue.getJSONArray("fotos").getString(0))
        assertEquals(2, MaintenanceRecords.summaries(all).size)
        call(activity, "historicoPendencias")
        assertTrue(views(activity.window.decorView).filterIsInstance<TextView>().any { it.text.contains("Pneu furado") && it.text.contains("Excluída") })
    }
    @Test fun equipmentDeletionRequiresConfirmationAndRetainsItsIssueInHistory() {
        val activity = activity()
        seed(activity)
        call(activity, "excluirEquipamento", "21569")
        ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(MaintenanceRecords.archivedEquipment(records(activity)).isEmpty())
        call(activity, "excluirEquipamento", "21569")
        ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf("33000"), MaintenanceRecords.summaries(records(activity)).map { it.number })
        assertEquals(listOf("21569"), MaintenanceRecords.archivedEquipment(records(activity)))
        assertEquals(2, MaintenanceRecords.issues(records(activity)).length())
        call(activity, "historicoEquipamentos")
        assertTrue(views(activity.window.decorView).filterIsInstance<TextView>().any { it.text.toString() == "Equipamento 21569" })
    }
    @Test fun tappingAnOperatorsMapOffersDeletionWithoutRemovingAnotherProfilesCopy() {
        val activity = activity("operador")
        val id = "mapa_teste"
        val pdf = File(activity.filesDir, "mapas/$id.pdf").apply { parentFile!!.mkdirs(); writeText("%PDF-test") }
        activity.getSharedPreferences("mapas", 0).edit().putString(id, "Meu mapa").commit()
        val other = TeamStore(activity.applicationContext, "santo_inacio_A").privateMapPrefs("mapas", "outra-pessoa")
        other.edit().putString(id, "Mapa de outra pessoa").commit()
        call(activity, "biblioteca")
        val title = views(activity.window.decorView).filterIsInstance<TextView>().single { it.text.toString() == "Meu mapa" }
        ((title.parent as View).parent as View).performClick()
        select(ShadowAlertDialog.getLatestAlertDialog(), "Excluir")
        ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(activity.getSharedPreferences("mapas", 0).contains(id))
        assertTrue(other.contains(id))
        assertTrue(pdf.isFile)
    }
}
