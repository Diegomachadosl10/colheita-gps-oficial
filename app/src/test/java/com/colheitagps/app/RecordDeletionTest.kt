package com.colheitagps.app

import android.app.Application
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = Application::class)
class RecordDeletionTest {
    private fun change() = JSONObject().put("tipo", "item").put("grupo", "ocorrencias_campo")
        .put("chave", "id1").put("autorUid", "opA").put("conteudo", "conteúdo original")
        .put("base", 0).put("excluido", true)
    @Test fun offlineDeletionHandlesBothAnUnsentCreationAndACreationWithLostAcknowledgment() {
        val pending = change()
        assertTrue(RecordDeletion.canDeleteUnconfirmed(pending, null))
        assertTrue(RecordDeletion.canDeleteUnconfirmed(pending, JSONObject(pending.toString()).put("versao", 1).put("excluido", false)))
    }
    @Test fun uncertainDeletionCannotEraseAnotherAuthorOrLaterChanges() {
        val pending = change()
        for ((field, value) in listOf("autorUid" to "opB", "chave" to "id2", "conteudo" to "alterado", "versao" to 2)) {
            assertFalse(field, RecordDeletion.canDeleteUnconfirmed(pending, JSONObject(pending.toString()).put("versao", 1).put(field, value)))
        }
    }
    @Test fun retryCanAcknowledgeAnAlreadyDeletedRecordWithoutRewritingItsHistory() {
        val pending = change()
        val remote = JSONObject(pending.toString()).put("versao", 8)
        assertTrue(RecordDeletion.alreadyDeleted(pending, remote))
        assertFalse(RecordDeletion.alreadyDeleted(pending, JSONObject(remote.toString()).put("autorUid", "outro")))
        assertFalse(RecordDeletion.alreadyDeleted(pending, JSONObject(remote.toString()).put("excluido", false)))
    }
}
