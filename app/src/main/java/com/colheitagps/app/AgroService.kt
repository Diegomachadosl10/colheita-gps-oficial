package com.colheitagps.app

import android.app.Activity
import android.content.Context
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import org.json.JSONObject
import java.net.URI
import java.net.URL
import java.util.concurrent.TimeUnit
import javax.net.ssl.HttpsURLConnection

internal object AgroService {
    const val DEFAULT_ENDPOINT="" // Never prefill production or an unpublished Beta URL.
    class ServiceError(val code: Int, message: String) : Exception(message)
    fun validEndpoint(value: String): Boolean = try {
        val uri=URI(value)
        uri.scheme=="https" && uri.host?.endsWith(".workers.dev")==true && uri.host.length>12 && uri.port in setOf(-1,443) &&
            uri.userInfo==null && uri.rawQuery==null && uri.rawFragment==null && uri.path in setOf("","/")
    } catch (_: Exception) { false }
    fun endpoint(context: Context): String {
        val document=Tasks.await(FirebaseFirestore.getInstance().document(BetaWorkspace.path("configuracao/servico")).get(Source.SERVER), 15, TimeUnit.SECONDS)
        val value=document.getString("url").orEmpty().trim().trimEnd('/')
        if (!BetaNotificationProtocol.endpoint(value)) throw ServiceError(503,"O administrador precisa configurar o endereço do Worker Beta, com beta no nome.")
        return value
    }
    private fun response(connection:HttpsURLConnection):JSONObject {
        val stream=if(connection.responseCode in 200..299) connection.inputStream else connection.errorStream
        val raw=stream?.bufferedReader()?.use {reader->
            val buffer=CharArray(4096);val text=StringBuilder()
            while(true) {val count=reader.read(buffer);if(count<0)break;require(text.length+count<=128000);text.append(buffer,0,count)}
            text.toString()
        }.orEmpty()
        return try {JSONObject(raw)} catch(_:Exception) {JSONObject()}
    }
    private fun verifyBeta(base:String,project:String) {
        val connection=URL("$base/health").openConnection() as HttpsURLConnection
        connection.requestMethod="GET";connection.connectTimeout=10000;connection.readTimeout=10000;connection.instanceFollowRedirects=false
        try {
            val result=response(connection)
            if(connection.responseCode!=200||!BetaNotificationProtocol.health(result,project))
                throw ServiceError(503,"O endereço não confirmou o ambiente Beta e o projeto do aplicativo. Nenhuma credencial do usuário foi enviada ao serviço.")
        } finally {connection.disconnect()}
    }
    fun call(context: Context, path: String, body: JSONObject): JSONObject {
        if (!BetaWorkspace.notificationsReady) throw ServiceError(503,"O serviço de avisos do Beta ainda não foi configurado. Nenhuma ação foi enviada à produção.")
        if(!BetaNotificationProtocol.supports(path)) throw ServiceError(403,"Esta ação não pertence ao serviço de avisos do Beta.")
        val user=FirebaseAuth.getInstance().currentUser ?: throw ServiceError(401,"Entre novamente.")
        val base=endpoint(context)
        val project=FirebaseApp.getInstance().options.projectId?:throw ServiceError(503,"Projeto do aplicativo indisponível.")
        verifyBeta(base,project)
        val token=Tasks.await(user.getIdToken(false),15,TimeUnit.SECONDS).token ?: throw ServiceError(401,"Entre novamente.")
        if (FirebaseAuth.getInstance().currentUser?.uid!=user.uid) throw ServiceError(401,"Sessão encerrada.")
        val connection=URL(base+BetaNotificationProtocol.PREFIX+path).openConnection() as HttpsURLConnection
        connection.requestMethod="POST"; connection.connectTimeout=15000; connection.readTimeout=30000; connection.instanceFollowRedirects=false
        connection.doOutput=true; connection.setRequestProperty("Authorization","Bearer $token"); connection.setRequestProperty("Content-Type","application/json")
        try {
            connection.outputStream.use { it.write(BetaNotificationProtocol.request(body).toString().toByteArray(Charsets.UTF_8)) }
            val status=connection.responseCode
            val result=response(connection)
            if(!BetaNotificationProtocol.envelope(result)) throw ServiceError(503,"O serviço respondeu em um formato incompatível com o Beta.")
            if(status !in 200..299) throw ServiceError(status,result.optString("message","Serviço Beta indisponível. Confira a conexão e a configuração."))
            if(result.opt("ok")!=true&&result.opt("pending")!=true) throw ServiceError(503,"O serviço Beta não confirmou o pedido.")
            return result
        } finally { connection.disconnect() }
    }
    fun async(activity: Activity, path: String, body: JSONObject, success: (JSONObject)->Unit, failure: (String)->Unit) {
        val uid=FirebaseAuth.getInstance().currentUser?.uid
        Thread {
            try { val data=call(activity.applicationContext,path,body); activity.runOnUiThread { if(!activity.isDestroyed&&FirebaseAuth.getInstance().currentUser?.uid==uid) success(data) } }
            catch(error: Exception) { activity.runOnUiThread { if(!activity.isDestroyed&&FirebaseAuth.getInstance().currentUser?.uid==uid) failure(error.message?:"Operação não concluída") } }
        }.start()
    }
}
