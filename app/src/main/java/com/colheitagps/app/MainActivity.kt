
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
import java.util.Locale
import kotlin.math.*

class MainActivity : Activity() {
    private lateinit var mapa: MapaView
    private lateinit var status: TextView
    private lateinit var resultado: TextView
    private lateinit var seletor: Spinner

    private val ids = mutableListOf<String>()
    private val nomes = mutableListOf<String>()
    private var selecionado = ""
    private var carregando = false
    private var gps: LocationManager? = null
    private var listener: LocationListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val tela = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            setOnApplyWindowInsetsListener { v, insets ->
                val b = insets.getInsets(
                    WindowInsets.Type.systemBars()
                )
                v.setPadding(b.left, b.top, b.right, b.bottom)
                insets
            }
        }

        val titulo = TextView(this).apply {
            text = "COLHEITA GPS 1.3"
            textSize = 21f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(20, 110, 50))
        }
        tela.addView(titulo)

        status = TextView(this).apply {
            text = "GPS desligado"
            gravity = Gravity.CENTER
            textSize = 12f
            setTextColor(Color.DKGRAY)
        }
        tela.addView(status)

        val linha1 = LinearLayout(this)
        linha1.addView(botao("ADICIONAR PDF") {
            escolherPdf()
        }, peso())
        linha1.addView(botao("ATIVAR GPS") {
            ativarGps()
        }, peso())
        tela.addView(linha1)

        seletor = Spinner(this)
        tela.addView(seletor)

        val linha2 = LinearLayout(this)
        linha2.addView(botao("MOVER") {
            mapa.modo = 0
        }, peso())
        linha2.addView(botao("PINTAR") {
            mapa.modo = 1
        }, peso())
        linha2.addView(botao("BORRACHA") {
            mapa.modo = 2
        }, peso())
        tela.addView(linha2)

        val linha3 = LinearLayout(this)
        linha3.addView(botao("ÁREA") {
            mapa.iniciarMedicao(3)
        }, peso())
        linha3.addView(botao("DISTÂNCIA") {
            mapa.iniciarMedicao(4)
        }, peso())
        linha3.addView(botao("CALIBRAR") {
            mapa.iniciarMedicao(5)
        }, peso())
        tela.addView(linha3)

        val linha4 = LinearLayout(this)
        linha4.addView(botao("DESFAZER") {
            mapa.desfazer()
        }, peso())
        linha4.addView(botao("CONCLUIR") {
            mapa.concluir()
        }, peso())
        linha4.addView(botao("PINTAR ÁREA") {
            mapa.pintarArea()
        }, peso())
        tela.addView(linha4)

        resultado = TextView(this).apply {
            text = "Selecione uma ferramenta"
            gravity = Gravity.CENTER
            textSize = 13f
            setTextColor(Color.BLACK)
        }
        tela.addView(resultado)

        val unidades = Button(this).apply {
            text = "ESCOLHER UNIDADE"
            textSize = 12f
            setOnClickListener { escolherUnidade() }
        }
        tela.addView(unidades)

        mapa = MapaView(this)
        mapa.avisar = { texto -> resultado.text = texto }
        tela.addView(
            mapa,
            LinearLayout.LayoutParams(-1, 0, 1f)
        )

        setContentView(tela)
        configurarSeletor()
        atualizarLista()

        val ultimo = getSharedPreferences("config", 0)
            .getString("ultimo", "") ?: ""
        if (ultimo in ids) {
            abrirMapa(ultimo)
            seletor.setSelection(ids.indexOf(ultimo))
        }
    }

    private fun peso() =
        LinearLayout.LayoutParams(0, -2, 1f)

    private fun botao(
        texto: String,
        acao: () -> Unit
    ): Button {
        return Button(this).apply {
            text = texto
            textSize = 10f
            setPadding(1, 0, 1, 0)
            setOnClickListener { acao() }
        }
    }

    private fun pastaMapas() =
        File(filesDir, "mapas").apply { mkdirs() }

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
            val arquivoTemp = File(cacheDir, "importando.pdf")
            val digest = MessageDigest.getInstance("SHA-256")

            contentResolver.openInputStream(uri)!!.use { entrada ->
                FileOutputStream(arquivoTemp).use { saida ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        val n = entrada.read(buffer)
                        if (n < 0) break
                        digest.update(buffer, 0, n)
                        saida.write(buffer, 0, n)
                    }
                }
            }

            val hash = digest.digest()
                .joinToString("") { "%02x".format(it) }

            val destino = File(pastaMapas(), "$hash.pdf")
            if (!destino.exists()) {
                arquivoTemp.copyTo(destino)
            }
            arquivoTemp.delete()

            getSharedPreferences("mapas", 0)
                .edit().putString(hash, nome).apply()

            selecionado = hash
            atualizarLista()
            abrirMapa(hash)

        } catch (e: Exception) {
            status.text = "Erro: ${e.message}"
        }
    }

    private fun obterNome(uri: Uri): String {
        var nome = "Mapa PDF"
        contentResolver.query(uri, null, null, null, null)?.use {
            val c = it.getColumnIndex(
                android.provider.OpenableColumns.DISPLAY_NAME
            )
            if (c >= 0 && it.moveToFirst()) {
                nome = it.getString(c) ?: nome
            }
        }
        return nome
    }

    private fun configurarSeletor() {
        seletor.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(
                    parent: AdapterView<*>?
                ) {}

                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    if (!carregando && position in ids.indices) {
                        val novo = ids[position]
                        if (novo != selecionado) abrirMapa(novo)
                    }
                }
            }
    }

    private fun atualizarLista() {
        carregando = true
        ids.clear()
        nomes.clear()

        val prefs = getSharedPreferences("mapas", 0)
        val arquivos = pastaMapas().listFiles()
            ?.filter { it.extension == "pdf" }
            ?.sortedBy { it.name } ?: emptyList()

        for (f in arquivos) {
            val id = f.nameWithoutExtension
            ids.add(id)
            nomes.add(prefs.getString(id, id) ?: id)
        }

        if (nomes.isEmpty()) nomes.add("Nenhum mapa")

        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            nomes
        )
        adapter.setDropDownViewResource(
            android.R.layout.simple_spinner_dropdown_item
        )
        seletor.adapter = adapter

        val pos = ids.indexOf(selecionado)
        if (pos >= 0) seletor.setSelection(pos)
        carregando = false
    }

    private fun abrirMapa(id: String) {
        try {
            val arquivo = File(pastaMapas(), "$id.pdf")
            val fd = ParcelFileDescriptor.open(
                arquivo, ParcelFileDescriptor.MODE_READ_ONLY
            )

            PdfRenderer(fd).use { pdf ->
                val pagina = pdf.openPage(0)
                val fator = minOf(
                    2f,
                    3000f / maxOf(pagina.width, pagina.height)
                )
                val w = (pagina.width * fator).toInt()
                    .coerceAtLeast(1)
                val h = (pagina.height * fator).toInt()
                    .coerceAtLeast(1)

                val bitmap = Bitmap.createBitmap(
                    w, h, Bitmap.Config.ARGB_8888
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
            getSharedPreferences("config", 0).edit()
                .putString("ultimo", id).apply()
            status.text = "Mapa carregado"

        } catch (e: Exception) {
            status.text = "Erro no PDF: ${e.message}"
        }
    }

    private fun escolherUnidade() {
        val opcoes = if (mapa.modo == 4) {
            arrayOf("Metros", "Quilômetros")
        } else {
            arrayOf(
                "Hectares",
                "Metros quadrados",
                "Quilômetros quadrados",
                "Alqueires paulistas"
            )
        }

        android.app.AlertDialog.Builder(this)
            .setTitle("Unidade de medida")
            .setItems(opcoes) { _, qual ->
                mapa.unidade = qual
                mapa.atualizarResultado()
            }
            .show()
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
            status.text = "Ative o GPS do celular"
            return
        }

        listener?.let { gps?.removeUpdates(it) }

        listener = LocationListener { local ->
            status.text = String.format(
                Locale.US,
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
    var unidade = "Hectares"
    var avisar: (String) -> Unit = {}

    private var imagem: Bitmap? = null
    private var id = ""
    private var escala = 1f
    private var dx = 0f
    private var dy = 0f
    private var ultimoX = 0f
    private var ultimoY = 0f
    private var metrosPorPixel = 0.0

    private val linhas =
        mutableListOf<MutableList<PointF>>()
    private val areas =
        mutableListOf<MutableList<PointF>>()
    private val pontos = mutableListOf<PointF>()
    private var atual: MutableList<PointF>? = null
    private var ultimoTipo = 3

    private val zoom = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(
                d: ScaleGestureDetector
            ): Boolean {
                val antiga = escala
                escala = (escala * d.scaleFactor)
                    .coerceIn(0.1f, 12f)
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
        areas.clear()
        pontos.clear()
        metrosPorPixel = 0.0
        modo = 0

        restaurar()

        post {
            escala = minOf(
                width.toFloat() / bitmap.width,
                height.toFloat() / bitmap.height
            ).coerceAtLeast(0.01f)
            dx = (width - bitmap.width * escala) / 2f
            dy = (height - bitmap.height * escala) / 2f
            invalidate()
        }
    }

    fun iniciarMedicao(tipo: Int) {
        modo = tipo
        if (tipo == 3 || tipo == 4) ultimoTipo = tipo
        pontos.clear()
        unidade = if (tipo == 4) "Metros" else "Hectares"
        avisar(
            when (tipo) {
                3 -> "Toque nos cantos da área"
                4 -> "Toque nos pontos do trajeto"
                else -> "Marque dois pontos de distância conhecida"
            }
        )
        invalidate()
    }

    fun desfazer() {
        if (pontos.isNotEmpty()) {
            pontos.removeAt(pontos.lastIndex)
        } else if (areas.isNotEmpty()) {
            areas.removeAt(areas.lastIndex)
        } else if (linhas.isNotEmpty()) {
            linhas.removeAt(linhas.lastIndex)
        }
        salvar()
        atualizarResultado()
        invalidate()
    }

    fun concluir() {
        if (modo == 5) {
            if (pontos.size != 2) {
                avisar("Marque exatamente dois pontos")
                return
            }
            val distanciaPixels = distancia(
                pontos[0], pontos[1]
            )
            if (distanciaPixels <= 0.0) return

            val campo = EditText(context).apply {
                inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                    android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
                hint = "Distância real em metros"
            }

            android.app.AlertDialog.Builder(context)
                .setTitle("Calibrar escala")
                .setMessage(
                    "Informe a distância real entre os dois pontos. " +
                    "Use uma distância conhecida e confiável."
                )
                .setView(campo)
                .setPositiveButton("Salvar") { _, _ ->
                    val metros = campo.text.toString()
                        .replace(",", ".").toDoubleOrNull()
                    if (metros != null && metros > 0.0) {
                        metrosPorPixel = metros / distanciaPixels
                        pontos.clear()
                        salvar()
                        avisar("Escala aproximada calibrada")
                        invalidate()
                    } else {
                        avisar("Distância inválida")
                    }
                }
                .setNegativeButton("Cancelar", null)
                .show()
            return
        }

        if (modo == 3 && pontos.size >= 3) {
            avisar("Área fechada. Toque em PINTAR ÁREA")
        } else {
            atualizarResultado()
        }
        invalidate()
    }

    fun pintarArea() {
        if (modo != 3 || pontos.size < 3) {
            avisar("Selecione ÁREA e marque pelo menos 3 pontos")
            return
        }
        areas.add(pontos.map { PointF(it.x, it.y) }
            .toMutableList())
        pontos.clear()
        salvar()
        avisar("Área pintada e salva")
        invalidate()
    }

    private fun distancia(a: PointF, b: PointF): Double {
        return hypot(
            (a.x - b.x).toDouble(),
            (a.y - b.y).toDouble()
        )
    }

    private fun areaPixels(p: List<PointF>): Double {
        if (p.size < 3) return 0.0
        var soma = 0.0
        for (i in p.indices) {
            val a = p[i]
            val b = p[(i + 1) % p.size]
            soma += a.x.toDouble() * b.y -
                b.x.toDouble() * a.y
        }
        return abs(soma) / 2.0
    }

    fun atualizarResultado() {
        if (modo != 3 && modo != 4) return

        if (metrosPorPixel <= 0.0) {
            avisar("Calibre a escala antes de medir")
            return
        }

        if (modo == 3) {
            val m2 = areaPixels(pontos) *
                metrosPorPixel * metrosPorPixel

            val valor = when (unidade) {
                "Metros quadrados" -> m2
                "Quilômetros quadrados" -> m2 / 1000000.0
                "Alqueires paulistas" -> m2 / 24200.0
                else -> m2 / 10000.0
            }
            val simbolo = when (unidade) {
                "Metros quadrados" -> "m²"
                "Quilômetros quadrados" -> "km²"
                "Alqueires paulistas" -> "alq"
                else -> "ha"
            }
            avisar(String.format(
                Locale("pt", "BR"),
                "Área aproximada: %.4f %s",
                valor, simbolo
            ))
        } else {
            var metros = 0.0
            for (i in 1 until pontos.size) {
                metros += distancia(
                    pontos[i - 1], pontos[i]
                ) * metrosPorPixel
            }
            val valor = if (unidade == "Quilômetros")
                metros / 1000.0 else metros
            val simbolo = if (unidade == "Quilômetros")
                "km" else "m"
            avisar(String.format(
                Locale("pt", "BR"),
                "Distância aproximada: %.3f %s",
                valor, simbolo
            ))
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

        val amarelo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(105, 255, 200, 0)
            style = Paint.Style.FILL
        }
        val contorno = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(230, 145, 0)
            strokeWidth = 3f
            style = Paint.Style.STROKE
        }

        for (area in areas) {
            if (area.size < 3) continue
            val path = criarCaminho(area, true)
            canvas.drawPath(path, amarelo)
            canvas.drawPath(path, contorno)
        }

        val pincel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(100, 255, 200, 0)
            strokeWidth = 22f
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        for (linha in linhas) {
            if (linha.isNotEmpty()) {
                canvas.drawPath(
                    criarCaminho(linha, false), pincel
                )
            }
        }

        if (pontos.isNotEmpty()) {
            val guia = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLUE
                strokeWidth = 3f
                style = Paint.Style.STROKE
            }
            canvas.drawPath(
                criarCaminho(pontos, modo == 3),
                guia
            )
            val ponto = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLUE
            }
            for (p in pontos) {
                canvas.drawCircle(p.x, p.y, 6f, ponto)
            }
        }

        canvas.restore()
    }

    private fun criarCaminho(
        lista: List<PointF>,
        fechar: Boolean
    ): Path {
        val path = Path()
        if (lista.isEmpty()) return path
        path.moveTo(lista[0].x, lista[0].y)
        if (lista.size == 1) {
            path.lineTo(lista[0].x + 0.1f, lista[0].y)
        }
        for (i in 1 until lista.size) {
            path.lineTo(lista[i].x, lista[i].y)
        }
        if (fechar) path.close()
        return path
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
                when (modo) {
                    1 -> {
                        atual = mutableListOf(PointF(x, y))
                        linhas.add(atual!!)
                    }
                    2 -> apagarPerto(x, y)
                    3, 4 -> {
                        pontos.add(PointF(x, y))
                        atualizarResultado()
                    }
                    5 -> {
                        if (pontos.size >= 2) pontos.clear()
                        pontos.add(PointF(x, y))
                        avisar("Pontos de calibração: ${pontos.size}/2")
                    }
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
                if (modo == 1 || modo == 2) salvar()
            }
        }
        invalidate()
        return true
    }

    private fun apagarPerto(x: Float, y: Float) {
        linhas.removeAll { linha ->
            linha.any {
                val a = it.x - x
                val b = it.y - y
                a * a + b * b < 900f
            }
        }
        salvar()
    }

    fun salvar() {
        if (id.isEmpty()) return

        fun converter(
            colecao: List<List<PointF>>
        ): JSONArray {
            val dados = JSONArray()
            for (linha in colecao) {
                val arr = JSONArray()
                for (p in linha) {
                    arr.put(
                        JSONObject()
                            .put("x", p.x)
                            .put("y", p.y)
                    )
                }
                dados.put(arr)
            }
            return dados
        }

        val dados = JSONObject()
            .put("linhas", converter(linhas))
            .put("areas", converter(areas))
            .put("escala", metrosPorPixel)

        context.getSharedPreferences("pinturas", 0)
            .edit().putString(id, dados.toString()).apply()
    }

    private fun restaurar() {
        try {
            val texto = context
                .getSharedPreferences("pinturas", 0)
                .getString(id, "") ?: ""
            if (texto.isEmpty()) return

            fun ler(
                dados: JSONArray,
                destino: MutableList<MutableList<PointF>>
            ) {
                for (i in 0 until dados.length()) {
                    val arr = dados.getJSONArray(i)
                    val linha = mutableListOf<PointF>()
                    for (j in 0 until arr.length()) {
                        val p = arr.getJSONObject(j)
                        linha.add(
                            PointF(
                                p.getDouble("x").toFloat(),
                                p.getDouble("y").toFloat()
                            )
                        )
                    }
                    destino.add(linha)
                }
            }

            if (texto.trim().startsWith("[")) {
                ler(JSONArray(texto), linhas)
            } else {
                val dados = JSONObject(texto)
                ler(dados.getJSONArray("linhas"), linhas)
                ler(dados.getJSONArray("areas"), areas)
                metrosPorPixel =
                    dados.optDouble("escala", 0.0)
            }
        } catch (_: Exception) {}
    }
}
