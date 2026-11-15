package com.colheitagps.app

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.*
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import org.json.JSONObject
import java.util.concurrent.TimeUnit

internal object AgroNotifications {
    private const val CHANNEL="equipe_dm_agro"
    fun enabled(context: Context, uid: String) = context.applicationContext.getSharedPreferences("avisos_$uid",0).getBoolean("ativo",false)
    fun registration(context: Context, uid: String, token: String) = JSONObject().put("token",token)
        .put("front",context.applicationContext.getSharedPreferences("avisos_$uid",0).getString("frente","santo_inacio"))
    fun runtimePermission(context:Context)=Build.VERSION.SDK_INT<33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED
    fun allowed(context: Context) = runtimePermission(context) && NotificationManagerCompat.from(context).areNotificationsEnabled() &&
        context.getSystemService(NotificationManager::class.java).getNotificationChannel(CHANNEL)?.importance!=NotificationManager.IMPORTANCE_NONE
    fun status(context:Context,uid:String,message:String) {
        context.applicationContext.getSharedPreferences("avisos_$uid",0).edit().putString("ultimoResultado",message).putLong("resultadoEm",System.currentTimeMillis()).apply()
    }
    fun mayDisplay(uid: String, profile: Map<String,Any>?, sourceTeam: String, selectedFront: String): Boolean =
        sourceTeam.substringBeforeLast('_') in TeamPolicy.fronts && sourceTeam.substringAfterLast('_') in TeamPolicy.shifts &&
            TeamPolicy.manages(uid,profile) && (if(TeamPolicy.primary(uid)) sourceTeam.substringBeforeLast('_')==selectedFront else TeamPolicy.team(profile)==sourceTeam)
    fun register(context: Context) {
        val uid=FirebaseAuth.getInstance().currentUser?.uid ?: return
        if(!enabled(context,uid)||!allowed(context)) return
        FirebaseMessaging.getInstance().token.addOnSuccessListener { token -> enqueue(context,"register",registration(context,uid,token),uid) }
    }
    fun event(context: Context, kind: String, team: String = "", key: String = "", revision:Long=0, persist:Boolean=false) {
        val uid=FirebaseAuth.getInstance().currentUser?.uid ?: return
        val body=if(kind=="user") JSONObject().put("kind","user").put("uid",uid) else JSONObject().put("kind","record").put("team",team).put("key",key).apply { if(revision>0) put("revision",revision) }
        enqueue(context,"event",body,uid,persist)
    }
    fun enqueue(context: Context, action: String, body: JSONObject, uid: String, persist:Boolean=false) {
        val builder=OneTimeWorkRequestBuilder<AgroNotificationWorker>()
            .setInputData(workDataOf("action" to action,"body" to body.toString(),"uid" to uid,"created" to System.currentTimeMillis()))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .addTag("avisos-$uid")
        if(action in setOf("display","event","register")&&Build.VERSION.SDK_INT>=31) builder.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
        val manager=WorkManager.getInstance(context.applicationContext)
        if(action=="event") {
            val eventKey=java.security.MessageDigest.getInstance("SHA-256").digest(body.toString().toByteArray()).joinToString("") { "%02x".format(it) }
            val operation=manager.enqueueUniqueWork("aviso-evento-$uid-$eventKey",ExistingWorkPolicy.KEEP,builder.build())
            if(persist) operation.result.get(5,TimeUnit.SECONDS)
        } else manager.enqueue(builder.build())
    }
    fun offer(activity: Activity, access: TeamAccess) {
        if(!access.manages) return
        register(activity)
        val prefs=activity.applicationContext.getSharedPreferences("avisos_${access.uid}",0)
        if(prefs.getBoolean("perguntou",false)) return
        prefs.edit().putBoolean("perguntou",true).apply()
        AlertDialog.Builder(activity).setTitle("Avisos da equipe")
            .setMessage(if(access.primary) "Receber avisos de Santo Inácio? Você pode escolher sua frente de notificações na próxima tela." else "Receber avisos de novos registros e solicitações da sua frente e turno?")
            .setNegativeButton("Agora não",null).setPositiveButton("Configurar") { _,_->activity.startActivity(Intent(activity,NotificationSettingsActivity::class.java)) }.show()
    }
    fun logout(context: Context) {
        val uid=FirebaseAuth.getInstance().currentUser?.uid ?: return
        context.getSystemService(NotificationManager::class.java).cancelAll()
        WorkManager.getInstance(context.applicationContext).cancelAllWorkByTag("avisos-$uid")
        if(enabled(context,uid)) FirebaseMessaging.getInstance().deleteToken()
    }
    fun tapIntent(context:Context,uid:String,data:JSONObject)=Intent(context,MainActivity::class.java)
        .putExtra("equipeAlerta",data.optString("team")).putExtra("eventoAlerta",data.optString("eventId")).putExtra("usuarioAlerta",uid)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    fun display(context: Context, uid: String, data: JSONObject) {
        if(!allowed(context)||!enabled(context,uid)) return
        val event=data.optString("eventId"); if(event.isBlank()||event.length>160) return
        val prefs=context.getSharedPreferences("avisos_recebidos_$uid",0)
        if(prefs.contains(event)) return
        val manager=context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL,"Novidades da equipe",NotificationManager.IMPORTANCE_DEFAULT))
        val intent=tapIntent(context,uid,data)
        val pending=PendingIntent.getActivity(context,(uid+event).hashCode(),intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification=NotificationCompat.Builder(context,CHANNEL).setSmallIcon(R.drawable.ic_agro_notification)
            .setContentTitle(data.optString("title","Novidade na equipe").take(100)).setContentText(data.optString("body").take(200))
            .setStyle(NotificationCompat.BigTextStyle().bigText(data.optString("body").take(200)))
            .setContentIntent(pending).setAutoCancel(true).setCategory(NotificationCompat.CATEGORY_EVENT).build()
        if(FirebaseAuth.getInstance().currentUser?.uid!=uid) return
        manager.notify((uid+event).hashCode(),notification)
        context.getSharedPreferences("avisos_$uid",0).edit().putLong("recebidoEm",System.currentTimeMillis()).apply()
        val edit=prefs.edit().putLong(event,System.currentTimeMillis())
        for((key,value) in prefs.all) if(value is Long && value<System.currentTimeMillis()-7*86400000L) edit.remove(key)
        edit.apply()
    }
}

class AgroMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        val uid=FirebaseAuth.getInstance().currentUser?.uid ?: return
        if(AgroNotifications.enabled(this,uid)) AgroNotifications.enqueue(this,"register",AgroNotifications.registration(this,uid,token),uid)
    }
    override fun onMessageReceived(message: RemoteMessage) {
        val uid=FirebaseAuth.getInstance().currentUser?.uid ?: return
        if(message.data["targetUid"]==uid) AgroNotifications.enqueue(this,"display",JSONObject(message.data),uid)
    }
}

class AgroNotificationWorker(context: Context,params: WorkerParameters) : Worker(context,params) {
    override fun doWork(): Result {
        val uid=inputData.getString("uid") ?: return Result.success()
        if(FirebaseAuth.getInstance().currentUser?.uid!=uid) return Result.success()
        val body=try { JSONObject(inputData.getString("body")?:"{}") } catch(_:Exception){return Result.failure()}
        return try {
            when(inputData.getString("action")) {
                "register" -> {
                    if(!AgroNotifications.enabled(applicationContext,uid)) return Result.success()
                    AgroService.call(applicationContext,"/devices/register",AgroNotifications.registration(applicationContext,uid,body.getString("token")))
                    Result.success()
                }
                "event" -> {
                    val data=AgroService.call(applicationContext,"/events/notify",body)
                    AgroNotifications.status(applicationContext,uid,if(data.optBoolean("pending")) "Envio de aviso na fila do serviço" else "Evento aceito pelo serviço de avisos")
                    if(data.optBoolean("pending")) Result.retry() else Result.success()
                }
                "display" -> {
                    if(System.currentTimeMillis()-inputData.getLong("created",0)>3600000) return Result.success()
                    val profile=Tasks.await(FirebaseFirestore.getInstance().document("usuarios/$uid").get(Source.SERVER),15,TimeUnit.SECONDS).data
                    val front=applicationContext.getSharedPreferences("avisos_$uid",0).getString("frente","santo_inacio").orEmpty()
                    if(AgroNotifications.mayDisplay(uid,profile,body.optString("team"),front))
                        AgroNotifications.display(applicationContext,uid,body)
                    Result.success()
                }
                else -> Result.failure()
            }
        } catch(error: AgroService.ServiceError) {
            AgroNotifications.status(applicationContext,uid,"Avisos: ${error.message}")
            if(error.code in setOf(400,401,403,404)) Result.success() else if(runAttemptCount<8) Result.retry() else Result.failure()
        } catch(_:Exception) {
            AgroNotifications.status(applicationContext,uid,"Não foi possível concluir o aviso; confira a conexão e tente o teste de notificação.")
            if(runAttemptCount<8) Result.retry() else Result.failure()
        }
    }
}
