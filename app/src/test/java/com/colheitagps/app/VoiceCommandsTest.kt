package com.colheitagps.app

import android.app.Application
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28,35],manifest=Config.NONE,application=Application::class)
class VoiceCommandsTest {
    private val now=LocalDateTime.of(2026,11,16,1,20)
    @Test fun defectsStayFaithfulAndRoutinesStayOutOfMaintenance() {
        for(text in listOf("DM AGRO, a colhedora 22282 deu problema no rolamento","Anota aí, a 22282 tá com problema no rolamento")) {
            val draft=VoiceCommands.parse(text,now=now)
            assertEquals("issue",draft.kind);assertEquals("22282",draft.fields.getString("equipment"));assertFalse(draft.fields.getBoolean("urgent"))
            assertTrue(draft.fields.getString("description").contains("rolamento"));assertFalse(draft.fields.getString("description").contains("trocar"))
        }
        for(text in listOf("DM AGRO colhedora 22282 parou para abastecer","a 22282 parou para giro de facas")) assertEquals("routine",VoiceCommands.parse(text,now=now).kind)
        assertEquals("issue",VoiceCommands.parse("a colhedora 22282 precisa trocar as facas",now=now).kind)
    }
    @Test fun correctionsKeepTheDescriptionButInvalidateThePreviouslySelectedRecord() {
        val original=VoiceCommands.parse("DM AGRO colhedora 22282 deu problema no rolamento urgente",now=now);original.fields.put("recordId","old")
        val corrected=VoiceCommands.parse("Não, é 22283",original,now=now)
        assertEquals("22283",corrected.fields.getString("equipment"));assertEquals(original.fields.getString("description"),corrected.fields.getString("description"));assertTrue(corrected.fields.getBoolean("urgent"));assertFalse(corrected.fields.has("recordId"))
        val normal=VoiceCommands.parse("não é urgente",corrected,now=now);assertFalse(normal.fields.getBoolean("urgent"));assertEquals("22283",normal.fields.getString("equipment"))
    }
    @Test fun stockCapturesSpokenNumbersAndMapRequestsAndAsksOnlyForMissingPlots() {
        val stock=VoiceCommands.parse("DM AGRO estoque do K/39, área quarenta hectares, produtividade oitenta e coloque o mapa pintado",now=now)
        assertEquals("stock",stock.kind);assertEquals("K/39",stock.fields.getString("lot"));assertEquals("40",stock.fields.getString("area"));assertEquals("80",stock.fields.getString("yield"));assertTrue(stock.fields.getBoolean("paintedMap"));assertEquals("plots",VoiceCommands.missing(stock)!!.first)
        val plots=VoiceCommands.parse("talhão nove",stock,"plots",now);assertEquals("9",plots.fields.getString("plots"));assertNull(VoiceCommands.missing(plots))
    }
    @Test fun sameAreaCurvesUsePlotRoadAndHalfSide() {
        val curve=VoiceCommands.parse("DM AGRO pinte a curva de três vírgula quinze do talhão nove pela metade mais perto do carreador",now=now)
        assertEquals("curve",curve.kind);assertEquals("3,15",curve.fields.getString("ha"));assertEquals("9",curve.fields.getString("plots"));assertEquals("half",curve.fields.getString("fraction"));assertTrue(curve.fields.getBoolean("road"))
        val side=VoiceCommands.parse("pela esquerda",curve,now=now);assertEquals("esquerda",side.fields.getString("side"));assertEquals("3,15",side.fields.getString("ha"))
    }
    @Test fun arrivalsAndExitsKeepCapturedTimeAndRejectNegatedExits() {
        val arrival=VoiceCommands.parse("DM AGRO o caminhão 10880 acabou de chegar",now=now)
        assertEquals("truck_entry",arrival.kind);assertEquals("01:20",arrival.fields.getString("time"))
        assertEquals("truck_exit",VoiceCommands.parse("o 10880 tá saindo agora",now=now).kind)
        assertNotEquals("truck_exit",VoiceCommands.parse("o 10880 não saiu ainda",now=now).kind)
        assertEquals("01:20",VoiceCommands.parse("não, é o 10881",arrival,now=now.plusMinutes(10)).fields.getString("time"))
    }
    @Test fun spokenClockTimesNeverSilentlyFallBackToNow() {
        assertEquals("15:20",VoiceCommands.parse("caminhão 10880 chegou às quinze e vinte",now=now).fields.getString("time"))
        assertEquals("23:25",VoiceCommands.parse("caminhão 10880 chegou às vinte e três e vinte e cinco",now=now).fields.getString("time"))
        assertEquals("17:00",VoiceCommands.parse("caminhão 10880 chegou às cinco da tarde",now=now).fields.getString("time"))
        assertNull(VoiceCommands.parse("caminhão 10880 chegou às tantas",now=now).fields.opt("time"))
        val missing=VoiceCommands.parse("caminhão 10880 chegou às tantas",now=now)
        assertEquals("15:20",VoiceCommands.parse("quinze e vinte",missing,"time",now).fields.getString("time"))
    }
    @Test fun partCodesKeepLeadingZerosAndDoNotBecomeEquipmentNumbers() {
        val part=VoiceCommands.parse("DM AGRO pedir duas peças rolamento item P001",now=now)
        assertEquals("part",part.kind);assertEquals("2",part.fields.getString("quantity"));assertEquals("P001",part.fields.getString("code"));assertEquals("",part.fields.optString("equipment"))
        val numeric=VoiceCommands.parse("pedir três unidades de rolamento código 000123 para equipamento 22282",now=now)
        assertEquals("000123",numeric.fields.getString("code"));assertEquals("22282",numeric.fields.getString("equipment"))
    }
    @Test fun changingLotOrMapCannotReusePreviousReferences() {
        val stock=VoiceCommands.Draft("stock",JSONObject().put("lot","K/39").put("recordId","stock39").put("plots","9").put("plots_derived",true).put("area","40").put("yield","80").put("photo","old-photo"))
        val corrected=VoiceCommands.parse("Não, estoque do K/40",stock,now=now)
        assertEquals("K/40",corrected.fields.getString("lot"));assertFalse(corrected.fields.has("recordId"));assertFalse(corrected.fields.has("photo"));assertFalse(corrected.fields.has("plots"));assertEquals("40",corrected.fields.getString("area"))
        val curve=VoiceCommands.Draft("curve",JSONObject().put("map","map1").put("refX",.1).put("refY",.2).put("curveId","old"))
        val changed=VoiceCommands.invalidate(curve,JSONObject(curve.fields.toString()).put("map","map2"));assertFalse(changed.has("refX"));assertFalse(changed.has("curveId"))
    }
}
