package com.colheitagps.app

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.view.Gravity
import android.view.View
import android.widget.*

/** Shared native controls, sized in dp and with explicit colors on every Android theme. */
internal class AccessUi(private val activity: Activity,attach:Boolean=true) {
    private val palette=AppTheme.colors(activity)
    val green=palette.accent
    val dark=palette.text
    val muted=palette.muted
    private val pale=palette.alternate
    private val root = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(20), dp(16), dp(24))
        setBackgroundColor(palette.background)
    }
    init {
      if(attach) {
        AppTheme.apply(activity)
        activity.setContentView(ScrollView(activity).apply {
            isFillViewport = true
            fitsSystemWindows = true
            setBackgroundColor(palette.background)
            addView(root)
        })
      }
    }
    fun dp(n: Int) = (n * activity.resources.displayMetrics.density).toInt()
    fun background(color: Int, radius: Int = 16) = GradientDrawable().apply {
        setColor(AppTheme.surface(activity,color)); cornerRadius = dp(radius).toFloat()
    }
    fun text(value: String, size: Float = 15f, color: Int = muted, bold: Boolean = false) = TextView(activity).apply {
        text = value; textSize = size; setTextColor(color)
        setLineSpacing(dp(3).toFloat(), 1f)
        if (bold) setTypeface(null, Typeface.BOLD)
    }
    fun add(container: LinearLayout, view: View, margin: Int = 12) {
        container.addView(view, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(margin) })
    }
    fun page(title: String, description: String): LinearLayout {
        root.removeAllViews()
        val brand=LinearLayout(activity).apply {
            orientation=LinearLayout.VERTICAL;setPadding(dp(18),dp(18),dp(18),dp(18));background=background(palette.header,18)
            addView(text("DM AGRO",28f,Color.WHITE,true))
            addView(text(BetaWorkspace.SUBTITLE,11f,0xFFD1E8D3.toInt(),true))
        }
        add(root,brand,0)
        add(root,text(BetaWorkspace.TITLE,12f,green,true),6)
        val card = card()
        add(root, card, 16)
        val heading=LinearLayout(activity).apply {orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
        heading.addView(text(title,22f,dark,true),LinearLayout.LayoutParams(0,-2,1f))
        heading.addView(TextView(activity).apply {
            text="?";textSize=20f;setTextColor(green);gravity=Gravity.CENTER;minWidth=dp(44);minHeight=dp(48);contentDescription="Ajuda desta tela"
            setOnClickListener {android.app.AlertDialog.Builder(activity).setTitle("Como usar • $title").setMessage(ScreenHelp.text(title)).setPositiveButton("Entendi",null).show()}
        })
        add(card,heading,0)
        add(card, text(description), 6)
        add(root, text("Criado por Diego Machado",12f,muted).apply { gravity = Gravity.CENTER }, 24)
        return card
    }
    fun card() = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(18), dp(16), dp(18))
        background = background(palette.surface,16).apply {setStroke(dp(1).coerceAtLeast(1),palette.border)}
        elevation = 0f
    }
    fun field(container: LinearLayout, label: String, password: Boolean = false): EditText {
        add(container, text(label, 13f, dark, true), 16)
        val field = EditText(activity).apply {
            contentDescription = label
            textSize = 16f; setTextColor(dark); setHintTextColor(muted)
            setSingleLine(true); minHeight = dp(54)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = background(pale, 12).apply { setStroke(dp(1),palette.border) }
            backgroundTintList = null
            inputType = if (password) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                else InputType.TYPE_CLASS_TEXT
            if (password) setTypeface(Typeface.DEFAULT)
        }
        if (!password) {
            add(container, field, 5)
            return field
        }
        val fieldBackground = field.background
        field.background = null
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = fieldBackground
        }
        field.transformationMethod = PasswordTransformationMethod.getInstance()
        row.addView(field, LinearLayout.LayoutParams(0, -2, 1f))
        var visible = false
        val eye = ImageButton(activity).apply {
            setImageResource(R.drawable.ic_password_show)
            imageTintList=ColorStateList.valueOf(green)
            contentDescription = "Mostrar senha: $label"
            tooltipText = "Mostrar senha"
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = android.graphics.drawable.RippleDrawable(
                ColorStateList.valueOf(0x22187447), background(Color.TRANSPARENT, 12), null
            )
            setOnClickListener {
                val start = field.selectionStart
                val end = field.selectionEnd
                visible = !visible
                field.transformationMethod = if (visible) HideReturnsTransformationMethod.getInstance()
                    else PasswordTransformationMethod.getInstance()
                if (start >= 0 && end >= 0) field.setSelection(start, end)
                setImageResource(if (visible) R.drawable.ic_password_hide else R.drawable.ic_password_show)
                contentDescription = "${if (visible) "Ocultar" else "Mostrar"} senha: $label"
                tooltipText = if (visible) "Ocultar senha" else "Mostrar senha"
            }
        }
        row.addView(eye, LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginEnd = dp(4) })
        add(container, row, 5)
        return field
    }
    fun profileChoice(container:LinearLayout):ProfileChoice {
        add(container,text("Perfil",13f,dark,true),16)
        add(container,text("Selecione uma opção",13f),3)
        return ProfileChoice(activity).also { choice ->
            add(container,choice,10)
            add(container,text("Supervisor: aguarda aprovação de Diego Machado. Demais perfis: aguardam aprovação do supervisor da própria frente.",13f,green),10)
        }
    }
    fun spinner(container: LinearLayout, label: String, values: Array<String>): Spinner {
        add(container, text(label, 13f, dark, true), 16)
        val adapter = object : ArrayAdapter<String>(activity, android.R.layout.simple_spinner_item, values) {
            override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View =
                (super.getView(position, convertView, parent) as TextView).apply {
                    setTextColor(dark); textSize = 16f; setPadding(dp(14), dp(14), dp(14), dp(14))
                }
            override fun getDropDownView(position: Int, convertView: View?, parent: android.view.ViewGroup): View =
                (super.getDropDownView(position, convertView, parent) as TextView).apply {
                    setTextColor(dark); setBackgroundColor(palette.surface); minHeight = dp(48)
                }
        }.apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        return Spinner(activity).apply {
            contentDescription = label
            this.adapter = adapter
            background = background(pale, 12)
            add(container, this, 5)
        }
    }
    fun button(container: LinearLayout, label: String, secondary: Boolean = false, action: () -> Unit): Button =
        Button(activity).apply {
            text=label;AppTheme.action(this,!secondary)
            setOnClickListener { action() }
            add(container, this, 14)
        }
}
