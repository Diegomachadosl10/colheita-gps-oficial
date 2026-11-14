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
class ProductivityTest {
    @Test fun favoritesPersistForOnlyTheSameAccountAndTeamAndNeverMutateTheCatalog() {
        val app=RuntimeEnvironment.getApplication();val s=TeamStore(app,"santo_inacio_C")
        s.prefs("mapas").edit().putString("map1","Fazenda Alfa.pdf").commit()
        val favorites=MapFavorites(app,s.team,"op1");favorites.toggle("map1")
        assertTrue(MapFavorites(app,s.team,"op1").ids().contains("map1"))
        assertTrue(MapFavorites(app,s.team,"op2").ids().isEmpty())
        assertTrue(MapFavorites(app,"colorado_C","op1").ids().isEmpty())
        assertEquals(setOf("map1"),s.prefs("mapas").all.keys)
        val before=favorites.ids();favorites.toggle("map1")
        assertEquals(setOf("map1"),before);assertTrue(favorites.ids().isEmpty())
    }
    @Test fun onlyOpenActiveIssuesRaiseUrgencyAndTheySortBeforeNormalEquipment() {
        fun issue(id:String,number:String,urgent:Boolean=false,done:Boolean=false)=JSONObject().put("id",id).put("equipamento",number).put("descricao","Óleo na mangueira").put("urgente",urgent).put("concluida",done)
        val normal=issue("1","100")
        val urgent=issue("2","900",true)
        val done=issue("3","800",true,true)
        val archived=RecordArchive.archive(issue("4","700",true),"sup","Gestor","hoje")
        val all=MaintenanceRecords.merge(JSONArray().put(normal).put(urgent).put(done).put(archived),JSONArray())
        val summaries=MaintenanceRecords.summaries(all)
        assertEquals("900",summaries.first().number);assertEquals(1,summaries.first().urgent)
        assertFalse(MaintenanceRecords.urgent(done));assertFalse(MaintenanceRecords.urgent(archived))
        assertEquals(listOf("900"),MaintenanceRecords.search(all,"900 oleo").map {it.number})
        assertTrue(MaintenanceRecords.search(all,"700").isEmpty())
        val wire=TeamContent.textOnly(urgent)
        assertTrue(wire.getBoolean("urgente"))
        assertTrue(TeamContent.preserveLocalMedia(wire,urgent).getBoolean("urgente"))
        val output=FieldReports.group("Pendência","pendencias",listOf(urgent)).rows.flatten().joinToString()
        assertTrue(output.contains("Prioridade: Urgente"))
    }
}
