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
    private var serviceField:android.widget.EditText?=null
    private var frontField:android.widget.Spinner?=null
    private var activating=false
    private var activateButton:android.widget.Button?=null
    private fun busy(value:Boolean,label:String="Ativando avisos…") {
        activating=value
        activateButton?.apply { isEnabled=!value; text=if(value) label else "Ativar avisos" }
    }
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); ui=AccessUi(this); load() }
    override fun onResume() { super.onResume(); if(access!=null&&!activating) render() }
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
            frontField=front
            front.setSelection(TeamPolicy.fronts.keys.indexOf(prefs.getString("frente","santo_inacio")).coerceAtLeast(0))
            ui.button(body,"Salvar frente dos meus avisos",true) {
                prefs.edit().putString("frente",TeamPolicy.fronts.keys.elementAt(front.selectedItemPosition)).apply()
                AgroNotifications.register(this); Toast.makeText(this,"Frente de notificações salva. Ative os avisos para confirmar o registro do aparelho.",Toast.LENGTH_LONG).show()
            }
        }
        val prefs=applicationContext.getSharedPreferences("avisos_${a.uid}",0)
        ui.add(body,ui.text(if(AgroNotifications.enabled(this,a.uid)&&AgroNotifications.allowed(this)) "Aparelho registrado • teste o recebimento" else "Avisos não ativados ou bloqueados no Android",17f,ui.green,true))
        fun date(value:Long)=java.text.SimpleDateFormat("dd/MM HH:mm",java.util.Locale("pt","BR")).format(java.util.Date(value))
        val received=prefs.getLong("recebidoEm",0)
        ui.add(body,ui.text(if(received>0) "Último aviso exibido: ${date(received)}" else "Nenhum aviso recebido e exibido neste aparelho ainda.",14f))
        prefs.getString("ultimoResultado",null)?.let { message->ui.add(body,ui.text(message,13f)) }
        ui.add(body,ui.text("O envio usa Firebase Cloud Messaging e o serviço gratuito configurado pelo administrador. A entrega depende da internet, da permissão de notificação e do Android. Mapas pessoais não geram avisos para a equipe."))
        activateButton=ui.button(body,"Ativar avisos") {
            prepareActivation()
        }
        ui.button(body,"Testar notificação neste celular",true) {
            if(activating) return@button
            if(!AgroNotifications.enabled(this,a.uid)||!AgroNotifications.allowed(this)) {
                android.app.AlertDialog.Builder(this).setMessage("Ative os avisos e confira a permissão no Android antes de testar.").setPositiveButton("OK",null).show()
                return@button
            }
            busy(true,"Enviando teste…")
            com.google.firebase.messaging.FirebaseMessaging.getInstance().token.addOnSuccessListener { token->
                AgroService.async(this,"/devices/test",JSONObject().put("token",token),{ response->
                    busy(false)
                    AgroNotifications.status(this,a.uid,"Teste aceito pelo Firebase; confira se a notificação chegou.")
                    android.app.AlertDialog.Builder(this).setMessage(response.optString("message")).setPositiveButton("Entendi") { _,_->render() }.show()
                },{error->busy(false);AgroNotifications.status(this,a.uid,error);android.app.AlertDialog.Builder(this).setMessage(error).setPositiveButton("OK",null).show()})
            }.addOnFailureListener { busy(false);Toast.makeText(this,"Não foi possível obter o registro do aparelho. Confira a internet e o Google Play Services.",Toast.LENGTH_LONG).show() }
        }
        ui.button(body,"Atualizar diagnóstico",true) { render() }
        ui.button(body,"Abrir notificações do Android",true) {
            try { startActivity(Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE,packageName)) }
            catch(_:Exception) { startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:$packageName"))) }
        }
        ui.button(body,"Desativar avisos neste aparelho",true) {
            applicationContext.getSharedPreferences("avisos_${a.uid}",0).edit().putBoolean("ativo",false).apply()
            getSystemService(android.app.NotificationManager::class.java).cancelAll(); render()
        }
        if(a.primary) {
            val url=ui.field(body,"Endereço do serviço gratuito Cloudflare")
            serviceField=url; url.setText(AgroService.DEFAULT_ENDPOINT)
            FirebaseFirestore.getInstance().document("configuracao/servico").get(Source.SERVER).addOnSuccessListener {
                if(!isDestroyed&&it.getString("url")?.let(AgroService::validEndpoint)==true) url.setText(it.getString("url"))
            }
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
    private fun prepareActivation() {
        val a=access ?: return
        if(activating) return
        fun permission() {
            if(Build.VERSION.SDK_INT>=33&&!AgroNotifications.runtimePermission(this)) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),71)
            else if(!AgroNotifications.allowed(this)) android.app.AlertDialog.Builder(this).setMessage("Os avisos estão bloqueados nas configurações do Android. Toque em Abrir notificações do Android e permita os avisos do DM AGRO.").setPositiveButton("Entendi",null).show()
            else enable()
        }
        if(!a.primary) { permission(); return }
        val value=serviceField?.text?.toString()?.trim()?.trimEnd('/').orEmpty()
        if(!AgroService.validEndpoint(value)) { serviceField?.error="Informe o endereço HTTPS do serviço"; return }
        busy(true,"Salvando serviço…")
        FirebaseFirestore.getInstance().document("configuracao/servico").set(mapOf("url" to value))
            .addOnSuccessListener {
                busy(false)
                if(!isDestroyed) {
                    frontField?.selectedItemPosition?.let { index->applicationContext.getSharedPreferences("avisos_${a.uid}",0).edit().putString("frente",TeamPolicy.fronts.keys.elementAt(index)).apply() }
                    permission()
                }
            }.addOnFailureListener { busy(false); Toast.makeText(this,"Não foi possível salvar o serviço. Confira a conexão e as regras publicadas.",Toast.LENGTH_LONG).show() }
    }
    private fun enable() {
        val a=access ?: return
        busy(true)
        com.google.firebase.messaging.FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            AgroService.async(this,"/devices/register",AgroNotifications.registration(this,a.uid,token),{
                applicationContext.getSharedPreferences("avisos_${a.uid}",0).edit().putBoolean("ativo",true).apply(); busy(false); render()
            },{error->busy(false);android.app.AlertDialog.Builder(this).setMessage(error).setPositiveButton("OK",null).show()})
        }.addOnFailureListener { busy(false); Toast.makeText(this,"Não foi possível registrar este aparelho. Confira a conexão e o Google Play Services.",Toast.LENGTH_LONG).show() }
    }
    override fun onRequestPermissionsResult(code:Int,permissions:Array<out String>,results:IntArray) {
        super.onRequestPermissionsResult(code,permissions,results)
        if(code==71&&AgroNotifications.allowed(this)) enable()
    }
}
