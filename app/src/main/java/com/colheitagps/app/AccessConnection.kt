package com.colheitagps.app

import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import com.google.firebase.FirebaseApp
import org.json.JSONObject
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLException

/** Read public project configuration over the selected network. No account/password/token is sent. */
internal object AccessConnection {
    fun response(code:Int,body:String,expectedProjectNumber:String?):String {
        if(code!=200) return "O serviço respondeu HTTP $code. Não foi feita tentativa de entrada nesta verificação."
        val project=try {JSONObject(body).optString("projectId")} catch(_:Exception) {""}
        return if(project.isBlank()||(!expectedProjectNumber.isNullOrBlank()&&project!=expectedProjectNumber))
            "A rede respondeu, mas não retornou a configuração esperada do acesso. Verifique se há uma página de login ou filtro na rede."
        else "O endereço do serviço de acesso respondeu nesta rede (HTTP 200). Isso não confirma login nem o acesso aos demais serviços do Google."
    }
    fun failure(error:Exception):String=when(error) {
        is UnknownHostException->"Falha de DNS: o aplicativo não conseguiu localizar o serviço nesta rede."
        is SSLException->"Falha na conexão segura TLS com o serviço nesta rede."
        is SocketTimeoutException->"O serviço não respondeu dentro do tempo de teste nesta rede."
        is SecurityException->"O Android recusou o acesso do aplicativo à rede."
        else->"Não foi possível conectar o aplicativo ao serviço nesta rede."
    }
    fun check(context:Context):String {
        val app=context.applicationContext
        val manager=app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network=manager.activeNetwork?:return "Sem rede ativa no Android."
        val capabilities=manager.getNetworkCapabilities(network)
        val kind=when {
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_VPN)==true->"VPN"
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)==true->"Wi-Fi"
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)==true->"Dados móveis"
            else->"Outra rede"
        }
        val version=app.packageManager.getPackageInfo(app.packageName,0).versionName.orEmpty()
        val prefix="DM AGRO $version • Android API ${Build.VERSION.SDK_INT}\nRede testada: $kind\n"
        return prefix+try {
            val options=FirebaseApp.getInstance().options
            val key=options.apiKey
            val signature=if(Build.VERSION.SDK_INT>=28) {
                val info=app.packageManager.getPackageInfo(app.packageName,PackageManager.GET_SIGNING_CERTIFICATES)
                info.signingInfo?.apkContentsSigners?.firstOrNull()
            } else {
                @Suppress("DEPRECATION")
                val info=app.packageManager.getPackageInfo(app.packageName,PackageManager.GET_SIGNATURES)
                @Suppress("DEPRECATION")
                info.signatures?.firstOrNull()
            }
            val url=URL("https://identitytoolkit.googleapis.com/v1/projects?key="+URLEncoder.encode(key,"UTF-8"))
            // Bind only this public probe to the network being tested. Never switch the app's network.
            val connection=network.openConnection(url) as HttpsURLConnection
            connection.connectTimeout=12000;connection.readTimeout=12000;connection.instanceFollowRedirects=false
            connection.setRequestProperty("X-Android-Package",app.packageName)
            signature?.let {sig->connection.setRequestProperty("X-Android-Cert",MessageDigest.getInstance("SHA-1").digest(sig.toByteArray()).joinToString(""){"%02X".format(it)})}
            try {
                val code=connection.responseCode
                val body=if(code==200) connection.inputStream.bufferedReader().use {reader->
                    val result=StringBuilder();val buffer=CharArray(1024)
                    while(result.length<16000) {val count=reader.read(buffer,0,minOf(buffer.size,16000-result.length));if(count<0) break;result.append(buffer,0,count)}
                    result.toString()
                } else ""
                response(code,body,options.gcmSenderId)
            } finally {connection.disconnect()}
        } catch(error:Exception) {failure(error)}
    }
}
