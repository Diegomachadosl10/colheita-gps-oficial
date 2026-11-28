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
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28,35],manifest=Config.NONE,application=Application::class)
class StableLocalBundleTest {
    private val app get()=RuntimeEnvironment.getApplication()
    private val team="santo_inacio_C"
    private fun actor(uid:String="synthetic-owner",role:String="supervisor",status:String="aprovado")=TeamAccess(uid,
        mapOf("status" to status,"papel" to role,"frenteId" to "santo_inacio","turno" to "C"))
    private fun pref(name:String)=app.getSharedPreferences(name,0)
    private fun hash(bytes:ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {"%02x".format(it)}
    private fun file(path:String,bytes:String="synthetic-file")=File(app.filesDir,"equipes/$team/$path").apply {parentFile!!.mkdirs();writeText(bytes)}
    private fun rows(data:JSONObject) {pref("equipe_${team}_registros_v3").edit().putString("pendencias",JSONArray().put(data).toString()).commit()}
    private fun entries(receipt:StableLocalBundle.Receipt):LinkedHashMap<String,ByteArray> = ZipFile(receipt.archive).use {zip->
        linkedMapOf<String,ByteArray>().apply {for(entry in zip.entries()) put(entry.name,zip.getInputStream(entry).use {it.readBytes()})}
    }
    // Deliberately forge the private checksum too, to exercise ZIP validation beyond the outer digest.
    private fun replace(receipt:StableLocalBundle.Receipt,contents:Map<String,ByteArray>):StableLocalBundle.Receipt {
        ZipOutputStream(receipt.archive.outputStream()).use {zip->for((name,data) in contents) {zip.putNextEntry(ZipEntry(name));zip.write(data);zip.closeEntry()}}
        return renewReceipt(receipt)
    }
    private fun renewReceipt(receipt:StableLocalBundle.Receipt):StableLocalBundle.Receipt {
        val updated=receipt.copy(sha256=hash(receipt.archive.readBytes()))
        val sidecar=File(receipt.archive.parentFile,"${receipt.fingerprint}.receipt.json")
        sidecar.writeText(JSONObject(sidecar.readText()).put("sha256",updated.sha256).toString())
        return updated
    }
    private fun pending(data:JSONObject)=JSONObject().put("tipo","item").put("grupo","pendencias").put("chave",data.getString("id"))
        .put("conteudo",data.toString()).put("autorUid","original-author").put("excluido",RecordArchive.archived(data)).put("base",7)

    @Test fun roundTripPreservesOriginalPdfPaintingsMarkedPhotosDraftsIdsAndPendingConflictsWithoutActivation() {
        val access=actor();val photo=file("fotos/marked.jpg","MARKED");file("fotos/original.jpg","ORIGINAL")
        file("fotos/marked.jpg.markup.json",JSONObject().put("original","fotos/original.jpg").put("marks",JSONArray()).toString())
        file("mapas/known.pdf","PDF-ORIGINAL-GEOREFERENCE")
        pref("equipe_${team}_mapas").edit().putString("known","Mapa original").commit()
        val paints="[{\"id\":\"paint-id\",\"pontos\":[[1,2],[3,4],[5,6]]}]"
        pref("equipe_${team}_pinturas").edit().putString("known",paints).commit()
        val row=JSONObject().put("id","issue-id").put("__arquivado",true).put("descricao","Histórico\ncom detalhes").put("foto",photo.path)
            .put("historicoExclusoes",JSONArray().put(JSONObject().put("acao","excluido")))
        rows(row)
        val sync=pref("sincronizacao_${access.uid}_$team")
        sync.edit().putString("q_pending",pending(row).toString()).putString("c_conflict",pending(row).put("base",6).toString()).putLong("n_notice",7).commit()
        pref("rascunhos_campo_${team}_${access.uid}").edit().putString("pendencia_nova",JSONObject().put("descricao","não salvo").put("foto",photo.path).toString()).commit()
        val beta=TeamStore(app,team);beta.prefs("registros_v3").edit().putString("pendencias","BETA-SENTINEL").commit()
        val expected=StableLocalInventory(app).capture(access,team)
        val bundle=StableLocalBundle(app);val receipt=bundle.create(access,team)
        val restored=bundle.restoreQuarantine(receipt,access)
        assertEquals(expected,restored.snapshot)
        assertEquals("MARKED",File(restored.directory,"files/fotos/marked.jpg").readText())
        assertEquals("ORIGINAL",File(restored.directory,"files/fotos/original.jpg").readText())
        assertEquals("PDF-ORIGINAL-GEOREFERENCE",File(restored.directory,"files/mapas/known.pdf").readText())
        assertEquals(paints,restored.snapshot.preferences.getValue("pinturas")["known"])
        assertEquals(expected,StableLocalInventory(app).capture(access,team));assertEquals(7,sync.getLong("n_notice",0))
        assertEquals("BETA-SENTINEL",beta.prefs("registros_v3").getString("pendencias",null))
        assertTrue(beta.files.listFiles().orEmpty().isEmpty());assertTrue(File(restored.directory,"snapshot.json").isFile)
        assertTrue(receipt.archive.canonicalPath.startsWith(app.noBackupFilesDir.canonicalPath+File.separator))
        assertFalse(receipt.archive.path.contains(access.uid));assertFalse(restored.snapshot.synchronization.containsKey("n_notice"))
    }

    @Test fun checkpointSurvivesRestartAndRestoresWhenOriginalDataIsNoLongerAvailable() {
        val access=actor();val original=file("one.jpg","ORIGINAL")
        rows(JSONObject().put("id","one").put("foto",original.path))
        val first=StableLocalBundle(app).create(access,team)
        original.delete();pref("equipe_${team}_registros_v3").edit().clear().commit()
        val restarted=StableLocalBundle(app) {_,_->error("Recovery must not recapture the damaged source")}
        assertEquals(first,restarted.open(access,team,first.fingerprint))
        val restored=restarted.restoreQuarantine(first,access)
        assertEquals("ORIGINAL",File(restored.directory,"files/one.jpg").readText())
        assertFalse(original.exists());assertTrue(pref("equipe_${team}_registros_v3").all.isEmpty())
    }

    @Test fun repeatedCheckpointIsIdempotentWhileNewSourceStateKeepsTheOlderCheckpoint() {
        val access=actor();val photo=file("one.jpg","FIRST");rows(JSONObject().put("id","one").put("foto",photo.path))
        val bundle=StableLocalBundle(app);val first=bundle.create(access,team);val bytes=first.archive.readBytes();val modified=first.archive.lastModified()
        assertEquals(first,bundle.create(access,team));assertArrayEquals(bytes,first.archive.readBytes());assertEquals(modified,first.archive.lastModified())
        photo.writeText("SECOND");val second=bundle.create(access,team)
        assertNotEquals(first.fingerprint,second.fingerprint);assertTrue(first.archive.exists());assertTrue(second.archive.exists())
        assertEquals("FIRST",File(bundle.restoreQuarantine(first,access).directory,"files/one.jpg").readText())
        assertEquals("SECOND",photo.readText())
    }

    @Test fun changesDuringCopyAndInvalidSourcesNeverProduceAReadyCheckpoint() {
        val access=actor();val photo=file("one.jpg","FIRST");rows(JSONObject().put("id","one").put("foto",photo.path))
        val inventory=StableLocalInventory(app);var captures=0
        val racing=StableLocalBundle(app) {who,which->if(++captures==2) photo.writeText("CHANGED");inventory.capture(who,which)}
        assertThrows(IllegalArgumentException::class.java) {racing.create(access,team)}
        val root=File(app.noBackupFilesDir,"beta_transition")
        assertTrue(root.walkTopDown().none {it.isFile});assertEquals("CHANGED",photo.readText())
        photo.delete()
        assertThrows(IllegalArgumentException::class.java) {StableLocalBundle(app).create(access,team)}
        assertTrue(root.walkTopDown().none {it.isFile})
    }

    @Test fun ownershipRoleApprovalAndTeamAreCheckedAgainBeforeRecovery() {
        val access=actor();rows(JSONObject().put("id","one"))
        val bundle=StableLocalBundle(app);val receipt=bundle.create(access,team)
        for(other in listOf(actor("other"),actor(role="operador"),actor(status="bloqueado"),
            TeamAccess(access.uid,access.profile+("frenteId" to "santa_ines"))))
            assertThrows(IllegalArgumentException::class.java) {bundle.restoreQuarantine(receipt,other)}
        assertThrows(IllegalArgumentException::class.java) {bundle.restoreQuarantine(receipt.copy(team="santa_ines_C"),access)}
        assertFalse(File(receipt.archive.parentFile,"restores").exists());assertTrue(receipt.archive.exists())
    }

    @Test fun privateMapOwnershipAndPreferenceTypesRemainDistinctAndImmutable() {
        val access=actor(role="operador");val owner=hash(access.uid.toByteArray())
        file("mapas/own.pdf");file("mapas/shared.pdf")
        pref("mapa_privado_${team}_${owner}_mapas").edit().putString("own","Meu mapa").commit()
        pref("equipe_${team}_mapas").edit().putString("shared","Mapa da equipe").commit()
        pref("inicio_${team}_${access.uid}").edit().putString("texto","Livre\n\"sem formato obrigatório\"")
            .putStringSet("favoritas",setOf("mapas","pendencias")).putBoolean("bool",false).putInt("int",0).putLong("long",0L).putFloat("float",-0.0f).commit()
        rows(JSONObject().put("id","one"))
        val bundle=StableLocalBundle(app);val restored=bundle.restoreQuarantine(bundle.create(access,team),access)
        assertEquals(listOf("mapas/own.pdf"),restored.snapshot.attachments.map {it.relativePath})
        assertFalse(restored.snapshot.preferences.containsKey("mapas"))
        val values=restored.snapshot.preferences.getValue("inicio")
        assertTrue(values["int"] is Int);assertTrue(values["long"] is Long);assertTrue(values["bool"] is Boolean)
        assertEquals(java.lang.Float.floatToRawIntBits(-0.0f),java.lang.Float.floatToRawIntBits(values["float"] as Float))
        assertEquals(setOf("mapas","pendencias"),values["favoritas"])
        assertThrows(UnsupportedOperationException::class.java) {(values["favoritas"] as MutableSet<String>).add("caminhoes")}
        assertThrows(UnsupportedOperationException::class.java) {(values as MutableMap<String,Any>)["int"]=7}
    }

    @Test fun alteredOrForeignArchivesAndForgedReceiptsAreRefusedWithoutOverwritingTheCheckpoint() {
        val access=actor();rows(JSONObject().put("id","one"));val bundle=StableLocalBundle(app);val receipt=bundle.create(access,team)
        val external=File(app.cacheDir,"external.zip").apply {writeBytes(receipt.archive.readBytes())}
        assertThrows(IllegalArgumentException::class.java) {bundle.restoreQuarantine(receipt.copy(archive=external),access)}
        assertThrows(IllegalArgumentException::class.java) {bundle.restoreQuarantine(receipt.copy(sha256="0".repeat(64)),access)}
        receipt.archive.appendText("unexpected bytes");val altered=receipt.archive.readBytes()
        assertThrows(IllegalArgumentException::class.java) {bundle.open(access,team,receipt.fingerprint)}
        assertThrows(IllegalArgumentException::class.java) {bundle.create(access,team)}
        assertArrayEquals(altered,receipt.archive.readBytes());assertTrue(pref("equipe_${team}_registros_v3").contains("pendencias"))
    }

    @Test fun zipTraversalUnknownEntriesHashMismatchAndSchemaMismatchLeaveNoRestorationBehind() {
        val access=actor();val original=file("one.jpg","ORIGINAL");rows(JSONObject().put("id","one").put("foto",original.path))
        val bundle=StableLocalBundle(app);var receipt=bundle.create(access,team);val good=entries(receipt)
        val badCases=listOf(
            LinkedHashMap(good).apply {put("files/../../escape.jpg","BAD".toByteArray())},
            LinkedHashMap(good).apply {put("unexpected.txt","BAD".toByteArray())},
            LinkedHashMap(good).apply {put("files/one.jpg","DIFFERENT".toByteArray())},
            LinkedHashMap(good).apply {
                val json=JSONObject(String(getValue("snapshot.json")))
                json.getJSONArray("attachments").getJSONObject(0).put("path","../../escape.jpg")
                put("snapshot.json",json.toString().toByteArray());put("files/../../escape.jpg",remove("files/one.jpg")!!)
            },
            LinkedHashMap(good).apply {put("snapshot.json",JSONObject(String(getValue("snapshot.json"))).put("schema",2).toString().toByteArray())}
        )
        for(bad in badCases) {
            receipt=replace(receipt,bad)
            assertThrows(IllegalArgumentException::class.java) {bundle.restoreQuarantine(receipt,access)}
            assertTrue(File(receipt.archive.parentFile,"restores").listFiles().orEmpty().isEmpty())
        }
        assertEquals("ORIGINAL",original.readText());assertFalse(File(receipt.archive.parentFile,"escape.jpg").exists())
    }

    @Test fun symlinksCannotRedirectCheckpointStorageOrRecovery() {
        val access=actor();rows(JSONObject().put("id","one"))
        val external=File(app.cacheDir,"outside").apply {mkdirs()}
        Files.createSymbolicLink(File(app.noBackupFilesDir,"beta_transition").toPath(),external.toPath())
        assertThrows(IllegalArgumentException::class.java) {StableLocalBundle(app).create(access,team)}
        assertTrue(external.listFiles().orEmpty().isEmpty())
        Files.delete(File(app.noBackupFilesDir,"beta_transition").toPath())
        val bundle=StableLocalBundle(app);val receipt=bundle.create(access,team)
        val outside=File(external,"outside.zip").apply {writeBytes(receipt.archive.readBytes())};val bytes=outside.readBytes()
        assertTrue(receipt.archive.delete());Files.createSymbolicLink(receipt.archive.toPath(),outside.toPath())
        assertThrows(IllegalArgumentException::class.java) {bundle.restoreQuarantine(receipt,access)}
        assertArrayEquals(bytes,outside.readBytes())
    }
}
