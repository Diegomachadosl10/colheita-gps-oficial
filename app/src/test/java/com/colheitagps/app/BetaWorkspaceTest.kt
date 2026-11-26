package com.colheitagps.app

import android.app.Application
import org.json.JSONObject
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28,35],manifest=Config.NONE,application=Application::class)
class BetaWorkspaceTest {
    @Test fun theSingleBetaWorkspaceNeverReadsOrOverwritesStablePreferencesOrFiles() {
        val app=RuntimeEnvironment.getApplication()
        val stable=app.getSharedPreferences("equipe_santo_inacio_A_registros_v3",0)
        stable.edit().putString("pendencias","real-data-sentinel").commit()
        val file=java.io.File(app.filesDir,"equipes/santo_inacio_A/mapas/original.pdf").apply { parentFile!!.mkdirs();writeText("original-sentinel") }
        val beta=TeamStore(app,"santo_inacio_A")
        assertNull(beta.prefs("registros_v3").getString("pendencias",null))
        assertFalse(beta.file("mapas/original.pdf").exists())
        beta.prefs("registros_v3").edit().putString("pendencias","synthetic-beta-data").commit()
        beta.file("mapas/original.pdf").apply {parentFile!!.mkdirs();writeText("beta-only")}
        assertEquals("real-data-sentinel",stable.getString("pendencias",null));assertEquals("original-sentinel",file.readText())
        assertEquals("ambientes/beta/usuarios",BetaWorkspace.path("usuarios"))
        assertFalse(BetaWorkspace.notificationsReady);assertFalse(BetaWorkspace.credentialChangesReady)
        assertThrows(IllegalArgumentException::class.java){BetaWorkspace.path("../usuarios")}
    }
    @Test fun theBetaCannotSendAccountOrNotificationActionsToTheProductionWorker() {
        val app=RuntimeEnvironment.getApplication()
        for(path in listOf("/accounts/delete","/accounts/correct","/events/notify","/devices/register")) {
            try { AgroService.call(app,path,JSONObject());fail("A Beta não deve enviar essa ação") }
            catch(error:AgroService.ServiceError) { assertEquals(503,error.code) }
        }
    }
    @Test fun typedStatusIsAuthoritativeAndJsonOrderingCannotCreateAnEndlessSyncLoop() {
        val payload=JSONObject().put("id","p").put("descricao","Texto livre").put("concluida",false)
            .put("fotos",JSONArray().put("@arquivo/foto.jpg")).put("campoNulo",JSONObject.NULL)
        val source=mapOf<String,Any>("tipo" to "item","grupo" to "pendencias","conteudo" to payload.toString())
        val wire=BetaRecordCodec.toWire(source)
        assertFalse(wire.containsKey("conteudo"));assertTrue(wire["dados"] is Map<*,*>)
        val restored=BetaRecordCodec.fromWire(wire)
        assertTrue(BetaRecordCodec.sameContent(payload.toString(),restored.getString("conteudo")))
        assertTrue(BetaRecordCodec.sameContent("{\"a\":1,\"b\":false}","{\"b\":false,\"a\":1.0}"))
        assertFalse(BetaRecordCodec.sameContent("[1,2]","[2,1]"))
        assertFalse(BetaRecordCodec.sameContent(payload.toString(),payload.put("concluida",true).toString()))
        assertThrows(IllegalArgumentException::class.java){BetaRecordCodec.fromWire(wire + ("conteudo" to "{}"))}
        assertThrows(IllegalArgumentException::class.java){BetaRecordCodec.fromWire(wire + ("formato" to 1L))}
    }
}
