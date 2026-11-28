package com.colheitagps.app

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class LocalCheckpointNativeTest {
    @Test fun checkpointReopensAndRestoresOriginalBytesWithoutInstallingPreferencesOnAndroid() {
        val target=InstrumentationRegistry.getInstrumentation().targetContext
        val id=UUID.randomUUID().toString();val fixture=File(target.cacheDir,"checkpoint-fixture-$id")
        val names=mutableSetOf<String>()
        val app=object:ContextWrapper(target) {
            override fun getApplicationContext():Context=this
            override fun getFilesDir()=File(fixture,"files").apply {mkdirs()}
            override fun getNoBackupFilesDir()=File(fixture,"no_backup").apply {mkdirs()}
            override fun getSharedPreferences(name:String,mode:Int):SharedPreferences {
                val scoped="checkpoint_fixture_${id}_$name";names.add(scoped)
                return super.getSharedPreferences(scoped,mode)
            }
        }
        try {
            val team="santo_inacio_C"
            val actor=TeamAccess("synthetic-native-owner",mapOf("status" to "aprovado","papel" to "supervisor","frenteId" to "santo_inacio","turno" to "C"))
            val source=File(app.filesDir,"equipes/$team/foto.jpg").apply {parentFile!!.mkdirs();writeText("SYNTHETIC-PHOTO-BYTES")}
            val preferences=app.getSharedPreferences("equipe_${team}_registros_v3",0)
            val raw=JSONArray().put(JSONObject().put("id","synthetic-issue").put("descricao","Relato original").put("foto",source.path)).toString()
            assertTrue(preferences.edit().putString("pendencias",raw).commit())
            val checkpoint=StableLocalBundle(app).create(actor,team)
            assertTrue(source.delete())
            val restarted=StableLocalBundle(app);val loaded=restarted.open(actor,team,checkpoint.fingerprint)
            assertEquals(checkpoint,loaded)
            val restored=restarted.restoreQuarantine(loaded,actor)
            assertEquals("SYNTHETIC-PHOTO-BYTES",File(restored.directory,"files/foto.jpg").readText())
            assertEquals(raw,preferences.getString("pendencias",null));assertFalse(source.exists())
            assertEquals(raw,restored.snapshot.preferences.getValue("registros_v3")["pendencias"])
            assertFalse(File(app.filesDir,"beta").exists())
        } finally {
            names.forEach {target.deleteSharedPreferences(it)}
            fixture.deleteRecursively()
        }
    }
}
