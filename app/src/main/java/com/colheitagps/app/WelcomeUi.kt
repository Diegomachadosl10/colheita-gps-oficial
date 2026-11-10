package com.colheitagps.app

import android.app.Activity
import android.app.AlertDialog
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.*

internal object WelcomeUi {
    fun render(activity:Activity,login:()->Unit,register:()->Unit) {
        val ui=AccessUi(activity,attach=false)
        val root=LinearLayout(activity).apply {
            orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER_HORIZONTAL
            setPadding(ui.dp(24),ui.dp(20),ui.dp(24),ui.dp(24))
            background=GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(0xFF0C3023.toInt(),0xFF14613C.toInt()))
        }
        activity.setContentView(ScrollView(activity).apply { isFillViewport=true;fitsSystemWindows=true;addView(root) })
        root.addView(FieldLandscape(activity),LinearLayout.LayoutParams(-1,ui.dp(132)))
        ui.add(root,ui.text("DM AGRO",38f,Color.WHITE,true).apply { gravity=Gravity.CENTER },8)
        ui.add(root,ui.text("COLHEITA GPS",16f,0xFFD3F2CC.toInt(),true).apply { gravity=Gravity.CENTER },0)
        ui.add(root,ui.text("Sua equipe conectada no campo.",15f,Color.WHITE).apply { gravity=Gravity.CENTER },10)
        val verse=DailyVerse.forDate()
        val reading=ui.card().apply { background=ui.background(0xFFE2F4D9.toInt(),20);elevation=0f }
        ui.add(root,reading,24)
        ui.add(reading,ui.text("VERSÍCULO DO DIA",12f,ui.green,true),0)
        ui.add(reading,ui.text(verse.text,19f,ui.dark).apply { setTextIsSelectable(true) },10)
        ui.add(reading,ui.text(verse.reference,14f,ui.green,true),8)
        ui.button(root,"Entrar") { login() }.apply { background=ui.background(0xFFB9E99E.toInt(),16);setTextColor(ui.dark) }
        ui.button(root,"Solicitar acesso",true) { register() }.apply {
            background=ui.background(Color.TRANSPARENT,16).apply { setStroke(ui.dp(1),0xFFD3F2CC.toInt()) };setTextColor(Color.WHITE)
        }
        ui.button(root,"Sobre o aplicativo",true) {
            AlertDialog.Builder(activity).setTitle("DM AGRO — Colheita GPS")
                .setMessage("Gestão da colheita com mapas e GPS, pendências, peças, estoque, chuva e relatórios.\n\nEntre com matrícula e senha ou solicite seu acesso. A liberação depende da aprovação do responsável.\n\nCriado por Diego Machado.")
                .setPositiveButton("Entendi",null).show()
        }.apply { background=ui.background(Color.TRANSPARENT);setTextColor(0xFFD3F2CC.toInt()) }
        ui.add(root,ui.text("Criado por Diego Machado",12f,0xFFD3F2CC.toInt()).apply { gravity=Gravity.CENTER },12)
    }
}

/** Original vector landscape: no download or third-party image service at app startup. */
internal class FieldLandscape(context:android.content.Context):View(context) {
    private val ink=Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(canvas:Canvas) {
        super.onDraw(canvas);canvas.save();canvas.scale(width/360f,height/132f)
        ink.color=0x22B9E99E;ink.style=Paint.Style.FILL;canvas.drawCircle(288f,35f,27f,ink)
        ink.color=0xFFB9E99E.toInt();ink.style=Paint.Style.STROKE;ink.strokeWidth=1.8f
        val horizon=Path().apply { moveTo(0f,80f);cubicTo(90f,60f,210f,110f,360f,73f) };canvas.drawPath(horizon,ink)
        for(i in -3..6) { val path=Path().apply { moveTo(170f+i*9,83f);quadTo(160f+i*20,110f,140f+i*57,132f) };canvas.drawPath(path,ink) }
        canvas.save();canvas.translate(35f,12f);canvas.scale(1.3f,1.3f)
        ink.strokeWidth=2.5f;canvas.drawCircle(25f,42f,13f,ink);canvas.drawCircle(25f,42f,6f,ink);canvas.drawCircle(69f,47f,8f,ink)
        canvas.drawLine(14f,4f,40f,4f,ink);canvas.drawLine(21f,5f,25f,25f,ink);canvas.drawRect(27f,9f,46f,28f,ink)
        canvas.drawLine(46f,10f,51f,41f,ink);canvas.drawLine(51f,41f,61f,41f,ink);canvas.drawLine(46f,27f,66f,27f,ink);canvas.drawLine(66f,27f,69f,38f,ink)
        canvas.restore();canvas.restore()
    }
}
