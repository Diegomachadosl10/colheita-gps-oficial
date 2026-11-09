package com.colheitagps.app

import android.app.Application
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = Application::class)
class TeamStoreTest {
    @Test fun frontAndShiftIsolatePreferencesAndFilesOnTheSamePhone() {
        val context = RuntimeEnvironment.getApplication()
        val a = TeamStore(context, "santo_inacio_A")
        val b = TeamStore(context, "santo_inacio_B")
        a.prefs("registros_v3").edit().putString("pendencias", "segredo da equipe A").commit()
        assertNull(b.prefs("registros_v3").getString("pendencias", null))
        a.file("foto.jpg").writeText("foto A")
        assertFalse(b.file("foto.jpg").exists())
    }
    @Test fun localMediaPathsTravelAsRelativeReferencesAndCannotEscapeAnotherTeam() {
        val context = RuntimeEnvironment.getApplication()
        val a = TeamStore(context, "santo_inacio_A")
        val b = TeamStore(context, "santo_inacio_B")
        val payload = JSONObject().put("foto", a.file("foto.jpg").absolutePath).toString()
        val encoded = a.encode(payload)
        assertTrue(JSONObject(encoded).getString("foto").startsWith("@arquivo/"))
        assertFalse(JSONObject(encoded).getString("foto").startsWith(a.files.absolutePath))
        assertEquals(b.file("foto.jpg").absolutePath, JSONObject(b.decode(encoded)).getString("foto"))
        assertEquals("", JSONObject(b.decode(JSONObject().put("foto", a.file("foto.jpg").absolutePath).toString())).getString("foto"))
        assertEquals("", JSONObject(b.decode("{\"foto\":\"@arquivo/../fora.jpg\"}")).getString("foto"))
        assertEquals("", JSONObject(b.decode("{\"foto\":\"/storage/emulated/0/foto-particular.jpg\"}")).getString("foto"))
        assertFalse(JSONObject(a.encode(JSONObject().put("id", "teste").put("__syncBase", 5).toString())).has("__syncBase"))
    }
    @Test fun legacyImportKeepsOriginalsAndDoesNotReplaceAnExistingScopedValue() {
        val context = RuntimeEnvironment.getApplication()
        val original = context.getSharedPreferences("mapas", 0)
        original.edit().putString("mapa-antigo", "Nome original").commit()
        val store = TeamStore(context, "colorado_C")
        store.prefs("mapas").edit().putString("mapa-antigo", "Nome já cadastrado na equipe").commit()
        store.importLegacy()
        assertEquals("Nome original", original.getString("mapa-antigo", null))
        assertEquals("Nome já cadastrado na equipe", store.prefs("mapas").getString("mapa-antigo", null))
    }
    @Test fun legacyPhotosAreCopiedAndRebasedWithoutLosingExistingTeamRecords() {
        val context = RuntimeEnvironment.getApplication()
        val photo = java.io.File(context.filesDir, "legado.jpg").apply { writeText("foto original") }
        val old = org.json.JSONArray().put(JSONObject().put("id", "antigo").put("foto", photo.absolutePath)).toString()
        context.getSharedPreferences("registros_v3", 0).edit().putString("pendencias", old).commit()
        val store = TeamStore(context, "colorado_C")
        store.prefs("registros_v3").edit().putString("pendencias", "[{\"id\":\"novo\"}]").commit()
        store.importLegacy()
        val merged = org.json.JSONArray(store.prefs("registros_v3").getString("pendencias", "[]"))
        assertEquals(2, merged.length())
        assertEquals("novo", merged.getJSONObject(0).getString("id"))
        assertEquals(store.file("legado.jpg").absolutePath, merged.getJSONObject(1).getString("foto"))
        assertEquals("foto original", store.file("legado.jpg").readText())
        assertEquals(old, context.getSharedPreferences("registros_v3", 0).getString("pendencias", null))
        assertTrue(photo.exists())
        store.importLegacy()
        assertEquals(2, org.json.JSONArray(store.prefs("registros_v3").getString("pendencias", "[]")).length())
    }
}
