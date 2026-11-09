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
