package com.colheitagps.app

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

internal data class ReportSection(val title:String,val columns:List<String>,val rows:List<List<String>>)
internal data class ReportPicture(val file:File,val caption:String)
internal data class FieldReport(val title:String,val team:String,val period:String="",val sections:List<ReportSection>,val pictures:List<ReportPicture> = emptyList())
internal object FieldReports {
    fun rows(all:JSONArray)=(0 until all.length()).mapNotNull {all.optJSONObject(it)}
    fun group(title:String,group:String,records:List<JSONObject>):ReportSection {
        fun fields(item:JSONObject,vararg pairs:Pair<String,String>)=pairs.mapNotNull {(label,key)->item.optString(key).takeIf {it.isNotBlank()}?.let {"$label: $it"}}.joinToString("\n")
        val data=records.map {record->
            val state=if(RecordArchive.archived(record)) "Excluído" else if(record.optBoolean("concluida")) "Concluído" else "Aberto"
            val cells=when {
                PartsRecords.isPart(record)->listOf(record.optString("codigoPeca").ifBlank {"Peça"},fields(record,"Descrição" to "nomePeca","Quantidade" to "quantidadePeca","Equipamento" to "equipamentoReferencia")+"\nSituação: "+if(RecordArchive.archived(record)) "Excluída" else PartsRecords.states[record.optString("situacaoPeca")].orEmpty())
                RainRecords.isRain(record)->listOf(record.optString("dataCadastro"),"Chuva: ${RainRecords.label(record)}\n"+fields(record,"Observação" to "observacaoChuva","Registrado por" to "registradoPorNome"))
                MaintenanceRecords.isEquipment(record)->listOf("Equipamento ${record.optString("equipamento")}","Situação: "+if(RecordArchive.archived(record)) "Excluído" else "Cadastrado")
                group=="pendencias"->listOf("Equipamento ${record.optString("equipamento")}",fields(record,"Pendência" to "descricao","O.S." to "osPendencia","Data" to "dataCadastro")+(if(record.optBoolean("urgente")) "\nPrioridade: Urgente" else "")+"\nSituação: $state")
                group in setOf("estoques_salvos","historico_estoque")->listOf("Lote ${record.optString("os")}",fields(record,"Talhões" to "talhoes","Área (ha)" to "areaRestante","Produtividade (t/ha)" to "produtividade","Data" to "dataCadastro","Registrado em" to "data"))
                group=="caminhoes"->listOf("Caminhão ${record.optString("numero")}","Turno ${record.optString("turno")}\n${TruckRecords.details(record)}")
                group=="ocorrencias_campo"->listOf(record.optString("tipo").ifBlank {"Ocorrência"},fields(record,"Equipamento" to "equipamento","Localização" to "localizacao","Descrição" to "descricao","GPS" to "gps","Precisão GPS (m)" to "precisaoGpsM","Latitude" to "latitude","Longitude" to "longitude","Data" to "dataCadastro")+"\nSituação: $state")
                group=="historico_os"->listOf("O.S. ${record.optString("numeroOs")}",fields(record,"Mapa" to "nomeMapa","Talhões" to "talhoes","Situação" to "statusOs","Encerrada em" to "encerradaEm","Encerrada por" to "encerradaPorNome","Reaberta em" to "reabertaEm","Reaberta como" to "reabertaComo","Excluída em" to "arquivadoEm","Restaurada em" to "restauradoEm"))
                group=="relatorios_turno"->listOf("Turno ${record.optString("turno")}",fields(record,"Data" to "dataCadastro","Descrição" to "descricao","O.S. / lote" to "os","Próximo turno" to "pendencias"))
                else->listOf(record.optString("tipo").ifBlank {record.optString("os").ifBlank {"Registro"}},fields(record,"Data" to "dataCadastro","Registrado em" to "data","Turno" to "turno","Quantidade (t)" to "quantidade","O.S." to "os","Descrição" to "descricao","Observação" to "observacao"))
            }
            val history=fields(record,"Concluído em" to "dataConclusao","Excluído em" to "arquivadoEm","Excluído por" to "arquivadoPorNome","Restaurado em" to "restauradoEm","Restaurado por" to "restauradoPorNome")
            if(history.isBlank()) cells else cells.dropLast(1)+listOf(cells.last()+"\n"+history)
        }
        return ReportSection(title,listOf("Registro","Informações"),data)
    }
    fun trucks(team:String,period:String,records:List<JSONObject>,summary:TruckRecords.Summary):FieldReport = FieldReport("Entrada e saída de caminhões",team,period,listOf(
        ReportSection("Resumo do período",listOf("Entradas","Saídas","Aguardando saída no período"),listOf(listOf(summary.entries.toString(),summary.exits.toString(),summary.pending.toString()))),
        ReportSection("Caminhões",listOf("Caminhão / turno","Entrada","Saída"),records.map {r->listOf("${r.optString("numero")} / ${r.optString("turno")}",
            "${TruckRecords.entryDay(r)?.let(TruckRecords::label)?:"Sem data"}\n${r.optString("chegada")}",
            if(r.optString("saida").isBlank()) r.optString("saidaAteCorte").takeIf {it.isNotBlank()}?.let {"Sem saída até\n$it"}?:"Aguardando saída" else "${TruckRecords.exitDay(r)?.let(TruckRecords::label)?:"Sem data"}\n${r.optString("saida")}")})
    ))
}
