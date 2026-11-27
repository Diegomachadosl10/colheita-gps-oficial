package com.colheitagps.app

import org.json.JSONArray
import java.math.BigDecimal
import java.math.RoundingMode

/** Current selected-team records only. Free text never becomes a guessed numerical value. */
internal object OperationalIndicators {
    data class Snapshot(val equipment: Int, val pending: Int, val urgent: Int, val lots: Int,
                        val stockArea: BigDecimal?, val numericLots: Int, val weightedYield: BigDecimal?,
                        val comparableLots: Int, val occurrences: Int)

    fun number(raw: String, unit: String): BigDecimal? {
        val suffix = if (unit == "ha") "(?:ha|hectares?)" else "(?:t/ha)"
        val match = Regex("^\\s*([0-9]{1,9}(?:[.,][0-9]{1,6})?)\\s*(?:$suffix)?\\s*$", RegexOption.IGNORE_CASE).matchEntire(raw) ?: return null
        return match.groupValues[1].replace(',', '.').toBigDecimalOrNull()
    }

    fun build(maintenance: JSONArray, stock: JSONArray, occurrences: JSONArray): Snapshot {
        val equipment = FieldReports.rows(RecordArchive.active(maintenance)).filter(MaintenanceRecords::isEquipment)
            .map { MaintenanceRecords.number(it.optString("equipamento")) }.filter { it.isNotBlank() }.distinct()
        val pending = MaintenanceShareData.open(maintenance)
        val lots = FieldReports.rows(RecordArchive.active(stock))
        val areas = lots.mapNotNull { number(it.optString("areaRestante"), "ha") }
        val comparable = lots.mapNotNull { row ->
            val area = number(row.optString("areaRestante"), "ha") ?: return@mapNotNull null
            val yield = number(row.optString("produtividade"), "t/ha") ?: return@mapNotNull null
            if (area.signum() > 0) area to yield else null
        }
        val denominator = comparable.fold(BigDecimal.ZERO) { sum, pair -> sum + pair.first }
        val mean = if (denominator.signum() == 0) null else comparable.fold(BigDecimal.ZERO) { sum, pair -> sum + pair.first * pair.second }
            .divide(denominator, 2, RoundingMode.HALF_UP)
        return Snapshot(equipment.size, pending.size, pending.count(MaintenanceRecords::urgent), lots.size,
            areas.takeIf { it.isNotEmpty() }?.fold(BigDecimal.ZERO, BigDecimal::add), areas.size, mean, comparable.size,
            FieldReports.rows(RecordArchive.active(occurrences)).count { !RainRecords.isRain(it) && !it.optBoolean("concluida") })
    }

    fun label(value: BigDecimal?) = value?.stripTrailingZeros()?.toPlainString()?.replace('.', ',') ?: "Sem dados numéricos"
}
