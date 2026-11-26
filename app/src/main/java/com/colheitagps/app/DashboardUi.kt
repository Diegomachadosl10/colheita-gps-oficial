package com.colheitagps.app

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.widget.*

internal enum class FieldIcon { BRAND, MAP, PENDING, EQUIPMENT, PART, RAIN, STOCK, ORDER, REPORT, OCCURRENCE, TRUCK, CALENDAR, PEOPLE, WEATHER, BELL, ADD, SHARE, HISTORY }

/** Native vector icons stay sharp at every Android font/display size. */
internal class FieldIconView(context: android.content.Context, private val icon: FieldIcon, private val iconColor:Int=AppTheme.colors(context).accent) : View(context) {
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = iconColor; strokeWidth = 2.2f; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.save(); val scale = minOf(width, height) / 48f
        canvas.translate((width - 48 * scale) / 2, (height - 48 * scale) / 2); canvas.scale(scale, scale)
        fun line(vararg points: Float) { val p = Path(); p.moveTo(points[0], points[1]); for (i in 2 until points.size step 2) p.lineTo(points[i], points[i + 1]); canvas.drawPath(p, ink) }
        fun rect(l: Float, t: Float, r: Float, b: Float) { canvas.drawRoundRect(l, t, r, b, 2f, 2f, ink) }
        fun circle(x: Float, y: Float, r: Float) { canvas.drawCircle(x, y, r, ink) }
        when (icon) {
            FieldIcon.ADD -> {line(24f,9f,24f,39f);line(9f,24f,39f,24f)}
            FieldIcon.SHARE -> {circle(12f,24f,6f);circle(36f,10f,6f);circle(36f,38f,6f);line(17f,21f,31f,13f);line(17f,27f,31f,35f)}
            FieldIcon.HISTORY -> {canvas.drawArc(7f,7f,43f,43f,-145f,295f,false,ink);line(6f,5f,6f,17f,18f,17f);line(25f,14f,25f,26f,33f,30f)}
            FieldIcon.BRAND -> {
                ink.style = Paint.Style.FILL
                val leaf = Path().apply { moveTo(22f, 22f); cubicTo(5f, 22f, 5f, 9f, 5f, 8f); cubicTo(19f, 7f, 25f, 13f, 22f, 22f); moveTo(25f, 21f); cubicTo(22f, 6f, 34f, 4f, 43f, 5f); cubicTo(43f, 14f, 37f, 23f, 25f, 21f) }
                canvas.drawPath(leaf, ink); ink.style = Paint.Style.STROKE
                line(5f, 32f, 18f, 27f, 43f, 27f); line(8f, 40f, 24f, 31f, 43f, 31f); line(19f, 44f, 32f, 35f, 42f, 35f)
            }
            FieldIcon.MAP -> { line(5f, 11f, 18f, 6f, 31f, 11f, 42f, 6f, 42f, 36f, 31f, 41f, 18f, 36f, 5f, 41f, 5f, 11f); line(18f, 7f, 18f, 36f); line(31f, 12f, 31f, 40f) }
            FieldIcon.PENDING -> { rect(11f, 8f, 38f, 43f); rect(19f, 4f, 30f, 11f); line(16f, 20f, 19f, 23f, 24f, 17f); line(28f, 20f, 33f, 20f); line(16f, 32f, 19f, 35f, 24f, 29f); line(28f, 32f, 33f, 32f) }
            FieldIcon.EQUIPMENT -> { circle(14f, 33f, 10f); circle(14f, 33f, 5f); circle(38f, 37f, 6f); line(21f, 36f, 31f, 36f, 32f, 20f, 22f, 20f, 18f, 8f, 7f, 8f); line(20f, 10f, 31f, 10f, 34f, 28f, 41f, 30f); line(36f, 20f, 36f, 10f) }
            FieldIcon.PART -> { circle(24f,24f,14f); circle(24f,24f,6f); for(i in 0..7) { val angle=i* Math.PI/4; line((24+14*kotlin.math.cos(angle)).toFloat(),(24+14*kotlin.math.sin(angle)).toFloat(),(24+21*kotlin.math.cos(angle)).toFloat(),(24+21*kotlin.math.sin(angle)).toFloat()) } }
            FieldIcon.RAIN, FieldIcon.WEATHER -> { val p=Path().apply { moveTo(12f, 29f); cubicTo(0f, 27f, 4f, 12f, 16f, 15f); cubicTo(18f, 0f, 36f, 5f, 36f, 18f); cubicTo(48f, 18f, 46f, 31f, 36f, 31f); lineTo(12f, 31f) }; canvas.drawPath(p, ink); if (icon == FieldIcon.RAIN) { line(16f, 36f, 12f, 43f); line(26f, 36f, 22f, 43f); line(36f, 36f, 32f, 43f) } else { circle(11f, 8f, 5f); line(2f, 7f, 0f, 7f); line(12f, 0f, 12f, 2f) } }
            FieldIcon.STOCK -> { rect(4f, 25f, 23f, 43f); rect(25f, 25f, 44f, 43f); rect(14f, 5f, 34f, 23f); line(21f, 6f, 21f, 13f, 26f, 13f, 26f, 6f); line(11f, 26f, 11f, 33f, 16f, 33f, 16f, 26f); line(32f, 26f, 32f, 33f, 37f, 33f, 37f, 26f) }
            FieldIcon.ORDER -> { line(12f, 43f, 12f, 5f, 29f, 5f, 39f, 15f, 39f, 43f, 12f, 43f); line(29f, 6f, 29f, 15f, 38f, 15f); line(18f, 24f, 32f, 24f); line(18f, 31f, 32f, 31f); line(18f, 37f, 27f, 37f) }
            FieldIcon.REPORT -> { rect(5f, 29f, 13f, 43f); rect(20f, 18f, 28f, 43f); rect(35f, 5f, 43f, 43f) }
            FieldIcon.OCCURRENCE -> { val p=Path().apply { moveTo(24f, 44f); cubicTo(8f, 27f, 6f, 23f, 9f, 13f); cubicTo(14f, -2f, 38f, 0f, 40f, 16f); cubicTo(42f, 26f, 32f, 37f, 24f, 44f) }; canvas.drawPath(p, ink); line(24f, 12f, 24f, 23f); circle(24f, 29f, 1f) }
            FieldIcon.TRUCK -> { rect(3f, 11f, 28f, 33f); line(28f, 20f, 36f, 20f, 44f, 28f, 44f, 36f, 28f, 36f); circle(12f, 37f, 5f); circle(36f, 37f, 5f) }
            FieldIcon.CALENDAR -> { rect(6f, 10f, 42f, 43f); line(7f, 20f, 41f, 20f); line(15f, 5f, 15f, 15f); line(33f, 5f, 33f, 15f); circle(16f, 29f, 1f); circle(27f, 29f, 1f); circle(16f, 36f, 1f); circle(27f, 36f, 1f) }
            FieldIcon.PEOPLE -> { circle(21f, 13f, 7f); circle(37f, 17f, 5f); canvas.drawArc(7f, 25f, 35f, 48f, 180f, 180f, false, ink); canvas.drawArc(29f, 28f, 47f, 45f, 190f, 150f, false, ink) }
            FieldIcon.BELL -> { val p=Path().apply { moveTo(8f, 34f); lineTo(12f, 28f); lineTo(12f, 18f); cubicTo(12f, 2f, 36f, 2f, 36f, 18f); lineTo(36f, 28f); lineTo(40f, 34f); close() }; canvas.drawPath(p, ink); canvas.drawArc(18f, 33f, 30f, 45f, 0f, 180f, false, ink) }
        }
        canvas.restore()
    }
}

internal class FieldIconDrawable(context:android.content.Context,icon:FieldIcon,color:Int):android.graphics.drawable.Drawable() {
    private val view=FieldIconView(context,icon,color)
    override fun draw(canvas:Canvas) {
        canvas.save();canvas.translate(bounds.left.toFloat(),bounds.top.toFloat())
        view.layout(0,0,bounds.width(),bounds.height());view.draw(canvas);canvas.restore()
    }
    override fun setAlpha(alpha:Int) {view.alpha=alpha/255f}
    override fun setColorFilter(filter:ColorFilter?) {}
    @Suppress("DEPRECATION") override fun getOpacity()=PixelFormat.TRANSLUCENT
}

internal class DashboardUi(private val activity: Activity) {
    private val palette=AppTheme.colors(activity)
    val mint=palette.muted
    fun dp(value: Int) = (value * activity.resources.displayMetrics.density).toInt()
    fun text(value: String, size: Float, color:Int=palette.text, bold: Boolean = false) = TextView(activity).apply {
        text = value; textSize = size; setTextColor(color); if (bold) setTypeface(null, Typeface.BOLD)
    }
    fun panel(action: (() -> Unit)? = null) = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(17), dp(18), dp(17), dp(18))
        val shape=GradientDrawable().apply {setColor(palette.surface);cornerRadius=dp(18).toFloat();setStroke(dp(1),palette.border)}
        background = if (action == null) shape else RippleDrawable(ColorStateList.valueOf(0x33B7E9AF), shape, null)
        if (action != null) setOnClickListener { action() }
        elevation = dp(2).toFloat()
    }
    fun brand(): View = LinearLayout(activity).apply {
        gravity = Gravity.CENTER_VERTICAL
        addView(FieldIconView(activity,FieldIcon.BRAND,0xFFB7E9AF.toInt()), LinearLayout.LayoutParams(dp(47), dp(47)))
        addView(LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(10), 0, 0, 0)
            addView(text("DM AGRO",26f,Color.WHITE,true))
            addView(text(BetaWorkspace.SUBTITLE,11f,0xFFD1E8D3.toInt()).apply { maxLines = 2 })
            addView(text("BETA GRATUITA",10f,0xFFB7E9AF.toInt(),true))
        }, LinearLayout.LayoutParams(0,-2,1f))
    }
    data class Tile(val title: String, val icon: FieldIcon, val action: () -> Unit, val badge: Int = 0)
    fun grid(container: LinearLayout, tiles: List<Tile>) {
        val columns = if (activity.resources.configuration.fontScale >= 1.45f || activity.resources.configuration.screenWidthDp in 1..319) 1 else 2
        for (rowItems in tiles.chunked(columns)) {
            val row = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; isBaselineAligned = false }
            for ((index, tile) in rowItems.withIndex()) {
                val card=panel(tile.action).apply {
                    orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;minimumHeight=dp(88)
                    contentDescription=tile.title+if(tile.badge>0) ", ${tile.badge} em aberto" else ""
                    addView(FieldIconView(activity,tile.icon),LinearLayout.LayoutParams(dp(32),dp(32)))
                    addView(LinearLayout(activity).apply {
                        orientation=LinearLayout.VERTICAL;setPadding(dp(10),0,0,0)
                        addView(text(tile.title,15f,bold=true))
                        if(tile.badge>0) addView(text("${tile.badge} em aberto",12f,mint))
                    },LinearLayout.LayoutParams(0,-2,1f))
                }
                row.addView(card, LinearLayout.LayoutParams(0, -1, 1f).apply { if (index > 0) marginStart = dp(12) })
            }
            // Both cells must fill the measured row. A fixed 1px spacer makes LinearLayout
            // collapse an odd final row while its real card requests MATCH_PARENT height.
            if (rowItems.size < columns) row.addView(View(activity), LinearLayout.LayoutParams(0, -1, 1f).apply { marginStart = dp(12) })
            container.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        }
    }
    companion object {
        fun name(access: TeamAccess?, authName: String = ""): String = access?.profile?.get("nome")?.toString()?.trim().orEmpty()
            .ifBlank { authName.trim() }.ifBlank { if (access?.primary == true) "Administrador" else "Pessoa da equipe" }
        fun status(message: String): String = when {
            message.contains("sincronizados", true) -> "Sincronizado"
            message.contains("conflito", true) || message.contains("indisponível", true) -> "Verificar sincronização"
            message.contains("aguardando", true) || message.contains("pendente", true) -> Regex("^[0-9]+ alterações aguardando envio").find(message)?.value?:if(message.startsWith("Salvo neste celular")) "Salvo neste celular • aguardando envio" else "Aguardando sincronização"
            else -> "Conectando à equipe…"
        }
    }
}
