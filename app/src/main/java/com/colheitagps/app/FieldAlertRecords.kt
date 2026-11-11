package com.colheitagps.app

import android.location.Location
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

/** Uses the existing occurrence group and authenticated notification pipeline, including offline storage. */
internal object FieldAlertRecords {
    const val GROUP="ocorrencias_campo"
    const val TYPE="Equipamento encalhado"
    const val PREFIX="EQUIPAMENTO ENCALHADO"
    fun isAlert(record:JSONObject)=record.optString("categoriaRegistro")=="encalhado"||record.optString("tipo")==TYPE
    fun currentLocation(location:Location?,elapsedNanos:Long):Location? {
        location?:return null
        val age=elapsedNanos-location.elapsedRealtimeNanos
        return location.takeIf {location.elapsedRealtimeNanos>0&&age in 0..120_000_000_000L&&
            it.latitude.isFinite()&&it.latitude in -90.0..90.0&&it.longitude.isFinite()&&it.longitude in -180.0..180.0&&
            it.hasAccuracy()&&it.accuracy.isFinite()&&it.accuracy>=0}
    }
    fun build(existing:JSONObject?,equipment:String,place:String,note:String,uid:String,name:String,team:String,
              location:Location?,nowMillis:Long,elapsedNanos:Long):JSONObject {
        val number=MaintenanceRecords.number(equipment)
        require(number.isNotBlank()&&number.length<=80) {"Informe o número do equipamento, com até 80 caracteres."}
        require(place.trim().length<=1000&&note.trim().length<=2000) {"Use até 1000 caracteres no local e 2000 na observação."}
        require(team.substringBeforeLast('_') in TeamPolicy.fronts&&team.substringAfterLast('_') in TeamPolicy.shifts)
        val record=existing?.let {JSONObject(it.toString())}?:JSONObject().put("id",UUID.randomUUID().toString())
            .put("registradoPorUid",uid).put("registradoPorNome",name).put("concluida",false)
            .put("dataCadastro",SimpleDateFormat("dd/MM/yyyy",Locale("pt","BR")).format(Date(nowMillis)))
            .put("horaCadastro",SimpleDateFormat("HH:mm",Locale("pt","BR")).format(Date(nowMillis)))
        record.put("categoriaRegistro","encalhado").put("tipo",TYPE).put("equipamento",number)
            .put("localizacao",place.trim()).put("observacaoEncalhado",note.trim())
            .put("descricao",PREFIX+". Preciso de apoio."+place.trim().takeIf {it.isNotBlank()}?.let {" Local: $it."}.orEmpty()+note.trim().takeIf {it.isNotBlank()}?.let {" $it"}.orEmpty())
            .put("frenteId",team.substringBeforeLast('_')).put("turno",team.substringAfterLast('_'))
        // Existing coordinates are kept when editing; attaching a fresh fix replaces them.
        if(existing==null) record.put("gps","")
        currentLocation(location,elapsedNanos)?.let {position->
            record.put("gps",String.format(Locale.US,"%.6f, %.6f",position.latitude,position.longitude))
                .put("latitude",position.latitude).put("longitude",position.longitude).put("precisaoGpsM",position.accuracy.toDouble())
                .put("gpsObtidoEm",position.time)
        }
        return record
    }
}
