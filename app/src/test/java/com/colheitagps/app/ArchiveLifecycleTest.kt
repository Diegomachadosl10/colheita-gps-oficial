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
@Config(sdk=[28],manifest=Config.NONE,application=Application::class)
class ArchiveLifecycleTest {
    private fun issue(id:String)=JSONObject().put("id",id).put("equipamento","21569").put("descricao","Pneu").put("fotos",JSONArray().put("/foto-local.jpg"))
    @Test fun restoringAnEquipmentRestoresOnlyItsDeletionBatchAndPreservesCompletedStatePhotosAndIds() {
        val previous=RecordArchive.archive(issue("old"),"sup","Gestor","ontem")
        val all=MaintenanceRecords.merge(JSONArray().put(issue("open")).put(issue("done").put("concluida",true)).put(previous),JSONArray())
        val deleted=MaintenanceRecords.archiveEquipment(all,"21569","sup","Gestor","hoje")
        val restored=MaintenanceRecords.restoreEquipment(deleted,"21569",true,"admin","Diego","depois")
        assertEquals(all.length(),restored.length());assertEquals(1,MaintenanceRecords.summaries(restored).single().open);assertEquals(1,MaintenanceRecords.summaries(restored).single().completed)
        val rows=MaintenanceRecords.issues(restored)
        assertFalse(RecordArchive.archived(rows.getJSONObject(0)));assertTrue(rows.getJSONObject(1).getBoolean("concluida"));assertTrue(RecordArchive.archived(rows.getJSONObject(2)))
        assertEquals("/foto-local.jpg",rows.getJSONObject(0).getJSONArray("fotos").getString(0));assertEquals("open",rows.getJSONObject(0).getString("id"))
        assertEquals(2,rows.getJSONObject(0).getJSONArray("historicoExclusoes").length())
        assertTrue(RecordArchive.archived(MaintenanceRecords.issues(deleted).getJSONObject(0)))
    }
    @Test fun restoringOnlyTheEquipmentLeavesItsIssuesInTheHistoryAndNormalSavesKeepArchivedRecords() {
        val all=MaintenanceRecords.merge(JSONArray().put(issue("p")),JSONArray())
        val deleted=MaintenanceRecords.archiveEquipment(all,"21569","sup","Gestor","hoje")
        val restored=MaintenanceRecords.restoreEquipment(deleted,"21569",false,"sup","Gestor","depois")
        assertEquals(0,MaintenanceRecords.summaries(restored).single().open);assertTrue(RecordArchive.archived(MaintenanceRecords.issues(restored).getJSONObject(0)))
        val archived=RecordArchive.archive(JSONObject().put("id","occ").put("descricao","Buraco"),"sup","Gestor","hoje")
        val merged=RecordArchive.merge(JSONArray().put(JSONObject().put("id","other")),JSONArray().put(archived))
        assertEquals(2,merged.length());assertEquals(1,RecordArchive.active(merged).length())
        val revived=RecordArchive.restore(archived,"sup","Gestor","depois")
        assertEquals(1,RecordArchive.merge(JSONArray().put(revived),JSONArray().put(archived)).length())
    }
    private fun wire(id:String,group:String,deleted:Boolean)=JSONObject().put("tipo","item").put("grupo",group).put("chave",id).put("autorUid","op")
        .put("conteudo",JSONObject().put("id",id).put("descricao","Preservado").toString()).put("versao",2).put("excluido",deleted)
    @Test fun oldDeletedStockAndOccurrenceBaselinesAreRecoveredAsArchivedWithoutOverwritingAnIntentionalRestore() {
        val app=RuntimeEnvironment.getApplication();val store=TeamStore(app,"santo_inacio_A");val prefs=app.getSharedPreferences("baseline",0)
        prefs.edit().putString("b_stock",wire("stock","estoques_salvos",true).toString()).putString("b_occ",wire("occ","ocorrencias_campo",true).toString()).commit()
        val access=TeamAccess("sup",mapOf("papel" to "supervisor","status" to "aprovado","frenteId" to "santo_inacio","turno" to "A"))
        ArchiveRecovery.recover(store,prefs,access);ArchiveRecovery.recover(store,prefs,access)
        val recovered=JSONArray(store.prefs("registros_v3").getString("estoques_salvos","[]"));assertEquals(1,recovered.length());assertTrue(RecordArchive.archived(recovered.getJSONObject(0)))
        val active=RecordArchive.restore(recovered.getJSONObject(0),"sup","Gestor","agora")
        store.prefs("registros_v3").edit().putString("estoques_salvos",JSONArray().put(active).toString()).commit()
        ArchiveRecovery.recover(store,prefs,access)
        assertFalse(RecordArchive.archived(JSONArray(store.prefs("registros_v3").getString("estoques_salvos","[]")).getJSONObject(0)))
    }
    @Test fun aLegacyOfflineOperatorDeleteBecomesAValidPendingSaveInsteadOfBlockingEverySubsequentSync() {
        val app=RuntimeEnvironment.getApplication();val store=TeamStore(app,"santo_inacio_A");val prefs=app.getSharedPreferences("baseline",0)
        prefs.edit().putString("q_occ",wire("occ","ocorrencias_campo",true).put("base",0).toString()).commit()
        val access=TeamAccess("op",mapOf("papel" to "operador","status" to "aprovado","frenteId" to "santo_inacio","turno" to "A"))
        ArchiveRecovery.recover(store,prefs,access)
        assertFalse(JSONObject(prefs.getString("q_occ","{}")).getBoolean("excluido"))
        assertEquals(1,RecordArchive.active(JSONArray(store.prefs("registros_v3").getString("ocorrencias_campo","[]"))).length())
        prefs.edit().putString("b_occ",wire("occ","ocorrencias_campo",true).toString()).putString("q_occ",wire("occ","ocorrencias_campo",true).toString()).commit()
        ArchiveRecovery.recover(store,prefs,access)
        assertFalse(prefs.contains("q_occ"));assertTrue(RecordArchive.archived(JSONArray(store.prefs("registros_v3").getString("ocorrencias_campo","[]")).getJSONObject(0)))
    }
}
