package com.colheitagps.app

import android.app.Application
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
import android.os.Looper

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28,35],manifest=Config.NONE,application=Application::class)
class BetaMaintenancePermissionTest {
    private fun activity(role:String):Pair<MainActivity,TeamStore> {
        val a=Robolectric.buildActivity(MainActivity::class.java).get()
        a.setTheme(android.R.style.Theme_Material_Light_NoActionBar)
        val store=TeamStore(a.applicationContext,"santo_inacio_A")
        val access=TeamAccess("person",mapOf("nome" to "Pessoa fictícia","papel" to role,"status" to "aprovado","frenteId" to "santo_inacio","turno" to "A"))
        ReflectionHelpers.setField(a,"teamAccess",access);ReflectionHelpers.setField(a,"teamStore",store)
        ReflectionHelpers.setField(a,"papelAtual",role);ReflectionHelpers.setField(a,"acessoConfirmado",true)
        store.prefs("registros_v3").edit().putString("pendencias",JSONArray().put(JSONObject().put("id","p").put("equipamento","FICTICIO").put("descricao","Rolamento").put("concluida",false)).toString()).commit()
        return a to store
    }
    @Test fun operatorsCannotConcludeEvenByCallingTheHandlerDirectlyAndNoSuchActionIsShown() {
        val (a,store)=activity("operador")
        ReflectionHelpers.callInstanceMethod<Unit>(a,"mudarSituacaoPendencia",ReflectionHelpers.ClassParameter.from(Int::class.javaPrimitiveType,0))
        assertNull(ShadowAlertDialog.getLatestAlertDialog())
        assertFalse(JSONArray(store.prefs("registros_v3").getString("pendencias","[]")).getJSONObject(0).getBoolean("concluida"))
        ReflectionHelpers.callInstanceMethod<Unit>(a,"opcoesPendencia",ReflectionHelpers.ClassParameter.from(Int::class.javaPrimitiveType,0))
        shadowOf(Looper.getMainLooper()).idle()
        val adapter=ShadowAlertDialog.getLatestAlertDialog().listView.adapter
        val actions=(0 until adapter.count).map {adapter.getItem(it).toString()}
        assertTrue(actions.contains("Editar pendência"))
        assertFalse(actions.any {it.contains("Concluir")||it.contains("Reabrir")||it.contains("Excluir")})
    }
    @Test fun aManagerMustConfirmBeforeARecordIsChanged() {
        val (a,store)=activity("supervisor")
        ReflectionHelpers.callInstanceMethod<Unit>(a,"mudarSituacaoPendencia",ReflectionHelpers.ClassParameter.from(Int::class.javaPrimitiveType,0))
        val dialog=ShadowAlertDialog.getLatestAlertDialog()
        assertNotNull(dialog)
        assertFalse(JSONArray(store.prefs("registros_v3").getString("pendencias","[]")).getJSONObject(0).getBoolean("concluida"))
        dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE).performClick()
        assertFalse(JSONArray(store.prefs("registros_v3").getString("pendencias","[]")).getJSONObject(0).getBoolean("concluida"))
    }
}
