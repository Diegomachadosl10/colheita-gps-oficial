
package com.colheitagps.app

import android.Manifest
import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.*
import android.graphics.pdf.PdfRenderer
import android.location.LocationManager
import android.location.LocationListener
import android.net.Uri
import android.view.*
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var mapa: MapaView
    private var gps: LocationManager? = null
    private var listener: LocationListener? = null
    private var mapaId = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val tela = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
        }

        val titulo = TextView(this).apply {
            text = "COLHEITA GPS"
            textSize = 24f
            setTextColor(Color.rgb(20, 100, 45))
            gravity = Gravity.CENTER
        }
        tela.addView(titulo)

        status = TextView(this).apply {
            text = "GPS aguardando ativação"
            setTextColor(Color.DKGRAY)
            gravity = Gravity.CENTER
        }
        tela.addView(status)

        val abrir = Button(this).apply {
            text = "ABRIR MAPA PDF"
            setOnClickListener {
                val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    type = "application/pdf"
                    addCategory(Intent.CATEGORY_OPENABLE)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                }
                startActivityForResult(i, 10)
            }
        }
        tela.addView(abrir)

        val botoes = LinearLayout(this)
        val pintar = Button(this).apply {
            text = "PINTAR"
            setOnClickListener { mapa.modo = 1 }
        }
        val apagar = Button(this).apply {
            text = "BORRACHA"
            setOnClickListener { mapa.modo = 2 }
        }
        val mover = Button(this).apply {
            text = "MOVER"
            setOnClickListener { mapa.modo = 0 }
        }

        for (b in arrayOf(pintar, apagar, mover)) {
            botoes.addView(
                b, LinearLayout.LayoutParams(0, -2, 1f)
            )
        }
        tela.addView(botoes)

        mapa = MapaView(this)
        tela.addView(
            mapa, LinearLayout.LayoutParams(-1, 0, 1f)
        )

        val ativar = Button(this).apply {
            text = "ATIVAR GPS"
            setOnClickListener { ativarGps() }
        }
        tela.addView(ativar)
        setContentView(tela)
    }

    override fun onActivityResult(
        requestCode: Int, resultCode: Int, data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 10 || resultCode != RESULT_OK) return
        val uri = data?.data ?: return

        try {
            contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Exception) {}

        try {
            val arquivo = File(cacheDir, "mapa_atual.pdf")
            contentResolver.openInputStream(uri)!!.use { entrada ->
                FileOutputStream(arquivo).use { saida ->
                    entrada.copyTo(saida)
                }
            }

            val fd = android.os.ParcelFileDescriptor.open(
                arquivo, android.os.ParcelFileDescriptor.MODE_READ_ONLY
            )
            PdfRenderer(fd).use { pdf ->
                val pagina = pdf.openPage(0)
                val largura = pagina.width * 2
                val altura = pagina.height * 2
                val bitmap = Bitmap.createBitmap(
                    largura, altura, Bitmap.Config.ARGB_8888
                )
                bitmap.eraseColor(Color.WHITE)
                pagina.render(
                    bitmap, null, null,
                    PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                )
                pagina.close()
                mapaId = uri.toString()
                mapa.carregar(bitmap, mapaId)
            }
            status.text = "Mapa carregado - página 1"
        } catch (e: Exception) {
            status.text = "Erro ao abrir PDF: ${e.message}"
        }
    }

    private fun ativarGps() {
        if (checkSelfPermission(
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 100
            )
            return
        }

        gps = getSystemService(Context.LOCATION_SERVICE)
                as LocationManager

        if (gps?.isProviderEnabled(
                LocationManager.GPS_PROVIDER
            ) != true
        ) {
            status.text = "Ative a localização do celular"
            return
        }

        listener?.let { gps?.removeUpdates(it) }
        listener = LocationListener { local ->
            status.text = "GPS: %.6f, %.6f | Precisão: %.0f m"
                .format(
                    java.util.Locale.US,
                    local.latitude,
                    local.longitude,
                    local.accuracy
                )
        }

        try {
            gps?.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                2000L, 1f, listener!!
            )
            status.text = "Aguardando sinal GPS..."
        } catch (e: SecurityException) {
            status.text = "Permissão GPS necessária"
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(
            requestCode, permissions, grantResults
        )
        if (requestCode == 100 &&
            grantResults.firstOrNull() ==
            PackageManager.PERMISSION_GRANTED
        ) ativarGps()
    }

    override fun onPause() {
        super.onPause()
        mapa.salvar()
    }

    override fun onDestroy() {
        listener?.let { gps?.removeUpdates(it) }
        super.onDestroy()
    }
}

class MapaView(context: Context) : View(context) {
    var modo = 0
    private var imagem: Bitmap? = null
    private var id = ""
    private val linhas = mutableListOf<MutableList<PointF>>()
    private var atual: MutableList<PointF>? = null
    private var escala = 1f
    private var dx = 0f
    private var dy = 0f
    private var ultimoX = 0f
    private var ultimoY = 0f
    private val zoom = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(d: ScaleGestureDetector): Boolean {
                val antiga = escala
                escala = (escala * d.scaleFactor).coerceIn(0.2f, 10f)
                val fator = escala / antiga
                dx = d.focusX - (d.focusX - dx) * fator
                dy = d.focusY - (d.focusY - dy) * fator
                invalidate()
                return true
            }
        }
    )

    fun carregar(bitmap: Bitmap, chave: String) {
        salvar()
        imagem?.recycle()
        imagem = bitmap
        id = chave
        linhas.clear()
        escala = minOf(
            width.toFloat() / bitmap.width,
            height.toFloat() / bitmap.height
        ).coerceAtLeast(0.01f)
        dx = (width - bitmap.width * escala) / 2f
        dy = (height - bitmap.height * escala) / 2f
        restaurar()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.rgb(235, 235, 235))
        canvas.save()
        canvas.translate(dx, dy)
        canvas.scale(escala, escala)
        imagem?.let {
            canvas.drawBitmap(it, 0f, 0f, null)
        }

        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(100, 255, 200, 0)
            strokeWidth = 22f
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        for (linha in linhas) {
            if (linha.isEmpty()) continue
            val caminho = Path()
            caminho.moveTo(linha[0].x, linha[0].y)
            if (linha.size == 1) {
                caminho.lineTo(linha[0].x + 0.1f, linha[0].y)
            }
            for (i in 1 until linha.size) {
                caminho.lineTo(linha[i].x, linha[i].y)
            }
            canvas.drawPath(caminho, p)
        }
        canvas.restore()
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        zoom.onTouchEvent(e)
        if (imagem == null) return true
        if (e.pointerCount > 1) {
            atual = null
            return true
        }

        val x = (e.x - dx) / escala
        val y = (e.y - dy) / escala

        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                ultimoX = e.x
                ultimoY = e.y
                if (modo == 1) {
                    atual = mutableListOf(PointF(x, y))
                    linhas.add(atual!!)
                } else if (modo == 2) {
                    apagarPerto(x, y)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                when (modo) {
                    0 -> {
                        dx += e.x - ultimoX
                        dy += e.y - ultimoY
                    }
                    1 -> atual?.add(PointF(x, y))
                    2 -> apagarPerto(x, y)
                }
                ultimoX = e.x
                ultimoY = e.y
            }
            MotionEvent.ACTION_UP -> {
                atual = null
                salvar()
            }
        }
        invalidate()
        return true
    }

    private fun apagarPerto(x: Float, y: Float) {
        linhas.removeAll { linha ->
            linha.any { p ->
                val a = p.x - x
                val b = p.y - y
                a * a + b * b < 900f
            }
        }
        salvar()
    }

    fun salvar() {
        if (id.isEmpty()) return
        val dados = JSONArray()
        for (linha in linhas) {
            val pontos = JSONArray()
            for (p in linha) {
                pontos.put(
                    JSONObject().put("x", p.x).put("y", p.y)
                )
            }
            dados.put(pontos)
        }
        context.getSharedPreferences("pinturas", 0)
            .edit().putString(id, dados.toString()).apply()
    }

    private fun restaurar() {
        try {
            val texto = context.getSharedPreferences("pinturas", 0)
                .getString(id, "[]") ?: "[]"
            val dados = JSONArray(texto)
            for (i in 0 until dados.length()) {
                val pontos = dados.getJSONArray(i)
                val linha = mutableListOf<PointF>()
                for (j in 0 until pontos.length()) {
                    val p = pontos.getJSONObject(j)
                    linha.add(
                        PointF(
                            p.getDouble("x").toFloat(),
                            p.getDouble("y").toFloat()
                        )
                    )
                }
                linhas.add(linha)
            }
        } catch (_: Exception) {}
    }
}
