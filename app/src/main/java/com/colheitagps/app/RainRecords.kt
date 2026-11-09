package com.colheitagps.app

import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

/** Rain is a field observation, using the existing team/author permissions and offline queue. */
internal object RainRecords {
    const val GROUP = "ocorrencias_campo"
    private val dateFormat = DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT)
    fun isRain(record: JSONObject) = record.optString("categoriaRegistro") == "chuva"
    fun date(value: String): LocalDate? = try { LocalDate.parse(value, dateFormat) } catch (_: Exception) { null }
    fun amount(value: String): BigDecimal? {
        val normalized = value.trim().replace(',', '.')
        if (!normalized.matches(Regex("[0-9]{1,4}(\\.[0-9]{1,2})?"))) return null
        return normalized.toBigDecimalOrNull()?.stripTrailingZeros()
    }
    fun id(date: LocalDate) = "chuva_$date"
    fun label(record: JSONObject): String = amount(record.optString("chuvaMm"))?.let {
        it.toPlainString().replace('.', ',') + " mm"
    } ?: "Volume inválido"
    fun readings(all: JSONArray): List<JSONObject> = (0 until all.length()).mapNotNull { all.optJSONObject(it) }
        .filter(::isRain).sortedByDescending { date(it.optString("dataCadastro")) }
    fun create(date: String, millimeters: String, observation: String, team: String,
               authorUid: String, authorName: String): JSONObject {
        val day = requireNotNull(date(date)) { "Data inválida" }
        val amount = requireNotNull(amount(millimeters)) { "Volume inválido" }
        require(team.substringBeforeLast('_') in TeamPolicy.fronts && team.substringAfterLast('_') in TeamPolicy.shifts) { "Equipe inválida" }
        return JSONObject().put("id", id(day)).put("categoriaRegistro", "chuva")
            .put("dataCadastro", date).put("chuvaMm", amount.toPlainString())
            .put("frenteId", team.substringBeforeLast('_')).put("turno", team.substringAfterLast('_'))
            .put("observacaoChuva", observation.trim()).put("registradoPorUid", authorUid)
            .put("registradoPorNome", authorName).put("tipo", "Registro de chuva")
            .put("localizacao", TeamPolicy.label(team))
            .put("descricao", "Chuva acumulada do turno: ${amount.toPlainString().replace('.', ',')} mm. ${observation.trim()}".trim())
    }
}
