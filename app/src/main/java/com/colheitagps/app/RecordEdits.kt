package com.colheitagps.app

import org.json.JSONArray
import org.json.JSONObject

/** Business-field audit only. Local media paths and synchronization metadata are never copied into the audit. */
internal object RecordEdits {
    private val labels=linkedMapOf("equipamento" to "Equipamento","descricao" to "Descrição","osPendencia" to "O.S.","urgente" to "Urgente","paradaChuva" to "Serviço para parada de chuva",
        "concluida" to "Concluída","os" to "Lote","talhoes" to "Talhões","areaRestante" to "Área (ha)","produtividade" to "Produtividade (t/ha)","observacoes" to "Observações",
        "numero" to "Caminhão","chegada" to "Entrada","saida" to "Saída","dataEntrada" to "Data da entrada","dataSaida" to "Data da saída",
        "codigoPeca" to "Código da peça","nomePeca" to "Peça","quantidadePeca" to "Quantidade","equipamentoReferencia" to "Equipamento da peça","situacaoPeca" to "Situação da peça",
        "pendenciaId" to "Vínculo com pendência","chuvaMm" to "Chuva (mm)","observacaoChuva" to "Observação da chuva","tipo" to "Tipo","localizacao" to "Localização",
        "preventivaServico" to "Próxima revisão", "preventivaData" to "Data da revisão", "horimetroAtual" to "Horímetro atual (h)", "proximaRevisaoHoras" to "Horímetro de revisão (h)")
    fun stamp(previous:JSONArray,current:JSONArray,uid:String,name:String,date:String):JSONArray {
        val before=FieldReports.rows(previous).associateBy {it.optString("id")}
        return JSONArray().apply {for(item in FieldReports.rows(current)) {
            val old=before[item.optString("id")];val row=JSONObject(item.toString());val changes=JSONArray()
            for((key,label) in labels) if(old!=null&&old.optString(key)!=row.optString(key)) changes.put(JSONObject().put("campo",label)
                .put("antes",if(key=="pendenciaId") if(old.optString(key).isBlank()) "Sem vínculo" else "Vinculada" else old.optString(key))
                .put("depois",if(key=="pendenciaId") if(row.optString(key).isBlank()) "Sem vínculo" else "Vinculada" else row.optString(key)))
            if(old!=null&&TeamContent.mediaFields.any {old.optString(it)!=row.optString(it)}) changes.put(JSONObject().put("campo","Fotos / anexos").put("antes","Anexos anteriores").put("depois","Anexos atualizados"))
            if(old==null||changes.length()>0) {
                row.put("atualizadoEm",date).put("atualizadoPorNome",name.ifBlank {"Integrante da equipe"}).put("atualizadoPorUid",uid)
                if(old==null) row.put("criadoPorNome",name.ifBlank {"Integrante da equipe"}).put("criadoEm",date)
                if(changes.length()>0) {
                    val history=old?.optJSONArray("historicoAlteracoes")?:JSONArray()
                    val out=JSONArray();for(i in maxOf(0,history.length()-99) until history.length()) out.put(history.get(i))
                    out.put(JSONObject().put("em",date).put("porNome",name.ifBlank {"Integrante da equipe"}).put("porUid",uid).put("campos",changes))
                    row.put("historicoAlteracoes",out)
                }
            } else if(old!=null) for(key in listOf("historicoAlteracoes","atualizadoEm","atualizadoPorNome","atualizadoPorUid","criadoPorNome","criadoEm")) if(old.has(key)) row.put(key,old.get(key))
            put(row)
        }}
    }
    fun text(row:JSONObject)=buildString {
        append("Última gravação: ${row.optString("atualizadoEm","Não registrada")}\nPor: ${row.optString("atualizadoPorNome","Não registrado")}\n")
        val history=row.optJSONArray("historicoAlteracoes")?:JSONArray()
        if(history.length()==0) append("\nNenhuma correção registrada nesta versão.")
        for(i in history.length()-1 downTo 0) {
            val event=history.getJSONObject(i);append("\n${event.optString("em")} • ${event.optString("porNome")}\n")
            for(change in FieldReports.rows(event.optJSONArray("campos")?:JSONArray())) append("${change.optString("campo")}: ${change.optString("antes").ifBlank {"—"}} → ${change.optString("depois").ifBlank {"—"}}\n")
        }
    }
}
