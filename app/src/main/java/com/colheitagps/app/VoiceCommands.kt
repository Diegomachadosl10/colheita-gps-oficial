package com.colheitagps.app

import org.json.JSONObject
import java.text.Normalizer
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Local, bounded intent/slot parser. Dictation never writes records and never diagnoses a defect. */
internal object VoiceCommands {
    data class Draft(val kind:String,val fields:JSONObject=JSONObject(),val utterance:String="",val captured:LocalDateTime=LocalDateTime.now()) {
        fun copyFields()=JSONObject(fields.toString())
    }
    val tools=linkedMapOf("pendencias" to listOf("pendencia","manutencao","equipamento"),"pecas" to listOf("peca","pedido de peca"),
        "chuva" to listOf("chuva"),"estoque" to listOf("estoque"),"ocorrencias" to listOf("ocorrencia"),"caminhoes" to listOf("caminhao","caminhoes"),
        "passagem" to listOf("passagem de turno"),"calendario" to listOf("calendario"),"os" to listOf("ordem de servico","ordens de servico","o.s."),
        "mapas" to listOf("mapa","biblioteca"),"previsao" to listOf("previsao","tempo"),"atualizacao" to listOf("atualizacao","atualizar aplicativo"),
        "usuarios" to listOf("usuario","aprovar equipe"),"configuracoes" to listOf("configuracoes"),"inicio" to listOf("inicio","tela inicial"))
    fun norm(value:String)=Normalizer.normalize(value,Normalizer.Form.NFD).replace(Regex("\\p{M}"),"").lowercase(java.util.Locale.ROOT)
    private val wake=Regex("^(?:d\\s*m\\s*agro|dmagro|de\\s+eme\\s+agro|deme\\s+agro|deneago|de[\\s-]*me\\s+agua)[,.:;!\\s-]*")
    fun wakes(text:String)=wake.containsMatchIn(norm(text).trim())
    fun withoutWake(text:String):String {val value=text.trim();val match=wake.find(norm(value));return if(match!=null) value.drop(match.value.length).trim() else value}
    fun confirms(text:String)=norm(text).trim().trim('.','!') in setOf("sim","confirma","confirmar","confirmo","pode salvar","salvar","pode registrar","registrar","pode pintar","pintar","isso mesmo","correto","enviar")
    fun cancels(text:String)=norm(text).trim() in setOf("cancelar","cancela","cancela isso","nao salvar","desistir","pare","parar","fechar")
    fun ordinal(text:String):Int? = mapOf("primeira" to 0,"primeiro" to 0,"um" to 0,"1" to 0,"segunda" to 1,"segundo" to 1,"dois" to 1,"2" to 1,"terceira" to 2,"terceiro" to 2,"tres" to 2,"3" to 2,"quarta" to 3,"quarto" to 3,"4" to 3).get(norm(text).trim().removePrefix("a ").removePrefix("o "))
    private val units=mapOf("zero" to 0,"um" to 1,"uma" to 1,"dois" to 2,"duas" to 2,"tres" to 3,"quatro" to 4,"cinco" to 5,"seis" to 6,"sete" to 7,"oito" to 8,"nove" to 9,
        "dez" to 10,"onze" to 11,"doze" to 12,"treze" to 13,"catorze" to 14,"quatorze" to 14,"quinze" to 15,"dezesseis" to 16,"dezessete" to 17,"dezoito" to 18,"dezenove" to 19,
        "vinte" to 20,"trinta" to 30,"quarenta" to 40,"cinquenta" to 50,"sessenta" to 60,"setenta" to 70,"oitenta" to 80,"noventa" to 90,
        "cem" to 100,"cento" to 100,"duzentos" to 200,"duzentas" to 200,"trezentos" to 300,"quatrocentos" to 400,"quinhentos" to 500,"seiscentos" to 600,"setecentos" to 700,"oitocentos" to 800,"novecentos" to 900)
    fun numberWords(text:String):String {
        val words=norm(text).trim().split(Regex("\\s+"));var current=0L;var total=0L
        if(words.isEmpty()) return ""
        for(word in words) when {word=="e"->Unit;word=="mil"->{total+=maxOf(1,current)*1000;current=0};word in units->current+=units.getValue(word);word.matches(Regex("[0-9]+"))->current+=word.toLong();else->return ""}
        return (total+current).toString()
    }
    private fun smallNumber(text:String):Int? {
        val words=norm(text).trim().split(Regex("\\s+"))
        if(words.size==1) return (words[0].toIntOrNull()?:units[words[0]])?.takeIf {it in 0..99}
        if(words.size==3&&words[1]=="e") {
            val first=units[words[0]]?:return null;val last=units[words[2]]?:return null
            if(first in 20..90&&first%10==0&&last in 1..9) return first+last
        }
        return null
    }
    private fun spokenTime(raw:String):String? {
        val text=norm(raw)
        if(text.contains("meio dia")) return "12:00"
        if(text.contains("meia noite")) return "00:00"
        val after=Regex("\\bas\\s+(.+)").find(text)?.groupValues?.get(1)?:return null
        val words=Regex("[a-z]+|[0-9]+").findAll(after).map {it.value}.takeWhile {it in units||it=="e"||it in setOf("hora","horas")||it.matches(Regex("[0-9]+"))}.filter {it !in setOf("hora","horas")}.toList()
        val options=mutableListOf<Pair<Int,Int>>()
        for(i in words.indices.filter {words[it]=="e"}) {
            val h=smallNumber(words.take(i).joinToString(" "));val m=smallNumber(words.drop(i+1).joinToString(" "))
            if(h!=null&&h in 0..23&&m!=null&&m in 0..59) options.add(h to m)
        }
        if(options.isEmpty()) smallNumber(words.joinToString(" "))?.takeIf {it in 0..23}?.let {options.add(it to 0)}
        if(options.distinct().size!=1) return null
        var (hour,minute)=options.single()
        if(Regex("da (?:tarde|noite)").containsMatchIn(text)&&hour in 1..11) hour+=12
        if(text.contains("12 da noite")) hour=0
        return "%02d:%02d".format(java.util.Locale.ROOT,hour,minute)
    }
    fun invalidate(previous:Draft?,fields:JSONObject):JSONObject {
        if(previous==null) return fields
        val old=previous.fields
        if(old.optString("lot")!=fields.optString("lot")) {
            for(key in listOf("recordId","map","curveId","photo","refX","refY")) fields.remove(key)
            for(key in listOf("plots","area","yield")) if(fields.optBoolean(key+"_derived")) {fields.remove(key);fields.remove(key+"_derived")}
        }
        if(old.optString("plots")!=fields.optString("plots")||old.optString("ha")!=fields.optString("ha")||old.optString("map")!=fields.optString("map")||old.optString("paintTarget")!=fields.optString("paintTarget")) for(key in listOf("curveId","refX","refY")) fields.remove(key)
        if(old.optString("equipment")!=fields.optString("equipment")||old.optString("number")!=fields.optString("number")) {
            fields.remove("recordId")
            for(key in listOf("service","serviceDate","currentHours","nextHours")) if(fields.optBoolean(key+"_derived")) {fields.remove(key);fields.remove(key+"_derived")}
        }
        return fields
    }
    fun numbers(text:String):String {
        val tokens=Regex("[a-z]+|[0-9]+(?:[,.][0-9]+)?|[^a-z0-9\\s]").findAll(norm(text)).map {it.value}.toList()
        val output=mutableListOf<String>();var i=0
        while(i<tokens.size) {
            if(tokens[i] !in units&&tokens[i]!="mil") {output.add(tokens[i]);i++;continue}
            val chunk=mutableListOf<String>();var j=i
            while(j<tokens.size&&(tokens[j] in units||tokens[j]=="mil"||(tokens[j]=="e"&&j+1<tokens.size&&tokens[j+1] in units))) {chunk.add(tokens[j]);j++}
            var value=numberWords(chunk.joinToString(" "))
            if(j+1<tokens.size&&tokens[j] in setOf("virgula","ponto")&&tokens[j+1] in units) {
                val decimals=mutableListOf<String>();j++
                while(j<tokens.size&&(tokens[j] in units||(tokens[j]=="e"&&j+1<tokens.size&&tokens[j+1] in units))) {decimals.add(tokens[j]);j++}
                val digits=if(decimals.all {it in units&&units.getValue(it)<10}) decimals.joinToString("") {units.getValue(it).toString()} else numberWords(decimals.joinToString(" "))
                value+=","+digits
            }
            output.add(value);i=j
        }
        return output.joinToString(" ").replace(Regex("([0-9])\\s*([,.])\\s*([0-9])"),"$1$2$3")
    }
    private fun match(text:String,regex:String)=Regex(regex).find(text)?.groupValues?.get(1).orEmpty()
    private fun id(text:String)=match(text,"\\b(?:colhedora|trator|equipamento|maquina|caminhao|carreta|o|a)\\s+([0-9]{2}\\s+[0-9]{3}|[0-9]{3,8})\\b").replace(" ","")
        .ifBlank {match(text,"\\b([0-9]{3,8})\\b")}
    private fun amount(text:String,prefix:String)=match(text,"(?:$prefix)\\s*(?:de|do|da|e|eh|esta|com|:)?\\s*([0-9]+(?:[,.][0-9]+)?)")
    fun parse(raw:String,previous:Draft?=null,waiting:String="",now:LocalDateTime=LocalDateTime.now()):Draft {
        val speech=withoutWake(raw).take(3000);val text=norm(speech);val n=numbers(text)
        val navigation=Regex("\\b(?:abr[ae]|abrir|mostr[ae]|mostrar|va para|ir para|entra em|ver)\\b").containsMatchIn(text)
        val sharing=Regex("\\b(?:compartilh|mand[ae]|envi[ae])").containsMatchIn(text)
        val tool=tools.entries.sortedByDescending {it.value.maxOf(String::length)}.firstOrNull {(_,aliases)->aliases.any {text.contains(it)}}?.key.orEmpty()
        val routine=text.contains("abastec")||Regex("(?:giro|girar|virar|inverter|vire).{0,20}facas?").containsMatchIn(text)
        val correction=Regex("^(?:nao[ ,]|na verdade|corrig|troqu[ae]|mude|coloc[ae] como|prioridade)").containsMatchIn(text)
        val completion=Regex("(?:conclu[ai]|concluir|resolvid|feito|terminou|finaliz|voltou a trabalhar)").containsMatchIn(text)
        val entry=Regex("(?:chegou|chegando|acabou de chegar|chegada|entrada|entrou|encostou)").containsMatchIn(text)
        val exit=Regex("(?:saindo|saiu|saida|foi embora|liberad)").containsMatchIn(text)&&!Regex("nao.{0,12}(?:saiu|saindo|liberad)").containsMatchIn(text)
        val painting=Regex("(?:pint[ae]|pintar|colorir|preench[ae]).{0,35}(?:curva|talhao)").containsMatchIn(text)
        val fresh=when {
            painting->"curve"
            navigation->"navigate"
            sharing&&tool.isNotBlank()->"share"
            completion&&(text.contains("o.s")||text.contains("ordem de servico"))->"order_close"
            routine->"routine"
            (entry||exit)&&(text.contains("caminh")||id(n).isNotBlank()||previous?.kind?.startsWith("truck")==true)->if(exit) "truck_exit" else "truck_entry"
            text.contains("estoque")||text.contains("produtividade")->"stock"
            text.contains("choveu")||text.contains("milimetro")||Regex("\\bchuva\\s+(?:de\\s+)?[0-9]").containsMatchIn(n)->"rain"
            text.contains("pedir")||text.contains("encomendar")||(text.contains("peca")&&Regex("pedido|precis|cadastr|registr|anot|adicion|nova").containsMatchIn(text))->"part"
            text.contains("ocorrencia")||(Regex("buraco|erosao|obstaculo").containsMatchIn(text)&&!Regex("colhedora|trator|maquina").containsMatchIn(text))->"occurrence"
            text.contains("horimetro")||text.contains("revisao preventiva")||text.contains("proxima revisao")->"preventive"
            completion&&(id(n).isNotBlank()||text.contains("pendencia"))->"issue_close"
            Regex("(?:colhedora|trator|equipamento|maquina|pendencia|rolamento|pneu)").containsMatchIn(text)||(id(n).isNotBlank()&&Regex("(?:problema|precis|parou|quebrou|queimou|trocar|deu ruim)").containsMatchIn(text))->"issue"
            else->""
        }
        val keep=previous!=null&&(correction||fresh.isBlank()||(fresh==previous.kind&&waiting.isNotBlank()))
        val kind=if(keep) previous!!.kind else fresh.ifBlank {previous?.kind?:"unknown"}
        val f=if(keep) previous!!.copyFields() else JSONObject()
        val number=if(kind=="share"&&tool=="pendencias") id(n) else if(kind in setOf("part","curve","stock","rain","order_close","navigate","share")) match(n,"\\b(?:colhedora|trator|equipamento|maquina|caminhao|para)\\s+([0-9]{3,8})\\b") else id(n)
        if(number.isNotBlank()) f.put(if(kind.startsWith("truck")) "number" else "equipment",number)
        var lotSpeech=n
        for((spoken,letter) in mapOf("ca|ka" to "k","jota" to "j","eme" to "m","ene" to "n","ele" to "l","ce" to "c","aga" to "h")) lotSpeech=lotSpeech.replace(Regex("\\b(?:$spoken)\\b(?=\\s*(?:barra|/|-)?\\s*[0-9])"),letter)
        val lot=Regex("(?:lote|estoque(?:\\s+(?:do|de))?)?\\s*\\b([a-z])\\s*(?:barra|/|-)?\\s*([0-9]{1,4})\\b").findAll(lotSpeech).firstOrNull {it.groupValues[1] !in setOf("a","e","o")||Regex("\\b(?:lote|estoque)\\b").containsMatchIn(n)||waiting=="lot"}
        if(lot!=null) f.put("lot",lot.groupValues[1].uppercase()+"/"+lot.groupValues[2])
        amount(n,"area(?: restante)?").takeIf {it.isNotBlank()}?.let {f.put("area",it);f.remove("area_derived")}
        amount(n,"produtividade|producao por hectare").takeIf {it.isNotBlank()}?.let {f.put("yield",it);f.remove("yield_derived")}
        amount(n,"talhao|talhoes").takeIf {it.isNotBlank()}?.let {f.put("plots",it);f.remove("plots_derived")}
        if(kind=="curve") {
            if(text.contains("curva")) f.put("paintTarget","curve")
            else if(painting||Regex("talhao.{0,15}(?:inteiro|todo|completo)").containsMatchIn(text)) {f.put("paintTarget","plot");f.remove("ha")}
            amount(n,"curva(?: de| com)?").takeIf {it.isNotBlank()}?.let {f.put("ha",it)}
            if(text.contains("metade")||text.contains("50%")||n.contains("50 %")) f.put("fraction","half") else if(Regex("inteir|toda|completa").containsMatchIn(text)||!f.has("fraction")) f.put("fraction","full")
            for(side in listOf("esquerda","direita","cima","baixo")) if(text.contains(side)) f.put("side",side)
            val topDown=Regex("\\b(?:de\\s+)?cima\\s+(?:para|pra|p|a)\\s+baixo\\b").containsMatchIn(text)
            val bottomUp=Regex("\\b(?:de\\s+)?baixo\\s+(?:para|pra|p|a)\\s+cima\\b").containsMatchIn(text)
            val top=Regex("\\b(?:cima|superior)\\b").containsMatchIn(text)
            val bottom=Regex("\\b(?:baixo|inferior)\\b").containsMatchIn(text)
            when {
                topDown&&!bottomUp->f.put("harvestDirection","top_down")
                bottomUp&&!topDown->f.put("harvestDirection","bottom_up")
                top&&!bottom->f.put("harvestDirection","top_down")
                bottom&&!top->f.put("harvestDirection","bottom_up")
                top||bottom||Regex("\\b(?:esquerd[ao]|direit[ao])\\b").containsMatchIn(text)->f.remove("harvestDirection")
            }
            if(topDown||bottomUp||(f.optString("fraction")=="half"&&(top||bottom))) {if(f.optString("side") in setOf("cima","baixo")) f.remove("side")}
            if(text.contains("carreador")) f.put("road",true)
        }
        if(kind=="navigate"||kind=="share") {f.put("tool",tool);f.put("photos",!text.contains("sem foto"));f.put("image",text.contains("imagem"));f.put("all",text.contains("todas")||text.contains("todos"))}
        if(kind=="share") {f.put("onlyUrgent",text.contains("urgente")&&!text.contains("nao urgente"));f.put("rainOnly",text.contains("parada de chuva"))}
        if(kind=="stock"&&Regex("mapa.{0,15}pintado").containsMatchIn(text)) f.put("paintedMap",true)
        if(Regex("nao (?:e |eh )?urgente|sem urgencia|normal|nao urgente").containsMatchIn(text)) f.put("urgent",false)
        else if(text.contains("urgente")||text.contains("prioridade alta")) f.put("urgent",true)
        if(!f.has("urgent")) f.put("urgent",false)
        if(kind in setOf("issue","issue_close","routine","occurrence","part")&&!correction&&(fresh.isNotBlank()||waiting=="description")) {
            var description=speech.replace(Regex("(?i)^(?:anota ai[, ]*|registre[, ]*|registrar[, ]*|crie[, ]*|nova pend[eê]ncia[, ]*)"),"")
            if(number.isNotBlank()) description=description.replace(Regex("(?i)^\\s*(?:(?:a|o)\\s+)?(?:(?:colhedora|trator|equipamento|m[aá]quina|caminh[aã]o)\\s+)?"+Regex.escape(number)+"\\s*[,.:;-]*\\s*"),"")
            description=description.replace(Regex("(?i)[,;]?\\s*(?:prioridade (?:normal|alta|urgente)|(?:n[aã]o (?:[eé] )?)?urgente)\\s*$"),"").trim()
            if(description.isNotBlank()) f.put("description",description)
        }
        if(kind=="routine") f.put("routine",if(text.contains("abastec")) "Abastecimento" else "Giro de facas")
        if(kind=="rain") {val mm=amount(n,"choveu|chuva(?: de)?").ifBlank {match(n,"([0-9]+(?:[,.][0-9]+)?)\\s*(?:mm|milimetros?)")};if(mm.isNotBlank()) f.put("mm",mm)}
        if(kind=="rain"&&previous!=null) {
            if(text.contains("acumulado")||text.contains("total")) f.put("rainMode","total")
            else if(text.contains("somar")||text.contains("adicional")||text.startsWith("mais ")) f.put("rainMode","add")
        }
        if(kind=="part") {
            amount(n,"quantidade|pedir|encomendar").ifBlank {match(n,"([0-9]+)\\s*(?:unidades?|pecas?)")}.takeIf {it.isNotBlank()}?.let {f.put("quantity",it)}
            match(text,"(?:codigo|item)\\s+(?:(?:e|eh)\\s+)?([a-z0-9]+(?:[-_.][a-z0-9]+)*)").takeIf {it.isNotBlank()}?.let {f.put("code",it.uppercase())}
        }
        if(kind=="preventive") {
            amount(n,"horimetro atual|horimetro esta|horimetro em").takeIf {it.isNotBlank()}?.let {f.put("currentHours",it);f.remove("currentHours_derived")}
            amount(n,"proxima revisao(?: em)?|revisao (?:em|com)").takeIf {it.isNotBlank()}?.let {f.put("nextHours",it);f.remove("nextHours_derived")}
            val service=Regex("revisao de (.+?)(?: (?:do equipamento|da colhedora|do trator|no horimetro|com horimetro|em [0-9])|$)").find(text)?.groupValues?.get(1)
            if(!service.isNullOrBlank()) {f.put("service",service);f.remove("service_derived")}
            val day=Regex("(?:dia|data)\\s+([0-9]{1,2})\\s*[/.-]\\s*([0-9]{1,2})\\s*[/.-]\\s*([0-9]{4})").find(n)
            if(day!=null) try {f.put("serviceDate",java.time.LocalDate.of(day.groupValues[3].toInt(),day.groupValues[2].toInt(),day.groupValues[1].toInt()).toString());f.remove("serviceDate_derived")} catch(_:Exception) {f.put("serviceDate","")}
        }
        if(kind=="order_close") amount(n,"ordem de servico|o[.]?\\s*s[.]?").takeIf {it.isNotBlank()}?.let {f.put("order",it)}
        if(keep&&waiting.isNotBlank()&&fresh.isBlank()&&!correction) {
            val answer=when(waiting) {"description","service","location"->speech;"lot"->f.optString("lot").ifBlank {speech.trim().uppercase()};"equipment","number"->number.ifBlank {n.trim().takeIf {it.matches(Regex("[0-9]{1,8}"))}.orEmpty()};else->match(n,"([0-9]+(?:[,.][0-9]+)?)")}
            if(answer.isNotBlank()) {f.put(waiting,answer);f.remove(waiting+"_derived")}
        }
        if(kind.startsWith("truck")) {
            if(!f.has("time")) f.put("time",now.format(DateTimeFormatter.ofPattern("HH:mm")))
            if(!f.has("date")) f.put("date",now.toLocalDate().toString())
            if(text.contains("ontem")) f.put("date",now.toLocalDate().minusDays(1).toString())
            val time=Regex("\\b([0-9]{1,2})\\s*(?::|h)\\s*([0-9]{2})\\b").find(n)
            if(time!=null) f.put("time",time.groupValues[1].padStart(2,'0')+":"+time.groupValues[2])
            else {
                val spoken=spokenTime(speech)?:if(waiting=="time") spokenTime("às $speech") else null
                if(spoken!=null) f.put("time",spoken)
                else if(Regex("\\bas\\b|meio dia|meia noite").containsMatchIn(text)) f.remove("time")
            }
        }
        return Draft(kind,invalidate(if(keep) previous else null,f),speech,if(keep) previous!!.captured else now)
    }
    fun missing(d:Draft):Pair<String,String>? {
        fun absent(key:String,prompt:String)=if(d.fields.optString(key).isBlank()) key to prompt else null
        return when(d.kind) {
            "stock"->absent("lot","Qual é o lote?")?:absent("plots","Quais são os talhões?")?:absent("area","Quantos hectares restam?")?:absent("yield","Qual é a produtividade em toneladas por hectare?")
            "issue","issue_close","routine"->absent("equipment","Qual é o número do equipamento?")?:absent("description","Qual é o serviço ou problema?")
            "truck_entry","truck_exit"->absent("number","Qual é o número do caminhão?")?:absent("time","Qual é o horário? Diga por exemplo quinze e vinte ou escreva 15:20.")
            "rain"->absent("mm","Quantos milímetros de chuva?")
            "part"->absent("description","Qual peça você precisa?")?:absent("quantity","Qual é a quantidade?")
            "preventive"->absent("equipment","Qual é o número do equipamento?")?:absent("service","Qual é o serviço da revisão?")?:
                (if(d.fields.optString("serviceDate").isBlank()) absent("nextHours","A revisão será em qual horímetro? Você também pode informar a data, por exemplo dia 20/11/2026.") else null)?:
                (if(d.fields.optString("nextHours").isNotBlank()) absent("currentHours","Qual é a leitura atual do horímetro?") else null)
            "occurrence"->absent("description","O que aconteceu e onde?")
            "curve"->absent("plots","Qual é o talhão?")?:if(d.fields.optString("paintTarget")=="plot") null else absent("ha","Qual é a área em hectares escrita na curva?")
            "order_close"->absent("order","Qual é o número da ordem de serviço?")
            "navigate","share"->absent("tool","Qual ferramenta deseja abrir?")
            else->"command" to "Diga o equipamento e o problema, um caminhão chegando ou saindo, um estoque ou a ferramenta que deseja abrir."
        }
    }
}
