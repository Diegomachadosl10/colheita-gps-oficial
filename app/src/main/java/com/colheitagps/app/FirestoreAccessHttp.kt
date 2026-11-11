package com.colheitagps.app

import com.google.firebase.Timestamp
import org.json.JSONObject
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import javax.net.ssl.HttpsURLConnection

/** Firebase ID tokens, not service accounts: the same Firestore Security Rules evaluate every GET. */
internal object FirestoreAccessHttp {
    fun path(project:String,uid:String,readiness:Boolean):String {
        require(project.matches(Regex("[a-z][a-z0-9-]{3,62}")))
        require(uid.isNotBlank()&&uid.length<=128&&!uid.contains('/'))
        val document=if(readiness) "configuracao/equipes" else "usuarios/"+URLEncoder.encode(uid,"UTF-8").replace("+","%20")
        return "https://firestore.googleapis.com/v1/projects/$project/databases/(default)/documents/$document"
    }
    fun parse(status:Int,body:String,expectedName:String):Map<String,Any>? {
        val json=try {JSONObject(body)} catch(_:Exception) {throw AccessFailure(if(status==401) "UNAUTHENTICATED" else if(status==403) "PERMISSION_DENIED" else "INVALID_RESPONSE")}
        if(status==404&&json.optJSONObject("error")?.optString("status")=="NOT_FOUND") return null
        if(status!=200) throw AccessFailure(when(status) {401->"UNAUTHENTICATED";403->"PERMISSION_DENIED";408,504->"TIMEOUT";else->"HTTP_$status"})
        if(json.optString("name")!=expectedName) throw AccessFailure("INVALID_RESPONSE")
        val values=json.optJSONObject("fields")?:JSONObject()
        return values.keys().asSequence().mapNotNull {key->values.optJSONObject(key)?.let(::value)?.let {key to it}}.toMap()
    }
    private fun value(field:JSONObject):Any?=when {
        field.has("stringValue")->field.getString("stringValue")
        field.has("booleanValue")->field.getBoolean("booleanValue")
        field.has("integerValue")->field.getString("integerValue").toLong()
        field.has("doubleValue")->field.getDouble("doubleValue")
        field.has("timestampValue")->Instant.parse(field.getString("timestampValue")).let {Timestamp(it.epochSecond,it.nano)}
        field.has("mapValue")->field.getJSONObject("mapValue").optJSONObject("fields")?.let {map->map.keys().asSequence().mapNotNull {key->map.optJSONObject(key)?.let(::value)?.let {key to it}}.toMap()}?:emptyMap<String,Any>()
        field.has("arrayValue")->field.getJSONObject("arrayValue").optJSONArray("values")?.let {array->(0 until array.length()).map {index->array.optJSONObject(index)?.let(::value)}}?:emptyList<Any>()
        field.has("nullValue")->null
        else->null
    }
    fun get(project:String,uid:String,idToken:String,readiness:Boolean=false):Map<String,Any>? {
        if(idToken.isBlank()) throw AccessFailure("UNAUTHENTICATED")
        val address=path(project,uid,readiness)
        val connection=URL(address).openConnection() as HttpsURLConnection
        connection.requestMethod="GET";connection.connectTimeout=10000;connection.readTimeout=10000;connection.instanceFollowRedirects=false
        connection.setRequestProperty("Authorization","Bearer $idToken");connection.setRequestProperty("Accept","application/json")
        connection.useCaches=false
        return try {
            val status=connection.responseCode
            val stream=if(status in 200..299) connection.inputStream else connection.errorStream
            val body=stream?.bufferedReader()?.use {reader->
                val text=StringBuilder();val buffer=CharArray(2048)
                while(text.length<=262144) {val count=reader.read(buffer);if(count<0) break;text.append(buffer,0,count)}
                if(text.length>262144) throw AccessFailure("INVALID_RESPONSE")
                text.toString()
            }.orEmpty()
            val expected="projects/$project/databases/(default)/documents/"+(if(readiness) "configuracao/equipes" else "usuarios/$uid")
            parse(status,body,expected)
        } finally {connection.disconnect()}
    }
}
