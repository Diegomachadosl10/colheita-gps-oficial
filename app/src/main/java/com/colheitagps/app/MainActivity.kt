
package com.colheitagps.app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.*
import android.graphics.pdf.PdfRenderer
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
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
    private lateinit var titulo: TextView
    private lateinit var gpsTexto: TextView
    private lateinit var mensagem: TextView
    private lateinit var ferramentas: LinearLayout
    private lateinit var acoes: LinearLayout

    private val verde = Color.rgb(39, 118, 70)
    private val fundo = Color.rgb(22, 30, 27)
    private val arquivos = mutableListOf<Pair<String, String>>()

    private var mapaAtual = ""
    private var gpsManager: LocationManager? = null
    private var gpsListener: LocationListener? = null
    private var ultimaLat: Double? = null
    private var ultimaLon: Double? = null

    private fun dp(n: Int): Int =
        (n * resources.displayMetrics.density).toInt()

    private fun texto(
        valor: String,
        tamanho: Float = 14f
    ): TextView = TextView(this).apply {
        text = valor
        textSize = tamanho
        setTextColor(Color.WHITE)
        gravity = Gravity.CENTER_VERTICAL
    }

    private fun botao(
        nome: String,
        acao: () -> Unit
    ): Button = Button(this).apply {
        text = nome
        textSize = 12f
        isAllCaps = false
        setTextColor(Color.WHITE)
        backgroundTintList =
            ColorStateList.valueOf(verde)
        setOnClickListener { acao() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = fundo
        window.navigationBarColor = fundo

        val raiz = FrameLayout(this).apply {
            setBackgroundColor(fundo)
        }

        mapa = MapaView(this)
        raiz.addView(
            mapa,
            FrameLayout.LayoutParams(-1, -1)
        )

        val cabecalho = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setBackgroundColor(Color.argb(230, 22, 30, 27))
        }

        titulo = texto("🪓 Colheita GPS", 17f)
        titulo.setOnClickListener { escolherMapa() }
        cabecalho.addView(titulo)

        gpsTexto = texto("GPS não iniciado", 12f)
        gpsTexto.setTextColor(Color.rgb(185, 225, 193))
        cabecalho.addView(gpsTexto)

        raiz.addView(
            cabecalho,
            FrameLayout.LayoutParams(
                -1, -2, Gravity.TOP
            )
        )

        mensagem = texto(
            "Abra um mapa PDF para começar", 12f
        ).apply {
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setBackgroundColor(
                Color.argb(225, 22, 30, 27)
            )
        }

        val msgParams = FrameLayout.LayoutParams(
            -1, -2, Gravity.TOP
        ).apply {
            topMargin = dp(75)
            leftMargin = dp(12)
            rightMargin = dp(12)
        }
        raiz.addView(mensagem, msgParams)

        val rodape = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(4), dp(8), dp(8))
        }

        acoes = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            visibility = View.GONE
            setBackgroundColor(
                Color.argb(235, 22, 30, 27)
            )
        }

        rodape.addView(acoes)

        ferramentas = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            visibility = View.GONE
            setBackgroundColor(
                Color.argb(240, 22, 30, 27)
            )
        }

        fun linha(vararg botoes: Button) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            botoes.forEach { b ->
                row.addView(
                    b,
                    LinearLayout.LayoutParams(
                        0, dp(49), 1f
                    )
                )
            }
            ferramentas.addView(row)
        }

        linha(
            botao("MAPAS") {
                esconderMenu()
                escolherMapa()
            },
            botao("GPS") {
                esconderMenu()
                iniciarGps()
            },
            botao("CENTRAR") {
                esconderMenu()
                mapa.centrarGps()
            }
        )

        linha(
            botao("PINTAR") {
                esconderMenu()
                menuPintura()
            },
            botao("MEDIR") {
                esconderMenu()
                menuMedidas()
            },
            botao("DESFAZER") {
                esconderMenu()
                mapa.desfazer()
            }
        )

        rodape.addView(ferramentas)

        val principal = botao("✚ FERRAMENTAS") {
            ferramentas.visibility =
                if (ferramentas.visibility == View.VISIBLE)
                    View.GONE else View.VISIBLE
        }

        rodape.addView(
            principal,
            LinearLayout.LayoutParams(
                dp(190), dp(55)
            )
        )

        raiz.addView(
            rodape,
            FrameLayout.LayoutParams(
                -1, -2, Gravity.BOTTOM
            )
        )

        mapa.avisar = { mensagem.text = it }
        mapa.avisarGps = { gpsTexto.text = it }

        setContentView(raiz)

        atualizarLista()

        val ultimo = getSharedPreferences(
            "config", 0
        ).getString("ultimo", "") ?: ""

        if (ultimo.isNotEmpty()) {
            abrirMapa(ultimo)
        }
    }

    private fun esconderMenu() {
        ferramentas.visibility = View.GONE
    }

    private fun mostrarAcoes(
        vararg opcoes: Pair<String, () -> Unit>
    ) {
        acoes.removeAllViews()

        if (opcoes.isEmpty()) {
            acoes.visibility = View.GONE
            return
        }

        acoes.visibility = View.VISIBLE

        opcoes.forEach { opcao ->
            acoes.addView(
                botao(opcao.first, opcao.second),
                LinearLayout.LayoutParams(
                    0, dp(49), 1f
                )
            )
        }
    }

    private fun pastaMapas(): File =
        File(filesDir, "mapas").apply {
            mkdirs()
        }

    private fun atualizarLista() {
        arquivos.clear()

        val preferencias =
            getSharedPreferences("mapas", 0)

        pastaMapas().listFiles()
            ?.filter {
                it.extension.equals("pdf", true)
            }
            ?.sortedBy {
                preferencias.getString(
                    it.nameWithoutExtension, it.name
                )
            }
            ?.forEach {
                arquivos.add(
                    it.nameWithoutExtension to
                    (preferencias.getString(
                        it.nameWithoutExtension,
                        it.name
                    ) ?: it.name)
                )
            }
    }

    private fun escolherMapa() {
        atualizarLista()

        val opcoes = mutableListOf(
            "＋ Importar novo mapa PDF"
        )

        opcoes.addAll(
            arquivos.map { it.second }
        )

        AlertDialog.Builder(this)
            .setTitle("Meus mapas")
            .setItems(opcoes.toTypedArray()) { _, i ->
                if (i == 0) importarPdf()
                else abrirMapa(arquivos[i - 1].first)
            }
            .setNegativeButton("Fechar", null)
            .show()
    }

    private fun importarPdf() {
        val intent = Intent(
            Intent.ACTION_OPEN_DOCUMENT
        ).apply {
            type = "application/pdf"
            addCategory(Intent.CATEGORY_OPENABLE)
        }

        startActivityForResult(intent, 10)
    }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(
            requestCode, resultCode, data
        )

        if (requestCode != 10 ||
            resultCode != RESULT_OK) return

        val uri = data?.data ?: return

        try {
            var nome = "Mapa PDF"

            contentResolver.query(
                uri, null, null, null, null
            )?.use { cursor ->
                val coluna = cursor.getColumnIndex(
                    OpenableColumns.DISPLAY_NAME
                )
                if (coluna >= 0 &&
                    cursor.moveToFirst()) {
                    nome = cursor.getString(coluna)
                        ?: nome
                }
            }

            val temporario = File(
                cacheDir, "importacao.pdf"
            )

            val digest = MessageDigest.getInstance(
                "SHA-256"
            )

            contentResolver.openInputStream(uri)!!
                .use { entrada ->
                    FileOutputStream(temporario)
                        .use { saida ->
                            val buffer = ByteArray(8192)
                            while (true) {
                                val n = entrada.read(buffer)
                                if (n < 0) break
                                digest.update(buffer, 0, n)
                                saida.write(buffer, 0, n)
                            }
                        }
                }

            val chave = digest.digest()
                .joinToString("") {
                    "%02x".format(it)
                }

            val destino = File(
                pastaMapas(), "$chave.pdf"
            )

            if (!destino.exists()) {
                temporario.copyTo(destino)
            }

            temporario.delete()

            getSharedPreferences("mapas", 0)
                .edit()
                .putString(chave, nome)
                .apply()

            atualizarLista()
            abrirMapa(chave)

        } catch (e: Exception) {
            mensagem.text =
                "Erro ao importar: ${e.message}"
        }
    }

    private fun abrirMapa(chave: String) {
        val arquivo = File(
            pastaMapas(), "$chave.pdf"
        )

        if (!arquivo.exists()) return

        try {
            val fd = ParcelFileDescriptor.open(
                arquivo,
                ParcelFileDescriptor.MODE_READ_ONLY
            )

            PdfRenderer(fd).use { pdf ->
                val pagina = pdf.openPage(0)

                try {
                    val w = pagina.width
                    val h = pagina.height

                    val fator = minOf(
                        3.0,
                        4000.0 / maxOf(w, h)
                    )

                    val limite = minOf(
                        1.0,
                        sqrt(
                            16000000.0 /
                            (w.toDouble() * h *
                             fator * fator)
                        )
                    )

                    val bw = maxOf(
                        1, (w * fator * limite).toInt()
                    )

                    val bh = maxOf(
                        1, (h * fator * limite).toInt()
                    )

                    val bitmap = Bitmap.createBitmap(
                        bw, bh,
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
                        w.toDouble(),
                        h.toDouble()
                    )

                    mapa.carregar(
                        bitmap, chave, geo
                    )
                } finally {
                    pagina.close()
                }
            }

            mapaAtual = chave

            val nome = getSharedPreferences(
                "mapas", 0
            ).getString(chave, "Mapa") ?: "Mapa"

            titulo.text = "🪓 $nome"

            getSharedPreferences("config", 0)
                .edit()
                .putString("ultimo", chave)
                .apply()

            ultimaLat?.let { lat ->
                ultimaLon?.let { lon ->
                    mapa.atualizarGps(lat, lon)
                }
            }

            mensagem.text = "Mapa carregado"

        } catch (e: Exception) {
            mensagem.text =
                "Erro ao abrir PDF: ${e.message}"
        }
    }

    private fun menuPintura() {
        val itens = arrayOf(
            "Preencher talhão com mira central",
            "Desenhar polígono tocando no mapa",
            "Pintar com pincel",
            "Apagar pintura",
            "Escolher cor",
            "Ajustar transparência"
        )

        AlertDialog.Builder(this)
            .setTitle("Pintura de talhões")
            .setItems(itens) { _, i ->
                when (i) {
                    0 -> iniciarMira()
                    1 -> iniciarPoligono()
                    2 -> {
                        mapa.modo = 1
                        mostrarAcoes(
                            "CONCLUIR" to {
                                mapa.modo = 0
                                mostrarAcoes()
                            }
                        )
                    }
                    3 -> {
                        mapa.modo = 2
                        mostrarAcoes(
                            "CONCLUIR" to {
                                mapa.modo = 0
                                mostrarAcoes()
                            }
                        )
                    }
                    4 -> escolherCor()
                    5 -> escolherOpacidade()
                }
            }.show()
    }

    private fun iniciarMira() {
        mapa.iniciar(7)

        mostrarAcoes(
            "＋ PONTO" to {
                mapa.adicionarPontoCentral()
            },
            "DESFAZER" to {
                mapa.desfazer()
            },
            "✓ PINTAR" to {
                confirmarPintura()
            }
        )
    }

    private fun iniciarPoligono() {
        mapa.iniciar(3)

        mostrarAcoes(
            "DESFAZER" to {
                mapa.desfazer()
            },
            "✓ PINTAR" to {
                confirmarPintura()
            }
        )
    }

    private fun confirmarPintura() {
        if (mapa.pintarArea()) {
            mapa.modo = 0
            mostrarAcoes()
        }
    }

    private fun escolherCor() {
        val nomes = arrayOf(
            "Amarelo", "Verde", "Azul",
            "Vermelho", "Laranja", "Roxo",
            "Rosa", "Ciano", "Cinza", "Branco"
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
            .setTitle("Cor da pintura")
            .setItems(nomes) { _, i ->
                mapa.corPintura = cores[i]
                mensagem.text =
                    "Cor selecionada: ${nomes[i]}"
            }
            .show()
    }

    private fun escolherOpacidade() {
        val nomes = arrayOf(
            "25% — bem transparente",
            "40% — recomendado",
            "60% — intermediário",
            "80% — forte"
        )

        val valores = intArrayOf(
            64, 102, 153, 204
        )

        AlertDialog.Builder(this)
            .setTitle("Transparência")
            .setItems(nomes) { _, i ->
                mapa.opacidade = valores[i]
                mensagem.text =
                    "Transparência ajustada"
            }
            .show()
    }

    private fun menuMedidas() {
        val itens = arrayOf(
            "Medir área",
            "Medir distância",
            "Calibrar escala aproximada"
        )

        AlertDialog.Builder(this)
            .setTitle("Ferramentas de medição")
            .setItems(itens) { _, i ->
                when (i) {
                    0 -> mapa.iniciar(3)
                    1 -> mapa.iniciar(4)
                    2 -> mapa.iniciar(5)
                }

                mostrarAcoes(
                    "DESFAZER" to {
                        mapa.desfazer()
                    },
                    "CONCLUIR" to {
                        mapa.concluir()
                    }
                )
            }
            .show()
    }

    private fun iniciarGps() {
        if (checkSelfPermission(
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION
                ), 100
            )
            return
        }

        gpsManager = getSystemService(
            Context.LOCATION_SERVICE
        ) as LocationManager

        if (gpsManager?.isProviderEnabled(
                LocationManager.GPS_PROVIDER
            ) != true
        ) {
            gpsTexto.text =
                "Ative a localização do celular"
            return
        }

        gpsListener?.let {
            gpsManager?.removeUpdates(it)
        }

        gpsListener = LocationListener { local ->
            ultimaLat = local.latitude
            ultimaLon = local.longitude

            gpsTexto.text = String.format(
                Locale("pt", "BR"),
                "GPS ±%.0f m • %.6f, %.6f",
                local.accuracy,
                local.latitude,
                local.longitude
            )

            mapa.atualizarGps(
                local.latitude,
                local.longitude
            )
        }

        try {
            gpsManager?.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                2000L,
                1f,
                gpsListener!!
            )
            gpsTexto.text =
                "Procurando sinal GPS..."
        } catch (_: SecurityException) {
            gpsTexto.text =
                "Permissão de localização necessária"
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
            iniciarGps()
        }
    }

    override fun onPause() {
        super.onPause()
        mapa.salvar()
    }

    override fun onDestroy() {
        gpsListener?.let {
            gpsManager?.removeUpdates(it)
        }
        super.onDestroy()
    }
}

class MapaView(context: Context) : View(context) {

    var modo = 0
    var corPintura = 0xFFFACC15.toInt()
    var opacidade = 102

    var avisar: (String) -> Unit = {}
    var avisarGps: (String) -> Unit = {}

    private var imagem: Bitmap? = null
    private var identificador = ""
    private var geo: GeoPdf? = null

    private var escala = 1f
    private var dx = 0f
    private var dy = 0f

    private var ultimoX = 0f
    private var ultimoY = 0f
    private var multitoque = false

    private var latGps: Double? = null
    private var lonGps: Double? = null

    private var metrosPorPixel = 0.0

    private val areas =
        mutableListOf<MutableList<PointF>>()

    private val coresAreas =
        mutableListOf<Int>()

    private val linhas =
        mutableListOf<MutableList<PointF>>()

    private val coresLinhas =
        mutableListOf<Int>()

    private val pontos =
        mutableListOf<PointF>()

    private var linhaAtual:
        MutableList<PointF>? = null

    private fun corAtual(): Int =
        Color.argb(
            opacidade,
            Color.red(corPintura),
            Color.green(corPintura),
            Color.blue(corPintura)
        )

    private val detector = ScaleGestureDetector(
        context,
        object :
            ScaleGestureDetector.SimpleOnScaleGestureListener() {

            override fun onScale(
                detector: ScaleGestureDetector
            ): Boolean {
                val anterior = escala

                escala = (
                    escala * detector.scaleFactor
                ).coerceIn(0.05f, 16f)

                val fator = escala / anterior

                dx = detector.focusX -
                    (detector.focusX - dx) * fator

                dy = detector.focusY -
                    (detector.focusY - dy) * fator

                invalidate()
                return true
            }
        }
    )

    fun carregar(
        bitmap: Bitmap,
        id: String,
        referencia: GeoPdf?
    ) {
        salvar()

        imagem?.recycle()
        imagem = bitmap
        identificador = id
        geo = referencia

        areas.clear()
        coresAreas.clear()
        linhas.clear()
        coresLinhas.clear()
        pontos.clear()

        metrosPorPixel = 0.0
        modo = 0

        restaurar()

        avisarGps(
            if (geo != null)
                "Mapa georreferenciado detectado"
            else
                "Georreferência não reconhecida"
        )

        post {
            if (width > 0 && height > 0) {
                escala = minOf(
                    width.toFloat() / bitmap.width,
                    height.toFloat() / bitmap.height
                )

                dx = (
                    width - bitmap.width * escala
                ) / 2f

                dy = (
                    height - bitmap.height * escala
                ) / 2f
            }

            invalidate()
        }
    }

    fun iniciar(tipo: Int) {
        modo = tipo
        pontos.clear()

        avisar(
            when (tipo) {
                7 -> "Mova o mapa sob a mira e adicione pontos"
                3 -> "Toque nos cantos do talhão"
                4 -> "Toque nos pontos da distância"
                5 -> "Marque dois pontos de escala"
                else -> "Ferramenta selecionada"
            }
        )

        invalidate()
    }

    fun adicionarPontoCentral() {
        if (modo != 7) {
            avisar("Ative a mira central primeiro")
            return
        }

        if (imagem == null || escala <= 0f) return

        pontos.add(
            PointF(
                (width / 2f - dx) / escala,
                (height / 2f - dy) / escala
            )
        )

        avisar(
            "${pontos.size} ponto(s) adicionados"
        )

        invalidate()
    }

    fun pintarArea(): Boolean {
        if (pontos.size < 3 ||
            (modo != 3 && modo != 7)
        ) {
            avisar(
                "Marque pelo menos três pontos"
            )
            return false
        }

        areas.add(
            pontos.map {
                PointF(it.x, it.y)
            }.toMutableList()
        )

        coresAreas.add(corAtual())

        pontos.clear()
        salvar()

        avisar("Talhão pintado e salvo")
        invalidate()

        return true
    }

    fun desfazer() {
        when {
            pontos.isNotEmpty() -> {
                pontos.removeAt(pontos.lastIndex)
            }
            linhas.isNotEmpty() -> {
                linhas.removeAt(linhas.lastIndex)
                if (coresLinhas.isNotEmpty())
                    coresLinhas.removeAt(
                        coresLinhas.lastIndex
                    )
            }
            areas.isNotEmpty() -> {
                areas.removeAt(areas.lastIndex)
                if (coresAreas.isNotEmpty())
                    coresAreas.removeAt(
                        coresAreas.lastIndex
                    )
            }
        }

        salvar()
        invalidate()
        avisar("Última ação desfeita")
    }

    fun atualizarGps(lat: Double, lon: Double) {
        latGps = lat
        lonGps = lon

        avisarGps(
            when {
                imagem == null ->
                    "Abra um mapa"
                geo == null ->
                    "GPS ativo • mapa não georreferenciado"
                gpsEmPixels() == null ->
                    "GPS fora dos limites do mapa"
                else ->
                    "GPS localizado no mapa"
            }
        )

        invalidate()
    }

    private fun gpsEmPixels(): PointF? {
        val img = imagem ?: return null
        val lat = latGps ?: return null
        val lon = lonGps ?: return null

        return geo?.gpsParaPixel(
            lat, lon,
            img.width, img.height
        )
    }

    fun centrarGps() {
        val p = gpsEmPixels()

        if (p == null) {
            avisar(
                "Não foi possível localizar o GPS neste mapa"
            )
            return
        }

        dx = width / 2f - p.x * escala
        dy = height / 2f - p.y * escala

        invalidate()
    }

    private fun caminho(
        pontos: List<PointF>,
        fechado: Boolean
    ): Path {
        val path = Path()

        if (pontos.isEmpty()) return path

        path.moveTo(
            pontos[0].x,
            pontos[0].y
        )

        for (i in 1 until pontos.size) {
            path.lineTo(
                pontos[i].x,
                pontos[i].y
            )
        }

        if (fechado) path.close()

        return path
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        canvas.drawColor(
            Color.rgb(215, 220, 216)
        )

        canvas.save()
        canvas.translate(dx, dy)
        canvas.scale(escala, escala)

        imagem?.let {
            canvas.drawBitmap(
                it, 0f, 0f, null
            )
        }

        areas.forEachIndexed { i, area ->
            if (area.size >= 3) {
                val tinta = Paint(
                    Paint.ANTI_ALIAS_FLAG
                ).apply {
                    color = coresAreas.getOrElse(i) {
                        0x66FACC15
                    }
                    style = Paint.Style.FILL
                }

                canvas.drawPath(
                    caminho(area, true),
                    tinta
                )

                val contorno = Paint(
                    Paint.ANTI_ALIAS_FLAG
                ).apply {
                    color = Color.rgb(
                        28, 92, 54
                    )
                    style = Paint.Style.STROKE
                    strokeWidth = 2f / escala
                }

                canvas.drawPath(
                    caminho(area, true),
                    contorno
                )
            }
        }

        linhas.forEachIndexed { i, linha ->
            val tinta = Paint(
                Paint.ANTI_ALIAS_FLAG
            ).apply {
                color = coresLinhas.getOrElse(i) {
                    0x66FACC15
                }
                style = Paint.Style.STROKE
                strokeWidth = 20f / escala
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }

            canvas.drawPath(
                caminho(linha, false),
                tinta
            )
        }

        if (pontos.isNotEmpty()) {
            val guia = Paint(
                Paint.ANTI_ALIAS_FLAG
            ).apply {
                color = Color.rgb(
                    15, 110, 245
                )
                style = Paint.Style.STROKE
                strokeWidth = 3f / escala
            }

            canvas.drawPath(
                caminho(
                    pontos,
                    modo == 3 || modo == 7
                ),
                guia
            )

            val ponto = Paint(
                Paint.ANTI_ALIAS_FLAG
            ).apply {
                color = Color.WHITE
            }

            val borda = Paint(
                Paint.ANTI_ALIAS_FLAG
            ).apply {
                color = Color.BLUE
                style = Paint.Style.STROKE
                strokeWidth = 2f / escala
            }

            pontos.forEach {
                canvas.drawCircle(
                    it.x, it.y,
                    6f / escala, ponto
                )
                canvas.drawCircle(
                    it.x, it.y,
                    6f / escala, borda
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
                color = Color.rgb(
                    20, 115, 255
                )
            }

            canvas.drawCircle(
                p.x, p.y,
                13f / escala, branco
            )

            canvas.drawCircle(
                p.x, p.y,
                9f / escala, azul
            )
        }

        canvas.restore()

        if (modo == 7) {
            val cx = width / 2f
            val cy = height / 2f

            val mira = Paint(
                Paint.ANTI_ALIAS_FLAG
            ).apply {
                color = Color.rgb(
                    0, 105, 245
                )
                strokeWidth = 3f
                style = Paint.Style.STROKE
            }

            canvas.drawCircle(
                cx, cy, 11f, mira
            )

            canvas.drawLine(
                cx - 30f, cy,
                cx - 14f, cy, mira
            )

            canvas.drawLine(
                cx + 14f, cy,
                cx + 30f, cy, mira
            )

            canvas.drawLine(
                cx, cy - 30f,
                cx, cy - 14f, mira
            )

            canvas.drawLine(
                cx, cy + 14f,
                cx, cy + 30f, mira
            )
        }
    }

    override fun onTouchEvent(
        e: MotionEvent
    ): Boolean {
        if (imagem == null) return true

        if (e.actionMasked ==
            MotionEvent.ACTION_POINTER_DOWN
        ) {
            multitoque = true
            linhaAtual = null
        }

        detector.onTouchEvent(e)

        if (e.pointerCount > 1 || multitoque) {
            if (e.actionMasked ==
                    MotionEvent.ACTION_UP ||
                e.actionMasked ==
                    MotionEvent.ACTION_CANCEL
            ) {
                multitoque = false
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
                        linhaAtual = mutableListOf(
                            PointF(x, y)
                        )

                        linhas.add(linhaAtual!!)
                        coresLinhas.add(corAtual())
                    }

                    2 -> apagarPerto(x, y)

                    3, 4 -> {
                        pontos.add(PointF(x, y))
                    }

                    5 -> {
                        if (pontos.size >= 2)
                            pontos.clear()

                        pontos.add(PointF(x, y))
                    }
                }
            }

            MotionEvent.ACTION_MOVE -> {
                when (modo) {
                    0, 7 -> {
                        dx += e.x - ultimoX
                        dy += e.y - ultimoY
                    }

                    1 -> {
                        linhaAtual?.add(
                            PointF(x, y)
                        )
                    }

                    2 -> apagarPerto(x, y)
                }

                ultimoX = e.x
                ultimoY = e.y
            }

            MotionEvent.ACTION_UP -> {
                linhaAtual = null

                if (modo == 1 || modo == 2)
                    salvar()
            }
        }

        invalidate()
        return true
    }

    private fun apagarPerto(
        x: Float,
        y: Float
    ) {
        val raio = 25f / escala

        for (i in linhas.indices.reversed()) {
            if (linhas[i].any {
                hypot(
                    it.x - x,
                    it.y - y
                ) < raio
            }) {
                linhas.removeAt(i)
                coresLinhas.removeAt(i)
            }
        }

        for (i in areas.indices.reversed()) {
            if (areas[i].any {
                hypot(
                    it.x - x,
                    it.y - y
                ) < raio
            }) {
                areas.removeAt(i)
                coresAreas.removeAt(i)
            }
        }

        invalidate()
    }

    private fun distancia(
        a: PointF,
        b: PointF
    ): Double =
        hypot(
            (a.x - b.x).toDouble(),
            (a.y - b.y).toDouble()
        )

    private fun coordenadasMetros(
        lista: List<PointF>
    ): List<Pair<Double, Double>>? {
        val ref = geo ?: return null
        val img = imagem ?: return null

        val resultado =
            mutableListOf<Pair<Double, Double>>()

        for (p in lista) {
            val m = ref.pixelParaMetros(
                p.x, p.y,
                img.width, img.height
            ) ?: return null

            resultado.add(m)
        }

        return resultado
    }

    private fun areaMedida(): Double? {
        if (pontos.size < 3) return null

        val metros = coordenadasMetros(pontos)

        if (metros != null) {
            var soma = 0.0

            for (i in metros.indices) {
                val j = (i + 1) % metros.size

                soma +=
                    metros[i].first * metros[j].second -
                    metros[j].first * metros[i].second
            }

            return abs(soma) / 2.0
        }

        if (geo != null ||
            metrosPorPixel <= 0.0
        ) return null

        var soma = 0.0

        for (i in pontos.indices) {
            val j = (i + 1) % pontos.size

            soma +=
                pontos[i].x * pontos[j].y -
                pontos[j].x * pontos[i].y
        }

        return abs(soma) / 2.0 *
            metrosPorPixel * metrosPorPixel
    }

    private fun distanciaMedida(): Double? {
        if (pontos.size < 2) return null

        val metros = coordenadasMetros(pontos)

        if (metros != null) {
            var total = 0.0

            for (i in 1 until metros.size) {
                total += hypot(
                    metros[i].first -
                        metros[i - 1].first,
                    metros[i].second -
                        metros[i - 1].second
                )
            }

            return total
        }

        if (geo != null ||
            metrosPorPixel <= 0.0
        ) return null

        var total = 0.0

        for (i in 1 until pontos.size) {
            total += distancia(
                pontos[i - 1],
                pontos[i]
            )
        }

        return total * metrosPorPixel
    }

    fun concluir() {
        when (modo) {
            3, 7 -> {
                val m2 = areaMedida()

                avisar(
                    if (m2 == null)
                        "Área sem escala geográfica válida"
                    else String.format(
                        Locale("pt", "BR"),
                        "Área: %.4f hectares",
                        m2 / 10000.0
                    )
                )
            }

            4 -> {
                val metros = distanciaMedida()

                avisar(
                    if (metros == null)
                        "Distância sem escala válida"
                    else String.format(
                        Locale("pt", "BR"),
                        "Distância: %.2f metros",
                        metros
                    )
                )
            }

            5 -> calibrarEscala()
        }
    }

    private fun calibrarEscala() {
        if (pontos.size != 2) {
            avisar("Marque dois pontos")
            return
        }

        val pixels = distancia(
            pontos[0], pontos[1]
        )

        if (pixels <= 0.0) return

        val campo = EditText(context).apply {
            hint = "Distância real em metros"
            inputType =
                android.text.InputType.TYPE_CLASS_NUMBER or
                android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        }

        AlertDialog.Builder(context)
            .setTitle("Calibrar medidas")
            .setMessage(
                "Esta calibração serve apenas para " +
                "medidas. Não posiciona o GPS no mapa."
            )
            .setView(campo)
            .setPositiveButton("Salvar") { _, _ ->
                val metros = campo.text.toString()
                    .replace(",", ".")
                    .toDoubleOrNull()

                if (metros != null && metros > 0) {
                    metrosPorPixel =
                        metros / pixels

                    pontos.clear()
                    salvar()

                    avisar("Escala salva")
                }
            }
            .setNegativeButton(
                "Cancelar", null
            )
            .show()
    }

    fun salvar() {
        if (identificador.isEmpty()) return

        fun converter(
            listas: List<List<PointF>>
        ): JSONArray {
            val resultado = JSONArray()

            listas.forEach { lista ->
                val arr = JSONArray()

                lista.forEach { p ->
                    arr.put(
                        JSONObject()
                            .put("x", p.x)
                            .put("y", p.y)
                    )
                }

                resultado.put(arr)
            }

            return resultado
        }

        val dados = JSONObject()
            .put("areas", converter(areas))
            .put("linhas", converter(linhas))
            .put(
                "coresAreas",
                JSONArray(coresAreas)
            )
            .put(
                "coresLinhas",
                JSONArray(coresLinhas)
            )
            .put(
                "escala",
                metrosPorPixel
            )

        context.getSharedPreferences(
            "pinturas", 0
        ).edit()
            .putString(
                identificador,
                dados.toString()
            )
            .apply()
    }

    private fun restaurar() {
        try {
            val texto = context.getSharedPreferences(
                "pinturas", 0
            ).getString(
                identificador, ""
            ) ?: ""

            if (texto.isEmpty()) return

            fun ler(
                arr: JSONArray,
                destino:
                    MutableList<MutableList<PointF>>
            ) {
                for (i in 0 until arr.length()) {
                    val lista = mutableListOf<PointF>()
                    val pontosJson =
                        arr.getJSONArray(i)

                    for (j in 0 until pontosJson.length()) {
                        val p =
                            pontosJson.getJSONObject(j)

                        lista.add(
                            PointF(
                                p.getDouble("x").toFloat(),
                                p.getDouble("y").toFloat()
                            )
                        )
                    }

                    destino.add(lista)
                }
            }

            if (texto.trim().startsWith("[")) {
                ler(JSONArray(texto), linhas)

                repeat(linhas.size) {
                    coresLinhas.add(
                        0x66FACC15
                    )
                }
            } else {
                val dados = JSONObject(texto)

                dados.optJSONArray("areas")?.let {
                    ler(it, areas)
                }

                dados.optJSONArray("linhas")?.let {
                    ler(it, linhas)
                }

                dados.optJSONArray("coresAreas")?.let {
                    for (i in 0 until it.length())
                        coresAreas.add(it.getInt(i))
                }

                dados.optJSONArray("coresLinhas")?.let {
                    for (i in 0 until it.length())
                        coresLinhas.add(it.getInt(i))
                }

                metrosPorPixel =
                    dados.optDouble("escala", 0.0)

                while (coresAreas.size < areas.size)
                    coresAreas.add(0x66FACC15)

                while (coresLinhas.size < linhas.size)
                    coresLinhas.add(0x66FACC15)
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
    private val alturaPdf: Double
) {
    private val latReferencia =
        (0 until 4).map {
            gpts[it * 2]
        }.average()

    private fun projetar(
        lat: Double,
        lon: Double
    ): Pair<Double, Double> {
        val r = 6378137.0

        return Pair(
            r * Math.toRadians(lon) *
                cos(Math.toRadians(latReferencia)),
            r * Math.toRadians(lat)
        )
    }

    private val mx = DoubleArray(4) {
        projetar(
            gpts[it * 2],
            gpts[it * 2 + 1]
        ).first
    }

    private val my = DoubleArray(4) {
        projetar(
            gpts[it * 2],
            gpts[it * 2 + 1]
        ).second
    }

    private val lx = DoubleArray(4) {
        lpts[it * 2]
    }

    private val ly = DoubleArray(4) {
        lpts[it * 2 + 1]
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

        repeat(25) {
            val ex =
                bilinear(xx, u, v) - x

            val ey =
                bilinear(yy, u, v) - y

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

            if (!u.isFinite() ||
                !v.isFinite()
            ) return null

            if (abs(du) + abs(dv) < 1e-10)
                return Pair(u, v)
        }

        return if (
            hypot(
                bilinear(xx, u, v) - x,
                bilinear(yy, u, v) - y
            ) < 0.1
        ) Pair(u, v) else null
    }

    fun gpsParaPixel(
        lat: Double,
        lon: Double,
        w: Int,
        h: Int
    ): PointF? {
        val xy = projetar(lat, lon)

        val uv = inversa(
            xy.first, xy.second,
            mx, my
        ) ?: return null

        if (uv.first !in 0.0..1.0 ||
            uv.second !in 0.0..1.0
        ) return null

        val px = bilinear(
            lx, uv.first, uv.second
        )

        val py = bilinear(
            ly, uv.first, uv.second
        )

        val pdfX = bbox[0] +
            px * (bbox[2] - bbox[0])

        val pdfY = bbox[1] +
            py * (bbox[3] - bbox[1])

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
        val pdfX =
            px / w * larguraPdf

        val pdfY =
            (1 - py / h) * alturaPdf

        val localX =
            (pdfX - bbox[0]) /
            (bbox[2] - bbox[0])

        val localY =
            (pdfY - bbox[1]) /
            (bbox[3] - bbox[1])

        val uv = inversa(
            localX, localY,
            lx, ly
        ) ?: return null

        if (uv.first !in 0.0..1.0 ||
            uv.second !in 0.0..1.0
        ) return null

        return Pair(
            bilinear(mx, uv.first, uv.second),
            bilinear(my, uv.first, uv.second)
        )
    }

    companion object {
        private val numeros = Regex(
            """[-+]?(?:\d+(?:\.\d*)?|\.\d+)(?:[eE][-+]?\d+)?"""
        )

        private fun lerArray(
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

            if (abre < 0 ||
                abre - pos > 40
            ) return null

            val fecha = texto.indexOf(
                ']', abre + 1
            )

            if (fecha < 0 ||
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
            largura: Double,
            altura: Double
        ): GeoPdf? {
            return try {
                if (arquivo.length() >
                    30000000L
                ) return null

                val texto =
                    arquivo.readBytes().toString(
                        Charsets.ISO_8859_1
                    )

                val gpsPos =
                    texto.indexOf("/GPTS")

                if (gpsPos < 0) return null

                val vpPos =
                    texto.lastIndexOf(
                        "/VP", gpsPos
                    )

                val bboxPos =
                    texto.lastIndexOf(
                        "/BBox", gpsPos
                    )

                if (vpPos < 0 ||
                    bboxPos < vpPos ||
                    gpsPos - vpPos > 15000
                ) return null

                val bbox = lerArray(
                    texto, "/BBox", bboxPos
                ) ?: return null

                val gpts = lerArray(
                    texto, "/GPTS", gpsPos
                ) ?: return null

                val lpts = lerArray(
                    texto, "/LPTS", gpsPos
                ) ?: return null

                if (bbox.size != 4 ||
                    gpts.size != 8 ||
                    lpts.size != 8
                ) return null

                if (bbox[2] <= bbox[0] ||
                    bbox[3] <= bbox[1]
                ) return null

                if ((0 until 4).any {
                    abs(gpts[it * 2]) > 90.0 ||
                    abs(gpts[it * 2 + 1]) > 180.0
                }) return null

                GeoPdf(
                    bbox, lpts, gpts,
                    largura, altura
                )

            } catch (_: Exception) {
                null
            }
        }
    }
}
