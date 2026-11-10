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
class MaintenanceRecordsTest {
    @Test fun deletingEquipmentArchivesItsOpenAndCompletedIssuesWithoutChangingOtherEquipmentOrPhotos() {
        val open = JSONObject().put("id", "aberta").put("equipamento", "21569").put("descricao", "Pneu")
            .put("fotos", JSONArray().put("/foto-local.jpg"))
        val done = JSONObject().put("id", "concluida").put("equipamento", "21569").put("concluida", true)
        val other = JSONObject().put("id", "outra").put("equipamento", "33000")
        val all = MaintenanceRecords.merge(JSONArray().put(open).put(done).put(other), JSONArray())
        val archived = MaintenanceRecords.archiveEquipment(all, " 21569 ", "supA", "Supervisor", "10/11/2026 10:00")
        assertEquals(all.length(), archived.length())
        assertEquals(listOf("33000"), MaintenanceRecords.summaries(archived).map { it.number })
        assertEquals(listOf("21569"), MaintenanceRecords.archivedEquipment(archived))
        val issues = MaintenanceRecords.issues(archived)
        val first = issues.getJSONObject(0)
        assertTrue(MaintenanceRecords.isArchived(first))
        assertEquals("/foto-local.jpg", first.getJSONArray("fotos").getString(0))
        assertFalse(first.optBoolean("concluida"))
        assertEquals("supA", first.getString("arquivadoPorUid"))
        assertTrue(issues.getJSONObject(1).getBoolean("concluida"))
        assertFalse(MaintenanceRecords.isArchived(issues.getJSONObject(2)))
        val savedAgain = MaintenanceRecords.merge(issues, archived)
        assertEquals(listOf("33000"), MaintenanceRecords.summaries(savedAgain).map { it.number })
        assertFalse(MaintenanceRecords.isArchived(open))
    }
    @Test fun archivingOneIssueKeepsTheEquipmentActiveAndRemoteStateOverridesUntrustedContentFlags() {
        val issue = JSONObject().put("id", "p1").put("equipamento", "21569")
        val all = MaintenanceRecords.merge(JSONArray().put(issue), JSONArray())
        val archived = MaintenanceRecords.merge(JSONArray().put(MaintenanceRecords.archive(issue, "supA", "Supervisor", "hoje")), all)
        assertEquals("21569", MaintenanceRecords.summaries(archived).single().number)
        assertEquals(0, MaintenanceRecords.summaries(archived).single().open)
        assertTrue(MaintenanceRecords.archivedEquipment(archived).isEmpty())
        val forged = JSONObject().put("__arquivado", true)
        assertFalse(MaintenanceRecords.isArchived(MaintenanceRecords.withArchiveState(forged, false)))
        assertTrue(MaintenanceRecords.isArchived(MaintenanceRecords.withArchiveState(JSONObject(), true)))
    }
    @Test fun receivingAnArchivedRecordAgainDoesNotProduceAnotherContentChange() {
        val original = MaintenanceRecords.archive(JSONObject().put("id", "p1").put("equipamento", "21569"),
            "supA", "Supervisor", "10/11/2026")
        val incoming = MaintenanceRecords.withArchiveState(original, true)
        assertEquals(original.toString(), incoming.toString())
        assertEquals(incoming.toString(), MaintenanceRecords.withArchiveState(incoming, true).toString())
    }
    @Test fun equipmentSurvivesClosingAndDeletingTheLastIssue() {
        val issue = JSONObject().put("id", "p1").put("equipamento", "21569").put("descricao", "Pneu")
        val stored = MaintenanceRecords.merge(JSONArray().put(issue), JSONArray())
        assertEquals(1, MaintenanceRecords.summaries(stored).single().open)
        issue.put("concluida", true)
        val completed = MaintenanceRecords.merge(JSONArray().put(issue), stored)
        assertEquals(0, MaintenanceRecords.summaries(completed).single().open)
        assertEquals(1, MaintenanceRecords.summaries(completed).single().completed)
        val deleted = MaintenanceRecords.merge(JSONArray(), completed)
        assertEquals("21569", MaintenanceRecords.summaries(deleted).single().number)
        assertEquals(0, MaintenanceRecords.issues(deleted).length())
        assertTrue(MaintenanceRecords.isEquipment(deleted.getJSONObject(0)))
    }
    @Test fun normalizedEquipmentHasTheSameIdentityOnTwoPhonesWithoutDroppingLeadingZeroes() {
        val first = JSONArray(); val second = JSONArray()
        MaintenanceRecords.ensureEquipment(first, "  ab 21569  ")
        MaintenanceRecords.ensureEquipment(second, "AB 21569")
        assertEquals(first.toString(), second.toString())
        assertFalse(MaintenanceRecords.ensureEquipment(first, "ab 21569"))
        assertNotEquals(MaintenanceRecords.equipmentId("0021569"), MaintenanceRecords.equipmentId("21569"))
    }
    @Test fun catalogDoesNotBecomeAPendingMaintenanceOrDisappearOnAnUnrelatedSave() {
        val old = JSONArray()
        MaintenanceRecords.ensureEquipment(old, "21569")
        MaintenanceRecords.ensureEquipment(old, "33000")
        val issue = JSONObject().put("id", "p2").put("equipamento", "33000").put("concluida", false)
        val merged = MaintenanceRecords.merge(JSONArray().put(issue), old)
        assertEquals(1, MaintenanceRecords.issues(merged).length())
        assertEquals(2, MaintenanceRecords.summaries(merged).size)
        assertEquals(1, MaintenanceRecords.summaries(merged).first().open)
        assertEquals("33000", MaintenanceRecords.summaries(merged).first().number)
    }
}
