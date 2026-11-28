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

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28,35],manifest=Config.NONE,application=Application::class)
class SyncBaselineTest {
    private val team="santo_inacio_C"
    private val store get()=TeamStore(RuntimeEnvironment.getApplication(),team)
    private fun row()=JSONObject().put("id","issue-1").put("descricao","Trocar rolamento").put("concluida",false)
    private fun record(data:JSONObject=row(),kind:String="item",group:String="pendencias",key:String="issue-1")=JSONObject()
        .put("tipo",kind).put("grupo",group).put("chave",key).put("conteudo",data.toString()).put("excluido",RecordArchive.archived(data))
        .put("autorUid","original-author").put("editadoPor","original-editor").put("versao",7)
        .put("equipeId",team).put("frenteId","santo_inacio").put("turno","C")
    private fun wire(record:JSONObject):Map<String,Any> = BetaRecordCodec.toWire(record.keys().asSequence().associateWith {record.get(it)})
    private fun decode(data:Map<String,Any>,id:String=SyncBaseline.id("item","pendencias","issue-1"))=SyncBaseline.decode(id,data,team)
    private fun adopt(current:JSONObject,server:JSONObject,baseline:Boolean=false,queued:JSONObject?=null,conflict:Boolean=false)=
        SyncBaseline.mayAdopt(store,current,server,baseline,queued,conflict)

    @Test fun identicalServerDataKeepsItsRealAuthorAndRevisionWithoutTouchingPhotosOrLocalRecords() {
        val s=store
        val photo=s.file("fotos/one.jpg").apply {parentFile!!.mkdirs();writeText("ORIGINAL-LOCAL-PHOTO")}
        val current=record(row().put("foto",photo.absolutePath).put("__arquivado",false))
        val saved=JSONArray().put(JSONObject(current.getString("conteudo"))).toString()
        s.prefs("registros_v3").edit().putString("pendencias",saved).commit()
        val server=decode(wire(record(TeamContent.textOnly(JSONObject(current.getString("conteudo"))))))
        val before=server.toString();val original=current.toString()
        assertTrue(adopt(current,server))
        assertEquals("original-author",server.getString("autorUid"));assertEquals(7,server.getInt("versao"))
        assertEquals(before,server.toString());assertEquals(original,current.toString())
        assertEquals(saved,s.prefs("registros_v3").getString("pendencias",null));assertEquals("ORIGINAL-LOCAL-PHOTO",photo.readText())
    }

    @Test fun anUnchangedBlindCreateIsRedundantButRealChangesAndRevisionBasedEditsRemainPending() {
        val current=record();val server=decode(wire(record()))
        assertTrue(adopt(current,server,queued=record().put("base",0).put("autorUid","current-user")))
        assertFalse(adopt(current,server,queued=record().put("base",1)))
        assertFalse(adopt(current,server,queued=record(row().put("descricao","Relato ainda não enviado")).put("base",0)))
        assertFalse(adopt(record(row().put("descricao","Relato local novo")),server))
        assertFalse(adopt(current,server,baseline=true));assertFalse(adopt(current,server,conflict=true))
        assertFalse(adopt(current,server,queued=record().put("base","0")))
        assertFalse(adopt(current.put("__syncBase",4),server))
        assertTrue(adopt(current.put("__syncBase",7),server))
    }

    @Test fun lifecycleStateAndBusinessIdentityCannotBeHiddenByNormalization() {
        val server=decode(wire(record()))
        assertFalse(adopt(record(row().put("__arquivado",true)),server))
        assertFalse(adopt(record(row().put("concluida",true)),server))
        assertFalse(adopt(record(group="ocorrencias_campo"),server))
        assertFalse(adopt(record(key="another"),server))
        val archived=record(row().put("__arquivado",true).put("historicoExclusoes",JSONArray().put("preserved-history")))
        assertTrue(adopt(archived,decode(wire(archived))))
        assertFalse(adopt(record(row().put("__arquivado",true)),decode(wire(archived))))
    }

    @Test fun equivalentMediaArchiveMarkersAndJsonOrderingDoNotCauseRepeatedUploads() {
        val s=store
        val current=record(row().put("foto",s.file("photo.jpg").path).put("__arquivado",false).put("__syncBase",2))
        val server=record(row().put("fotos",JSONArray().put("@arquivo/old.jpg")))
        val a=SyncBaseline.content(s,current);val b=SyncBaseline.content(s,server)
        assertTrue(BetaRecordCodec.sameContent(a,b))
        assertFalse(JSONObject(a).has("__arquivado"));assertFalse(JSONObject(a).has("__syncBase"));assertFalse(JSONObject(a).has("foto"))
        assertTrue(JSONObject(a).getBoolean("anexosManuais"))
        assertFalse(BetaRecordCodec.sameContent(a,SyncBaseline.content(s,record(row().put("descricao","Different")))))
        val archived=record().put("excluido",true)
        assertTrue(JSONObject(SyncBaseline.content(s,archived)).getBoolean("__arquivado"))
        assertFalse(JSONObject(archived.getString("conteudo")).has("__arquivado"))
    }

    @Test fun preferenceBaselinesKeepOrderedMapPointsAndDoNotAcquireRecordArchiveFlags() {
        val current=record(kind="preferencia",group="pinturas",key="map-1").put("conteudo","[{\"x\":1,\"y\":2},{\"x\":3,\"y\":4}]")
        val server=SyncBaseline.decode(SyncBaseline.id("preferencia","pinturas","map-1"),wire(current),team)
        assertTrue(adopt(current,server))
        assertFalse(adopt(JSONObject(current.toString()).put("conteudo","[{\"x\":3,\"y\":4},{\"x\":1,\"y\":2}]"),server))
        assertFalse(adopt(JSONObject(current.toString()).put("excluido",true),server))
        assertTrue(SyncBaseline.content(store,current).startsWith("["))
    }

    @Test fun rejectsOtherTeamsForgedDocumentIdsUnsupportedFormatsAndInvalidTypedData() {
        val good=wire(record())
        assertEquals("issue-1",decode(good).getString("chave"))
        val invalid=listOf(
            good+("equipeId" to "santa_ines_C"),good+("frenteId" to "santa_ines"),good+("turno" to "A"),
            good+("versao" to 7.5),good+("versao" to 0),good+("formato" to 2.1),good+("formato" to 1),
            good+("autorUid" to ""),good+("editadoPor" to 4),good+("excluido" to "false"),
            good+("conteudo" to "{}"),good+("dados" to mapOf("id" to "other")),
            good+("dados" to mapOf("id" to "issue-1","concluida" to "false")),
            good+("dados" to mapOf("id" to "issue-1","__arquivado" to true)),
            good+("dados" to mapOf("id" to "issue-1","__arquivado" to "false"))
        )
        for(data in invalid) assertThrows(IllegalArgumentException::class.java) {decode(data)}
        assertThrows(IllegalArgumentException::class.java) {decode(good,"forged-second-document-for-the-same-record")}
        val bad=record(group="unknown")
        assertThrows(IllegalArgumentException::class.java) {decode(wire(bad),SyncBaseline.id("item","unknown","issue-1"))}
    }
}
