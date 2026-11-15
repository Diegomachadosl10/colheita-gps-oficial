package com.colheitagps.app

import android.graphics.*
import android.media.ExifInterface
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import kotlin.math.*

internal data class PhotoMark(val kind: String, val x: Float, val y: Float, val endX: Float, val endY: Float, val text: String = "") {
    fun json() = JSONObject().put("kind", kind).put("x", x).put("y", y).put("endX", endX).put("endY", endY).put("text", text)
    companion object {
        fun read(row: JSONObject): PhotoMark {
            val mark = PhotoMark(row.getString("kind"), row.getDouble("x").toFloat(), row.getDouble("y").toFloat(), row.getDouble("endX").toFloat(), row.getDouble("endY").toFloat(), row.optString("text"))
            require(mark.kind in setOf("arrow", "circle", "text") && listOf(mark.x, mark.y, mark.endX, mark.endY).all { it.isFinite() && it in 0f..1f } && mark.text.length <= 140)
            return mark
        }
    }
}

/** All coordinates are relative to the oriented photograph, never the screen or a map/GPS. */
internal object PhotoMarkup {
    data class Document(val original: File, val marks: List<PhotoMark>)
    fun attached(record: JSONObject, path: String): Boolean = listOf("foto", "fotoEstoque").any { record.optString(it) == path } ||
        record.optJSONArray("fotos")?.let { photos -> (0 until photos.length()).any { photos.optString(it) == path } } == true
    fun replace(record: JSONObject, old: String, replacement: String): JSONObject {
        require(attached(record, old)) { "A foto do registro foi alterada em outro aparelho." }
        return JSONObject(record.toString()).apply {
            for (key in listOf("foto", "fotoEstoque")) if (optString(key) == old) put(key, replacement)
            optJSONArray("fotos")?.let { photos -> for (i in 0 until photos.length()) if (photos.optString(i) == old) photos.put(i, replacement) }
        }
    }
    fun checked(root: File, file: File): File = file.canonicalFile.also {
        require(it.path.startsWith(root.canonicalPath + File.separator) && it.isFile) { "Foto não disponível nesta equipe." }
    }
    fun open(root: File, file: File): Document {
        val image = checked(root, file)
        val sidecar = File(image.path + ".markup.json")
        if (!sidecar.isFile) return Document(image, emptyList())
        val json = JSONObject(sidecar.readText())
        val source = json.getString("original")
        require(!source.startsWith('/') && source.split('/').none { it == ".." })
        val original = checked(root, File(root, source))
        val marks = json.getJSONArray("marks")
        require(marks.length() <= 40)
        return Document(original, (0 until marks.length()).map { PhotoMark.read(marks.getJSONObject(it)) })
    }
    fun decode(file: File, limit: Int = 1800): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Não foi possível abrir a imagem." }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > limit) sample *= 2
        val raw = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 }) ?: error("Foto indisponível")
        val orientation = try { ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) } catch (_: Exception) { ExifInterface.ORIENTATION_NORMAL }
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.setRotate(90f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.setRotate(270f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(270f)
        }
        if (matrix.isIdentity) return raw
        return Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true).also { if (it !== raw) raw.recycle() }
    }
    fun draw(canvas: Canvas, rect: RectF, marks: List<PhotoMark>) {
        val unit = min(rect.width(), rect.height())
        if (unit <= 0) return
        canvas.save(); canvas.clipRect(rect)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(218, 32, 40); strokeWidth = max(3f, unit * .006f); style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
        marks.forEach { mark ->
            val x = rect.left + mark.x * rect.width(); val y = rect.top + mark.y * rect.height()
            val endX = rect.left + mark.endX * rect.width(); val endY = rect.top + mark.endY * rect.height()
            when (mark.kind) {
                "circle" -> canvas.drawOval(RectF(min(x, endX), min(y, endY), max(x, endX), max(y, endY)), paint)
                "arrow" -> {
                    canvas.drawLine(x, y, endX, endY, paint)
                    val angle = atan2(endY - y, endX - x); val length = unit * .04f
                    for (offset in listOf(-.55f, .55f)) canvas.drawLine(endX, endY, endX - cos(angle + offset) * length, endY - sin(angle + offset) * length, paint)
                }
                "text" -> {
                    val font = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = max(16f, unit * .035f); typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
                    val maxWidth = rect.width() - unit * .04f
                    val lines = mutableListOf<String>(); var rest = mark.text.replace('\n', ' ').trim()
                    while (rest.isNotEmpty()) { val count = font.breakText(rest, true, maxWidth, null).coerceAtLeast(1); lines.add(rest.take(count)); rest = rest.drop(count) }
                    val lineHeight = font.fontSpacing; val pad = unit * .012f
                    val boxWidth = (lines.maxOfOrNull { font.measureText(it) } ?: 0f) + 2 * pad
                    val boxHeight = lines.size * lineHeight + 2 * pad
                    val left = x.coerceIn(rect.left, max(rect.left, rect.right - boxWidth))
                    val top = y.coerceIn(rect.top, max(rect.top, rect.bottom - boxHeight))
                    canvas.drawRoundRect(RectF(left, top, left + boxWidth, top + boxHeight), pad, pad, Paint().apply { color = Color.argb(220, 150, 15, 25) })
                    lines.forEachIndexed { i, line -> canvas.drawText(line, left + pad, top + pad - font.ascent() + i * lineHeight, font) }
                }
            }
        }
        canvas.restore()
    }
    fun save(root: File, original: File, marks: List<PhotoMark>): File {
        require(marks.isNotEmpty() && marks.size <= 40)
        marks.forEach { PhotoMark.read(it.json()) }
        val source = checked(root, original)
        val image = decode(source)
        var output: Bitmap? = null
        val file = File(root, "fotos_marcadas/${UUID.randomUUID()}.jpg"); file.parentFile!!.mkdirs()
        try {
            output = image.copy(Bitmap.Config.ARGB_8888, true) ?: error("Memória insuficiente")
            draw(Canvas(output), RectF(0f, 0f, output.width.toFloat(), output.height.toFloat()), marks)
            file.outputStream().use { check(output.compress(Bitmap.CompressFormat.JPEG, 92, it)) }
            File(file.path + ".markup.json").writeText(JSONObject().put("original", source.relativeTo(root.canonicalFile).invariantSeparatorsPath).put("marks", JSONArray(marks.map { it.json() })).toString())
            return file
        } catch (e: Exception) { file.delete(); File(file.path + ".markup.json").delete(); throw e }
        finally { output?.recycle(); image.recycle() }
    }
}
