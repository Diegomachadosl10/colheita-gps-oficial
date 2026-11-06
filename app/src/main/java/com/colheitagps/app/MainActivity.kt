
package com.colheitagps.app

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.graphics.pdf.PdfRenderer
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
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
    private lateinit var gpsNoMapa: TextView
    private lateinit var resultado: TextView
    private lateinit var seletor: Spinner
    private val ids = mutableListOf<String>()
    private val nomes = mutableListOf<String>()
    private var selecionado = ""
    private var preenchendo = false
    private var gps: LocationManager? = null
    private var listener: LocationListener? = null
    private var ultimaLat: Double? = null
    private var ultimaLon: Double? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tela = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            setOnApplyWindowInsetsListener { v, ins ->
                val b = ins.getInsets(WindowInsets.Type.systemBars())
                v.setPadding(b.left, b.top, b.right, b.bottom)
                ins
            }
        }
        tela.addView(TextView(this).apply {
            text = "COLHEITA GPS 1.4"
            textSize = 21f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(15, 108, 49))
        })
        status = TextView(this).apply {
            text = "GPS desligado"
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(Color.DKGRAY)
        }
        tela.addView(status)
        gpsNoMapa = TextView(this).apply {
            text = "Adicione um mapa GeoPDF"
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(20, 90, 180))
        }
        tela.addView(gpsNoMapa)

        val linha1 = LinearLayout(this)
        linha1.addView(botao("ADICIONAR PDF") { escolherPdf() }, peso())
        linha1.addView(botao("ATIVAR GPS") { ativarGps() }, peso())
        tela.addView(linha1)
        seletor = Spinner(this)
        tela.addView(seletor)

        val linha2 = LinearLayout(this)
        linha2.addView(botao("MOVER") { mapa.modo = 0 }, peso())
        linha2.addView(botao("PINTAR") { mapa.modo = 1 }, peso())
        linha2.addView(botao("BORRACHA") { mapa.modo = 2 }, peso())
        tela.addView(linha2)

        val linha3 = LinearLayout(this)
        linha3.addView(botao("ÁREA") { mapa.iniciarMedicao(3) }, peso())
        linha3.addView(botao("DISTÂNCIA") { mapa.iniciarMedicao(4) }, peso())
        linha3.addView(botao("CALIBRAR") { mapa.iniciarMedicao(5) }, peso())
        tela.addView(linha3)

        val linha4 = LinearLayout(this)
        linha4.addView(botao("DESFAZER") { mapa.desfazer() }, peso())
        linha4.addView(botao("CONCLUIR") { mapa.concluir() }, peso())
        linha4.addView(botao("PINTAR ÁREA") { mapa.pintarArea() }, peso())
        tela.addView(linha4)

        val linha5 = LinearLayout(this)
        linha5.addView(botao("CENTRAR GPS") { mapa.centrarNoGps() }, peso())
        linha5.addView(botao("ESCOLHER UNIDADE") { escolherUnidade() }, peso())
        tela.addView(linha5)

        resultado = TextView(this).apply {
            text = "Selecione uma ferramenta"
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(Color.BLACK)
        }
        tela.addView(resultado)
        mapa = MapaView(this)
        mapa.avisar = { resultado.text = it }
        mapa.avisarGps = { gpsNoMapa.text = it }
        tela.addView(mapa, LinearLayout.LayoutParams(-1, 0, 1f))
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

    private fun peso() = LinearLayout.LayoutParams(0, -2, 1f)

    private fun botao(nome: String, acao: () -> Unit) = Button(this).apply {
        text = nome
        textSize = 10f
        setPadding(1, 0, 1, 0)
        setOnClickListener { acao() }
    }

    private fun pastaMapas() = File(filesDir, "mapas").apply { mkdirs() }

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
            val temp = File(cacheDir, "importando.pdf")
            val digest = MessageDigest.getInstance("SHA-256")
            contentResolver.openInputStream(uri)!!.use { entrada ->
                FileOutputStream(temp).use { saida ->
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
            if (!destino.exists()) temp.copyTo(destino)
            temp.delete()
            getSharedPreferences("mapas", 0).edit()
                .putString(hash, nome).apply()
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
                    if (!preenchendo && position in ids.indices) {
                        val novo = ids[position]
                        if (novo != selecionado) abrirMapa(novo)
                    }
                }
            }
    }

    private fun atualizarLista() {
        preenchendo = true
        ids.clear()
        nomes.clear()
        val prefs = getSharedPreferences("mapas", 0)
        val arquivos = pastaMapas().listFiles()
            ?.filter { it.extension.equals("pdf", ignoreCase = true) }
            ?.sortedBy { it.name } ?: emptyList()

        for (f in arquivos) {
            val chave = f.nameWithoutExtension
            ids.add(chave)
            nomes.add(prefs.getString(chave, chave) ?: chave)
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
        val i = ids.indexOf(selecionado)
        if (i >= 0) seletor.setSelection(i)
        preenchendo = false
    }

    private fun abrirMapa(chave: String) {
        try {
            val arquivo = File(pastaMapas(), "$chave.pdf")
            val fd = ParcelFileDescriptor.open(
                arquivo,
                ParcelFileDescriptor.MODE_READ_ONLY
            )
            PdfRenderer(fd).use { pdf ->
                val pagina = pdf.openPage(0)
                try {
                    val wPdf = pagina.width
                    val hPdf = pagina.height
                    val fator = minOf(
                        2f,
                        3000f / maxOf(wPdf, hPdf)
                    )
                    val w = (wPdf * fator).toInt().coerceAtLeast(1)
                    val h = (hPdf * fator).toInt().coerceAtLeast(1)
                    val bitmap = Bitmap.createBitmap(
                        w, h, Bitmap.Config.ARGB_8888
                    )
                    bitmap.eraseColor(Color.WHITE)
                    pagina.render(
                        bitmap,
                        null,
                        null,
                        PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                    )
                    val geo = GeoPdf.ler(
                        arquivo,
                        wPdf.toDouble(),
                        hPdf.toDouble()
                    )
                    mapa.carregar(bitmap, chave, geo)
                } finally {
                    pagina.close()
                }
            }
            selecionado = chave
            getSharedPreferences("config", 0)
                .edit().putString("ultimo", chave).apply()
            status.text = "Mapa carregado"
            val lat = ultimaLat
            val lon = ultimaLon
            if (lat != null && lon != null) {
                mapa.atualizarGps(lat, lon)
            }
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
            .setItems(opcoes) { _, indice ->
                mapa.unidade = opcoes[indice]
                mapa.atualizarResultado()
            }.show()
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
            status.text = "Ative a localização do celular"
            return
        }

        listener?.let { gps?.removeUpdates(it) }

        listener = LocationListener { loc ->
            ultimaLat = loc.latitude
            ultimaLon = loc.longitude
            status.text = String.format(
                Locale.US,
                "GPS: %.6f, %.6f | Precisão: %.0f m",
                loc.latitude,
                loc.longitude,
                loc.accuracy
            )
            mapa.atualizarGps(loc.latitude, loc.longitude)
        }

        try {
            gps?.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                2000L,
                1f,
                listener!!
            )
            status.text = "Aguardando sinal GPS..."
        } catch (_: SecurityException) {
            status.text = "Permissão de GPS necessária"
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(
            requestCode,
            permissions,
            grantResults
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
    var unidade = "Hectares"
    var avisar: (String) -> Unit = {}
    var avisarGps: (String) -> Unit = {}

    private var imagem: Bitmap? = null
    private var id = ""
    private var geo: GeoPdf? = null
    private var gpsLat: Double? = null
    private var gpsLon: Double? = null
    private var escala = 1f
    private var dx = 0f
    private var dy = 0f
    private var ultimoX = 0f
    private var ultimoY = 0f
    private var metrosPorPixel = 0.0

    private val linhas = mutableListOf<MutableList<PointF>>()
    private val areas = mutableListOf<MutableList<PointF>>()
    private val pontos = mutableListOf<PointF>()
    private var atual: MutableList<PointF>? = null

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

    fun carregar(
        bitmap: Bitmap,
        chave: String,
        referencia: GeoPdf?
    ) {
        salvar()
        imagem?.recycle()
        imagem = bitmap
        id = chave
        geo = referencia
        linhas.clear()
        areas.clear()
        pontos.clear()
        metrosPorPixel = 0.0
        modo = 0
        restaurar()

        avisarGps(
            if (referencia != null) {
                "Mapa geográfico reconhecido"
            } else {
                "PDF sem georreferência automática reconhecível"
            }
        )

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

    private fun gpsEmPixels(): PointF? {
        val img = imagem ?: return null
        val lat = gpsLat ?: return null
        val lon = gpsLon ?: return null
        return geo?.gpsParaPixel(
            lat,
            lon,
            img.width,
            img.height
        )
    }

    fun atualizarGps(lat: Double, lon: Double) {
        gpsLat = lat
        gpsLon = lon

        avisarGps(
            when {
                imagem == null ->
                    "Abra um mapa para ver a localização"
                geo == null ->
                    "Este PDF não tem GeoPDF reconhecível"
                gpsEmPixels() == null ->
                    "GPS fora dos limites deste mapa"
                else ->
                    "Bolinha azul localizada no mapa"
            }
        )
        invalidate()
    }

    fun centrarNoGps() {
        val p = gpsEmPixels()
        if (p == null) {
            avisar(
                "GPS não está dentro de um mapa georreferenciado"
            )
            return
        }
        dx = width / 2f - p.x * escala
        dy = height / 2f - p.y * escala
        modo = 0
        invalidate()
        avisar("Mapa centralizado na localização")
    }

    fun iniciarMedicao(tipo: Int) {
        modo = tipo
        pontos.clear()
        unidade = if (tipo == 4) "Metros" else "Hectares"

        avisar(
            when (tipo) {
                3 -> "Toque nos cantos da área"
                4 -> "Toque nos pontos do trajeto"
                else -> "Marque 2 pontos com distância conhecida"
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
                pontos[0],
                pontos[1]
            )
            if (distanciaPixels <= 0.0) return

            val campo = EditText(context).apply {
                inputType =
                    android.text.InputType.TYPE_CLASS_NUMBER or
                    android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
                hint = "Distância real em metros"
            }

            android.app.AlertDialog.Builder(context)
                .setTitle("Calibrar escala aproximada")
                .setMessage(
                    "Informe a distância conhecida entre os dois pontos. " +
                    "Isto NÃO calibra a localização GPS no mapa."
                )
                .setView(campo)
                .setPositiveButton("Salvar") { _, _ ->
                    val metros = campo.text.toString()
                        .replace(",", ".")
                        .toDoubleOrNull()

                    if (metros != null && metros > 0.0) {
                        metrosPorPixel =
                            metros / distanciaPixels
                        pontos.clear()
                        salvar()
                        avisar(
                            "Escala de medidas aproximada salva"
                        )
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
            atualizarResultado()
            avisar(
                "Área fechada. Toque em PINTAR ÁREA"
            )
        } else {
            atualizarResultado()
        }
        invalidate()
    }

    fun pintarArea() {
        if (modo != 3 || pontos.size < 3) {
            avisar(
                "Escolha ÁREA e marque ao menos três pontos"
            )
            return
        }

        val medida = areaReal(pontos)
        areas.add(
            pontos.map {
                PointF(it.x, it.y)
            }.toMutableList()
        )
        pontos.clear()
        salvar()

        avisar(
            if (medida != null) {
                String.format(
                    Locale("pt", "BR"),
                    "Área pintada e salva: %.3f ha",
                    medida / 10000.0
                )
            } else {
                "Área pintada e salva (sem medida real)"
            }
        )
        invalidate()
    }

    private fun distancia(
        a: PointF,
        b: PointF
    ): Double {
        return hypot(
            (a.x - b.x).toDouble(),
            (a.y - b.y).toDouble()
        )
    }

    private fun areaPixels(
        p: List<PointF>
    ): Double {
        if (p.size < 3) return 0.0
        var soma = 0.0
        val base = p[0]

        for (i in 1 until p.size - 1) {
            soma += (
                (p[i].x - base.x).toDouble() *
                (p[i + 1].y - base.y) -
                (p[i + 1].x - base.x).toDouble() *
                (p[i].y - base.y)
            )
        }
        return abs(soma) / 2.0
    }

    private fun pontosEmMetros(
        p: List<PointF>
    ): List<Pair<Double, Double>>? {
        val ref = geo ?: return null
        val img = imagem ?: return null

        return p.map {
            ref.pixelParaMetros(
                it.x,
                it.y,
                img.width,
                img.height
            ) ?: return null
        }
    }

    private fun areaReal(
        p: List<PointF>
    ): Double? {
        if (p.size < 3) return null
        val metros = pontosEmMetros(p)

        if (metros != null) {
            val a = metros[0]
            var soma = 0.0

            for (i in 1 until metros.size - 1) {
                soma += (
                    (metros[i].first - a.first) *
                    (metros[i + 1].second - a.second) -
                    (metros[i + 1].first - a.first) *
                    (metros[i].second - a.second)
                )
            }
            return abs(soma) / 2.0
        }

        if (geo != null) return null
        if (metrosPorPixel <= 0.0) return null

        return areaPixels(p) *
            metrosPorPixel * metrosPorPixel
    }

    private fun distanciaReal(
        p: List<PointF>
    ): Double? {
        if (p.size < 2) return null
        val metros = pontosEmMetros(p)

        if (metros != null) {
            var soma = 0.0
            for (i in 1 until metros.size) {
                soma += hypot(
                    metros[i].first - metros[i - 1].first,
                    metros[i].second - metros[i - 1].second
                )
            }
            return soma
        }

        if (geo != null) return null
        if (metrosPorPixel <= 0.0) return null

        var soma = 0.0
        for (i in 1 until p.size) {
            soma += distancia(p[i - 1], p[i])
        }
        return soma * metrosPorPixel
    }

    fun atualizarResultado() {
        if (modo != 3 && modo != 4) return

        if (modo == 3 && pontos.size < 3) {
            avisar("Marque pelo menos três cantos")
            return
        }

        if (modo == 4 && pontos.size < 2) {
            avisar("Marque pelo menos dois pontos")
            return
        }

        if (modo == 3) {
            val m2 = areaReal(pontos)
            if (m2 == null) {
                avisar(
                    "Sem medida real: GeoPDF ou escala necessária"
                )
                return
            }

            val valor = when (unidade) {
                "Metros quadrados" -> m2
                "Quilômetros quadrados" ->
                    m2 / 1000000.0
                "Alqueires paulistas" ->
                    m2 / 24200.0
                else -> m2 / 10000.0
            }

            val simbolo = when (unidade) {
                "Metros quadrados" -> "m²"
                "Quilômetros quadrados" -> "km²"
                "Alqueires paulistas" -> "alq"
                else -> "ha"
            }

            avisar(
                String.format(
                    Locale("pt", "BR"),
                    "Área: %.4f %s",
                    valor,
                    simbolo
                )
            )
        } else {
            val metros = distanciaReal(pontos)
            if (metros == null) {
                avisar(
                    "Sem medida real: GeoPDF ou escala necessária"
                )
                return
            }

            val valor =
                if (unidade == "Quilômetros") {
                    metros / 1000.0
                } else {
                    metros
                }

            val simbolo =
                if (unidade == "Quilômetros") "km"
                else "m"

            avisar(
                String.format(
                    Locale("pt", "BR"),
                    "Distância: %.3f %s",
                    valor,
                    simbolo
                )
            )
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

        val amarelo = Paint(
            Paint.ANTI_ALIAS_FLAG
        ).apply {
            color = Color.argb(105, 255, 200, 0)
            style = Paint.Style.FILL
        }

        val contorno = Paint(
            Paint.ANTI_ALIAS_FLAG
        ).apply {
            color = Color.rgb(230, 145, 0)
            strokeWidth = 3f / escala
            style = Paint.Style.STROKE
        }

        for (area in areas) {
            if (area.size >= 3) {
                val caminho = caminho(area, true)
                canvas.drawPath(caminho, amarelo)
                canvas.drawPath(caminho, contorno)
            }
        }

        val pincel = Paint(
            Paint.ANTI_ALIAS_FLAG
        ).apply {
            color = Color.argb(100, 255, 200, 0)
            strokeWidth = 22f / escala
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        for (linha in linhas) {
            if (linha.isNotEmpty()) {
                canvas.drawPath(
                    caminho(linha, false),
                    pincel
                )
            }
        }

        if (pontos.isNotEmpty()) {
            val guia = Paint(
                Paint.ANTI_ALIAS_FLAG
            ).apply {
                color = Color.BLUE
                strokeWidth = 3f / escala
                style = Paint.Style.STROKE
            }

            canvas.drawPath(
                caminho(pontos, modo == 3),
                guia
            )

            val azul = Paint(
                Paint.ANTI_ALIAS_FLAG
            ).apply {
                color = Color.BLUE
            }

            for (p in pontos) {
                canvas.drawCircle(
                    p.x,
                    p.y,
                    5f / escala,
                    azul
                )
            }
        }

        gpsEmPixels()?.let { p ->
            val branco = Paint(
                Paint.ANTI_ALIAS_FLAG
            ).apply {
                color = Color.WHITE
            }

            val azul = Paint(
                Paint.ANTI_ALIAS_FLAG
            ).apply {
                color = Color.rgb(17, 108, 255)
            }

            canvas.drawCircle(
                p.x,
                p.y,
                13f / escala,
                branco
            )
            canvas.drawCircle(
                p.x,
                p.y,
                9f / escala,
                azul
            )
        }

        canvas.restore()
    }

    private fun caminho(
        p: List<PointF>,
        fechar: Boolean
    ): Path {
        val path = Path()
        if (p.isEmpty()) return path

        path.moveTo(p[0].x, p[0].y)

        if (p.size == 1) {
            path.lineTo(
                p[0].x + 0.1f,
                p[0].y
            )
        }

        for (i in 1 until p.size) {
            path.lineTo(p[i].x, p[i].y)
        }

        if (fechar) path.close()
        return path
    }

    override fun onTouchEvent(
        e: MotionEvent
    ): Boolean {
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
                        atual = mutableListOf(
                            PointF(x, y)
                        )
                        linhas.add(atual!!)
                    }

                    2 -> apagarPerto(x, y)

                    3, 4 -> {
                        pontos.add(PointF(x, y))
                        atualizarResultado()
                    }

                    5 -> {
                        if (pontos.size >= 2) {
                            pontos.clear()
                        }
                        pontos.add(PointF(x, y))
                        avisar(
                            "Pontos para escala: ${pontos.size}/2"
                        )
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
                if (modo == 1 || modo == 2) {
                    salvar()
                }
            }
        }

        invalidate()
        return true
    }

    private fun apagarPerto(
        x: Float,
        y: Float
    ) {
        linhas.removeAll { linha ->
            linha.any { p ->
                val ax = p.x - x
                val ay = p.y - y
                ax * ax + ay * ay < 900f
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

        context.getSharedPreferences(
            "pinturas", 0
        ).edit()
            .putString(id, dados.toString())
            .apply()
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
                dados.optJSONArray("linhas")?.let {
                    ler(it, linhas)
                }
                dados.optJSONArray("areas")?.let {
                    ler(it, areas)
                }
                metrosPorPixel =
                    dados.optDouble("escala", 0.0)
            }
        } catch (_: Exception) {
        }
    }
}

class GeoPdf private constructor(
    private val bbox: DoubleArray,
    private val lpts: DoubleArray,
    private val gpts: DoubleArray,
    private val larguraPdf: Double,
    private val alturaPdf: Double,
    private val zonaUtm: Int?,
    private val sul: Boolean
) {
    private val latReferencia =
        (0 until 4).map {
            gpts[it * 2]
        }.average()

    private val mundox = DoubleArray(4) { i ->
        projetar(
            gpts[i * 2],
            gpts[i * 2 + 1]
        ).first
    }

    private val mundoy = DoubleArray(4) { i ->
        projetar(
            gpts[i * 2],
            gpts[i * 2 + 1]
        ).second
    }

    private val lpx = DoubleArray(4) {
        lpts[it * 2]
    }

    private val lpy = DoubleArray(4) {
        lpts[it * 2 + 1]
    }

    private fun projetar(
        lat: Double,
        lon: Double
    ): Pair<Double, Double> {
        val zona = zonaUtm

        if (zona == null) {
            val r = 6378137.0
            return Pair(
                r * Math.toRadians(lon) *
                    cos(Math.toRadians(latReferencia)),
                r * Math.toRadians(lat)
            )
        }

        val a = 6378137.0
        val f = 1.0 / 298.257222101
        val e2 = f * (2 - f)
        val ep2 = e2 / (1 - e2)

        val phi = Math.toRadians(lat)
        val lambda = Math.toRadians(lon)
        val centro = Math.toRadians(
            zona * 6.0 - 183.0
        )

        val seno = sin(phi)
        val coss = cos(phi)
        val tan2 = tan(phi).pow(2)
        val c = ep2 * coss * coss
        val aa = coss * (lambda - centro)
        val n = a / sqrt(
            1 - e2 * seno * seno
        )

        val e4 = e2 * e2
        val e6 = e4 * e2

        val m = a * (
            (1 - e2 / 4 - 3 * e4 / 64 -
                5 * e6 / 256) * phi -
            (3 * e2 / 8 + 3 * e4 / 32 +
                45 * e6 / 1024) * sin(2 * phi) +
            (15 * e4 / 256 +
                45 * e6 / 1024) * sin(4 * phi) -
            (35 * e6 / 3072) * sin(6 * phi)
        )

        val easting = 500000.0 +
            0.9996 * n * (
                aa +
                (1 - tan2 + c) *
                    aa.pow(3) / 6 +
                (5 - 18 * tan2 +
                    tan2 * tan2 +
                    72 * c - 58 * ep2) *
                    aa.pow(5) / 120
            )

        var northing = 0.9996 * (
            m + n * tan(phi) * (
                aa * aa / 2 +
                (5 - tan2 + 9 * c +
                    4 * c * c) *
                    aa.pow(4) / 24 +
                (61 - 58 * tan2 +
                    tan2 * tan2 +
                    600 * c - 330 * ep2) *
                    aa.pow(6) / 720
            )
        )

        if (sul) northing += 10000000.0

        return Pair(easting, northing)
    }

    private fun bilinear(
        a: DoubleArray,
        u: Double,
        v: Double
    ): Double {
        return a[0] * (1 - u) * (1 - v) +
            a[1] * (1 - u) * v +
            a[2] * u * v +
            a[3] * u * (1 - v)
    }

    private fun inversa(
        x: Double,
        y: Double,
        xx: DoubleArray,
        yy: DoubleArray
    ): Pair<Double, Double>? {
        var u = 0.5
        var v = 0.5

        repeat(20) {
            val ex = bilinear(xx, u, v) - x
            val ey = bilinear(yy, u, v) - y

            val xu =
                (xx[3] - xx[0]) * (1 - v) +
                (xx[2] - xx[1]) * v

            val xv =
                (xx[1] - xx[0]) * (1 - u) +
                (xx[2] - xx[3]) * u

            val yu =
                (yy[3] - yy[0]) * (1 - v) +
                (yy[2] - yy[1]) * v

            val yv =
                (yy[1] - yy[0]) * (1 - u) +
                (yy[2] - yy[3]) * u

            val det = xu * yv - xv * yu
            if (abs(det) < 1e-12) return null

            val du = (ex * yv - ey * xv) / det
            val dv = (ey * xu - ex * yu) / det

            u -= du
            v -= dv

            if (!u.isFinite() || !v.isFinite()) {
                return null
            }

            if (abs(du) + abs(dv) < 1e-10) {
                return Pair(u, v)
            }
        }

        val erro = hypot(
            bilinear(xx, u, v) - x,
            bilinear(yy, u, v) - y
        )

        return if (erro < 0.1) {
            Pair(u, v)
        } else {
            null
        }
    }

    fun gpsParaPixel(
        lat: Double,
        lon: Double,
        w: Int,
        h: Int
    ): PointF? {
        val xy = projetar(lat, lon)

        val uv = inversa(
            xy.first,
            xy.second,
            mundox,
            mundoy
        ) ?: return null

        if (
            uv.first !in -0.0001..1.0001 ||
            uv.second !in -0.0001..1.0001
        ) {
            return null
        }

        val lx = bilinear(
            lpx,
            uv.first,
            uv.second
        )

        val ly = bilinear(
            lpy,
            uv.first,
            uv.second
        )

        if (
            lx !in -0.0001..1.0001 ||
            ly !in -0.0001..1.0001
        ) {
            return null
        }

        val pdfX = bbox[0] +
            lx * (bbox[2] - bbox[0])

        val pdfY = bbox[1] +
            ly * (bbox[3] - bbox[1])

        return PointF(
            (pdfX / larguraPdf * w).toFloat(),
            ((1 - pdfY / alturaPdf) * h).toFloat()
        )
    }

    fun pixelParaMetros(
        px: Float,
        py: Float,
        w: Int,
        h: Int
    ): Pair<Double, Double>? {
        val pdfX = px / w * larguraPdf
        val pdfY = (1 - py / h) * alturaPdf

        val lx = (
            pdfX - bbox[0]
        ) / (bbox[2] - bbox[0])

        val ly = (
            pdfY - bbox[1]
        ) / (bbox[3] - bbox[1])

        if (
            lx !in 0.0..1.0 ||
            ly !in 0.0..1.0
        ) {
            return null
        }

        val uv = inversa(
            lx,
            ly,
            lpx,
            lpy
        ) ?: return null

        if (
            uv.first !in -0.001..1.001 ||
            uv.second !in -0.001..1.001
        ) {
            return null
        }

        return Pair(
            bilinear(
                mundox,
                uv.first,
                uv.second
            ),
            bilinear(
                mundoy,
                uv.first,
                uv.second
            )
        )
    }

    companion object {
        private val numeros = Regex(
            """[-+]?(?:\d+(?:\.\d*)?|\.\d+)(?:[eE][-+]?\d+)?"""
        )

        private fun arrayDepois(
            texto: String,
            chave: String,
            inicio: Int
        ): DoubleArray? {
            val pos = texto.indexOf(
                chave,
                inicio
            )
            if (pos < 0) return null

            val abre = texto.indexOf(
                '[',
                pos + chave.length
            )

            if (
                abre < 0 ||
                abre - pos > 40
            ) {
                return null
            }

            val fecha = texto.indexOf(
                ']',
                abre + 1
            )

            if (
                fecha < 0 ||
                fecha - abre > 2500
            ) {
                return null
            }

            return numeros.findAll(
                texto.substring(
                    abre + 1,
                    fecha
                )
            ).mapNotNull {
                it.value.toDoubleOrNull()
            }.toList().toDoubleArray()
        }

        fun ler(
            arquivo: File,
            larguraPdf: Double,
            alturaPdf: Double
        ): GeoPdf? {
            return try {
                if (
                    arquivo.length() >
                    30000000L
                ) {
                    return null
                }

                val texto = arquivo.readBytes()
                    .toString(Charsets.ISO_8859_1)

                val indiceGps = texto.indexOf(
                    "/GPTS"
                )

                if (indiceGps < 0) {
                    return null
                }

                val indiceVp = texto.lastIndexOf(
                    "/VP",
                    indiceGps
                )

                val indiceBBox = texto.lastIndexOf(
                    "/BBox",
                    indiceGps
                )

                if (
                    indiceVp < 0 ||
                    indiceBBox < indiceVp ||
                    indiceGps - indiceVp > 15000
                ) {
                    return null
                }

                val bbox = arrayDepois(
                    texto,
                    "/BBox",
                    indiceBBox
                ) ?: return null

                val gpts = arrayDepois(
                    texto,
                    "/GPTS",
                    indiceGps
                ) ?: return null

                val lpts = arrayDepois(
                    texto,
                    "/LPTS",
                    indiceGps
                ) ?: return null

                if (
                    bbox.size != 4 ||
                    gpts.size != 8 ||
                    lpts.size != 8 ||
                    bbox[2] <= bbox[0] ||
                    bbox[3] <= bbox[1]
                ) {
                    return null
                }

                if (
                    (0 until 4).any {
                        abs(gpts[it * 2]) > 90.0 ||
                        abs(gpts[it * 2 + 1]) > 180.0
                    }
                ) {
                    return null
                }

                val trecho = texto.substring(
                    indiceVp,
                    minOf(
                        texto.length,
                        indiceGps + 500
                    )
                )

                val utm = Regex(
                    """UTM[_ ]Zone[_ ](\d{1,2})([NS])""",
                    RegexOption.IGNORE_CASE
                ).find(trecho)

                val zona = utm?.groupValues
                    ?.get(1)?.toIntOrNull()

                val sul = utm?.groupValues
                    ?.get(2)?.equals("S", true)
                    ?: true

                GeoPdf(
                    bbox,
                    lpts,
                    gpts,
                    larguraPdf,
                    alturaPdf,
                    zona,
                    sul
                )
            } catch (_: Exception) {
                null
            }
        }
    }
}
