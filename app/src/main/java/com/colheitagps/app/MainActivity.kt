
package com.colheitagps.app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.graphics.pdf.PdfRenderer
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
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

    private val verde = Color.rgb(20, 139, 81)
    private val escuro = Color.rgb(23, 32, 29)
    private val claro = Color.rgb(244, 247, 245)

    private lateinit var raiz: FrameLayout
    private var mapa: MapaView? = null
    private var mapaAtual = ""
    private var nomeAtual = ""

    private var gpsManager: LocationManager? = null
    private var gpsListener: LocationListener? = null
    private var ultimaLocalizacao: Location? = null
    private var gpsStatus: TextView? = null

    private fun dp(valor: Int): Int =
        (valor * resources.displayMetrics.density).toInt()

    private fun texto(
        valor: String,
        tamanho: Float = 15f,
        cor: Int = escuro
    ): TextView {
        return TextView(this).apply {
            text = valor
            textSize = tamanho
            setTextColor(cor)
            gravity = Gravity.CENTER_VERTICAL
        }
    }

    private fun botao(
        nome: String,
        acao: () -> Unit
    ): Button {
        return Button(this).apply {
            text = nome
            textSize = 12f
            isAllCaps = false
            setTextColor(Color.WHITE)
            backgroundTintList = ColorStateList.valueOf(verde)
            setOnClickListener { acao() }
        }
    }

    private fun circulo(
        simbolo: String,
        acao: () -> Unit
    ): TextView {
        return TextView(this).apply {
            text = simbolo
            textSize = 29f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(verde)
            }
            elevation = dp(8).toFloat()
            setOnClickListener { acao() }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = escuro
        window.navigationBarColor = Color.BLACK

        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
        }

        mostrarBiblioteca()
    }

    private fun criarRaiz(): FrameLayout {
        val tela = FrameLayout(this)
        tela.setBackgroundColor(claro)

        tela.setOnApplyWindowInsetsListener { view, insets ->
            if (Build.VERSION.SDK_INT >= 30) {
                val barras = insets.getInsets(
                    WindowInsets.Type.statusBars() or
                        WindowInsets.Type.navigationBars() or
                        WindowInsets.Type.displayCutout()
                )

                view.setPadding(
                    barras.left,
                    barras.top,
                    barras.right,
                    barras.bottom
                )
            } else {
                @Suppress("DEPRECATION")
                view.setPadding(
                    insets.systemWindowInsetLeft,
                    insets.systemWindowInsetTop,
                    insets.systemWindowInsetRight,
                    insets.systemWindowInsetBottom
                )
            }
            insets
        }

        raiz = tela
        return tela
    }

    private fun pasta(): File =
        File(filesDir, "mapas").apply { mkdirs() }

    private fun preferencias() =
        getSharedPreferences("mapas", MODE_PRIVATE)

    private fun listaMapas(): List<File> {
        return pasta().listFiles()
            ?.filter { it.extension.equals("pdf", true) }
            ?.sortedBy { it.name.lowercase() }
            ?: emptyList()
    }

    private fun mostrarBiblioteca() {
        mapa?.salvar()
        mapaAtual = ""
        mapa = null
        gpsStatus = null

        val tela = criarRaiz()

        val coluna = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val topo = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(18))
            setBackgroundColor(escuro)
        }

        topo.addView(texto("COLHEITA GPS", 23f, Color.WHITE))
        topo.addView(
            texto(
                "Meus mapas de colheita",
                14f,
                0xFFB6DCC6.toInt()
            )
        )

        coluna.addView(topo)

        val lista = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(15), dp(12), dp(15), dp(100))
        }

        val arquivos = listaMapas()

        if (arquivos.isEmpty()) {
            lista.addView(
                texto(
                    "Nenhum mapa importado.\n\nToque no botão + para adicionar um PDF.",
                    16f,
                    0xFF66746B.toInt()
                ).apply {
                    gravity = Gravity.CENTER
                    setPadding(dp(18), dp(70), dp(18), dp(30))
                }
            )
        } else {
            lista.addView(
                texto(
                    "${arquivos.size} mapa(s) disponível(is)",
                    13f,
                    0xFF65756B.toInt()
                ).apply {
                    setPadding(dp(4), dp(6), 0, dp(12))
                }
            )
        }

        arquivos.forEach { arquivo ->
            val chave = arquivo.nameWithoutExtension
            val nome = preferencias().getString(chave, arquivo.name)
                ?: arquivo.name

            val item = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(13), dp(12), dp(8), dp(12))
                background = GradientDrawable().apply {
                    setColor(Color.WHITE)
                    cornerRadius = dp(15).toFloat()
                }
                elevation = dp(2).toFloat()
            }

            item.addView(
                texto("▧", 32f, verde).apply {
                    gravity = Gravity.CENTER
                },
                LinearLayout.LayoutParams(dp(52), dp(58))
            )

            val info = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
            }

            info.addView(
                texto(nome, 16f, escuro).apply {
                    maxLines = 2
                }
            )
            info.addView(
                texto(
                    "Mapa PDF • Toque para abrir",
                    12f,
                    0xFF758379.toInt()
                )
            )

            item.addView(
                info,
                LinearLayout.LayoutParams(0, -2, 1f)
            )

            item.addView(
                texto("⋮", 26f, escuro).apply {
                    gravity = Gravity.CENTER
                    setOnClickListener { menuMapa(chave, nome) }
                },
                LinearLayout.LayoutParams(dp(42), dp(52))
            )

            item.setOnClickListener { abrirMapa(chave) }

            lista.addView(
                item,
                LinearLayout.LayoutParams(-1, -2).apply {
                    bottomMargin = dp(11)
                }
            )
        }

        val rolagem = ScrollView(this).apply {
            isFillViewport = true
            addView(lista)
        }

        coluna.addView(
            rolagem,
            LinearLayout.LayoutParams(-1, 0, 1f)
        )

        tela.addView(
            coluna,
            FrameLayout.LayoutParams(-1, -1)
        )

        tela.addView(
            circulo("+") { importarPdf() },
            FrameLayout.LayoutParams(
                dp(62),
                dp(62),
                Gravity.BOTTOM or Gravity.END
            ).apply {
                rightMargin = dp(22)
                bottomMargin = dp(22)
            }
        )

        setContentView(tela)
        tela.requestApplyInsets()
    }

    private fun importarPdf() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "application/pdf"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        startActivityForResult(intent, 10)
    }

    private fun menuMapa(chave: String, nome: String) {
        AlertDialog.Builder(this)
            .setTitle(nome)
            .setItems(
                arrayOf("Abrir", "Renomear", "Excluir")
            ) { _, escolha ->
                when (escolha) {
                    0 -> abrirMapa(chave)
                    1 -> renomearMapa(chave, nome)
                    2 -> excluirMapa(chave, nome)
                }
            }
            .show()
    }

    private fun renomearMapa(chave: String, nome: String) {
        val campo = EditText(this).apply {
            setText(nome)
            setSingleLine(true)
        }

        AlertDialog.Builder(this)
            .setTitle("Renomear mapa")
            .setView(campo)
            .setPositiveButton("Salvar") { _, _ ->
                val novo = campo.text.toString().trim()
                if (novo.isNotEmpty()) {
                    preferencias().edit()
                        .putString(chave, novo)
                        .apply()
                    mostrarBiblioteca()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun excluirMapa(chave: String, nome: String) {
        AlertDialog.Builder(this)
            .setTitle("Excluir mapa?")
            .setMessage(
                "Excluir \"$nome\" e suas marcações salvas?"
            )
            .setPositiveButton("Excluir") { _, _ ->
                File(pasta(), "$chave.pdf").delete()

                getSharedPreferences("pinturas", MODE_PRIVATE)
                    .edit()
                    .remove(chave)
                    .apply()

                preferencias().edit().remove(chave).apply()
                mostrarBiblioteca()
            }
            .setNegativeButton("Cancelar", null)
            .show()
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
            var nome = "Novo mapa.pdf"

            contentResolver.query(
                uri, null, null, null, null
            )?.use { cursor ->
                val indice = cursor.getColumnIndex(
                    OpenableColumns.DISPLAY_NAME
                )
                if (indice >= 0 && cursor.moveToFirst()) {
                    nome = cursor.getString(indice) ?: nome
                }
            }

            val temporario = File(cacheDir, "novo_mapa.pdf")
            val digest = MessageDigest.getInstance("SHA-256")

            contentResolver.openInputStream(uri)!!.use { entrada ->
                FileOutputStream(temporario).use { saida ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        val quantidade = entrada.read(buffer)
                        if (quantidade < 0) break
                        digest.update(buffer, 0, quantidade)
                        saida.write(buffer, 0, quantidade)
                    }
                }
            }

            val chave = digest.digest().joinToString("") {
                "%02x".format(it)
            }

            val destino = File(pasta(), "$chave.pdf")

            if (!destino.exists()) {
                temporario.copyTo(destino)
            }
            temporario.delete()

            preferencias().edit()
                .putString(chave, nome)
                .apply()

            mostrarBiblioteca()
        } catch (e: Exception) {
            Toast.makeText(
                this,
                "Erro ao importar: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun abrirMapa(chave: String) {
        val arquivo = File(pasta(), "$chave.pdf")
        if (!arquivo.exists()) return

        try {
            val fd = ParcelFileDescriptor.open(
                arquivo,
                ParcelFileDescriptor.MODE_READ_ONLY
            )

            var imagem: Bitmap

            // Abertura explícita para evitar problemas de inferência.
            val renderer = PdfRenderer(fd)

            try {
                val pagina: PdfRenderer.Page = renderer.openPage(0)

                try {
                    val largura = pagina.width
                    val altura = pagina.height

                    val fator = minOf(
                        3.0,
                        4000.0 / maxOf(largura, altura)
                    )

                    val limite = minOf(
                        1.0,
                        sqrt(
                            16000000.0 /
                                (largura.toDouble() *
                                    altura.toDouble() *
                                    fator * fator)
                        )
                    )

                    val w = maxOf(
                        1,
                        (largura * fator * limite).toInt()
                    )
                    val h = maxOf(
                        1,
                        (altura * fator * limite).toInt()
                    )

                    imagem = Bitmap.createBitmap(
                        w, h, Bitmap.Config.ARGB_8888
                    )

                    imagem.eraseColor(Color.WHITE)

                    pagina.render(
                        imagem,
                        null,
                        null,
                        PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                    )
                } finally {
                    pagina.close()
                }
            } finally {
                renderer.close()
            }

            mapaAtual = chave
            nomeAtual = preferencias().getString(
                chave, arquivo.name
            ) ?: arquivo.name

            val geo = GeoPdf.ler(arquivo)

            mostrarTelaMapa(imagem, chave, geo)

        } catch (e: Exception) {
            mapaAtual = ""
            Toast.makeText(
                this,
                "Erro ao abrir mapa: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun mostrarTelaMapa(
        imagem: Bitmap,
        chave: String,
        geo: GeoPdf?
    ) {
        val tela = criarRaiz()

        val visualizador = MapaView(this)
        mapa = visualizador

        visualizador.carregar(imagem, chave, geo)

        tela.addView(
            visualizador,
            FrameLayout.LayoutParams(-1, -1)
        )

        val topo = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(7), dp(10), dp(7))
            setBackgroundColor(escuro)
        }

        topo.addView(
            texto("‹", 34f, Color.WHITE).apply {
                gravity = Gravity.CENTER
                setOnClickListener {
                    visualizador.salvar()
                    mostrarBiblioteca()
                }
            },
            LinearLayout.LayoutParams(dp(48), dp(52))
        )

        val info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        info.addView(
            texto(nomeAtual, 16f, Color.WHITE).apply {
                maxLines = 1
                ellipsize =
                    android.text.TextUtils.TruncateAt.END
            }
        )

        gpsStatus = texto(
            if (geo != null)
                "Georreferência detectada — verificar precisão"
            else
                "Georreferência não reconhecida",
            11f,
            0xFFB9D7C4.toInt()
        )

        info.addView(gpsStatus)

        topo.addView(
            info,
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        topo.addView(
            texto("◎", 30f, Color.WHITE).apply {
                gravity = Gravity.CENTER
                setOnClickListener {
                    if (ultimaLocalizacao == null) {
                        iniciarGps()
                    } else {
                        visualizador.centrarGps()
                    }
                }
            },
            LinearLayout.LayoutParams(dp(48), dp(52))
        )

        tela.addView(
            topo,
            FrameLayout.LayoutParams(
                -1, -2, Gravity.TOP
            )
        )

        tela.addView(
            circulo("+") { menuFerramentas() },
            FrameLayout.LayoutParams(
                dp(62),
                dp(62),
                Gravity.BOTTOM or Gravity.END
            ).apply {
                rightMargin = dp(22)
                bottomMargin = dp(22)
            }
        )

        setContentView(tela)
        tela.requestApplyInsets()

        ultimaLocalizacao?.let {
            visualizador.atualizarGps(
                it.latitude, it.longitude
            )
        }
    }

    private fun menuFerramentas() {
        val opcoes = arrayOf(
            "Pintar talhão",
            "Medir área",
            "Medir distância",
            "Pintar com pincel",
            "Apagar pintura",
            "Desfazer",
            "GPS e localização",
            "Voltar aos mapas"
        )

        AlertDialog.Builder(this)
            .setTitle("Ferramentas")
            .setItems(opcoes) { _, escolha ->
                when (escolha) {
                    0 -> iniciarFerramenta(7)
                    1 -> iniciarFerramenta(8)
                    2 -> iniciarFerramenta(9)
                    3 -> iniciarFerramenta(1)
                    4 -> iniciarFerramenta(2)
                    5 -> mapa?.desfazer()
                    6 -> iniciarGps()
                    7 -> mostrarBiblioteca()
                }
            }
            .show()
    }

    private fun iniciarFerramenta(tipo: Int) {
        val visualizador = mapa ?: return
        visualizador.iniciar(tipo)

        val antigo = raiz.findViewWithTag<View>("painelFerramenta")
        if (antigo != null) raiz.removeView(antigo)

        val painel = LinearLayout(this).apply {
            tag = "painelFerramenta"
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setBackgroundColor(escuro)
        }

        val titulo = when (tipo) {
            7 -> "PINTAR TALHÃO"
            8 -> "MEDIR ÁREA"
            9 -> "MEDIR DISTÂNCIA"
            1 -> "PINCEL"
            else -> "BORRACHA"
        }

        painel.addView(
            texto(titulo, 14f, Color.WHITE).apply {
                gravity = Gravity.CENTER
            }
        )

        val linha = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        fun adicionar(nome: String, acao: () -> Unit) {
            linha.addView(
                botao(nome, acao),
                LinearLayout.LayoutParams(0, dp(50), 1f)
            )
        }

        if (tipo in 7..9) {
            adicionar("+ Ponto") {
                visualizador.adicionarPontoCentral()
            }

            adicionar("Desfazer") {
                visualizador.desfazer()
            }

            adicionar(
                if (tipo == 9) "Concluir" else "Fechar"
            ) {
                if (tipo == 7) {
                    if (visualizador.fecharArea()) {
                        escolherCorPintura()
                    }
                } else {
                    visualizador.concluirMedida()
                }
            }
        } else {
            adicionar("Cor") { escolherCorPintura() }
            adicionar("Desfazer") { visualizador.desfazer() }
        }

        adicionar("Sair") {
            visualizador.cancelarFerramenta()
            raiz.removeView(painel)
        }

        painel.addView(linha)

        raiz.addView(
            painel,
            FrameLayout.LayoutParams(
                -1, -2, Gravity.BOTTOM
            ).apply {
                bottomMargin = dp(94)
            }
        )
    }

    private fun escolherCorPintura() {
        val nomes = arrayOf(
            "Amarelo", "Verde", "Azul", "Vermelho",
            "Laranja", "Roxo", "Rosa", "Ciano"
        )

        val cores = intArrayOf(
            0xFFFACC15.toInt(),
            0xFF22C55E.toInt(),
            0xFF3B82F6.toInt(),
            0xFFEF4444.toInt(),
            0xFFF97316.toInt(),
            0xFFA855F7.toInt(),
            0xFFEC4899.toInt(),
            0xFF06B6D4.toInt()
        )

        AlertDialog.Builder(this)
            .setTitle("Escolher cor")
            .setItems(nomes) { _, i ->
                mapa?.corPintura = cores[i]

                if (mapa?.modo == 7 && mapa?.areaFechada == true) {
                    confirmarPintura()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun confirmarPintura() {
        val opcoes = arrayOf(
            "Transparente — 25%",
            "Recomendado — 40%",
            "Intermediário — 60%"
        )

        val valores = intArrayOf(64, 102, 153)

        AlertDialog.Builder(this)
            .setTitle("Transparência da pintura")
            .setItems(opcoes) { _, i ->
                val visualizador = mapa ?: return@setItems
                visualizador.opacidade = valores[i]

                if (visualizador.salvarArea()) {
                    encerrarPainel()
                    Toast.makeText(
                        this,
                        "Talhão pintado e salvo",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .setNegativeButton("Voltar", null)
            .show()
    }

    private fun encerrarPainel() {
        val painel = raiz.findViewWithTag<View>("painelFerramenta")
        if (painel != null) raiz.removeView(painel)
        mapa?.cancelarFerramenta()
    }

    private fun iniciarGps() {
        if (
            checkSelfPermission(
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                100
            )
            return
        }

        gpsManager = getSystemService(
            Context.LOCATION_SERVICE
        ) as LocationManager

        if (
            gpsManager?.isProviderEnabled(
                LocationManager.GPS_PROVIDER
            ) != true
        ) {
            Toast.makeText(
                this,
                "Ative a localização do celular",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        gpsListener?.let { gpsManager?.removeUpdates(it) }

        gpsListener = LocationListener { local: Location ->
            ultimaLocalizacao = local
            mapa?.atualizarGps(local.latitude, local.longitude)

            gpsStatus?.text = String.format(
                Locale("pt", "BR"),
                "GPS ±%.0f m • %.5f, %.5f",
                local.accuracy,
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
            gpsStatus?.text = "Procurando sinal GPS..."
        } catch (_: SecurityException) {
            gpsStatus?.text = "Permissão GPS necessária"
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

        if (
            requestCode == 100 &&
            grantResults.firstOrNull() ==
            PackageManager.PERMISSION_GRANTED
        ) {
            iniciarGps()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (mapaAtual.isNotEmpty()) {
            mostrarBiblioteca()
        } else {
            super.onBackPressed()
        }
    }

    override fun onPause() {
        super.onPause()
        mapa?.salvar()
    }

    override fun onDestroy() {
        gpsListener?.let { gpsManager?.removeUpdates(it) }
        super.onDestroy()
    }
}

class MapaView(context: Context) : View(context) {

    var modo = 0
    var areaFechada = false
        private set

    var corPintura = 0xFFFACC15.toInt()
    var opacidade = 102

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

    private val pontos = mutableListOf<PointF>()
    private val areas = mutableListOf<MutableList<PointF>>()
    private val coresAreas = mutableListOf<Int>()
    private val linhas = mutableListOf<MutableList<PointF>>()
    private val coresLinhas = mutableListOf<Int>()

    private var linhaAtual: MutableList<PointF>? = null

    private fun corAtual(): Int {
        return Color.argb(
            opacidade,
            Color.red(corPintura),
            Color.green(corPintura),
            Color.blue(corPintura)
        )
    }

    private val detector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
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
        chave: String,
        referencia: GeoPdf?
    ) {
        imagem = bitmap
        identificador = chave
        geo = referencia

        areas.clear()
        coresAreas.clear()
        linhas.clear()
        coresLinhas.clear()
        pontos.clear()

        restaurar()

        post {
            if (width > 0 && height > 0) {
                escala = minOf(
                    width.toFloat() / bitmap.width,
                    height.toFloat() / bitmap.height
                )

                dx = (width - bitmap.width * escala) / 2f
                dy = (height - bitmap.height * escala) / 2f
                invalidate()
            }
        }
    }

    fun iniciar(tipo: Int) {
        modo = tipo
        pontos.clear()
        areaFechada = false
        invalidate()
    }

    fun cancelarFerramenta() {
        modo = 0
        pontos.clear()
        areaFechada = false
        linhaAtual = null
        invalidate()
    }

    fun adicionarPontoCentral() {
        if (modo !in 7..9 || imagem == null) return
        if (areaFechada) return

        pontos.add(
            PointF(
                (width / 2f - dx) / escala,
                (height / 2f - dy) / escala
            )
        )
        invalidate()
    }

    fun fecharArea(): Boolean {
        if (modo != 7 || pontos.size < 3) {
            Toast.makeText(
                context,
                "Adicione pelo menos 3 pontos",
                Toast.LENGTH_SHORT
            ).show()
            return false
        }

        areaFechada = true
        invalidate()
        return true
    }

    fun salvarArea(): Boolean {
        if (modo != 7 || !areaFechada || pontos.size < 3) {
            return false
        }

        areas.add(
            pontos.map { PointF(it.x, it.y) }.toMutableList()
        )
        coresAreas.add(corAtual())

        pontos.clear()
        areaFechada = false
        salvar()
        invalidate()
        return true
    }

    fun desfazer() {
        when {
            pontos.isNotEmpty() -> {
                areaFechada = false
                pontos.removeAt(pontos.lastIndex)
            }
            linhas.isNotEmpty() -> {
                linhas.removeAt(linhas.lastIndex)
                coresLinhas.removeAt(coresLinhas.lastIndex)
            }
            areas.isNotEmpty() -> {
                areas.removeAt(areas.lastIndex)
                coresAreas.removeAt(coresAreas.lastIndex)
            }
        }

        salvar()
        invalidate()
    }

    fun concluirMedida() {
        if (modo == 8) {
            if (pontos.size < 3) {
                aviso("Adicione pelo menos 3 pontos")
                return
            }

            areaFechada = true
            invalidate()

            val coordenadas = pontosEmMetros()

            if (coordenadas == null) {
                aviso("Georreferência não disponível para medir hectares")
                return
            }

            var soma = 0.0

            for (i in coordenadas.indices) {
                val j = (i + 1) % coordenadas.size

                soma +=
                    coordenadas[i].first * coordenadas[j].second -
                    coordenadas[j].first * coordenadas[i].second
            }

            val hectares = abs(soma) / 20000.0

            aviso(
                String.format(
                    Locale("pt", "BR"),
                    "Área aproximada: %.4f hectares",
                    hectares
                )
            )
        }

        if (modo == 9) {
            if (pontos.size < 2) {
                aviso("Adicione pelo menos 2 pontos")
                return
            }

            val coordenadas = pontosEmMetros()

            if (coordenadas == null) {
                aviso("Georreferência não disponível para medir distância")
                return
            }

            var metros = 0.0

            for (i in 1 until coordenadas.size) {
                metros += hypot(
                    coordenadas[i].first - coordenadas[i - 1].first,
                    coordenadas[i].second - coordenadas[i - 1].second
                )
            }

            aviso(
                String.format(
                    Locale("pt", "BR"),
                    "Distância aproximada: %.2f metros",
                    metros
                )
            )
        }
    }

    private fun aviso(mensagem: String) {
        AlertDialog.Builder(context)
            .setMessage(mensagem)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun pontosEmMetros(): List<Pair<Double, Double>>? {
        val referencia = geo ?: return null
        val img = imagem ?: return null

        val resultado = mutableListOf<Pair<Double, Double>>()

        pontos.forEach { ponto ->
            val valor = referencia.pixelParaMetros(
                ponto.x,
                ponto.y,
                img.width,
                img.height
            ) ?: return null

            resultado.add(valor)
        }

        return resultado
    }

    fun atualizarGps(lat: Double, lon: Double) {
        latGps = lat
        lonGps = lon
        invalidate()
    }

    private fun gpsPixel(): PointF? {
        val img = imagem ?: return null
        val lat = latGps ?: return null
        val lon = lonGps ?: return null

        return geo?.gpsParaPixel(
            lat, lon, img.width, img.height
        )
    }

    fun centrarGps() {
        val ponto = gpsPixel()

        if (ponto == null) {
            aviso(
                "Não foi possível posicionar o GPS neste mapa. Verifique a georreferência."
            )
            return
        }

        dx = width / 2f - ponto.x * escala
        dy = height / 2f - ponto.y * escala
        invalidate()
    }

    private fun caminho(
        lista: List<PointF>,
        fechar: Boolean
    ): Path {
        val path = Path()
        if (lista.isEmpty()) return path

        path.moveTo(lista[0].x, lista[0].y)

        for (i in 1 until lista.size) {
            path.lineTo(lista[i].x, lista[i].y)
        }

        if (fechar) path.close()
        return path
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        canvas.drawColor(0xFFE5EAE6.toInt())
        canvas.save()
        canvas.translate(dx, dy)
        canvas.scale(escala, escala)

        imagem?.let {
            canvas.drawBitmap(it, 0f, 0f, null)
        }

        areas.forEachIndexed { indice, area ->
            if (area.size >= 3) {
                val tinta = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = coresAreas.getOrElse(indice) {
                        0x66FACC15
                    }
                    style = Paint.Style.FILL
                }

                canvas.drawPath(caminho(area, true), tinta)

                val borda = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0xFF17804D.toInt()
                    style = Paint.Style.STROKE
                    strokeWidth = 2f / escala
                }

                canvas.drawPath(caminho(area, true), borda)
            }
        }

        linhas.forEachIndexed { indice, linha ->
            val tinta = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = coresLinhas.getOrElse(indice) {
                    0x66FACC15
                }
                style = Paint.Style.STROKE
                strokeWidth = 17f / escala
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }

            canvas.drawPath(caminho(linha, false), tinta)
        }

        if (pontos.isNotEmpty()) {
            val linha = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF087CDE.toInt()
                style = Paint.Style.STROKE
                strokeWidth = 3f / escala
                strokeJoin = Paint.Join.ROUND
            }

            canvas.drawPath(
                caminho(pontos, areaFechada),
                linha
            )

            if (areaFechada && pontos.size >= 3) {
                val preenchimento = Paint(
                    Paint.ANTI_ALIAS_FLAG
                ).apply {
                    color = 0x3322C55E
                    style = Paint.Style.FILL
                }

                canvas.drawPath(
                    caminho(pontos, true),
                    preenchimento
                )
            }

            pontos.forEach { ponto ->
                val fundo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.WHITE
                }

                val contorno = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0xFF087CDE.toInt()
                    style = Paint.Style.STROKE
                    strokeWidth = 2f / escala
                }

                canvas.drawCircle(
                    ponto.x, ponto.y, 6f / escala, fundo
                )
                canvas.drawCircle(
                    ponto.x, ponto.y, 6f / escala, contorno
                )
            }
        }

        gpsPixel()?.let { ponto ->
            val branco = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
            }

            val azul = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF1674E8.toInt()
            }

            canvas.drawCircle(
                ponto.x, ponto.y, 13f / escala, branco
            )
            canvas.drawCircle(
                ponto.x, ponto.y, 9f / escala, azul
            )
        }

        canvas.restore()

        if (modo in 7..9) {
            val cx = width / 2f
            val cy = height / 2f

            val mira = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF087CDE.toInt()
                strokeWidth = 3f
                style = Paint.Style.STROKE
            }

            canvas.drawCircle(cx, cy, 10f, mira)
            canvas.drawLine(cx - 30, cy, cx - 14, cy, mira)
            canvas.drawLine(cx + 14, cy, cx + 30, cy, mira)
            canvas.drawLine(cx, cy - 30, cx, cy - 14, mira)
            canvas.drawLine(cx, cy + 14, cx, cy + 30, mira)
        }
    }

    override fun onTouchEvent(evento: MotionEvent): Boolean {
        if (imagem == null) return true

        if (
            evento.actionMasked ==
            MotionEvent.ACTION_POINTER_DOWN
        ) {
            multitoque = true
            linhaAtual = null
        }

        detector.onTouchEvent(evento)

        if (evento.pointerCount > 1 || multitoque) {
            if (
                evento.actionMasked == MotionEvent.ACTION_UP ||
                evento.actionMasked == MotionEvent.ACTION_CANCEL
            ) {
                multitoque = false
            }

            invalidate()
            return true
        }

        val x = (evento.x - dx) / escala
        val y = (evento.y - dy) / escala

        when (evento.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                ultimoX = evento.x
                ultimoY = evento.y

                if (modo == 1) {
                    linhaAtual = mutableListOf(PointF(x, y))
                    linhas.add(linhaAtual!!)
                    coresLinhas.add(corAtual())
                }

                if (modo == 2) apagarPerto(x, y)
            }

            MotionEvent.ACTION_MOVE -> {
                when (modo) {
                    0, 7, 8, 9 -> {
                        dx += evento.x - ultimoX
                        dy += evento.y - ultimoY
                    }
                    1 -> linhaAtual?.add(PointF(x, y))
                    2 -> apagarPerto(x, y)
                }

                ultimoX = evento.x
                ultimoY = evento.y
            }

            MotionEvent.ACTION_UP -> {
                linhaAtual = null

                if (modo == 1 || modo == 2) {
                    salvar()
                }
            }
        }

        invalidate()
        return true
    }

    private fun apagarPerto(x: Float, y: Float) {
        val raio = 25f / escala

        for (i in linhas.indices.reversed()) {
            if (linhas[i].any {
                hypot(it.x - x, it.y - y) < raio
            }) {
                linhas.removeAt(i)
                coresLinhas.removeAt(i)
            }
        }

        for (i in areas.indices.reversed()) {
            if (areas[i].any {
                hypot(it.x - x, it.y - y) < raio
            }) {
                areas.removeAt(i)
                coresAreas.removeAt(i)
            }
        }
    }

    fun salvar() {
        if (identificador.isEmpty()) return

        fun converter(
            listas: List<List<PointF>>
        ): JSONArray {
            val resultado = JSONArray()

            listas.forEach { lista ->
                val arr = JSONArray()

                lista.forEach { ponto ->
                    arr.put(
                        JSONObject()
                            .put("x", ponto.x)
                            .put("y", ponto.y)
                    )
                }

                resultado.put(arr)
            }

            return resultado
        }

        val dados = JSONObject()
            .put("areas", converter(areas))
            .put("linhas", converter(linhas))
            .put("coresAreas", JSONArray(coresAreas))
            .put("coresLinhas", JSONArray(coresLinhas))

        context.getSharedPreferences("pinturas", 0)
            .edit()
            .putString(identificador, dados.toString())
            .apply()
    }

    private fun restaurar() {
        try {
            val textoSalvo = context.getSharedPreferences(
                "pinturas", 0
            ).getString(identificador, "") ?: ""

            if (textoSalvo.isEmpty()) return

            fun ler(
                arr: JSONArray,
                destino: MutableList<MutableList<PointF>>
            ) {
                for (i in 0 until arr.length()) {
                    val lista = mutableListOf<PointF>()
                    val pontosJson = arr.getJSONArray(i)

                    for (j in 0 until pontosJson.length()) {
                        val ponto = pontosJson.getJSONObject(j)

                        lista.add(
                            PointF(
                                ponto.getDouble("x").toFloat(),
                                ponto.getDouble("y").toFloat()
                            )
                        )
                    }

                    destino.add(lista)
                }
            }

            if (textoSalvo.trim().startsWith("[")) {
                ler(JSONArray(textoSalvo), linhas)

                repeat(linhas.size) {
                    coresLinhas.add(0x66FACC15)
                }
            } else {
                val dados = JSONObject(textoSalvo)

                dados.optJSONArray("areas")?.let {
                    ler(it, areas)
                }

                dados.optJSONArray("linhas")?.let {
                    ler(it, linhas)
                }

                dados.optJSONArray("coresAreas")?.let {
                    for (i in 0 until it.length()) {
                        coresAreas.add(it.getInt(i))
                    }
                }

                dados.optJSONArray("coresLinhas")?.let {
                    for (i in 0 until it.length()) {
                        coresLinhas.add(it.getInt(i))
                    }
                }

                while (coresAreas.size < areas.size) {
                    coresAreas.add(0x66FACC15)
                }

                while (coresLinhas.size < linhas.size) {
                    coresLinhas.add(0x66FACC15)
                }
            }
        } catch (_: Exception) {
        }
    }
}

class GeoPdf private constructor(
    private val bbox: DoubleArray,
    private val lpts: DoubleArray,
    private val gpts: DoubleArray
) {

    private val latReferencia =
        (0 until 4).map { gpts[it * 2] }.average()

    private fun projetar(
        lat: Double,
        lon: Double
    ): Pair<Double, Double> {
        val raio = 6378137.0

        return Pair(
            raio * Math.toRadians(lon) *
                cos(Math.toRadians(latReferencia)),
            raio * Math.toRadians(lat)
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

    private val lx = DoubleArray(4) { lpts[it * 2] }
    private val ly = DoubleArray(4) { lpts[it * 2 + 1] }

    private fun bilinear(
        valores: DoubleArray,
        u: Double,
        v: Double
    ): Double {
        return valores[0] * (1 - u) * (1 - v) +
            valores[1] * (1 - u) * v +
            valores[2] * u * v +
            valores[3] * u * (1 - v)
    }

    private fun inversa(
        x: Double,
        y: Double,
        xx: DoubleArray,
        yy: DoubleArray
    ): Pair<Double, Double>? {
        var u = 0.5
        var v = 0.5

        repeat(25) {
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

            val determinante = xu * yv - xv * yu

            if (abs(determinante) < 1e-12) return null

            val du = (ex * yv - ey * xv) / determinante
            val dv = (ey * xu - ex * yu) / determinante

            u -= du
            v -= dv

            if (!u.isFinite() || !v.isFinite()) return null

            if (abs(du) + abs(dv) < 1e-10) {
                return Pair(u, v)
            }
        }

        return if (
            hypot(
                bilinear(xx, u, v) - x,
                bilinear(yy, u, v) - y
            ) < 0.1
        ) {
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
            mx,
            my
        ) ?: return null

        if (
            uv.first !in 0.0..1.0 ||
            uv.second !in 0.0..1.0
        ) return null

        val px = bilinear(lx, uv.first, uv.second)
        val py = bilinear(ly, uv.first, uv.second)

        val pdfX = bbox[0] + px * (bbox[2] - bbox[0])
        val pdfY = bbox[1] + py * (bbox[3] - bbox[1])

        return PointF(
            (pdfX * w).toFloat(),
            ((1.0 - pdfY) * h).toFloat()
        )
    }

    fun pixelParaMetros(
        px: Float,
        py: Float,
        w: Int,
        h: Int
    ): Pair<Double, Double>? {
        val pdfX = px.toDouble() / w
        val pdfY = 1.0 - py.toDouble() / h

        val localX =
            (pdfX - bbox[0]) / (bbox[2] - bbox[0])

        val localY =
            (pdfY - bbox[1]) / (bbox[3] - bbox[1])

        val uv = inversa(
            localX,
            localY,
            lx,
            ly
        ) ?: return null

        if (
            uv.first !in 0.0..1.0 ||
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
            val pos = texto.indexOf(chave, inicio)
            if (pos < 0) return null

            val abre = texto.indexOf('[', pos + chave.length)
            if (abre < 0 || abre - pos > 40) return null

            val fecha = texto.indexOf(']', abre + 1)
            if (fecha < 0 || fecha - abre > 2500) return null

            return numeros.findAll(
                texto.substring(abre + 1, fecha)
            ).mapNotNull {
                it.value.toDoubleOrNull()
            }.toList().toDoubleArray()
        }

        fun ler(arquivo: File): GeoPdf? {
            return try {
                if (arquivo.length() > 30000000L) return null

                val texto = arquivo.readBytes().toString(
                    Charsets.ISO_8859_1
                )

                val gpsPos = texto.indexOf("/GPTS")
                if (gpsPos < 0) return null

                val vpPos = texto.lastIndexOf("/VP", gpsPos)
                val bboxPos = texto.lastIndexOf("/BBox", gpsPos)

                if (
                    vpPos < 0 ||
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

                if (
                    bbox.size != 4 ||
                    gpts.size != 8 ||
                    lpts.size != 8
                ) return null

                if (
                    bbox[2] <= bbox[0] ||
                    bbox[3] <= bbox[1]
                ) return null

                if ((0 until 4).any {
                        abs(gpts[it * 2]) > 90.0 ||
                            abs(gpts[it * 2 + 1]) > 180.0
                    }
                ) return null

                GeoPdf(bbox, lpts, gpts)

            } catch (_: Exception) {
                null
            }
        }
    }
}
