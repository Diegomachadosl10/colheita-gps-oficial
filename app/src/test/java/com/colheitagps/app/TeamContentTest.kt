package com.colheitagps.app

import android.app.Application
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = Application::class)
class TeamContentTest {
    @Test fun freeModeSendsTextWithoutPrivateMediaPaths() {
        val source = JSONObject().put("id", "registro").put("descricao", "Pneu furado")
            .put("fotos", JSONArray().put("/data/user/0/app/files/equipes/time/foto.jpg"))
            .put("fotoEstoque", "/data/user/0/app/files/equipes/time/estoque.jpg")
        val wire = TeamContent.textOnly(source)
        assertEquals("Pneu furado", wire.getString("descricao"))
        assertTrue(wire.getBoolean("anexosManuais"))
        assertFalse(wire.has("fotos")); assertFalse(wire.has("fotoEstoque"))
        assertTrue(source.has("fotos"))
        assertFalse(TeamPolicy.AUTOMATIC_ATTACHMENTS)
    }
    @Test fun receivingUpdatedTextKeepsThePhotoAlreadySavedOnThisPhone() {
        val existing = JSONObject().put("id", "registro").put("foto", "/foto-local.jpg")
        val incoming = JSONObject().put("id", "registro").put("descricao", "Supervisor concluiu")
            .put("foto", "/foto-de-outro-aparelho.jpg")
        val merged = TeamContent.preserveLocalMedia(incoming, existing)
        assertEquals("Supervisor concluiu", merged.getString("descricao"))
        assertEquals("/foto-local.jpg", merged.getString("foto"))
        assertFalse(TeamContent.preserveLocalMedia(incoming, null).has("foto"))
    }
}
