package com.colheitagps.app

import android.app.Application
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28,35],manifest=Config.NONE,application=Application::class)
class StableLocalInventoryTest {
    private val app get()=RuntimeEnvironment.getApplication()
    private val team="santo_inacio_C"
    private fun actor(uid:String="synthetic-user",manager:Boolean=true)=TeamAccess(uid,mapOf("status" to "aprovado","papel" to if(manager) "supervisor" else "operador","frenteId" to "santo_inacio","turno" to "C"))
    private fun hash(text:String)=MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") {"%02x".format(it)}
    private fun pref(name:String)=app.getSharedPreferences(name,0)
    private fun stable(relative:String,bytes:String="synthetic-file")=File(app.filesDir,"equipes/$team/$relative").apply {parentFile!!.mkdirs();writeText(bytes)}
    private fun save(group:String,rows:JSONArray) {pref("equipe_${team}_registros_v3").edit().putString(group,rows.toString()).commit()}
    private fun wire(id:String,group:String="pendencias",content:JSONObject=JSONObject().put("id",id))=JSONObject()
        .put("tipo","item").put("grupo",group).put("chave",id).put("autorUid","original-author")
        .put("versao",7).put("base",7).put("excluido",RecordArchive.archived(content)).put("conteudo",content.toString())

    @Test fun capturesRecordsQueueConflictsPaintingsAndOriginalPhotosWithoutWritingEitherWorkspace() {
        val access=actor()
        val original=stable("fotos/original.jpg","ORIGINAL")
        val marked=stable("fotos/marcada.jpg","MARKED")
        val sidecar=stable("fotos/marcada.jpg.markup.json",JSONObject().put("original","fotos/original.jpg").put("marks",JSONArray()).toString())
        val pdf=stable("mapas/known.pdf","PDF-WITH-ORIGINAL-METADATA")
        pref("equipe_${team}_mapas").edit().putString("known","Mapa de teste").commit()
        val paints="[{\"id\":\"paint-1\",\"pontos\":[[1,2],[3,4],[5,6]]}]"
        pref("equipe_${team}_pinturas").edit().putString("known",paints).commit()
        val row=JSONObject().put("id","p-1").put("descricao","Relato livre\ncom detalhes").put("fotos",JSONArray().put(marked.path))
            .put("__arquivado",true).put("historicoExclusoes",JSONArray().put(JSONObject().put("acao","excluido")))
        val raw=JSONArray().put(row).toString()
        pref("equipe_${team}_registros_v3").edit().putString("pendencias",raw).commit()
        val id=hash("item|pendencias|p-1");val baseline=wire("p-1").toString()
        val sync=pref("sincronizacao_${access.uid}_$team")
        sync.edit().putString("b_$id",baseline).putString("q_$id",wire("p-1",content=row).toString())
            .putString("c_other",wire("conflict").toString()).putLong("n_$id",7).commit()
        val beta=TeamStore(app,team)
        beta.prefs("registros_v3").edit().putString("pendencias","beta-sentinel").commit()
        val result=StableLocalInventory(app).capture(access,team)
        assertTrue(result.locallyConsistent);assertEquals(raw,result.preferences.getValue("registros_v3")["pendencias"])
        assertEquals(baseline,result.synchronization["b_$id"]);assertTrue(result.synchronization.containsKey("q_$id"));assertTrue(result.synchronization.containsKey("c_other"))
        assertFalse(result.synchronization.containsKey("n_$id"));assertEquals(7,sync.getLong("n_$id",0))
        assertEquals(paints,result.preferences.getValue("pinturas")["known"])
        assertEquals(setOf("fotos/original.jpg","fotos/marcada.jpg","fotos/marcada.jpg.markup.json","mapas/known.pdf"),result.attachments.map {it.relativePath}.toSet())
        assertEquals("ORIGINAL",original.readText());assertEquals("MARKED",marked.readText());assertTrue(sidecar.exists());assertEquals("PDF-WITH-ORIGINAL-METADATA",pdf.readText())
        assertEquals("beta-sentinel",beta.prefs("registros_v3").getString("pendencias",null));assertTrue(beta.files.listFiles().orEmpty().isEmpty())
        assertEquals(raw,pref("equipe_${team}_registros_v3").getString("pendencias",null))
    }

    @Test fun operatorInventoryNeverAdoptsSharedMapsAnotherPersonsMapsOrManagerOnlyRecords() {
        val access=actor("operator-one",false)
        stable("mapas/own.pdf");stable("mapas/other.pdf");stable("mapas/shared.pdf")
        stable("mapa_relatorio_own.png","UNSTAMPED-PAINTED-CACHE")
        pref("mapa_privado_${team}_${hash(access.uid)}_mapas").edit().putString("own","Mapa pessoal").putBoolean("_migrado",true).commit()
        pref("mapa_privado_${team}_${hash("other-user")}_mapas").edit().putString("other","Mapa de outra pessoa").commit()
        pref("equipe_${team}_mapas").edit().putString("shared","Mapa compartilhado").commit()
        save("pendencias",JSONArray().put(JSONObject().put("id","p1").put("descricao","Ajustar")))
        save("estoques_salvos",JSONArray().put(JSONObject().put("id","s1").put("os","PRIVATE-MANAGER-LOT")))
        pref("avisos_${access.uid}").edit().putString("token","FAKE-TOKEN-NOT-TO-COPY").commit()
        val result=StableLocalInventory(app).capture(access,team)
        assertTrue(result.locallyConsistent)
        assertEquals(listOf("mapas/own.pdf"),result.attachments.map {it.relativePath})
        assertFalse(result.preferences.containsKey("mapas"));assertFalse(result.preferences.getValue("registros_v3").containsKey("estoques_salvos"))
        assertEquals("Mapa pessoal",result.preferences.getValue("privado/mapas")["own"])
        assertFalse(result.preferences.toString().contains("FAKE-TOKEN"));assertFalse(File(app.filesDir,"beta").exists())
    }

    @Test fun pendingPhotosAndPartsCameraDraftsArePreservedEvenWithoutASavedRecord() {
        val access=actor("one",false)
        val queued=stable("pendente.jpg","PENDING-ONLY")
        val camera=stable("pecas/camera.jpg","CAMERA-DRAFT")
        val data=wire("pending",content=JSONObject().put("id","pending").put("foto",queued.path))
        pref("sincronizacao_${access.uid}_$team").edit().putString("q_${hash("item|pendencias|pending")}",data.toString()).commit()
        pref("rascunho_peca_${team}_${access.uid}").edit().putString("json",JSONObject().put("codigoPeca","123-A").put("cameraPeca",camera.path).toString()).commit()
        val result=StableLocalInventory(app).capture(access,team)
        assertTrue(result.locallyConsistent)
        assertEquals(setOf("pendente.jpg","pecas/camera.jpg"),result.attachments.map {it.relativePath}.toSet())
        assertTrue(result.preferences.getValue("registros_v3").isEmpty());assertEquals(data.toString(),result.synchronization.values.single())
        assertTrue(result.preferences.getValue("rascunho_peca").getValue("json").toString().contains("123-A"))
    }

    @Test fun fingerprintsAreRepeatableAndDetectChangedFilesPreferencesQueuesAndAccountScope() {
        val access=actor();val image=stable("foto.jpg","FIRST")
        save("pendencias",JSONArray().put(JSONObject().put("id","p1").put("foto",image.path)))
        pref("inicio_${team}_${access.uid}").edit().putStringSet("favoritas",setOf("Mapas","Pendências")).commit()
        val inventory=StableLocalInventory(app)
        val first=inventory.capture(access,team)
        assertEquals(first.fingerprint,inventory.capture(access,team).fingerprint)
        assertEquals(hash("FIRST"),first.attachments.single().sha256)
        image.writeText("SECOND")
        val second=inventory.capture(access,team)
        assertNotEquals(first.fingerprint,second.fingerprint);assertEquals(hash("FIRST"),first.attachments.single().sha256)
        pref("sincronizacao_${access.uid}_$team").edit().putString("q_new",wire("new").toString()).commit()
        assertNotEquals(second.fingerprint,inventory.capture(access,team).fingerprint)
        assertNotEquals(inventory.capture(access,team).fingerprint,inventory.capture(actor("another"),team).fingerprint)
        @Suppress("UNCHECKED_CAST")
        val stars=first.preferences.getValue("inicio").getValue("favoritas") as MutableSet<String>
        assertThrows(UnsupportedOperationException::class.java) {stars.add("changed")}
    }

    @Test fun malformedRowsDuplicateIdsMissingPhotosAndUnknownGroupsBlockAConsistentPlanWithoutDroppingRawData() {
        val broken="[{\"id\":\"same\",\"foto\":\"@arquivo/missing.jpg\"},{\"id\":\"same\"}]"
        pref("equipe_${team}_registros_v3").edit().putString("pendencias",broken).putString("unknown_future_group","raw-sentinel").commit()
        val result=StableLocalInventory(app).capture(actor(),team)
        assertFalse(result.locallyConsistent);assertTrue(result.issues.contains("RECORD_ID_INVALID_OR_DUPLICATE"));assertTrue(result.issues.contains("UNKNOWN_RECORD_GROUP"))
        assertEquals(setOf("missing.jpg"),result.missing)
        assertEquals(broken,result.preferences.getValue("registros_v3")["pendencias"])
        assertEquals("raw-sentinel",pref("equipe_${team}_registros_v3").getString("unknown_future_group",null))
        pref("equipe_${team}_registros_v3").edit().putString("pendencias","invalid-json").commit()
        assertTrue(StableLocalInventory(app).capture(actor(),team).issues.contains("OPERATIONAL_JSON_INVALID"))
    }

    @Test fun traversalForeignFilesAndSymlinkedPhotosNeverEnterTheInventory() {
        val external=File(app.filesDir,"outside.jpg").apply {writeText("DO-NOT-READ")}
        val link=stable("link.jpg");link.delete();Files.createSymbolicLink(link.toPath(),external.toPath())
        save("pendencias",JSONArray().put(JSONObject().put("id","p1").put("fotos",JSONArray()
            .put("@arquivo/../outside.jpg").put(external.path).put(link.path))))
        val result=StableLocalInventory(app).capture(actor(),team)
        assertFalse(result.locallyConsistent);assertTrue(result.attachments.isEmpty());assertTrue(result.issues.contains("ATTACHMENT_PATH_INVALID"))
        assertEquals("DO-NOT-READ",external.readText())
    }

    @Test fun sourceWorkspaceSymlinksAndUnauthorizedTeamsAreRejectedBeforeCapture() {
        val foreign=File(app.filesDir,"equipes/santa_ines_C").apply {mkdirs()}
        val root=File(app.filesDir,"equipes/$team");root.parentFile!!.mkdirs();Files.createSymbolicLink(root.toPath(),foreign.toPath())
        assertThrows(IllegalArgumentException::class.java) {StableLocalInventory(app).capture(actor(),team)}
        assertThrows(IllegalArgumentException::class.java) {StableLocalInventory(app).capture(actor(),"santa_ines_C")}
        assertThrows(IllegalArgumentException::class.java) {StableLocalInventory(app).capture(TeamAccess("pending",actor().profile+("status" to "pendente")),team)}
    }

    @Test fun nestedPhotoProvenanceIsPreservedAndCyclesAreReported() {
        val a=stable("a.jpg");stable("b.jpg");stable("c.jpg")
        stable("a.jpg.markup.json",JSONObject().put("original","b.jpg").put("marks",JSONArray()).toString())
        val b=stable("b.jpg.markup.json",JSONObject().put("original","c.jpg").put("marks",JSONArray()).toString())
        save("pendencias",JSONArray().put(JSONObject().put("id","p1").put("foto",a.path)))
        val first=StableLocalInventory(app).capture(actor(),team)
        assertTrue(first.locallyConsistent);assertEquals(5,first.attachments.size)
        b.writeText(JSONObject().put("original","a.jpg").put("marks",JSONArray()).toString())
        val cyclic=StableLocalInventory(app).capture(actor(),team)
        assertFalse(cyclic.locallyConsistent);assertTrue(cyclic.issues.contains("PHOTO_ORIGINAL_CYCLE"))
    }
}
