package com.colheitagps.app

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

internal object ShiftHandover {
    private val dateTime = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
    fun latest(shift: String, now: LocalDateTime): TruckShift.Period {
        val today = TruckShift.period(shift, now.toLocalDate())
        return if (now < today.start) TruckShift.period(shift, now.toLocalDate().minusDays(1)) else today
    }
    data class Summary(val text: String, val report: FieldReport)
    fun build(team: String, period: TruckShift.Period, now: LocalDateTime, maintenance: JSONArray,
              stock: JSONArray, trucks: JSONArray, note: String): Summary {
        require(team.substringAfterLast('_') == period.shift)
        require(now >= period.start) { "O turno selecionado ainda não começou." }
        val issues = MaintenanceShareData.open(maintenance).sortedWith(compareByDescending<JSONObject> { MaintenanceRecords.urgent(it) }.thenBy { it.optString("equipamento") })
        val parts = FieldReports.rows(PartsRecords.rows(maintenance)).filter { !PartsRecords.historical(it) }
        val lots = FieldReports.rows(RecordArchive.active(stock))
        val cutoff = minOf(now, period.end)
        val vehicles = TruckShift.snapshot(trucks, period, cutoff)
        val sections = mutableListOf<ReportSection>()
        val text = buildString {
            append("PASSAGEM DE TURNO • DM AGRO\n${TeamPolicy.label(team)}\n${period.label()}\nPreparado em: ${now.format(dateTime)}\n")
            append("\nSituação atual dos registros disponíveis na equipe:\n")
            append("\nPENDÊNCIAS ABERTAS (${issues.size})\n")
            if (issues.isEmpty()) append("Nenhuma pendência aberta.\n")
            issues.forEach { row -> append("• ${row.optString("equipamento")}: ${row.optString("descricao")}${if (MaintenanceRecords.urgent(row)) " [URGENTE]" else ""}${if (row.optBoolean("paradaChuva")) " [PARADA DE CHUVA]" else ""}\n") }
            sections.add(ReportSection("Pendências abertas agora", listOf("Equipamento", "Serviço", "Prioridade"), issues.map { listOf(it.optString("equipamento"), it.optString("descricao"), if (MaintenanceRecords.urgent(it)) "Urgente" else "Normal") }))
            append("\nPEÇAS AGUARDADAS (${parts.size})\n")
            if (parts.isEmpty()) append("Nenhuma peça aguardada.\n")
            parts.forEach { row -> append("• ${row.optString("codigoPeca").ifBlank { "Sem código" }} • ${row.optString("nomePeca")} • ${row.optInt("quantidadePeca")} un. • ${PartsRecords.states[row.optString("situacaoPeca")] ?: "A pedir"}${row.optString("equipamentoReferencia").takeIf { it.isNotBlank() }?.let { " • Equipamento $it" }.orEmpty()}\n") }
            sections.add(ReportSection("Peças aguardadas agora", listOf("Código / peça", "Quantidade", "Situação / equipamento"), parts.map { listOf("${it.optString("codigoPeca")} ${it.optString("nomePeca")}", "${it.optInt("quantidadePeca")} un.", "${PartsRecords.states[it.optString("situacaoPeca")] ?: "A pedir"} • ${it.optString("equipamentoReferencia")}") }))
            append("\nESTOQUE ATUAL (${lots.size} lotes)\n")
            if (lots.isEmpty()) append("Nenhum estoque cadastrado.\n")
            lots.forEach { row -> append("• Lote ${row.optString("os")} • Talhões: ${row.optString("talhoes")} • ${StockBatch.quantity(row.optString("areaRestante"),"ha")} • ${StockBatch.quantity(row.optString("produtividade"),"t/ha")}\n") }
            sections.add(ReportSection("Estoque atual", listOf("Lote / talhões", "Área", "Produtividade"), lots.map { listOf("${it.optString("os")} • ${it.optString("talhoes")}", StockBatch.quantity(it.optString("areaRestante"),"ha"), StockBatch.quantity(it.optString("produtividade"),"t/ha")) }))
            append("\nCAMINHÕES DO PERÍODO • ATÉ ${cutoff.format(dateTime)}\nEntradas: ${vehicles.summary.entries} • Saídas: ${vehicles.summary.exits} • Sem saída: ${vehicles.summary.pending}\n")
            vehicles.rows.filter { it.optString("saida").isBlank() }.forEach { row -> append("• Caminhão ${row.optString("numero")} • Entrada ${row.optString("chegada")}${FieldWorkflow.waitMinutes(row, cutoff)?.let { " • Espera: ${FieldWorkflow.waitLabel(it)}" }.orEmpty()}\n") }
            sections.add(ReportSection("Caminhões no período • ${vehicles.summary.entries} entradas / ${vehicles.summary.exits} saídas", listOf("Sem saída", "Entrada", "Espera até o corte"), vehicles.rows.filter { it.optString("saida").isBlank() }.map { listOf(it.optString("numero"), it.optString("chegada"), FieldWorkflow.waitMinutes(it, cutoff)?.let(FieldWorkflow::waitLabel).orEmpty()) }))
            if (note.isNotBlank()) { append("\nRECADO PARA O PRÓXIMO TURNO\n${note.trim()}\n"); sections.add(ReportSection("Recado para o próximo turno", listOf("Observação"), listOf(listOf(note.trim())))) }
        }
        return Summary(text.trim(), FieldReport("Passagem de turno", TeamPolicy.label(team), "${period.label()}\nPreparado em ${now.format(dateTime)}", sections))
    }
}
