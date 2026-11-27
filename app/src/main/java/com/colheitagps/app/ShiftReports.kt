package com.colheitagps.app

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

/** A saved account of a shift; live operational summaries are never silently copied into it. */
internal object ShiftReports {
    const val GROUP = "relatorios_turno"
    private val timestamp = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
    data class Draft(val date: LocalDate, val description: String, val pending: String = "", val lot: String = "")

    fun save(previous: JSONObject?, draft: Draft, team: String, uid: String, name: String,
             photos: List<String>, now: LocalDateTime): JSONObject {
        require(uid.isNotBlank() && team.substringBeforeLast('_') in TeamPolicy.fronts && team.substringAfterLast('_') in TeamPolicy.shifts)
        require(previous == null || !RecordArchive.archived(previous)) { "Restaure o relatório antes de editar." }
        require(draft.date <= now.toLocalDate()) { "A data do relatório não pode estar no futuro." }
        require(draft.description.isNotBlank()) { "Escreva o relato do turno." }
        require(draft.description.length <= 12000 && draft.pending.length <= 6000 && draft.lot.length <= 200) { "O texto ultrapassa o limite do relatório." }
        require(photos.size <= 12) { "Adicione até 12 fotos por relatório." }
        return (previous?.let { JSONObject(it.toString()) } ?: JSONObject().put("id", UUID.randomUUID().toString())
            .put("registradoPorUid", uid).put("registradoPorNome", name).put("criadoEm", now.format(timestamp)))
            .put("dataCadastro", TruckRecords.label(draft.date)).put("turno", team.substringAfterLast('_'))
            .put("descricao", draft.description.trim()).put("pendencias", draft.pending.trim()).put("os", draft.lot.trim())
            .put("fotos", JSONArray(photos.distinct())).put("atualizadoEm", now.format(timestamp))
    }

    fun text(team: String, record: JSONObject): String = buildString {
        append("DM AGRO • RELATÓRIO DE TURNO\n${TeamPolicy.label(team)}\n")
        append("Data operacional: ${record.optString("dataCadastro")}\n")
        record.optString("registradoPorNome").takeIf { it.isNotBlank() }?.let { append("Responsável: $it\n") }
        record.optString("os").takeIf { it.isNotBlank() }?.let { append("Lote / O.S.: $it\n") }
        append("\nRELATO\n${record.optString("descricao")}\n")
        record.optString("pendencias").takeIf { it.isNotBlank() }?.let { append("\nPARA O PRÓXIMO TURNO\n$it\n") }
    }.trim()
}
