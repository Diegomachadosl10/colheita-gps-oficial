package com.colheitagps.app

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale

/** Whitelisted business fields only; exporting never changes the stored records. */
internal object MaintenanceShareData {
    fun issue(record:JSONObject)=!MaintenanceRecords.isEquipment(record)&&!PartsRecords.isPart(record)
    fun open(all:JSONArray,equipment:String?=null):List<JSONObject> {
        val records=FieldReports.rows(all)
        val excluded=records.filter {MaintenanceRecords.isEquipment(it)&&RecordArchive.archived(it)}
            .map {MaintenanceRecords.number(it.optString("equipamento"))}.toSet()
        return ordered(records.filter {issue(it)&&!RecordArchive.archived(it)&&!it.optBoolean("concluida")&&
            MaintenanceRecords.number(it.optString("equipamento")) !in excluded&&
            (equipment==null||MaintenanceRecords.number(it.optString("equipamento"))==MaintenanceRecords.number(equipment))})
    }
    fun ordered(rows:List<JSONObject>)=rows.filter(::issue).sortedWith(
        compareBy<JSONObject> {MaintenanceRecords.number(it.optString("equipamento"))}
            .thenByDescending {MaintenanceRecords.urgent(it)}.thenBy {it.optString("id")}
    ).map {JSONObject(it.toString())}
    fun status(row:JSONObject)=when {
        RecordArchive.archived(row)->"Excluída • preservada no histórico"
        row.optBoolean("concluida")->"Concluída"
        else->"Em aberto"
    }
    fun details(row:JSONObject):String=buildString {
        append(row.optString("descricao").ifBlank {"Sem descrição"})
        if(row.optBoolean("urgente")) append("\nPrioridade: Urgente")
        if(row.optBoolean("paradaChuva")) append("\nServiço para parada de chuva")
        row.optString("situacaoPecas").takeIf {it.isNotBlank()}?.let {append("\nPeças: $it")}
        row.optString("osPendencia").takeIf {it.isNotBlank()}?.let {append("\nO.S.: $it")}
        row.optString("dataCadastro").takeIf {it.isNotBlank()}?.let {append("\nData: $it")}
        append("\nSituação: ${status(row)}")
        row.optString("dataConclusao").takeIf {it.isNotBlank()&&row.optBoolean("concluida")}?.let {append("\nConcluída em: $it")}
        row.optString("arquivadoEm").takeIf {it.isNotBlank()&&RecordArchive.archived(row)}?.let {append("\nExcluída em: $it")}
    }
    fun message(rows:List<JSONObject>,team:String,date:String):String {
        val ordered=ordered(rows);require(ordered.isNotEmpty())
        val allOpen=ordered.all {!RecordArchive.archived(it)&&!it.optBoolean("concluida")}
        val groups=ordered.groupBy {MaintenanceRecords.number(it.optString("equipamento")).ifBlank {"Não informado"}}
        val sentAt=Regex("^([0-9]{2}/[0-9]{2}/[0-9]{4})\\s+([0-9]{2}:[0-9]{2})$").matchEntire(date.trim())
            ?.let {"${it.groupValues[1]} às ${it.groupValues[2]}"}?:date.trim()
        return buildString {
            append("PENDÊNCIAS:\n\n$team\n\nEnviado em: $sentAt")
            for((groupIndex,group) in groups.entries.withIndex()) {
                val (equipment,records)=group
                append(if(groupIndex==0) "\n\n" else "\n\n\n")
                append("EQUIPAMENTO - $equipment\n\nPendências:\n")
                append(records.joinToString("\n\n\n") {record->
                    val description=record.optString("descricao").trim().ifBlank {"Sem descrição"}
                        .replaceFirstChar {if(it.isLowerCase()) it.titlecase(Locale("pt","BR")) else it.toString()}
                    val fields=mutableListOf(description)
                    if(record.optBoolean("urgente")) fields.add("Prioridade: URGENTE")
                    record.optString("osPendencia").takeIf {it.isNotBlank()}?.let {fields.add("O.S.: $it")}
                    record.optString("situacaoPecas").takeIf {it.isNotBlank()}?.let {fields.add("Peças: $it")}
                    if(record.optBoolean("paradaChuva")) fields.add("Parada de chuva: Pode ser feito")
                    if(!allOpen) fields.add("Situação: ${status(record)}")
                    record.optString("dataConclusao").takeIf {it.isNotBlank()&&record.optBoolean("concluida")}?.let {fields.add("Concluída em: $it")}
                    record.optString("arquivadoEm").takeIf {it.isNotBlank()&&RecordArchive.archived(record)}?.let {fields.add("Excluída em: $it")}
                    fields.joinToString("\n\n")
                })
            }
        }.trimEnd()
    }
    fun photos(root:File,row:JSONObject):List<File> {
        val paths=mutableListOf<String>()
        row.optJSONArray("fotos")?.let {values->for(i in 0 until values.length()) paths.add(values.optString(i))}
        paths.add(row.optString("foto"))
        val base=root.canonicalPath+File.separator
        return paths.filter {it.isNotBlank()}.mapNotNull {path->
            try {File(path).canonicalFile.takeIf {it.isFile&&it.path.startsWith(base)}} catch(_:Exception) {null}
        }.distinctBy {it.path}
    }
}
