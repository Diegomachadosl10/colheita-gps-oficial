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
    val green = 0xFF187447.toInt()
    val dark = 0xFF102D23.toInt()
    val muted = 0xFF52665B.toInt()
    private val pale = 0xFFEEF5EF.toInt()
    private val root = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(24), dp(28), dp(24), dp(28))
        background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(dark, 0xFF205B3D.toInt()))
    }
    init {
      if(attach) {
        activity.window.statusBarColor = dark
        activity.window.navigationBarColor = dark
        activity.setContentView(ScrollView(activity).apply {
            isFillViewport = true
            fitsSystemWindows = true
            setBackgroundColor(dark)
            addView(root)
        })
      }
    }
    fun dp(n: Int) = (n * activity.resources.displayMetrics.density).toInt()
    fun background(color: Int, radius: Int = 16) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat()
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
        add(root, text("DM AGRO", 30f, Color.WHITE, true), 0)
        add(root, text("COLHEITA GPS  •  GESTÃO NO CAMPO", 11f, 0xFFC4E6CD.toInt(), true), 2)
        val card = card()
        add(root, card, 26)
        add(card, text(title, 24f, dark, true), 0)
        add(card, text(description), 6)
        add(root, text("Criado por Diego Machado", 12f, 0xFFC4E6CD.toInt()).apply { gravity = Gravity.CENTER }, 24)
        return card
    }
    fun card() = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(22), dp(20), dp(22))
        background = background(Color.WHITE, 22)
        elevation = dp(3).toFloat()
    }
    fun field(container: LinearLayout, label: String, password: Boolean = false): EditText {
        add(container, text(label, 13f, dark, true), 16)
        val field = EditText(activity).apply {
            contentDescription = label
            textSize = 16f; setTextColor(dark); setHintTextColor(muted)
            setSingleLine(true); minHeight = dp(54)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = background(pale, 12).apply { setStroke(dp(1), 0xFFCEDCD1.toInt()) }
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
            imageTintList = null
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
        add(container,text("Tipo de acesso solicitado",13f,dark,true),16)
        add(container,text("Selecione uma opção",13f),3)
        return ProfileChoice(activity).also { choice ->
            add(container,choice,10)
            add(container,text("O perfil depende de aprovação. Supervisores são aprovados pelo administrador principal.",13f,green),10)
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
                    setTextColor(dark); setBackgroundColor(Color.WHITE); minHeight = dp(48)
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
            text = label; isAllCaps = false; textSize = 15f
            setTypeface(null, Typeface.BOLD)
            minHeight = dp(54); minimumHeight = dp(54)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            backgroundTintList = null
            background = background(if (secondary) pale else green, 14)
            setTextColor(ColorStateList(arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
                intArrayOf(0xFF637A6B.toInt(), if (secondary) dark else Color.WHITE)))
            setOnClickListener { action() }
            add(container, this, 14)
        }
}
