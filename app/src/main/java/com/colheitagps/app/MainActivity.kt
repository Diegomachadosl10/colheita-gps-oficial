package com.colheitagps.app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
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
    private lateinit var gpsInfo: TextView
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

    private val fundo = Color.rgb(23, 29, 26)
    private val verde = Color.rgb(188, 222, 128)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val tela = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(fundo)
            setOnApplyWindowInsetsListener { v, ins ->
                val b = ins.getInsets(
                    WindowInsets.Type.systemBars()
                )
                v.setPadding(b.left, b.top, b.right, b.bottom)
                ins
            }
        }

        tela.addView(TextView(this).apply {
            text = "🪓 COLHEITA GPS 1.5"
            textSize = 21f
            gravity = Gravity.CENTER
            setTextColor(verde)
            setPadding(0, 8, 0, 8)
        })

        status = TextView(this).apply {
            text = "GPS desligado"
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(Color.LTGRAY)
        }
        tela.addView(status)

        gpsInfo = TextView(this).apply {
            text = "Adicione um mapa GeoPDF"
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(133, 181, 250))
        }
        tela.addView(gpsInfo)

        val topo = LinearLayout(this)
        topo.addView(
            botao("+ PDF") { escolherPdf() },
            peso()
        )
        topo.addView(
            botao("GPS") { ativarGps() },
            peso()
        )
        topo.addView(
            botao("CENTRAR") { mapa.centrarNoGps() },
            peso()
        )
        tela.addView(topo)

        seletor = Spinner(this)
        tela.addView(seletor)

        val ferramentas = LinearLayout(this)
        ferramentas.addView(
            botao("MOVER") { mapa.modo = 0 },
            peso()
        )
        ferramentas.addView(
            botao("PINTAR") { menuPintura() },
            peso()
        )
        ferramentas.addView(
            botao("MEDIR") { menuMedidas() },
            peso()
        )
        ferramentas.addView(
            botao("DESFAZER") { mapa.desfazer() },
            peso()
        )
        tela.addView(ferramentas)

        resultado = TextView(this).apply {
            text = "Selecione uma ferramenta"
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(4, 6, 4, 6)
        }
        tela.addView(resultado)

        mapa = MapaView(this)
        mapa.avisar = { resultado.text = it }
        mapa.avisarGps = { gpsInfo.text = it }

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
        nome: String,
        acao: () -> Unit
    ) = Button(this).apply {
        text = nome
        textSize = 10f
        setTextColor(Color.WHITE)
        setBackgroundTintList(
            android.content.res.ColorStateList.valueOf(
                Color.rgb(47, 70, 53)
            )
        )
        setPadding(1, 0, 1, 0)
        setOnClickListener { acao() }
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
        super.onActivityResult(
            requestCode,
            resultCode,
            data
        )

        if (requestCode != 10 || resultCode != RESULT_OK)
            return

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

            if (!destino.exists())
                temp.copyTo(destino)

            temp.delete()

            getSharedPreferences("mapas", 0)
                .edit()
                .putString(hash, nome)
                .apply()

            selecionado = hash
            atualizarLista()
            abrirMapa(hash)

        } catch (e: Exception) {
            status.text = "Erro ao importar: ${e.message}"
        }
    }

    private fun obterNome(uri: Uri): String {
        var nome = "Mapa PDF"

        contentResolver.query(
            uri, null, null, null, null
        )?.use {
            val c = it.getColumnIndex(
                android.provider.OpenableColumns.DISPLAY_NAME
            )
            if (c >= 0 && it.moveToFirst())
                nome = it.getString(c) ?: nome
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
                        if (novo != selecionado)
                            abrirMapa(novo)
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
            ?.filter {
                it.extension.equals("pdf", true)
            }
            ?.sortedBy { it.name }
            ?: emptyList()

        for (f in arquivos) {
            val chave = f.nameWithoutExtension
            ids.add(chave)
            nomes.add(
                prefs.getString(chave, chave) ?: chave
            )
        }

        if (nomes.isEmpty())
            nomes.add("Nenhum mapa")

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
        if (i >= 0)
            seletor.setSelection(i)

        preenchendo = false
    }

    private fun abrirMapa(chave: String) {
        try {
            val arquivo = File(
                pastaMapas(),
                "$chave.pdf"
            )

            val fd = ParcelFileDescriptor.open(
                arquivo,
                ParcelFileDescriptor.MODE_READ_ONLY
            )

            PdfRenderer(fd).use { pdf ->
                val pagina = pdf.openPage(0)

                try {
                    val wp = pagina.width
                    val hp = pagina.height

                    val fator = minOf(
                        3f,
                        4000f / maxOf(wp, hp)
                    )

                    val limite = minOf(
                        1f,
                        sqrt(
                            18000000.0 /
                                (wp.toDouble() * hp *
                                    fator * fator)
                        ).toFloat()
                    )

                    val w = (wp * fator * limite)
                        .toInt().coerceAtLeast(1)

                    val h = (hp * fator * limite)
                        .toInt().coerceAtLeast(1)

                    val bitmap = Bitmap.createBitmap(
                        w, h,
                        Bitmap.Config.ARGB_8888
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
                        wp.toDouble(),
                        hp.toDouble()
                    )

                    mapa.carregar(bitmap, chave, geo)

                } finally {
                    pagina.close()
                }
            }

            selecionado = chave

            getSharedPreferences("config", 0)
                .edit()
                .putString("ultimo", chave)
                .apply()

            status.text = "Mapa carregado"

            val lat = ultimaLat
            val lon = ultimaLon

            if (lat != null && lon != null)
                mapa.atualizarGps(lat, lon)

        } catch (e: Exception) {
            status.text = "Erro no PDF: ${e.message}"
        }
    }

    private fun menuPintura() {
        val opcoes = arrayOf(
            "Pincel",
            "Preencher área",
            "Retângulo",
            "Borracha",
            "Escolher cor",
            "Transparência",
            "Concluir pintura"
        )

        AlertDialog.Builder(this)
            .setTitle("Pintar talhões")
            .setItems(opcoes) { _, i ->
                when (i) {
                    0 -> mapa.modo = 1
                    1 -> mapa.iniciarMedicao(3)
                    2 -> mapa.iniciarMedicao(6)
                    3 -> mapa.modo = 2
                    4 -> escolherCor()
                    5 -> escolherTransparencia()
                    6 -> mapa.pintarArea()
                }
            }
            .show()
    }

    private fun escolherCor() {
        val nomes = arrayOf(
            "Amarelo",
            "Verde",
            "Azul",
            "Vermelho",
            "Laranja",
            "Roxo",
            "Rosa",
            "Ciano",
            "Cinza",
            "Branco"
        )

        val cores = intArrayOf(
            0xFFFACC15.toInt(),
            0xFF22C55E.toInt(),
            0xFF3B82F6.toInt(),
            0xFFEF4444.toInt(),
            0xFFF97316.toInt(),
            0xFFA855F7.toInt(),
            0xFFEC4899.toInt(),
            0xFF06B6D4.toInt(),
            0xFF94A3B8.toInt(),
            Color.WHITE
        )

        AlertDialog.Builder(this)
            .setTitle("Escolher cor")
            .setItems(nomes) { _, i ->
                mapa.corPintura = cores[i]
                mapa.modo = 1
                resultado.text = "Cor: ${nomes[i]}"
            }
            .show()
    }

    private fun escolherTransparencia() {
        val opcoes = arrayOf(
            "25%",
            "40%",
            "60%",
            "80%"
        )

        AlertDialog.Builder(this)
            .setTitle("Intensidade da pintura")
            .setItems(opcoes) { _, i ->
                mapa.opacidade = intArrayOf(
                    64, 102, 153, 204
                )[i]
            }
            .show()
    }

    private fun menuMedidas() {
        val opcoes = arrayOf(
            "Retângulo",
            "Área por pontos",
            "Distância",
            "Calibrar escala",
            "Concluir",
            "Pintar área",
            "Unidades"
        )

        AlertDialog.Builder(this)
            .setTitle("Ferramentas de medição")
            .setItems(opcoes) { _, i ->
                when (i) {
                    0 -> mapa.iniciarMedicao(6)
                    1 -> mapa.iniciarMedicao(3)
                    2 -> mapa.iniciarMedicao(4)
                    3 -> mapa.iniciarMedicao(5)
                    4 -> mapa.concluir()
                    5 -> mapa.pintarArea()
                    6 -> escolherUnidade()
                }
            }
            .show()
    }

    private fun escolherUnidade() {
        val opcoes = if (mapa.modo == 4)
            arrayOf("Metros", "Quilômetros")
        else
            arrayOf(
                "Hectares",
                "Metros quadrados",
                "Quilômetros quadrados",
                "Alqueires paulistas"
            )

        AlertDialog.Builder(this)
            .setTitle("Unidade")
            .setItems(opcoes) { _, i ->
                mapa.unidade = opcoes[i]
                mapa.atualizarResultado()
            }
            .show()
    }

    private fun ativarGps() {
        if (
            checkSelfPermission(
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION
                ),
                100
            )
            return
        }

        gps = getSystemService(
            Context.LOCATION_SERVICE
        ) as LocationManager

        if (
            gps?.isProviderEnabled(
                LocationManager.GPS_PROVIDER
            ) != true
        ) {
            status.text = "Ative a localização"
            return
        }

        listener?.let {
            gps?.removeUpdates(it)
        }

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

            mapa.atualizarGps(
                loc.latitude,
                loc.longitude
            )
        }

        try {
            gps?.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                2000L,
                1f,
                listener!!
            )

            status.text = "Aguardando GPS..."

        } catch (_: SecurityException) {
            status.text = "Permissão necessária"
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

        if (
            requestCode == 100 &&
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
        listener?.let {
            gps?.removeUpdates(it)
        }
        super.onDestroy()
    }
}

class MapaView(context: Context) : View(context) {
    var modo = 0
    var unidade = "Hectares"
    var corPintura = 0xFFFACC15.toInt()
    var opacidade = 105

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

    private val linhas =
        mutableListOf<MutableList<PointF>>()

    private val areas =
        mutableListOf<MutableList<PointF>>()

    private val coresLinhas = mutableListOf<Int>()
    private val coresAreas = mutableListOf<Int>()
    private val pontos = mutableListOf<PointF>()

    private var atual: MutableList<PointF>? = null
    private var gestoMultitoque = false

    private val zoom = ScaleGestureDetector(
        context,
        object :
            ScaleGestureDetector.SimpleOnScaleGestureListener() {

            override fun onScale(
                d: ScaleGestureDetector
            ): Boolean {
                val antiga = escala

                escala = (
                    escala * d.scaleFactor
                ).coerceIn(0.1f, 12f)

                val fator = escala / antiga

                dx = d.focusX -
                    (d.focusX - dx) * fator

                dy = d.focusY -
                    (d.focusY - dy) * fator

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
        coresLinhas.clear()
        coresAreas.clear()
        pontos.clear()

        metrosPorPixel = 0.0
        modo = 0

        restaurar()

        avisarGps(
            if (referencia != null)
                "Mapa geográfico reconhecido"
            else
                "Georreferência não reconhecida"
        )

        post {
            escala = minOf(
                width.toFloat() / bitmap.width,
                height.toFloat() / bitmap.height
            ).coerceAtLeast(0.01f)

            dx = (
                width - bitmap.width * escala
            ) / 2f

            dy = (
                height - bitmap.height * escala
            ) / 2f

            invalidate()
        }
    }

    private fun gpsEmPixels(): PointF? {
        val img = imagem ?: return null
        val lat = gpsLat ?: return null
        val lon = gpsLon ?: return null

        return geo?.gpsParaPixel(
            lat, lon,
            img.width, img.height
        )
    }

    fun atualizarGps(lat: Double, lon: Double) {
        gpsLat = lat
        gpsLon = lon

        avisarGps(
            when {
                imagem == null ->
                    "Abra um mapa"
                geo == null ->
                    "GeoPDF não reconhecido"
                gpsEmPixels() == null ->
                    "GPS fora deste mapa"
                else ->
                    "Bolinha azul no mapa"
            }
        )

        invalidate()
    }

    fun centrarNoGps() {
        val p = gpsEmPixels()

        if (p == null) {
            avisar("GPS não localizado no mapa")
            return
        }

        dx = width / 2f - p.x * escala
        dy = height / 2f - p.y * escala

        modo = 0
        invalidate()

        avisar("GPS centralizado")
    }

    fun iniciarMedicao(tipo: Int) {
        modo = tipo
        pontos.clear()

        unidade = if (tipo == 4)
            "Metros"
        else
            "Hectares"

        avisar(
            when (tipo) {
                3 -> "Toque nos cantos da área"
                4 -> "Toque nos pontos do trajeto"
                5 -> "Marque dois pontos conhecidos"
                6 -> "Arraste para desenhar o retângulo"
                else -> "Selecione pontos"
            }
        )

        invalidate()
    }

    fun desfazer() {
        if (pontos.isNotEmpty()) {
            pontos.removeAt(pontos.lastIndex)
        } else if (areas.isNotEmpty()) {
            areas.removeAt(areas.lastIndex)
            if (coresAreas.isNotEmpty())
                coresAreas.removeAt(coresAreas.lastIndex)
        } else if (linhas.isNotEmpty()) {
            linhas.removeAt(linhas.lastIndex)
            if (coresLinhas.isNotEmpty())
                coresLinhas.removeAt(coresLinhas.lastIndex)
        }

        salvar()
        atualizarResultado()
        invalidate()
    }

    fun concluir() {
        if (modo == 5) {
            if (pontos.size != 2) {
                avisar("Marque dois pontos")
                return
            }

            val distanciaPixels = distancia(
                pontos[0], pontos[1]
            )

            if (distanciaPixels <= 0.0)
                return

            val campo = EditText(context).apply {
                inputType =
                    android.text.InputType.TYPE_CLASS_NUMBER or
                    android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL

                hint = "Distância em metros"
            }

            AlertDialog.Builder(context)
                .setTitle("Calibrar medidas")
                .setMessage(
                    "Informe a distância real. " +
                    "Isso não calibra a posição GPS."
                )
                .setView(campo)
                .setPositiveButton("Salvar") { _, _ ->
                    val metros = campo.text.toString()
                        .replace(",", ".")
                        .toDoubleOrNull()

                    if (metros != null && metros > 0) {
                        metrosPorPixel =
                            metros / distanciaPixels

                        pontos.clear()
                        salvar()
                        avisar("Escala salva")
                        invalidate()
                    } else {
                        avisar("Distância inválida")
                    }
                }
                .setNegativeButton("Cancelar", null)
                .show()

            return
        }

        atualizarResultado()
        invalidate()
    }

    fun pintarArea() {
        if (
            (modo != 3 && modo != 6) ||
            pontos.size < 3
        ) {
            avisar("Marque uma área primeiro")
            return
        }

        val medida = areaReal(pontos)

        areas.add(
            pontos.map {
                PointF(it.x, it.y)
            }.toMutableList()
        )

        coresAreas.add(
            Color.argb(
                opacidade,
                Color.red(corPintura),
                Color.green(corPintura),
                Color.blue(corPintura)
            )
        )

        pontos.clear()
        salvar()

        avisar(
            if (medida != null)
                String.format(
                    Locale("pt", "BR"),
                    "Pintado: %.3f ha",
                    medida / 10000.0
                )
            else
                "Área pintada e salva"
        )

        invalidate()
    }

    private fun distancia(
        a: PointF,
        b: PointF
    ): Double = hypot(
        (a.x - b.x).toDouble(),
        (a.y - b.y).toDouble()
    )

    private fun areaPixels(
        p: List<PointF>
    ): Double {
        if (p.size < 3) return 0.0

        var soma = 0.0
        val base = p[0]

        for (i in 1 until p.size - 1) {
            soma +=
                (p[i].x - base.x).toDouble() *
                (p[i + 1].y - base.y) -
                (p[i + 1].x - base.x).toDouble() *
                (p[i].y - base.y)
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
                it.x, it.y,
                img.width, img.height
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
                soma +=
                    (metros[i].first - a.first) *
                    (metros[i + 1].second - a.second) -
                    (metros[i + 1].first - a.first) *
                    (metros[i].second - a.second)
            }

            return abs(soma) / 2.0
        }

        if (geo != null) return null
        if (metrosPorPixel <= 0) return null

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
                    metros[i].first -
                        metros[i - 1].first,
                    metros[i].second -
                        metros[i - 1].second
                )
            }

            return soma
        }

        if (geo != null) return null
        if (metrosPorPixel <= 0) return null

        var soma = 0.0

        for (i in 1 until p.size)
            soma += distancia(p[i - 1], p[i])

        return soma * metrosPorPixel
    }

    fun atualizarResultado() {
        if (modo != 3 && modo != 4 && modo != 6)
            return

        if (
            (modo == 3 || modo == 6) &&
            pontos.size < 3
        ) {
            avisar("Marque pelo menos três cantos")
            return
        }

        if (modo == 4 && pontos.size < 2) {
            avisar("Marque dois pontos")
            return
        }

        if (modo == 3 || modo == 6) {
            val m2 = areaReal(pontos)

            if (m2 == null) {
                avisar("Escala ou GeoPDF necessário")
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
                    valor, simbolo
                )
            )
        } else {
            val metros = distanciaReal(pontos)

            if (metros == null) {
                avisar("Escala ou GeoPDF necessário")
                return
            }

            val valor =
                if (unidade == "Quilômetros")
                    metros / 1000.0
                else metros

            val simbolo =
                if (unidade == "Quilômetros")
                    "km"
                else "m"

            avisar(
                String.format(
                    Locale("pt", "BR"),
                    "Distância: %.3f %s",
                    valor, simbolo
                )
            )
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        canvas.drawColor(
            Color.rgb(235, 235, 235)
        )

        canvas.save()
        canvas.translate(dx, dy)
        canvas.scale(escala, escala)

        imagem?.let {
            canvas.drawBitmap(it, 0f, 0f, null)
        }

        for (i in areas.indices) {
            val area = areas[i]
            if (area.size < 3) continue

            val cor = coresAreas.getOrElse(i) {
                Color.argb(105, 255, 200, 0)
            }

            val preenchimento = Paint(
                Paint.ANTI_ALIAS_FLAG
            ).apply {
                color = cor
                style = Paint.Style.FILL
            }

            val contorno = Paint(
                Paint.ANTI_ALIAS_FLAG
            ).apply {
                color = Color.rgb(
                    Color.red(cor),
                    Color.green(cor),
                    Color.blue(cor)
                )
                strokeWidth = 2f / escala
                style = Paint.Style.STROKE
            }

            val caminho = caminho(area, true)

            canvas.drawPath(
                caminho, preenchimento
            )
            canvas.drawPath(
                caminho, contorno
            )
        }

        for (i in linhas.indices) {
            val linha = linhas[i]
            if (linha.isEmpty()) continue

            val cor = coresLinhas.getOrElse(i) {
                Color.argb(105, 255, 200, 0)
            }

            val pincel = Paint(
                Paint.ANTI_ALIAS_FLAG
            ).apply {
                color = cor
                strokeWidth = 22f / escala
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }

            canvas.drawPath(
                caminho(linha, false),
                pincel
            )
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
                caminho(
                    pontos,
                    modo == 3 || modo == 6
                ),
                guia
            )

            val azul = Paint(
                Paint.ANTI_ALIAS_FLAG
            ).apply {
                color = Color.BLUE
            }

            for (p in pontos)
                canvas.drawCircle(
                    p.x, p.y,
                    6f / escala,
                    azul
                )
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
                p.x, p.y,
                13f / escala,
                branco
            )

            canvas.drawCircle(
                p.x, p.y,
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

        if (p.size == 1)
            path.lineTo(
                p[0].x + 0.1f,
                p[0].y
            )

        for (i in 1 until p.size)
            path.lineTo(
                p[i].x,
                p[i].y
            )

        if (fechar)
            path.close()

        return path
    }

    override fun onTouchEvent(
        e: MotionEvent
    ): Boolean {
        if (imagem == null) return true

        if (
            e.actionMasked ==
            MotionEvent.ACTION_POINTER_DOWN
        ) {
            gestoMultitoque = true
            atual = null
        }

        zoom.onTouchEvent(e)

        if (
            e.pointerCount > 1 ||
            gestoMultitoque
        ) {
            if (
                e.actionMasked == MotionEvent.ACTION_UP ||
                e.actionMasked == MotionEvent.ACTION_CANCEL
            ) {
                gestoMultitoque = false
            }

            invalidate()
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

                        coresLinhas.add(
                            Color.argb(
                                opacidade,
                                Color.red(corPintura),
                                Color.green(corPintura),
                                Color.blue(corPintura)
                            )
                        )
                    }

                    2 -> apagarPerto(x, y)

                    3, 4 -> {
                        pontos.add(PointF(x, y))
                        atualizarResultado()
                    }

                    5 -> {
                        if (pontos.size >= 2)
                            pontos.clear()

                        pontos.add(PointF(x, y))

                        avisar(
                            "Escala: ${pontos.size}/2"
                        )
                    }

                    6 -> {
                        pontos.clear()

                        repeat(4) {
                            pontos.add(
                                PointF(x, y)
                            )
                        }
                    }
                }
            }

            MotionEvent.ACTION_MOVE -> {
                when (modo) {
                    0 -> {
                        dx += e.x - ultimoX
                        dy += e.y - ultimoY
                    }

                    1 -> atual?.add(
                        PointF(x, y)
                    )

                    2 -> apagarPerto(x, y)

                    6 -> {
                        if (pontos.size == 4) {
                            val a = pontos[0]

                            pontos[1] = PointF(
                                a.x, y
                            )

                            pontos[2] = PointF(
                                x, y
                            )

                            pontos[3] = PointF(
                                x, a.y
                            )

                            atualizarResultado()
                        }
                    }
                }

                ultimoX = e.x
                ultimoY = e.y
            }

            MotionEvent.ACTION_UP -> {
                atual = null

                if (modo == 1 || modo == 2)
                    salvar()

                if (modo == 6)
                    atualizarResultado()
            }
        }

        invalidate()
        return true
    }

    private fun apagarPerto(
        x: Float,
        y: Float
    ) {
        for (i in linhas.indices.reversed()) {
            if (
                linhas[i].any { p ->
                    val ax = p.x - x
                    val ay = p.y - y

                    ax * ax + ay * ay < 900f
                }
            ) {
                linhas.removeAt(i)

                if (i < coresLinhas.size)
                    coresLinhas.removeAt(i)
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
            .put(
                "coresLinhas",
                JSONArray(coresLinhas)
            )
            .put(
                "coresAreas",
                JSONArray(coresAreas)
            )
            .put(
                "linhas",
                converter(linhas)
            )
            .put(
                "areas",
                converter(areas)
            )
            .put(
                "escala",
                metrosPorPixel
            )

        context.getSharedPreferences(
            "pinturas", 0
        ).edit()
            .putString(id, dados.toString())
            .apply()
    }

    private fun restaurar() {
        try {
            val texto = context.getSharedPreferences(
                "pinturas", 0
            ).getString(id, "") ?: ""

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

                metrosPorPixel = dados.optDouble(
                    "escala", 0.0
                )

                dados.optJSONArray("coresLinhas")?.let {
                    for (i in 0 until it.length())
                        coresLinhas.add(it.getInt(i))
                }

                dados.optJSONArray("coresAreas")?.let {
                    for (i in 0 until it.length())
                        coresAreas.add(it.getInt(i))
                }
            }

        } catch (_: Exception) {}
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
            (1 - e2 / 4 -
                3 * e4 / 64 -
                5 * e6 / 256) * phi -
            (3 * e2 / 8 +
                3 * e4 / 32 +
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
                    72 * c -
                    58 * ep2) *
                    aa.pow(5) / 120
            )

        var northing = 0.9996 * (
            m + n * tan(phi) * (
                aa * aa / 2 +
                (5 - tan2 +
                    9 * c +
                    4 * c * c) *
                    aa.pow(4) / 24 +
                (61 - 58 * tan2 +
                    tan2 * tan2 +
                    600 * c -
                    330 * ep2) *
                    aa.pow(6) / 720
            )
        )

        if (sul)
            northing += 10000000.0

        return Pair(easting, northing)
    }

    private fun bilinear(
        a: DoubleArray,
        u: Double,
        v: Double
    ): Double =
        a[0] * (1 - u) * (1 - v) +
        a[1] * (1 - u) * v +
        a[2] * u * v +
        a[3] * u * (1 - v)

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

            if (abs(det) < 1e-12)
                return null

            val du =
                (ex * yv - ey * xv) / det

            val dv =
                (ey * xu - ex * yu) / det

            u -= du
            v -= dv

            if (!u.isFinite() || !v.isFinite())
                return null

            if (abs(du) + abs(dv) < 1e-10)
                return Pair(u, v)
        }

        val erro = hypot(
            bilinear(xx, u, v) - x,
            bilinear(yy, u, v) - y
        )

        return if (erro < 0.1)
            Pair(u, v)
        else null
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
        ) return null

        val lx = bilinear(
            lpx, uv.first, uv.second
        )

        val ly = bilinear(
            lpy, uv.first, uv.second
        )

        if (
            lx !in -0.0001..1.0001 ||
            ly !in -0.0001..1.0001
        ) return null

        val pdfX = bbox[0] +
            lx * (bbox[2] - bbox[0])

        val pdfY = bbox[1] +
            ly * (bbox[3] - bbox[1])

        return PointF(
            (pdfX / larguraPdf * w).toFloat(),
            (
                (1 - pdfY / alturaPdf) * h
            ).toFloat()
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
        ) return null

        val uv = inversa(
            lx, ly, lpx, lpy
        ) ?: return null

        if (
            uv.first !in -0.001..1.001 ||
            uv.second !in -0.001..1.001
        ) return null

        return Pair(
            bilinear(
                mundox, uv.first, uv.second
            ),
            bilinear(
                mundoy, uv.first, uv.second
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
                chave, inicio
            )

            if (pos < 0) return null

            val abre = texto.indexOf(
                '[', pos + chave.length
            )

            if (
                abre < 0 ||
                abre - pos > 40
            ) return null

            val fecha = texto.indexOf(
                ']', abre + 1
            )

            if (
                fecha < 0 ||
                fecha - abre > 2500
            ) return null

            return numeros.findAll(
                texto.substring(
                    abre + 1, fecha
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
                    arquivo.length() > 30000000L
                ) return null

                val texto = arquivo.readBytes()
                    .toString(
                        Charsets.ISO_8859_1
                    )

                val indiceGps =
                    texto.indexOf("/GPTS")

                if (indiceGps < 0)
                    return null

                val indiceVp =
                    texto.lastIndexOf(
                        "/VP", indiceGps
                    )

                val indiceBBox =
                    texto.lastIndexOf(
                        "/BBox", indiceGps
                    )

                if (
                    indiceVp < 0 ||
                    indiceBBox < indiceVp ||
                    indiceGps - indiceVp > 15000
                ) return null

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
                ) return null

                if (
                    (0 until 4).any {
                        abs(gpts[it * 2]) > 90.0 ||
                        abs(gpts[it * 2 + 1]) > 180.0
                    }
                ) return null

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
                    ?.get(2)
                    ?.equals("S", true)
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