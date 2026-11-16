package com.colheitagps.app

import android.app.Activity
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

/** Shared record builders/permissions, with fresh lookups at confirmation and no eager writes. */
internal class VoiceOperations(private val host:Host) {
    interface Host {
        val activity:Activity
        val access:TeamAccess?
        val store:TeamStore?
        fun scope():String
        fun active():Boolean
        fun read(group:String):JSONArray
        fun save(group:String,records:JSONArray)
        fun canEdit(group:String,record:JSONObject):Boolean
        fun revision(group:String,id:String):Long
        fun navigate(tool:String)
        fun share(tool:String,image:Boolean,photos:Boolean,filters:JSONObject)
        fun maps():List<VoiceMap>
        fun paintedMaps():List<PaintedMapPicker.Item>
        fun currentMap():String
        fun curve(draft:VoiceCommands.Draft,ui:FieldVoice)
        fun closeOrder(map:String,number:String)
    }
    data class VoiceMap(val id:String,val name:String)
    private fun rows(group:String)=FieldReports.rows(host.read(group))
    private fun message(text:String)=Toast.makeText(host.activity,text,Toast.LENGTH_LONG).show()
    private fun validScope(scope:String)=host.active()&&host.scope()==scope
    private fun requireScope(scope:String) {check(validScope(scope)) {"Sua conta ou equipe mudou. Confira o comando novamente."}}
    private fun date(d:VoiceCommands.Draft)=d.captured.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
    private fun guarded(scope:String,body:()->Unit):()->Unit = {
        try {requireScope(scope);body()} catch(e:Exception) {message(e.message?:"Confira o registro e tente novamente.")}
    }
    private fun replace(group:String,old:JSONObject?,replacement:JSONObject,scope:String,base:Long) {
        requireScope(scope)
        val current=host.read(group);val id=replacement.optString("id")
        val index=(0 until current.length()).firstOrNull {current.optJSONObject(it)?.optString("id")==id}
        if(old!=null) {
            val actual=index?.let {current.getJSONObject(it)}?:error("O registro foi removido. Confira a lista da equipe.")
            require(!RecordArchive.archived(actual)&&host.canEdit(group,actual)) {"Você não pode alterar este registro."}
            fun clean(row:JSONObject)=JSONObject(row.toString()).apply {remove("__syncBase")}.toString()
            require(clean(actual)==clean(old)) {"Este registro mudou enquanto você conferia. Abra o comando novamente."}
        } else require(index==null) {"O registro já foi criado. Confira a lista antes de repetir."}
        replacement.put("__syncBase",base)
        if(index==null) current.put(replacement) else current.put(index,replacement)
        host.save(group,current)
    }
    fun prepare(d:VoiceCommands.Draft,ui:FieldVoice) {
        if(!ui.isCurrent(d)||host.access==null||host.store==null) return
        val f=d.fields;val access=host.access!!;val scope=host.scope();val team=host.store!!.team
        if((d.kind in setOf("stock","truck_entry","truck_exit","order_close","preventive")||d.kind in setOf("navigate","share")&&f.optString("tool") in setOf("estoque","caminhoes","passagem","calendario","os","usuarios"))&&!access.manages) {
            ui.show(d,"Esta ação é exclusiva do supervisor ou administrador. Sua conta continua com as mesmas permissões.");return
        }
        if(d.kind=="stock"&&f.optString("lot").isNotBlank()&&f.optString("recordId").isBlank()) {
            val matching=rows("estoques_salvos").filter {!RecordArchive.archived(it)&&FieldWorkflow.lot(it.optString("os"))==FieldWorkflow.lot(f.optString("lot"))}
            if(matching.size>1) {ui.show(d,"Há mais de um estoque desse lote. Qual deseja atualizar?",options=matching.map {row->FieldVoice.Choice("${row.optString("os")} • Talhões ${row.optString("talhoes")} • ${row.optString("areaRestante")} ha") {ui.replace(d,d.copyFields().put("recordId",row.optString("id")))}});return}
            if(matching.size==1) {
                val row=matching.single();val copy=d.copyFields().put("recordId",row.optString("id"))
                for((key,field) in listOf("plots" to "talhoes","area" to "areaRestante","yield" to "produtividade")) if(copy.optString(key).isBlank()) copy.put(key,row.optString(field)).put(key+"_derived",true)
                ui.replace(d,copy);return
            }
        }
        if(d.kind=="stock"&&f.optString("recordId").isNotBlank()) {
            val row=rows("estoques_salvos").firstOrNull {it.optString("id")==f.optString("recordId")}
            if(row!=null) {
                val copy=d.copyFields();var filled=false
                for((key,field) in listOf("plots" to "talhoes","area" to "areaRestante","yield" to "produtividade")) if(copy.optString(key).isBlank()) {copy.put(key,row.optString(field)).put(key+"_derived",true);filled=true}
                if(filled) {ui.replace(d,copy);return}
            }
        }
        if(d.kind=="preventive"&&f.optString("equipment").isNotBlank()) {
            val equipment=rows("pendencias").firstOrNull {MaintenanceRecords.isEquipment(it)&&MaintenanceRecords.number(it.optString("equipamento"))==MaintenanceRecords.number(f.optString("equipment"))&&!RecordArchive.archived(it)}
            if(equipment!=null) {
                val copy=d.copyFields();var filled=false
                for((key,field) in listOf("service" to "preventivaServico","serviceDate" to "preventivaData","currentHours" to "horimetroAtual","nextHours" to "proximaRevisaoHoras")) if(copy.optString(key).isBlank()&&equipment.optString(field).isNotBlank()) {copy.put(key,equipment.get(field)).put(key+"_derived",true);filled=true}
                if(filled) {ui.replace(d,copy);return}
            }
        }
        VoiceCommands.missing(d)?.let {(key,prompt)->ui.show(d,prompt,missing=key);return}
        if(d.kind=="curve") {host.curve(d,ui);return}
        val label=TeamPolicy.label(team)
        when(d.kind) {
            "navigate"->ui.show(d,"Abrir ${toolName(f.getString("tool"))}?",action=guarded(scope) {host.navigate(f.getString("tool"))})
            "share"->ui.show(d,"Preparar compartilhamento de ${toolName(f.getString("tool"))} de $label${f.optString("equipment").takeIf {it.isNotBlank()}?.let {", equipamento $it"}.orEmpty()}${if(f.optBoolean("onlyUrgent")) ", somente urgentes" else ""}${if(f.optBoolean("rainOnly")) ", serviços para parada de chuva" else ""}${if(!f.optBoolean("photos",true)) ", sem fotos" else ""}? Você confere a prévia antes de Enviar.",action=guarded(scope) {host.share(f.getString("tool"),f.optBoolean("image"),f.optBoolean("photos",true),f)})
            "issue","routine","occurrence","part"->{
                val equipment=MaintenanceRecords.number(f.optString("equipment"))
                if(d.kind=="issue"&&MaintenanceRecords.archivedEquipment(host.read("pendencias")).contains(equipment)) {ui.show(d,"O equipamento $equipment está excluído. Peça ao gestor para restaurar antes de adicionar pendências.");return}
                val row=JSONObject().put("id",UUID.randomUUID().toString()).put("dataCadastro",date(d)).put("descricao",f.getString("description"))
                val group=if(d.kind in setOf("issue","part")) "pendencias" else "ocorrencias_campo"
                val summary=when(d.kind) {
                    "issue"->{row.put("equipamento",equipment).put("urgente",f.optBoolean("urgent")).put("concluida",false);"Pendência do equipamento $equipment: ${f.getString("description")}. Prioridade ${if(f.optBoolean("urgent")) "urgente" else "normal"}."}
                    "routine"->{row.put("categoriaRegistro","rotina").put("equipamento",equipment).put("tipo",f.getString("routine")).put("registradoPorUid",access.uid).put("registradoPorNome",access.profile["nome"]?.toString().orEmpty());"${f.getString("routine")} do equipamento $equipment, às ${d.captured.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))}. Registro de rotina em Ocorrências."}
                    "part"->{
                        val quantity=f.optString("quantity").toIntOrNull()
                        if(quantity==null||quantity !in 1..999999) {ui.show(d,"Informe uma quantidade inteira válida.",missing="quantity");return}
                        val part=PartsRecords.build(null,f.optString("code"),f.getString("description"),quantity.toString(),equipment,"a_pedir",access.uid,access.profile["nome"]?.toString().orEmpty(),date(d))
                        for(key in part.keys()) row.put(key,part.get(key))
                        "Peça: ${f.getString("description")}. Quantidade: $quantity. ${if(equipment.isNotBlank()) "Equipamento $equipment." else "Sem equipamento de referência."}"
                    }
                    else->{row.put("tipo","Ocorrência por voz").put("localizacao",f.optString("location",label));"Ocorrência de campo: ${f.getString("description")}."}
                }
                ui.show(d,"$summary\n$label\nConfirma?",action=guarded(scope) {
                    if(d.kind=="issue") require(equipment !in MaintenanceRecords.archivedEquipment(host.read("pendencias"))) {"O equipamento foi excluído. Restaure antes de continuar."}
                    replace(group,null,row,scope,0);host.navigate(if(group=="pendencias") if(d.kind=="part") "pecas" else "pendencias" else "ocorrencias");message("Registro salvo neste celular • aguardando sincronização")
                })
            }
            "issue_close"->{
                val all=MaintenanceShareData.open(host.read("pendencias"),f.getString("equipment"))
                if(all.isEmpty()) {ui.show(d,"Não há pendência aberta desse equipamento nesta equipe.");return}
                val selected=all.firstOrNull {it.optString("id")==f.optString("recordId")}
                if(selected==null) {ui.show(d,"Qual pendência foi resolvida? Voltar a trabalhar não encerra os outros serviços.",options=all.map {row->FieldVoice.Choice(row.optString("descricao")) {ui.replace(d,d.copyFields().put("recordId",row.optString("id")))}});return}
                if(!host.canEdit("pendencias",selected)) {ui.show(d,"Somente o autor ou o gestor pode concluir esta pendência.");return}
                val base=host.revision("pendencias",selected.optString("id"))
                ui.show(d,"Concluir somente esta pendência do equipamento ${selected.optString("equipamento")}: ${selected.optString("descricao")}?",action=guarded(scope) {
                    replace("pendencias",selected,JSONObject(selected.toString()).put("concluida",true).put("dataConclusao",date(d)),scope,base);host.navigate("pendencias")
                })
            }
            "truck_entry","truck_exit"->{
                val number=f.getString("number");val day=try {LocalDate.parse(f.getString("date"))} catch(_:Exception) {null}
                val hour=TruckRecords.time(f.optString("time"))
                if(day==null||hour==null) {ui.show(d,"Qual é o horário? Use, por exemplo, 15:20.",missing="time");return}
                val open=rows("caminhoes").filter {!RecordArchive.archived(it)&&it.optString("numero")==number&&it.optString("saida").isBlank()}
                if(d.kind=="truck_entry"&&open.isNotEmpty()) {ui.show(d,"O caminhão $number já tem entrada sem saída. Confira a saída antes de registrar outra chegada.");return}
                if(d.kind=="truck_exit"&&open.isEmpty()) {ui.show(d,"Não encontrei entrada em aberto para o caminhão $number nesta equipe. Confira o número ou a equipe.");return}
                var old:JSONObject?=null
                if(d.kind=="truck_exit") {
                    old=open.firstOrNull {it.optString("id")==f.optString("recordId")}?:open.singleOrNull()
                    if(old==null) {ui.show(d,"Qual entrada do caminhão $number deseja encerrar?",options=open.map {row->FieldVoice.Choice(TruckRecords.details(row)) {ui.replace(d,d.copyFields().put("recordId",row.optString("id")))}});return}
                }
                try {
                    if(d.kind=="truck_entry") TruckShift.requireEntry(team.substringAfterLast('_'),day.atTime(hour))
                    val row=if(old==null) TruckRecords.build(null,number,f.getString("time"),"",day,null,team)
                        else TruckRecords.build(old,number,old.optString("chegada"),f.getString("time"),TruckRecords.entryDay(old)?:error("Confira a data da entrada."),day,team)
                    val original=old;val base=old?.let {host.revision("caminhoes",it.optString("id"))}?:0L
                    ui.show(d,"${if(d.kind=="truck_entry") "Entrada" else "Saída"} do caminhão $number em ${TruckRecords.label(day)}, às ${f.getString("time")}.\n$label\nConfirma?",action=guarded(scope) {
                        if(original==null) require(rows("caminhoes").none {!RecordArchive.archived(it)&&it.optString("numero")==number&&it.optString("saida").isBlank()}) {"Esse caminhão já recebeu uma entrada. Confira a lista."}
                        replace("caminhoes",original,row,scope,base);host.navigate("caminhoes")
                    })
                } catch(e:Exception) {ui.show(d,e.message?:"Confira a data e o horário.",missing="time")}
            }
            "stock"->{
                val original=rows("estoques_salvos").firstOrNull {it.optString("id")==f.optString("recordId")}
                if(f.optString("recordId").isNotBlank()&&(original==null||RecordArchive.archived(original))) {ui.show(d,"O estoque foi removido. Confira o lote antes de continuar.");return}
                val row=original?.let {JSONObject(it.toString())}?:JSONObject().put("id",UUID.randomUUID().toString()).put("dataCadastro",date(d))
                row.put("os",f.getString("lot")).put("talhoes",f.getString("plots")).put("areaRestante",f.getString("area")).put("produtividade",f.getString("yield"))
                try {FieldWorkflow.requireStock(row)} catch(e:Exception) {ui.show(d,e.message?:"Confira os números do estoque.");return}
                if(f.optBoolean("paintedMap")) {
                    val maps=host.paintedMaps();val chosen=maps.firstOrNull {it.file.path==f.optString("photo")}
                    if(chosen==null) {
                        val lot=FieldWorkflow.lot(f.getString("lot"));val matching=maps.filter {Regex(Regex.escape(lot)+"(?![0-9])").containsMatchIn(FieldWorkflow.lot(it.title))}
                        if(matching.size==1) {ui.replace(d,d.copyFields().put("photo",matching.single().file.path));return}
                        if(maps.isEmpty()) {ui.show(d,"Não há mapa pintado salvo neste celular. Salve a pintura na biblioteca antes de anexar ao estoque.");return}
                        val candidates=matching.ifEmpty {maps}
                        ui.show(d,"Qual mapa pintado deve acompanhar o lote?",options=candidates.map {item->FieldVoice.Choice(item.label) {ui.replace(d,d.copyFields().put("photo",item.file.path))}});return
                    }
                    row.put("fotoEstoque",chosen.file.path)
                }
                val base=original?.let {host.revision("estoques_salvos",it.optString("id"))}?:0L
                ui.show(d,"${if(original==null) "Salvar" else "Atualizar"} estoque do lote ${f.getString("lot")}, talhões ${f.getString("plots")}, área ${f.getString("area")} hectares, produtividade ${f.getString("yield")} toneladas por hectare.${if(f.optBoolean("paintedMap")) " Com mapa pintado salvo." else ""}\n$label\nConfirma?",action=guarded(scope) {
                    if(f.optBoolean("paintedMap")) require(host.paintedMaps().any {it.file.path==row.optString("fotoEstoque")}) {"O mapa selecionado não está mais disponível."}
                    replace("estoques_salvos",original,row,scope,base);host.navigate("estoque");message("Estoque salvo. Use Compartilhar para enviar a mensagem e o mapa.")
                })
            }
            "rain"->{
                val amount=RainRecords.amount(f.getString("mm"))
                if(amount==null) {ui.show(d,"Quantos milímetros? Informe um número válido.",missing="mm");return}
                val day=d.captured.toLocalDate();val original=rows("ocorrencias_campo").firstOrNull {it.optString("id")==RainRecords.id(day)}
                if(original!=null&&!host.canEdit("ocorrencias_campo",original)) {ui.show(d,"O registro de chuva deste turno só pode ser corrigido pelo autor ou gestor.");return}
                if(original!=null&&f.optString("rainMode").isBlank()) {
                    ui.show(d,"Já há ${RainRecords.label(original)} neste dia/turno. Os ${f.getString("mm")} mm são o acumulado ou devem ser somados?",options=listOf(
                        FieldVoice.Choice("Acumulado do turno") {ui.replace(d,d.copyFields().put("rainMode","total"))},FieldVoice.Choice("Somar ao registro atual") {ui.replace(d,d.copyFields().put("rainMode","add"))}));return
                }
                val total=if(original!=null&&f.optString("rainMode")=="add") (RainRecords.amount(original.optString("chuvaMm"))?:java.math.BigDecimal.ZERO)+amount else amount
                val created=try {RainRecords.create(TruckRecords.label(day),total.toPlainString(),original?.optString("observacaoChuva").orEmpty(),team,access.uid,access.profile["nome"]?.toString().orEmpty())} catch(e:Exception) {ui.show(d,e.message?:"Confira o volume.");return}
                val row=original?.let {JSONObject(it.toString()).put("chuvaMm",created.getString("chuvaMm")).put("descricao",created.getString("descricao"))}?:created
                val base=original?.let {host.revision("ocorrencias_campo",it.optString("id"))}?:0L
                ui.show(d,"Chuva acumulada: ${RainRecords.label(row)}, dia ${TruckRecords.label(day)}.\n$label\nConfirma?",action=guarded(scope) {replace("ocorrencias_campo",original,row,scope,base);host.navigate("chuva")})
            }
            "preventive"->{
                val equipment=MaintenanceRecords.number(f.getString("equipment"))
                val original=rows("pendencias").firstOrNull {MaintenanceRecords.isEquipment(it)&&MaintenanceRecords.number(it.optString("equipamento"))==equipment&&!RecordArchive.archived(it)}
                if(original==null) {ui.show(d,"Não encontrei esse equipamento ativo. Cadastre ou restaure o equipamento em Pendências antes de programar a revisão.");return}
                try {
                    val row=PreventiveMaintenance.update(original,f.getString("service"),f.optString("serviceDate"),f.optString("currentHours"),f.optString("nextHours"))
                    val base=host.revision("pendencias",original.getString("id"))
                    ui.show(d,"Revisão do equipamento $equipment:\n${PreventiveMaintenance.status(row).text}\nConfirma?",action=guarded(scope) {replace("pendencias",original,row,scope,base);host.navigate("pendencias")})
                } catch(e:Exception) {ui.show(d,e.message?:"Confira os dados da revisão.")}
            }
            "order_close"->{
                val map=host.maps().firstOrNull {it.id==f.optString("map")}
                if(map==null) {ui.show(d,"De qual mapa é a ordem de serviço?",options=host.maps().map {item->FieldVoice.Choice(item.name) {ui.replace(d,d.copyFields().put("map",item.id))}});return}
                ui.show(d,"Abrir encerramento da O.S. ${f.getString("order")} do mapa ${map.name}? O histórico será preservado.",action=guarded(scope) {host.closeOrder(map.id,f.getString("order"))})
            }
            else->ui.show(d,"Não consegui identificar uma ação com segurança. Diga a ferramenta e os dados do registro.",missing="command")
        }
    }
    private fun toolName(tool:String)=mapOf("pendencias" to "pendências","pecas" to "peças a pedir","chuva" to "chuva por turno","estoque" to "estoque","ocorrencias" to "ocorrências","caminhoes" to "caminhões","passagem" to "passagem de turno","calendario" to "calendário","os" to "ordens de serviço","mapas" to "mapas","previsao" to "previsão do tempo","atualizacao" to "atualização","usuarios" to "gestão de usuários","configuracoes" to "configurações","inicio" to "início")[tool]?:tool
}
