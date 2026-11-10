package com.colheitagps.app

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.Manifest
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import org.json.JSONObject

class NotificationSettingsActivity : Activity() {
    private lateinit var ui: AccessUi
    private var access: TeamAccess?=null
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); ui=AccessUi(this); load() }
    private fun load() {
        AccessRepository.loadTeamAccess(this,{ a ->
            if(!a.manages) { finish(); return@loadTeamAccess }; access=a; render()
        }, { ui.page("Notificações","Não foi possível verificar seu acesso.").also { ui.button(it,"Voltar"){finish()} } })
    }
    private fun render() {
        val a=access ?: return
        val body=ui.page("Notificações no celular",if(a.primary) "Escolha a frente que você acompanha. Seu acesso de administrador às demais equipes permanece." else "Novos registros e solicitações de ${TeamPolicy.label(a.team!!)}.")
        if(a.primary) {
            val prefs=applicationContext.getSharedPreferences("avisos_${a.uid}",0)
            val front=ui.spinner(body,"Minha frente de notificações",TeamPolicy.fronts.values.toTypedArray())
            front.setSelection(TeamPolicy.fronts.keys.indexOf(prefs.getString("frente","santo_inacio")).coerceAtLeast(0))
            ui.button(body,"Salvar frente dos meus avisos",true) {
                prefs.edit().putString("frente",TeamPolicy.fronts.keys.elementAt(front.selectedItemPosition)).apply()
                AgroNotifications.register(this); Toast.makeText(this,"Frente de notificações salva. Ative os avisos para confirmar o registro do aparelho.",Toast.LENGTH_LONG).show()
            }
        }
        ui.add(body,ui.text(if(AgroNotifications.enabled(this,a.uid)&&AgroNotifications.allowed(this)) "Avisos autorizados neste aparelho" else "Avisos não ativados neste aparelho",17f,ui.green,true))
        ui.add(body,ui.text("O envio usa Firebase Cloud Messaging e o serviço gratuito configurado pelo administrador. A entrega depende da internet, da permissão de notificação e do Android. Mapas pessoais não geram avisos para a equipe."))
        ui.button(body,"Ativar avisos") {
            if(Build.VERSION.SDK_INT>=33&&!AgroNotifications.allowed(this)) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),71) else enable()
        }
        ui.button(body,"Desativar avisos neste aparelho",true) {
            applicationContext.getSharedPreferences("avisos_${a.uid}",0).edit().putBoolean("ativo",false).apply()
            getSystemService(android.app.NotificationManager::class.java).cancelAll(); render()
        }
        if(a.primary) {
            val url=ui.field(body,"Endereço do serviço gratuito Cloudflare")
            FirebaseFirestore.getInstance().document("configuracao/servico").get(Source.SERVER).addOnSuccessListener { if(!isDestroyed) url.setText(it.getString("url").orEmpty()) }
            ui.button(body,"Salvar endereço do serviço") {
                val value=url.text.toString().trim().trimEnd('/')
                if(!AgroService.validEndpoint(value)) { url.error="Use o endereço HTTPS do seu Worker, terminado em workers.dev"; return@button }
                FirebaseFirestore.getInstance().document("configuracao/servico").set(mapOf("url" to value))
                    .addOnSuccessListener { Toast.makeText(this,"Endereço salvo. Agora ative os avisos.",Toast.LENGTH_LONG).show() }
                    .addOnFailureListener { Toast.makeText(this,"Publique as regras desta versão e confira a conexão.",Toast.LENGTH_LONG).show() }
            }
            ui.button(body,"Abrir painel gratuito Cloudflare",true) { startActivity(Intent(Intent.ACTION_VIEW,android.net.Uri.parse("https://dash.cloudflare.com/"))) }
        }
        ui.button(body,"Voltar",true){finish()}
    }
    private fun enable() {
        val a=access ?: return
        com.google.firebase.messaging.FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            AgroService.async(this,"/devices/register",AgroNotifications.registration(this,a.uid,token),{
                applicationContext.getSharedPreferences("avisos_${a.uid}",0).edit().putBoolean("ativo",true).apply(); render()
            },{error->android.app.AlertDialog.Builder(this).setMessage(error).setPositiveButton("OK",null).show()})
        }.addOnFailureListener { Toast.makeText(this,"Não foi possível registrar este aparelho. Confira a conexão e o Google Play Services.",Toast.LENGTH_LONG).show() }
    }
    override fun onRequestPermissionsResult(code:Int,permissions:Array<out String>,results:IntArray) {
        super.onRequestPermissionsResult(code,permissions,results)
        if(code==71&&AgroNotifications.allowed(this)) enable()
    }
}
