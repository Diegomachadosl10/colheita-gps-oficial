package com.colheitagps.app

import android.app.Activity
import android.content.Context
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import org.json.JSONObject
import java.net.URI
import java.net.URL
import java.util.concurrent.TimeUnit
import javax.net.ssl.HttpsURLConnection

internal object AgroService {
    const val DEFAULT_ENDPOINT="https://dm-agro-servico.dieguiin-msl.workers.dev"
    class ServiceError(val code: Int, message: String) : Exception(message)
    fun validEndpoint(value: String): Boolean = try {
        val uri=URI(value)
        uri.scheme=="https" && uri.host?.endsWith(".workers.dev")==true && uri.host.length>12 && uri.port in setOf(-1,443) &&
            uri.userInfo==null && uri.rawQuery==null && uri.rawFragment==null && uri.path in setOf("","/")
    } catch (_: Exception) { false }
    fun endpoint(context: Context): String {
        val document=Tasks.await(FirebaseFirestore.getInstance().document("configuracao/servico").get(Source.SERVER), 15, TimeUnit.SECONDS)
        val value=document.getString("url").orEmpty().trim().trimEnd('/')
        if (!validEndpoint(value)) throw ServiceError(503,"O administrador precisa configurar o serviço gratuito da Cloudflare em Notificações.")
        return value
    }
    fun call(context: Context, path: String, body: JSONObject): JSONObject {
        val user=FirebaseAuth.getInstance().currentUser ?: throw ServiceError(401,"Entre novamente.")
        val base=endpoint(context)
        val token=Tasks.await(user.getIdToken(true),15,TimeUnit.SECONDS).token ?: throw ServiceError(401,"Entre novamente.")
        if (FirebaseAuth.getInstance().currentUser?.uid!=user.uid) throw ServiceError(401,"Sessão encerrada.")
        val connection=URL(base+path).openConnection() as HttpsURLConnection
        connection.requestMethod="POST"; connection.connectTimeout=15000; connection.readTimeout=30000; connection.instanceFollowRedirects=false
        connection.doOutput=true; connection.setRequestProperty("Authorization","Bearer $token"); connection.setRequestProperty("Content-Type","application/json")
        try {
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val status=connection.responseCode
            val stream=if (status in 200..299) connection.inputStream else connection.errorStream
            val text=stream?.bufferedReader()?.use { reader ->
                val buffer=CharArray(4096); val result=StringBuilder()
                while (true) { val count=reader.read(buffer); if(count<0) break; require(result.length+count<=128000); result.append(buffer,0,count) }
                result.toString()
            }.orEmpty()
            val result=try { JSONObject(text) } catch (_: Exception) { JSONObject() }
            if (status !in 200..299) throw ServiceError(status,if(status==404&&path=="/devices/test")
                "O serviço Cloudflare ainda está na versão anterior. O administrador precisa publicar o Worker 1.4.0 para liberar o teste de entrega."
                else result.optString("message","Serviço indisponível. Confira a conexão e a configuração."))
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
