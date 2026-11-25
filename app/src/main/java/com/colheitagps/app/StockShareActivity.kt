package com.colheitagps.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.CheckBox
import android.widget.ImageView
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** One prepared shipment: all selected lots, with an optional local photo for each lot. */
class StockShareActivity:Activity() {
    private lateinit var ui:AccessUi
    private var access:TeamAccess?=null
    private var store:TeamStore?=null
    private var generation=0
    private var pendingLot:String?=null
    private var pendingUri:Uri?=null
    private val omitted=linkedSetOf<String>()
    private val ids get()=intent.getStringArrayListExtra("ids").orEmpty().toSet()
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState);ui=AccessUi(this)
        pendingLot=savedInstanceState?.getString("pendingLot")
        pendingUri=savedInstanceState?.getString("pendingUri")?.let(Uri::parse)
        omitted.addAll(savedInstanceState?.getStringArrayList("omitted").orEmpty())
    }
    override fun onResume() {super.onResume();load()}
    override fun onPause() {generation++;access=null;super.onPause()}
    override fun onSaveInstanceState(outState:Bundle) {
        outState.putString("pendingLot",pendingLot);outState.putString("pendingUri",pendingUri?.toString());outState.putStringArrayList("omitted",ArrayList(omitted))
        super.onSaveInstanceState(outState)
    }
    private fun notice(text:String) {if(!isDestroyed&&!isFinishing) AlertDialog.Builder(this).setMessage(text).setPositiveButton("OK",null).show()}
    private fun load() {
        val token=++generation
        ui.page("Preparar envio de estoques","Preparando os lotes…")
        AccessRepository.loadTeamAccess(this,{ a->
            if(token!=generation||isDestroyed||isFinishing) return@loadTeamAccess
            val team=intent.getStringExtra("team").orEmpty()
            if(!a.manages||(!a.primary&&a.team!=team)||ids.isEmpty()) {denied();return@loadTeamAccess}
            store=try {TeamStore(applicationContext,team)} catch(_:IllegalArgumentException) {denied();return@loadTeamAccess}
            access=a
            if(pendingUri!=null) receivePhoto() else render()
        },{if(token==generation&&!isDestroyed) denied()})
    }
    private fun denied() {
        access=null
        ui.page("Envio de estoques","Não foi possível confirmar seu acesso a esta equipe. Confira a conexão e suas permissões.").also {
            ui.button(it,"Tentar novamente") {load()};ui.button(it,"Voltar",true){finish()}
        }
    }
    private fun records():List<JSONObject> {
        val s=store?:return emptyList()
        return synchronized(s.lock) {
            val all=try {JSONArray(s.prefs("registros_v3").getString("estoques_salvos","[]"))} catch(_:Exception) {JSONArray()}
            StockBatch.selected(all,ids)
        }
    }
    private fun render() {
        val a=access?:return;val s=store?:return
        val records=records()
        val body=ui.page("Preparar envio de estoques","${TeamPolicy.label(s.team)}\nEscolha em quais lotes deseja enviar foto. Os dados seguem na mensagem; fotos e mapas escolhidos vão anexados.")
        if(records.size!=ids.size) ui.add(body,ui.text("Algum estoque selecionado foi removido. Volte e confira a seleção.",15f,ui.green,true))
        for(record in records) {
            val id=record.optString("id");val photo=StockShareFiles.photo(s.files,record)
            val card=ui.card().apply {background=ui.background(0xFFF1F6F1.toInt());elevation=0f};ui.add(body,card,14)
            ui.add(card,ui.text("Lote ${record.optString("os")}",18f,ui.dark,true),0)
            ui.add(card,ui.text("Talhões: ${record.optString("talhoes")}\nÁrea: ${record.optString("areaRestante")} ha • ${record.optString("produtividade")} t/ha",13f),6)
            if(photo!=null) {
                StockShareFiles.decode(photo,360)?.let { bitmap->
                    ui.add(card,ImageView(this).apply {setImageBitmap(bitmap);adjustViewBounds=true;maxHeight=ui.dp(120);contentDescription="Foto do lote ${record.optString("os")}"},10)
                }
                ui.add(card,CheckBox(this).apply {
                    text="Enviar foto deste lote";setTextColor(ui.dark);minHeight=ui.dp(48);isChecked=id !in omitted
                    buttonTintList=android.content.res.ColorStateList.valueOf(ui.green)
                    setOnCheckedChangeListener { _,checked->if(checked) omitted.remove(id) else omitted.add(id) }
                },4)
            } else ui.add(card,ui.text(if(record.optBoolean("anexosManuais")) "A foto não está neste celular. Você pode escolher outra ou enviar sem foto." else "Sem foto • os dados seguem na mensagem",13f),8)
            ui.button(card,if(photo==null) "Adicionar foto a este lote" else "Trocar foto deste lote",true) { choosePhoto(id) }
        }
        val enabled=records.isNotEmpty()&&records.size==ids.size&&a.manages
        ui.button(body,"Enviar mensagem e fotos") {sendStock()}.isEnabled=enabled
        ui.button(body,"Voltar",true){finish()}
    }
    private fun choosePhoto(id:String) {
        val s=store?:return;if(access?.manages!=true) return
        val maps=PaintedMapPicker.items(s)
        fun gallery() {
            pendingLot=id
            try {startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {type="image/*";addCategory(Intent.CATEGORY_OPENABLE);addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)},PHOTO)}
            catch(_:Exception) {pendingLot=null;notice("Não foi possível abrir a galeria. Tente novamente.")}
        }
        if(maps.isEmpty()) {gallery();return}
        AlertDialog.Builder(this).setTitle("Foto para este lote").setItems(arrayOf("Escolher foto da galeria","Usar mapa pintado salvo")) { _,option->
            if(option==0) gallery() else PaintedMapPicker.show(this,maps) { file->
                try {attachPhoto(id,file);omitted.remove(id);render()} catch(error:Exception) {notice(error.message?:"Não foi possível anexar o mapa.")}
            }
        }.setNegativeButton("Cancelar",null).show()
    }
    /** Modify only this lot's local attachment using the current stored record, never a stale snapshot. */
    private fun attachPhoto(id:String,file:File) {
        check(access?.manages==true)
        require(id in ids)
        val s=store?:error("Equipe indisponível")
        require(StockShareFiles.photo(s.files,JSONObject().put("fotoEstoque",file.path))!=null)
        synchronized(s.lock) {
            val prefs=s.prefs("registros_v3");val all=JSONArray(prefs.getString("estoques_salvos","[]"))
            val record=(0 until all.length()).mapNotNull {all.optJSONObject(it)}.firstOrNull {it.optString("id")==id}?:error("Este lote foi removido. Volte e confira sua seleção.")
            require(!RecordArchive.archived(record)) {"Este estoque foi excluído. Restaure antes de editar a foto."}
            record.put("fotoEstoque",file.path);check(prefs.edit().putString("estoques_salvos",all.toString()).commit())
            val active=s.prefs("controle_colheita");val progress=try {JSONObject(active.getString("progresso","{}")?:"{}")} catch(_:Exception) {JSONObject()}
            if(progress.optString("id")==id) active.edit().putString("progresso",progress.put("fotoEstoque",file.path).toString()).commit()
        }
    }
    private fun receivePhoto() {
        val uri=pendingUri?:return;val id=pendingLot?:return
        val s=store?:return;if(access?.manages!=true) return
        var copied:File?=null
        try {copied=StockShareFiles.copyImage(this,s.files,uri);attachPhoto(id,copied);omitted.remove(id)}
        catch(error:Exception) {copied?.delete();notice(error.message?:"Não foi possível adicionar a foto.")}
        try {contentResolver.releasePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)} catch(_:Exception) {}
        pendingUri=null;pendingLot=null
        render()
    }
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if(requestCode!=PHOTO) return
        if(resultCode==RESULT_OK&&data?.data!=null) {
            pendingUri=data.data
            try {contentResolver.takePersistableUriPermission(pendingUri!!,Intent.FLAG_GRANT_READ_URI_PERMISSION)} catch(_:Exception) {}
            if(access!=null) receivePhoto()
        } else {pendingLot=null;pendingUri=null;if(access!=null) render()}
    }
    private fun selectedForSharing():List<JSONObject> {
        val selected=records().map {JSONObject(it.toString()).apply {if(optString("id") in omitted) remove("fotoEstoque")}}
        require(selected.isNotEmpty()&&selected.size==ids.size&&selected.all {StockBatch.complete(it)}) {"Confira os lotes selecionados antes de enviar."}
        return selected
    }
    private fun sendStock() {
        val a=access?:return;val s=store?:return
        if(!a.manages) return
        val selected=try {selectedForSharing()} catch(error:Exception) {notice(error.message?:"Confira os lotes selecionados antes de enviar.");return}
        StockSharing.share(this,s,selected) {access?.uid==a.uid&&access?.manages==true&&store?.team==s.team}
    }
    companion object {private const val PHOTO=8810}
}
