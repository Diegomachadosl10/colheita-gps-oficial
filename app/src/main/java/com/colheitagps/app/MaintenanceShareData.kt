package com.colheitagps.app

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

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
        row.optString("osPendencia").takeIf {it.isNotBlank()}?.let {append("\nO.S.: $it")}
        row.optString("dataCadastro").takeIf {it.isNotBlank()}?.let {append("\nData: $it")}
        append("\nSituação: ${status(row)}")
        row.optString("dataConclusao").takeIf {it.isNotBlank()&&row.optBoolean("concluida")}?.let {append("\nConcluída em: $it")}
        row.optString("arquivadoEm").takeIf {it.isNotBlank()&&RecordArchive.archived(row)}?.let {append("\nExcluída em: $it")}
    }
    fun message(rows:List<JSONObject>,team:String,date:String):String {
        val ordered=ordered(rows);require(ordered.isNotEmpty())
        val allOpen=ordered.all {!RecordArchive.archived(it)&&!it.optBoolean("concluida")}
        return buildString {
            append("DM AGRO • ${if(allOpen) "PENDÊNCIAS EM ABERTO" else "PENDÊNCIAS DE MANUTENÇÃO"}\n$team\nEnviado em: $date\n")
            append("${ordered.size} pendência${if(ordered.size==1) "" else "s"}\n")
            var index=0
            for((equipment,records) in ordered.groupBy {MaintenanceRecords.number(it.optString("equipamento")).ifBlank {"Não informado"}}) {
                append("\nEQUIPAMENTO $equipment\n")
                for(record in records) {append("\n${++index}. ${details(record)}\n")}
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
