package com.colheitagps.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.*
import android.os.Bundle
import android.view.*
import android.widget.*
import com.google.firebase.auth.FirebaseAuth
import org.json.JSONArray
import java.io.File

class PhotoMarkupActivity : Activity() {
    private var editor: PhotoMarkupView? = null
    private var saving = false
    override fun onCreate(state: Bundle?) {
        super.onCreate(state); AppTheme.apply(this)
        val uid = intent.getStringExtra("uid").orEmpty()
        if (uid.isBlank() || FirebaseAuth.getInstance().currentUser?.uid != uid) { finish(); return }
        try {
            val root = TeamStore(applicationContext, intent.getStringExtra("team").orEmpty()).files
            val doc = PhotoMarkup.open(root, File(intent.getStringExtra("photo").orEmpty()))
            val marks = state?.getString("marks")?.let { text -> val rows = JSONArray(text); (0 until rows.length()).map { PhotoMark.read(rows.getJSONObject(it)) } } ?: doc.marks
            val ui = AccessUi(this, false); val palette = AppTheme.colors(this)
            val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; fitsSystemWindows = true; setBackgroundColor(palette.background); setPadding(ui.dp(12), ui.dp(12), ui.dp(12), ui.dp(12)) }
            layout.addView(ui.text("Marcar foto", 22f, palette.text, true))
            layout.addView(ui.text("Arraste para desenhar. A foto original fica preservada.", 14f))
            val view = PhotoMarkupView(this, PhotoMarkup.decode(doc.original), marks, doc.marks); editor = view
            val tools = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            val buttons = mutableListOf<Button>()
            for ((label, kind) in listOf("Seta" to "arrow", "Círculo" to "circle", "Texto" to "text")) {
                val button = Button(this).apply { text = label; AppTheme.action(this, kind == "arrow") }
                button.setOnClickListener {
                    if (kind == "text") {
                        val input = EditText(this).apply { hint = "Observação (até 140 caracteres)"; filters = arrayOf(android.text.InputFilter.LengthFilter(140)); AppTheme.field(this) }
                        AlertDialog.Builder(this).setTitle("Texto na foto").setMessage("Escreva e depois toque na foto onde deseja colocar o texto.").setView(input)
                            .setNegativeButton("Cancelar", null).setPositiveButton("Posicionar") { _, _ -> if (input.text.isNotBlank()) { view.note = input.text.toString().trim(); view.tool = kind; buttons.forEach { AppTheme.action(it, it === button) } } }.show()
                    } else { view.tool = kind; buttons.forEach { AppTheme.action(it, it === button) } }
                }
                buttons.add(button); tools.addView(button, LinearLayout.LayoutParams(0, -2, 1f))
            }
            layout.addView(tools)
            layout.addView(view, LinearLayout.LayoutParams(-1, 0, 1f))
            val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            actions.addView(Button(this).apply { text = "Desfazer"; AppTheme.action(this); setOnClickListener { view.undo() } }, LinearLayout.LayoutParams(0, -2, 1f))
            actions.addView(Button(this).apply { text = "Cancelar"; AppTheme.action(this); setOnClickListener { onBackPressed() } }, LinearLayout.LayoutParams(0, -2, 1f))
            layout.addView(actions)
            val save = Button(this).apply { text = "Salvar foto"; AppTheme.action(this, true) }
            save.setOnClickListener {
                if (saving) return@setOnClickListener
                if (FirebaseAuth.getInstance().currentUser?.uid != uid) { finish(); return@setOnClickListener }
                if(view.marks.isEmpty()) {
                    if(doc.marks.isEmpty()) {Toast.makeText(this,"Adicione uma marcação primeiro.",Toast.LENGTH_SHORT).show();return@setOnClickListener}
                    setResult(RESULT_OK,Intent().putExtra("markedPhoto",doc.original.path).putExtra("sourcePhoto",intent.getStringExtra("photo")));finish();return@setOnClickListener
                }
                saving = true; save.isEnabled = false
                val captured = view.marks.toList()
                Thread {
                    var created: File? = null
                    try {
                        created = PhotoMarkup.save(root, doc.original, captured)
                        val result = created
                        runOnUiThread {
                            if (!isFinishing && !isDestroyed && FirebaseAuth.getInstance().currentUser?.uid == uid) {
                                setResult(RESULT_OK, Intent().putExtra("markedPhoto", result.path).putExtra("sourcePhoto", intent.getStringExtra("photo"))); finish()
                            } else { result.delete(); File(result.path + ".markup.json").delete() }
                        }
                    } catch (_: Throwable) {
                        created?.delete()
                        runOnUiThread { if (!isFinishing && !isDestroyed) { saving = false; save.isEnabled = true; Toast.makeText(this, "Não foi possível salvar a foto. Tente novamente.", Toast.LENGTH_LONG).show() } }
                    }
                }.start()
            }
            layout.addView(save); setContentView(layout)
        } catch (_: Exception) { Toast.makeText(this, "Não foi possível abrir esta foto.", Toast.LENGTH_LONG).show(); finish() }
        catch (_: OutOfMemoryError) { Toast.makeText(this, "Memória insuficiente para abrir a foto.", Toast.LENGTH_LONG).show(); finish() }
    }
    override fun onSaveInstanceState(out: Bundle) { editor?.let { out.putString("marks", JSONArray(it.marks.map(PhotoMark::json)).toString()) }; super.onSaveInstanceState(out) }
    @Deprecated("Native back navigation")
    override fun onBackPressed() {
        if (saving) return
        if (editor?.changed == true) AlertDialog.Builder(this).setTitle("Descartar marcações?").setMessage("A foto original continuará salva.").setNegativeButton("Continuar editando", null).setPositiveButton("Descartar") { _, _ -> finish() }.show()
        else finish()
    }
    override fun onDestroy() { editor?.release(); editor = null; super.onDestroy() }
}

internal class PhotoMarkupView(context: android.content.Context, private val bitmap: Bitmap, initial: List<PhotoMark>, baseline:List<PhotoMark> = initial) : View(context) {
    val marks = initial.toMutableList()
    private val originalMarks = baseline.toList()
    val changed get() = marks != originalMarks
    var tool = "arrow"
    var note = ""
    private var start: PointF? = null
    private var preview: PhotoMark? = null
    private val imageRect = RectF()
    init { contentDescription = "Foto para marcações. Escolha seta, círculo ou texto."; setBackgroundColor(Color.rgb(25, 30, 28)); isSaveEnabled = false }
    fun undo() { if (marks.isNotEmpty()) marks.removeAt(marks.lastIndex); preview = null; start = null; invalidate() }
    fun release() { bitmap.recycle() }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val scale = minOf(width.toFloat() / bitmap.width, height.toFloat() / bitmap.height)
        val w = bitmap.width * scale; val h = bitmap.height * scale
        imageRect.set((width - w) / 2, (height - h) / 2, (width + w) / 2, (height + h) / 2)
        canvas.drawBitmap(bitmap, null, imageRect, Paint(Paint.FILTER_BITMAP_FLAG))
        PhotoMarkup.draw(canvas, imageRect, marks + listOfNotNull(preview))
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (imageRect.width() <= 0 || imageRect.height() <= 0) return false
        fun point() = PointF(((event.x - imageRect.left) / imageRect.width()).coerceIn(0f, 1f), ((event.y - imageRect.top) / imageRect.height()).coerceIn(0f, 1f))
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (!imageRect.contains(event.x, event.y)) return false
                if(marks.size>=40) {Toast.makeText(context,"Limite de 40 marcações. Use Desfazer para ajustar a foto.",Toast.LENGTH_SHORT).show();return false}
                parent?.requestDisallowInterceptTouchEvent(true); start = point(); return true
            }
            MotionEvent.ACTION_MOVE -> { val from = start ?: return false; val end = point(); preview = PhotoMark(tool, from.x, from.y, end.x, end.y, note); invalidate(); return true }
            MotionEvent.ACTION_UP -> {
                val from = start ?: return false; val end = point()
                if (tool == "text" && note.isNotBlank()) marks.add(PhotoMark(tool, end.x, end.y, end.x, end.y, note))
                else if ((tool=="circle"&&kotlin.math.abs(from.x-end.x)>.01f&&kotlin.math.abs(from.y-end.y)>.01f)||
                    (tool=="arrow"&&kotlin.math.abs(from.x - end.x) + kotlin.math.abs(from.y - end.y) > .015f)) marks.add(PhotoMark(tool, from.x, from.y, end.x, end.y))
                preview = null; start = null; invalidate(); performClick(); return true
            }
            MotionEvent.ACTION_CANCEL -> { preview = null; start = null; invalidate(); return true }
        }
        return true
    }
    override fun performClick(): Boolean { super.performClick(); return true }
}
