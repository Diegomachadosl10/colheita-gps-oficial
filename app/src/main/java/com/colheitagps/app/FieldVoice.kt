package com.colheitagps.app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.*
import android.speech.*
import android.speech.tts.*
import android.view.*
import android.widget.*
import java.util.Locale

/** Foreground-only microphone, explicit opt-in, and a conversation that never writes by itself. */
internal class FieldVoice(private val activity:Activity,private val scope:()->String,private val authorized:()->Boolean,
    private val home:()->Boolean,private val prepare:(VoiceCommands.Draft,FieldVoice)->Unit,private val status:(String)->Unit) {
    data class Choice(val label:String,val action:()->Unit)
    private val handler=Handler(Looper.getMainLooper())
    private var recognizer:SpeechRecognizer?=null
    private var recognitionGeneration=0L
    private var listening=false;private var resumed=true;private var retries=0;private var speaking=false
    private var tts:TextToSpeech?=null;private var ttsReady=false
    private var pendingSpeech=""
    private var utterance=0L
    private var requestedEnable=false;private var permissionScope=""
    private var dialog:AlertDialog?=null
    private var capturedScope=""
    private var draft:VoiceCommands.Draft?=null
    private var waiting=""
    private var confirm:(()->Unit)?=null
    private var choices:List<Choice> = emptyList()
    private var message:TextView?=null;private var transcript:TextView?=null;private var choiceColumn:LinearLayout?=null
    private var image:ImageView?=null;private var bitmap:Bitmap?=null
    private var onImageTap:((Float,Float)->Unit)?=null
    private val preference get()=activity.applicationContext.getSharedPreferences("voz_"+java.security.MessageDigest.getInstance("SHA-256").digest(scope().toByteArray()).joinToString("") {"%02x".format(it)},0)
    val enabled get()=preference.getBoolean("inicio",false)
    val opened get()=dialog?.isShowing==true
    fun isCurrent(value:VoiceCommands.Draft)=draft===value&&scope()==capturedScope&&authorized()&&!activity.isFinishing&&!activity.isDestroyed
    private fun permission()=activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED
    fun enable() {
        if(!permission()) {requestedEnable=true;permissionScope=scope();activity.requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO),905);return}
        preference.edit().putBoolean("inicio",true).apply();retries=0;sync()
    }
    fun disable() {preference.edit().putBoolean("inicio",false).apply();stop();status("Voz desativada")}
    fun permissionResult(granted:Boolean) {
        if(permissionScope!=scope()||!authorized()) return
        if(granted) {if(requestedEnable) enable() else retry()} else status("Microfone não autorizado • comandos por texto disponíveis")
        requestedEnable=false
    }
    fun resume() {resumed=true;retries=0;sync()}
    fun pause() {resumed=false;stop();tts?.stop();speaking=false}
    fun sync() {handler.removeCallbacks(restart);if(listening&&!eligible()) stop();if(resumed&&authorized()&&((opened)||(home()&&enabled))) handler.postDelayed(restart,500) else stop()}
    private val restart=Runnable {listen()}
    private fun eligible()=resumed&&authorized()&&!activity.isFinishing&&!activity.isDestroyed&&
        (if(opened) scope()==capturedScope&&dialog?.window?.decorView?.hasWindowFocus()==true else home()&&enabled&&activity.hasWindowFocus())
    private fun listen() {
        if(listening||speaking||tts?.isSpeaking==true||!eligible()||!permission()||retries>=3) return
        if(!SpeechRecognizer.isRecognitionAvailable(activity)) {status("Comandos por texto • reconhecimento de voz indisponível");return}
        try {
            val generation=++recognitionGeneration
            recognizer?.destroy();recognizer=null
            if(recognizer==null) {
                recognizer=SpeechRecognizer.createSpeechRecognizer(activity).apply {setRecognitionListener(object:RecognitionListener {
                    override fun onReadyForSpeech(params:Bundle?) {if(generation==recognitionGeneration) status(if(opened) "Ouvindo sua resposta…" else "Ouvindo • diga DM AGRO")}
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB:Float) {}
                    override fun onBufferReceived(buffer:ByteArray?) {}
                    override fun onEndOfSpeech() {if(generation==recognitionGeneration) status("Entendendo a fala…")}
                    override fun onError(error:Int) {
                        if(generation!=recognitionGeneration) return
                        listening=false
                        if(!eligible()) return
                        retries++
                        if(error==SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS||retries>=3) status("Voz pausada • toque no microfone para ouvir novamente")
                        else {status(if(error==SpeechRecognizer.ERROR_NETWORK||error==SpeechRecognizer.ERROR_NETWORK_TIMEOUT) "Confira a conexão do reconhecimento de voz" else "Aguardando sua fala…");handler.postDelayed(restart,1500)}
                    }
                    override fun onResults(results:Bundle?) {
                        if(generation!=recognitionGeneration) return
                        listening=false;if(!eligible()) return
                        val text=results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                        retries=0
                        if(text.isNotBlank()) {if(opened) receive(text) else if(VoiceCommands.wakes(text)) open(text)}
                        if(eligible()&&!speaking) handler.postDelayed(restart,800)
                    }
                    override fun onPartialResults(partialResults:Bundle?) {}
                    override fun onEvent(eventType:Int,params:Bundle?) {}
                })}
            }
            listening=true
            recognizer!!.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE,"pt-BR").putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,3).putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,false))
        } catch(_:Exception) {listening=false;status("Não foi possível ouvir • use o comando por texto")}
    }
    private fun stop() {handler.removeCallbacks(restart);listening=false;recognitionGeneration++;try {recognizer?.cancel();recognizer?.destroy()} catch(_:Exception) {} finally {recognizer=null}}
    fun retry() {retries=0;if(!permission()) {requestedEnable=false;permissionScope=scope();activity.requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO),905)} else {stop();handler.postDelayed(restart,200)}}
    fun open(initial:String?=null) {
        if(!authorized()) return
        if(opened) {initial?.let(::receive);return}
        stop();capturedScope=scope();draft=null;waiting="";confirm=null;choices=emptyList()
        val ui=AccessUi(activity,false)
        val body=LinearLayout(activity).apply {orientation=LinearLayout.VERTICAL;setPadding(ui.dp(16),ui.dp(8),ui.dp(16),ui.dp(16))}
        transcript=ui.text("",13f).also {ui.add(body,it,0)}
        message=ui.text("Fale naturalmente. Ex.: DM AGRO, caminhão 10880 chegou agora. Você também pode escrever o comando abaixo.",16f,ui.dark).also {ui.add(body,it,8)}
        image=ImageView(activity).apply {adjustViewBounds=true;maxHeight=ui.dp(320);contentDescription="Prévia das curvas do mapa";visibility=View.GONE}
        image!!.setOnTouchListener {view,event->
            if(event.action==MotionEvent.ACTION_UP&&bitmap!=null&&onImageTap!=null) {
                val b=bitmap!!;val scale=minOf(view.width.toFloat()/b.width,view.height.toFloat()/b.height)
                val left=(view.width-b.width*scale)/2;val top=(view.height-b.height*scale)/2
                val x=(event.x-left)/(b.width*scale);val y=(event.y-top)/(b.height*scale)
                if(x in 0f..1f&&y in 0f..1f) onImageTap?.invoke(x,y)
                view.performClick();true
            } else onImageTap!=null
        };ui.add(body,image!!,10)
        choiceColumn=LinearLayout(activity).apply {orientation=LinearLayout.VERTICAL};ui.add(body,choiceColumn!!,8)
        val input=EditText(activity).apply {hint="Falar ou escrever um comando / correção";AppTheme.field(this);setSingleLine(false)};ui.add(body,input,10)
        val row=LinearLayout(activity).apply {orientation=LinearLayout.HORIZONTAL}
        row.addView(Button(activity).apply {text="🎙 Ouvir";AppTheme.action(this);setOnClickListener {retry()}},LinearLayout.LayoutParams(0,-2,1f))
        row.addView(Button(activity).apply {text="Interpretar";AppTheme.action(this);setOnClickListener {if(input.text.isNotBlank()) {val text=input.text.toString();input.setText("");receive(text)}}},LinearLayout.LayoutParams(0,-2,1f).apply {marginStart=ui.dp(8)})
        ui.add(body,row,8)
        val created=AlertDialog.Builder(activity).setTitle("Comandos do DM AGRO").setView(ScrollView(activity).apply {addView(body)})
            .setNegativeButton("Cancelar") {_,_->close()}.setNeutralButton("Desligar voz") {_,_->disable();close()}.setPositiveButton("Confirmar",null).create()
        dialog=created;created.setOnShowListener {created.getButton(AlertDialog.BUTTON_POSITIVE).apply {isEnabled=false;setOnClickListener {execute()}};created.window?.decorView?.viewTreeObserver?.addOnWindowFocusChangeListener {sync()};retries=0;sync()}
        created.setOnDismissListener {stop();tts?.stop();speaking=false;image?.setImageDrawable(null);bitmap?.recycle();bitmap=null;dialog=null;draft=null;confirm=null;choices=emptyList();onImageTap=null;status(if(enabled) "Voz ativada • diga DM AGRO no início" else "Voz desativada");sync()}
        created.show()
        if(initial!=null) receive(initial)
    }
    fun receive(text:String) {
        if(!opened||scope()!=capturedScope||!authorized()) {close();return}
        stop();tts?.stop();speaking=false;retries=0;transcript?.text="Você disse: $text"
        if(VoiceCommands.cancels(text)) {close();return}
        if(VoiceCommands.confirms(text)&&confirm!=null) {execute();return}
        val ordinal=VoiceCommands.ordinal(text)
        if(ordinal!=null&&ordinal in choices.indices) {choices[ordinal].action();return}
        if(choices.isNotEmpty()) {
            fun words(value:String)=Regex("[a-z]+|[0-9]+").findAll(VoiceCommands.numbers(value)).map {it.value.trimStart('0').ifBlank {"0"}}.filter {it !in setOf("a","o","as","os","de","do","da","dos","das","e","essa","esse","aquela","aquele","foi","era","por","para","com","quero")}.toSet()
            val said=words(text)
            val found=choices.filter {said.isNotEmpty()&&words(it.label).containsAll(said)}
            if(found.size==1) {found.single().action();return}
            if(!VoiceCommands.wakes(text)&&!Regex("^(?:nao|corrig|na verdade|mude|troqu|coloc|abr|ir para)").containsMatchIn(VoiceCommands.norm(text).trim())) {
                speak("Qual opção? Diga primeira, segunda ou escolha na tela.");return
            }
        }
        val parsed=VoiceCommands.parse(text,draft,waiting)
        draft=parsed;confirm=null;choices=emptyList();dialog?.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled=false
        prepare(parsed,this)
    }
    fun replace(value:VoiceCommands.Draft,fields:org.json.JSONObject) {if(isCurrent(value)) {val next=value.copy(fields=VoiceCommands.invalidate(value,fields));draft=next;prepare(next,this)}}
    fun show(value:VoiceCommands.Draft,text:String,missing:String="",options:List<Choice> = emptyList(),preview:Bitmap?=null,
             imageTap:((Float,Float)->Unit)?=null,action:(()->Unit)?=null) {
        if(!isCurrent(value)) {preview?.recycle();return}
        waiting=missing;confirm=action;choices=options;message?.text=text
        image?.setImageDrawable(null);bitmap?.recycle();bitmap=preview;onImageTap=imageTap
        image?.visibility=if(preview==null) View.GONE else View.VISIBLE;image?.setImageBitmap(preview)
        choiceColumn?.removeAllViews();for((i,option) in options.withIndex()) choiceColumn?.addView(Button(activity).apply {this.text="${i+1}. ${option.label}";AppTheme.action(this);setOnClickListener {if(isCurrent(value)) option.action()}})
        dialog?.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled=action!=null
        speak(if(options.isNotEmpty()) "${options.size} opções encontradas. Diga primeira, segunda ou escolha na tela." else text.take(230))
    }
    private fun execute() {val action=confirm?:return;if(!authorized()||scope()!=capturedScope) {close();return};confirm=null;close();action()}
    private fun speak(text:String) {
        stop()
        if(!resumed||!authorized()||!opened) return
        pendingSpeech=text
        if(tts==null) {
            tts=TextToSpeech(activity.applicationContext) {status->activity.runOnUiThread {ttsReady=status==TextToSpeech.SUCCESS;tts?.language=Locale("pt","BR");if(ttsReady&&opened) speak(pendingSpeech) else sync()}}
            return
        }
        if(!ttsReady) {sync();return}
        speaking=true
        val current=(++utterance).toString()
        tts!!.setOnUtteranceProgressListener(object:UtteranceProgressListener() {
            override fun onStart(id:String?) {}
            override fun onDone(id:String?) {handler.post {if(id==current) {speaking=false;sync()}}}
            @Deprecated("Legacy TTS callback") override fun onError(id:String?) {handler.post {if(id==current) {speaking=false;sync()}}}
        })
        if(tts!!.speak(text,TextToSpeech.QUEUE_FLUSH,null,current)!=TextToSpeech.SUCCESS) {speaking=false;sync()}
    }
    fun close() {dialog?.dismiss();stop()}
    fun destroy() {resumed=false;close();recognizer?.destroy();recognizer=null;tts?.shutdown();tts=null;handler.removeCallbacksAndMessages(null)}
}
