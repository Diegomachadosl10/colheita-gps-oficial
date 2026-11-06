
package com.colheitagps.app

import android.Manifest
import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.*
import android.graphics.pdf.PdfRenderer
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.view.*
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

class MainActivity : Activity() {

    private lateinit var tela: LinearLayout
    private lateinit var mapa: MapaView
    private lateinit var status: TextView
    private lateinit var seletor: Spinner

    private val nomes = mutableListOf<String>()
    private val ids = mutableListOf<String>()

    private var gps: LocationManager? = null
    private var listener: LocationListener? = null
    private var selecionado = ""
    private var carregandoLista = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        tela = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
        }

        // Respeita barras do sistema Android
        tela.setOnApplyWindowInsetsListener { v, insets ->
            val barras = insets.getInsets(
                WindowInsets.Type.systemBars()
            )
            v.setPadding(
                barras.left, barras.top,
                barras.right, barras.bottom
            )
            insets
        }

        val titulo = TextView(this).apply {
            text = "COLHEITA GPS 1.2"
            textSize = 22f
            setTextColor(Color.rgb(20, 100, 45))
            gravity = Gravity.CENTER
        }
        tela.addView(titulo)

        status = TextView(this).apply {
            text = "GPS desligado"
            textSize = 13f
            setTextColor(Color.DKGRAY)
            gravity = Gravity.CENTER
        }
        tela.addView(status)

        val linha1 = LinearLayout(this)

        val adicionar = Button(this).apply {
            text = "ADICIONAR PDF"
            textSize = 12f
            setOnClickListener { escolherPdf() }
        }

        val ativar = Button(this).apply {
            text = "ATIVAR GPS"
            textSize = 12f
            setOnClickListener { ativarGps() }
        }

        linha1.addView(
            adicionar,
            LinearLayout.LayoutParams(0, -2, 1f)
        )
        linha1.addView(
            ativar,
            LinearLayout.LayoutParams(0, -2, 1f)
        )
        tela.addView(linha1)

        seletor = Spinner(this)
        tela.addView(seletor)

        val linha2 = LinearLayout(this)

        val pintar = Button(this).apply {
            text = "PINTAR"
            textSize = 11f
            setOnClickListener { mapa.modo = 1 }
        }
        val borracha = Button(this).apply {
            text = "BORRACHA"
            textSize = 11f
            setOnClickListener { mapa.modo = 2 }
        }
        val mover = Button(this).apply {
            text = "MOVER"
            textSize = 11f
            setOnClickListener { mapa.modo = 0 }
        }

        for (botao in arrayOf(pintar, borracha, mover)) {
            linha2.addView(
                botao,
                LinearLayout.LayoutParams(0, -2, 1f)
            )
        }
        tela.addView(linha2)

        mapa = MapaView(this)
        tela.addView(
            mapa,
            LinearLayout.LayoutParams(-1, 0, 1f)
        )

        setContentView(tela)
        configurarSeletor()
        atualizarLista()
    }

    private fun pastaMapas(): File {
        return File(filesDir, "mapas").apply { mkdirs() }
    }

    private fun escolherPdf() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "application/pdf"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        startActivityForResult(i, 10)
    }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode != 10 || resultCode != RESULT_OK) return
        val uri = data?.data ?: return

        try {
            val nome = obterNome(uri)
            val bytes = contentResolver.openInputStream(uri)!!
                .use { it.readBytes() }

            val hash = MessageDigest.getInstance("SHA-256")
                .digest(bytes)
                .joinToString("") { "%02x".format(it) }

            val arquivo = File(pastaMapas(), "$hash.pdf")
            if (!arquivo.exists()) {
                FileOutputStream(arquivo).use {
                    it.write(bytes)
                }
            }

            val prefs = getSharedPreferences("mapas", 0)
            prefs.edit().putString(hash, nome).apply()

            selecionado = hash
            atualizarLista()
            abrirMapa(hash)

        } catch (e: Exception) {
            status.text = "Erro ao importar: ${e.message}"
        }
    }

    private fun obterNome(uri: Uri): String {
        var nome = "Mapa PDF"
        contentResolver.query(uri, null, null, null, null)?.use {
            val coluna = it.getColumnIndex(
                android.provider.OpenableColumns.DISPLAY_NAME
            )
            if (coluna >= 0 && it.moveToFirst()) {
                nome = it.getString(coluna) ?: nome
            }
        }
        return nome
    }

    private fun configurarSeletor() {
        seletor.onItemSelectedListener =
            object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(
                    parent: android.widget.AdapterView<*>?
                ) {}

                override fun onItemSelected(
                    parent: android.widget.AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    if (carregandoLista) return
                    if (position in ids.indices) {
                        val novo = ids[position]
                        if (novo != selecionado) {
                            abrirMapa(novo)
                        }
                    }
                }
            }
    }

    private fun atualizarLista() {
        carregandoLista = true
        nomes.clear()
        ids.clear()

        val prefs = getSharedPreferences("mapas", 0)
        val arquivos = pastaMapas()
            .listFiles()
            ?.filter { it.extension == "pdf" }
            ?.sortedBy { it.name }
            ?: emptyList()

        for (arquivo in arquivos) {
            val id = arquivo.nameWithoutExtension
            ids.add(id)
            nomes.add(prefs.getString(id, id) ?: id)
        }

        if (nomes.isEmpty()) {
            nomes.add("Nenhum mapa importado")
        }

        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            nomes
        )
        adapter.setDropDownViewResource(
            android.R.layout.simple_spinner_dropdown_item
        )
        seletor.adapter = adapter

        val posicao = ids.indexOf(selecionado)
        if (posicao >= 0) {
            seletor.setSelection(posicao)
        }

        carregandoLista = false
    }

    private fun abrirMapa(id: String) {
        try {
            val arquivo = File(pastaMapas(), "$id.pdf")
            val fd = ParcelFileDescriptor.open(
                arquivo,
                ParcelFileDescriptor.MODE_READ_ONLY
            )

            PdfRenderer(fd).use { pdf ->
                val pagina = pdf.openPage(0)

                val fator = minOf(
                    2f,
                    3000f / maxOf(pagina.width, pagina.height)
                )
                val largura = (pagina.width * fator)
                    .toInt().coerceAtLeast(1)
                val altura = (pagina.height * fator)
                    .toInt().coerceAtLeast(1)

                val bitmap = Bitmap.createBitmap(
                    largura, altura,
                    Bitmap.Config.ARGB_8888
                )
                bitmap.eraseColor(Color.WHITE)

                pagina.render(
                    bitmap, null, null,
                    PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                )
                pagina.close()

                mapa.carregar(bitmap, id)
            }

            selecionado = id
            status.text = "Mapa carregado"
            getSharedPreferences("config", 0)
                .edit().putString("ultimo", id).apply()

        } catch (e: Exception) {
            status.text = "Erro no mapa: ${e.message}"
        }
    }

    private fun ativarGps() {
        if (checkSelfPermission(
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                100
            )
            return
        }

        gps = getSystemService(Context.LOCATION_SERVICE)
                as LocationManager

        if (gps?.isProviderEnabled(
                LocationManager.GPS_PROVIDER
            ) != true
        ) {
            status.text = "Ligue a localização do Android"
            return
        }

        listener?.let { gps?.removeUpdates(it) }

        listener = LocationListener { local ->
            status.text = String.format(
                java.util.Locale.US,
                "GPS: %.6f, %.6f | Precisão: %.0f m",
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
            status.text = "Procurando sinal GPS..."
        } catch (e: SecurityException) {
            status.text = "Permissão de GPS necessária"
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
        ) {
            ativarGps()
        }
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

    private val linhas =
        mutableListOf<MutableList<PointF>>()
    private var atual: MutableList<PointF>? = null

    private var escala = 1f
    private var dx = 0f
    private var dy = 0f
    private var ultimoX = 0f
    private var ultimoY = 0f

    private val zoom = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(
                detector: ScaleGestureDetector
            ): Boolean {
                val antiga = escala
                escala = (escala * detector.scaleFactor)
                    .coerceIn(0.1f, 12f)
                val fator = escala / antiga

                dx = detector.focusX -
                    (detector.focusX - dx) * fator
                dy = detector.focusY -
                    (detector.focusY - dy) * fator

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

        post {
            escala = minOf(
                width.toFloat() / bitmap.width,
                height.toFloat() / bitmap.height
            ).coerceAtLeast(0.01f)

            dx = (width - bitmap.width * escala) / 2f
            dy = (height - bitmap.height * escala) / 2f

            restaurar()
            invalidate()
        }
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

        val pincel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
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
                caminho.lineTo(
                    linha[0].x + 0.1f,
                    linha[0].y
                )
            }

            for (i in 1 until linha.size) {
                caminho.lineTo(linha[i].x, linha[i].y)
            }

            canvas.drawPath(caminho, pincel)
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
            linha.any { ponto ->
                val a = ponto.x - x
                val b = ponto.y - y
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
                    JSONObject()
                        .put("x", p.x)
                        .put("y", p.y)
                )
            }

            dados.put(pontos)
        }

        context.getSharedPreferences("pinturas", 0)
            .edit()
            .putString(id, dados.toString())
            .apply()
    }

    private fun restaurar() {
        try {
            val texto = context
                .getSharedPreferences("pinturas", 0)
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
