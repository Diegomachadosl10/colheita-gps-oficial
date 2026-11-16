package com.colheitagps.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.view.Gravity
import android.widget.*

/** Preview the exact prepared payload; the chooser opens only after confirmation. */
internal object SharePreview {
    @Suppress("DEPRECATION")
    fun review(activity:Activity,intent:Intent,allowed:()->Boolean,send:()->Unit) {
        if(activity.isFinishing||activity.isDestroyed||!allowed()) return
        val ui=AccessUi(activity,false)
        val body=LinearLayout(activity).apply {orientation=LinearLayout.VERTICAL;setPadding(ui.dp(18),ui.dp(8),ui.dp(18),ui.dp(16))}
        val uris=if(intent.action==Intent.ACTION_SEND_MULTIPLE) intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
            else listOfNotNull(intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
        var index=0;var bitmap:Bitmap?=null
        val counter=ui.text("",14f,ui.dark,true)
        val image=ImageView(activity).apply {adjustViewBounds=true;maxHeight=ui.dp(300);contentDescription="Prévia da imagem para compartilhar"}
        fun render() {
            image.setImageDrawable(null);bitmap?.recycle();bitmap=null
            if(uris.isEmpty()) return
            counter.text="Imagem ${index+1} de ${uris.size}"
            try {
                val options=BitmapFactory.Options().apply {inJustDecodeBounds=true}
                activity.contentResolver.openInputStream(uris[index])?.use {BitmapFactory.decodeStream(it,null,options)}
                var sample=1;while(maxOf(options.outWidth,options.outHeight)/sample>1000) sample*=2
                bitmap=activity.contentResolver.openInputStream(uris[index])?.use {BitmapFactory.decodeStream(it,null,BitmapFactory.Options().apply {inSampleSize=sample})}
                image.setImageBitmap(bitmap)
                if(bitmap==null) counter.text="Imagem ${index+1} de ${uris.size} • prévia indisponível"
            } catch(_:Exception) {counter.text="Imagem ${index+1} de ${uris.size} • prévia indisponível"}
        }
        if(uris.isNotEmpty()) {
            ui.add(body,counter,0);ui.add(body,image,8)
            if(uris.size>1) {
                val navigation=LinearLayout(activity).apply {gravity=Gravity.CENTER;orientation=LinearLayout.HORIZONTAL}
                for((label,delta) in listOf("‹ Anterior" to -1,"Próxima ›" to 1)) navigation.addView(Button(activity).apply {
                    text=label;AppTheme.action(this);setOnClickListener {index=(index+delta+uris.size)%uris.size;render()}
                },LinearLayout.LayoutParams(0,-2,1f).apply {marginEnd=ui.dp(6)})
                ui.add(body,navigation,8)
            }
            render()
        }
        val text=intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        if(text.isNotBlank()) ui.add(body,ui.text(text,15f,ui.dark).apply {setTextIsSelectable(true)},12)
        val dialog=AlertDialog.Builder(activity).setTitle("Conferir envio").setView(ScrollView(activity).apply {addView(body)})
            .setNegativeButton("Voltar",null).setPositiveButton("Enviar") {_,_->if(allowed()&&!activity.isFinishing&&!activity.isDestroyed) send()}.create()
        dialog.setOnDismissListener {image.setImageDrawable(null);bitmap?.recycle();bitmap=null};dialog.show()
    }
}
