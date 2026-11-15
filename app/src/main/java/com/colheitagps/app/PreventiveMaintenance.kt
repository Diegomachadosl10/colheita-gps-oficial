package com.colheitagps.app

import org.json.JSONObject
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** The next service belongs to the equipment record and shares its existing team permissions. */
internal object PreventiveMaintenance {
    val fields = listOf("preventivaServico", "preventivaData", "horimetroAtual", "proximaRevisaoHoras")
    enum class State { NONE, SCHEDULED, SOON, DUE }
    data class Status(val state: State, val text: String)
    private fun number(value: String): BigDecimal? = value.takeIf(FieldWorkflow::decimal)?.replace(',', '.')?.toBigDecimalOrNull()
    fun date(value: String): LocalDate? = try { LocalDate.parse(value) } catch (_: Exception) { null }
    fun update(record: JSONObject, service: String, day: String, current: String, next: String): JSONObject {
        require(MaintenanceRecords.isEquipment(record) && !RecordArchive.archived(record)) { "Restaure o equipamento antes de programar a revisão." }
        require(service.trim().length in 1..160) { "Descreva a próxima revisão, com até 160 caracteres." }
        require(day.isNotBlank() || next.isNotBlank()) { "Informe a data ou o horímetro da próxima revisão." }
        require(day.isBlank() || date(day) != null) { "Escolha uma data válida." }
        require(current.isBlank() || number(current) != null) { "Informe um horímetro atual válido, sem separador de milhar." }
        require(next.isBlank() || number(next) != null) { "Informe um horímetro de revisão válido." }
        require(next.isBlank() || current.isNotBlank()) { "Informe também o horímetro atual para acompanhar a revisão por horas." }
        return JSONObject(record.toString()).put("preventivaServico", service.trim()).put("preventivaData", day)
            .put("horimetroAtual", current.trim()).put("proximaRevisaoHoras", next.trim())
    }
    fun status(record: JSONObject?, today: LocalDate = LocalDate.now()): Status {
        if (record == null || RecordArchive.archived(record) || record.optString("preventivaServico").isBlank()) return Status(State.NONE, "")
        val parts = mutableListOf(record.optString("preventivaServico"))
        var state = State.SCHEDULED
        date(record.optString("preventivaData"))?.let { day ->
            val days = ChronoUnit.DAYS.between(today, day)
            state = if (days <= 0) State.DUE else if (days <= 7) State.SOON else state
            parts.add("Data: ${day.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))}")
        }
        val current = number(record.optString("horimetroAtual"))
        val next = number(record.optString("proximaRevisaoHoras"))
        if (current != null && next != null) {
            val remaining = next - current
            val byHours = if (remaining.signum() <= 0) State.DUE else if (remaining <= BigDecimal(50)) State.SOON else State.SCHEDULED
            if (byHours.ordinal > state.ordinal) state = byHours
            parts.add("Horímetro: ${record.optString("horimetroAtual")} h • Revisão: ${record.optString("proximaRevisaoHoras")} h")
            parts.add(if (remaining.signum() <= 0) "Horímetro de revisão atingido" else "Faltam ${remaining.stripTrailingZeros().toPlainString().replace('.', ',')} h")
        }
        if (current != null && next == null) parts.add("Horímetro atual: ${record.optString("horimetroAtual")} h")
        val label = when (state) { State.DUE -> "Revisão a fazer"; State.SOON -> "Revisão próxima"; else -> "Revisão programada" }
        return Status(state, "$label • ${parts.joinToString("\n")}")
    }
}
