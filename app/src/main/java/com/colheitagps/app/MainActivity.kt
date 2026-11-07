
package com.colheitagps.app

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.graphics.pdf.PdfRenderer
import android.location.*
import android.os.*
import android.provider.OpenableColumns
import android.view.*
import android.widget.*
import org.json.*
import java.io.*
import java.security.MessageDigest
import java.util.Locale
import kotlin.math.*

class MainActivity : Activity() {
    private val verde = Color.rgb(20,139,81)
    private val escuro = Color.rgb(23,32,29)
    private var raiz: FrameLayout? = null
    private var mapa: MapaView? = null
    private var chaveAtual = ""
    private var nomeAtual = ""
    private var gpsManager: LocationManager? = null
    private var gpsListener: LocationListener? = null
    private var local: Location? = null
    private var status: TextView? = null

    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()

    private fun texto(s: String, size: Float = 15f, color: Int = escuro) =
        TextView(this).apply {
            text = s
            textSize = size
            setTextColor(color)
            gravity = Gravity.CENTER_VERTICAL
        }

    private fun botao(s: String, acao: () -> Unit) =
        Button(this).apply {
            text = s
            textSize = 12f
            isAllCaps = false
            setTextColor(Color.WHITE)
            backgroundTintList = ColorStateList.valueOf(verde)
            setOnClickListener { acao() }
        }

    private fun redondo(s: String, acao: () -> Unit) =
        TextView(this).apply {
            text = s
            textSize = 29f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(verde)
            }
            elevation = dp(7).toFloat()
            setOnClickListener { acao() }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = escuro
        window.navigationBarColor = Color.BLACK
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
        }
        biblioteca()
    }

    private fun tela(): FrameLayout =
        FrameLayout(this).apply {
            setBackgroundColor(0xFFF4F7F5.toInt())
            setOnApplyWindowInsetsListener { v, ins ->
                if (Build.VERSION.SDK_INT >= 30) {
                    val b = ins.getInsets(
                        WindowInsets.Type.statusBars() or
                        WindowInsets.Type.navigationBars() or
                        WindowInsets.Type.displayCutout()
                    )
                    v.setPadding(b.left, b.top, b.right, b.bottom)
                } else {
                    @Suppress("DEPRECATION")
                    v.setPadding(
                        ins.systemWindowInsetLeft,
                        ins.systemWindowInsetTop,
                        ins.systemWindowInsetRight,
                        ins.systemWindowInsetBottom
                    )
                }
                ins
            }
            raiz = this
        }

    private fun pasta() =
        File(filesDir, "mapas").apply { mkdirs() }

    private fun prefs() =
        getSharedPreferences("mapas", 0)

    private fun arquivos() =
        pasta().listFiles()
            ?.filter { it.extension.equals("pdf", true) }
            ?.sortedBy { it.name }
            ?: emptyList()

    private fun nome(id: String) =
        prefs().getString(id, "Mapa PDF") ?: "Mapa PDF"

    private fun biblioteca() {
        mapa?.salvar()
        mapa = null
        chaveAtual = ""
        status = null

        val base = tela()
        val coluna = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val cab = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(escuro)
            setPadding(dp(20), dp(20), dp(20), dp(18))
        }
        cab.addView(texto("COLHEITA GPS", 23f, Color.WHITE))
        cab.addView(
            texto("Meus mapas de colheita", 14f, 0xFFB6DCC6.toInt())
        )
        coluna.addView(cab)

        val lista = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(15), dp(12), dp(15), dp(100))
        }

        val todos = arquivos()
        if (todos.isEmpty()) {
            lista.addView(
                texto("Toque em + para importar seu mapa PDF.", 16f).apply {
                    setPadding(dp(10), dp(50), dp(10), dp(20))
                }
            )
        }

        for (f in todos) {
            val id = f.nameWithoutExtension
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14), dp(12), dp(10), dp(12))
                background = GradientDrawable().apply {
                    setColor(Color.WHITE)
                    cornerRadius = dp(14).toFloat()
                }
                elevation = dp(2).toFloat()
            }

            item.addView(
                texto("▧", 30f, verde),
                LinearLayout.LayoutParams(dp(48), dp(58))
            )

            val info = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
            }
            info.addView(texto(nome(id), 16f).apply { maxLines = 2 })
            info.addView(texto("Mapa PDF • Abrir", 12f, Color.GRAY))

            item.addView(info, LinearLayout.LayoutParams(0, -2, 1f))

            item.addView(
                texto("⋮", 26f).apply {
                    gravity = Gravity.CENTER
                    setOnClickListener { opcoesMapa(id) }
                },
                LinearLayout.LayoutParams(dp(44), dp(54))
            )

            item.setOnClickListener { abrir(id) }
            lista.addView(
                item,
                LinearLayout.LayoutParams(-1, -2).apply {
                    bottomMargin = dp(11)
                }
            )
        }

        val scroll = ScrollView(this).apply {
            addView(lista)
        }
        coluna.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        base.addView(coluna, FrameLayout.LayoutParams(-1, -1))

        base.addView(
            redondo("+") { importar() },
            FrameLayout.LayoutParams(
                dp(62), dp(62), Gravity.BOTTOM or Gravity.END
            ).apply {
                bottomMargin = dp(22)
                rightMargin = dp(22)
            }
        )

        setContentView(base)
        base.requestApplyInsets()
    }

    private fun opcoesMapa(id: String) {
        AlertDialog.Builder(this)
            .setTitle(nome(id))
            .setItems(
                arrayOf("Abrir", "O.S. de trabalho", "Renomear", "Excluir")
            ) { _, i ->
                when (i) {
                    0 -> abrir(id)
                    1 -> ordens(id)
                    2 -> renomear(id)
                    3 -> excluir(id)
                }
            }.show()
    }

    private fun renomear(id: String) {
        val entrada = EditText(this).apply {
            setText(nome(id))
            setSingleLine(true)
        }

        AlertDialog.Builder(this)
            .setTitle("Renomear mapa")
            .setView(entrada)
            .setPositiveButton("Salvar") { _, _ ->
                val s = entrada.text.toString().trim()
                if (s.isNotEmpty()) {
                    prefs().edit().putString(id, s).apply()
                    biblioteca()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun excluir(id: String) {
        AlertDialog.Builder(this)
            .setTitle("Excluir mapa?")
            .setMessage(
                "Isso também apaga as pinturas e os números de O.S. deste mapa."
            )
            .setPositiveButton("Excluir") { _, _ ->
                File(pasta(), "$id.pdf").delete()
                prefs().edit().remove(id).apply()
                getSharedPreferences("pinturas", 0)
                    .edit().remove(id).apply()
                getSharedPreferences("ordens_servico", 0)
                    .edit().remove(id).apply()
                biblioteca()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun ordens(id: String) {
        val pref = getSharedPreferences("ordens_servico", 0)

        fun lista(): MutableList<String> {
            val a = mutableListOf<String>()
            try {
                val j = JSONArray(pref.getString(id, "[]"))
                for (i in 0 until j.length()) {
                    a.add(j.getString(i))
                }
            } catch (_: Exception) {}
            return a
        }

        fun guardar(a: List<String>) {
            pref.edit()
                .putString(id, JSONArray(a).toString())
                .apply()
        }

        val dados = lista()
        val itens = (
            dados.map { "O.S. $it" } +
                "＋ Adicionar número"
            ).toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("O.S. • ${nome(id)}")
            .setItems(itens) { _, pos ->
                val novo = pos == dados.size

                val campo = EditText(this).apply {
                    inputType = android.text.InputType.TYPE_CLASS_TEXT
                    setSingleLine(true)
                    if (!novo) setText(dados[pos])
                    hint = "Número da O.S."
                }

                val dialogo = AlertDialog.Builder(this)
                    .setTitle(if (novo) "Nova O.S." else "Editar O.S.")
                    .setView(campo)
                    .setPositiveButton("Salvar") { _, _ ->
                        val numero = campo.text.toString().trim()
                        if (numero.isNotEmpty()) {
                            if (novo) {
                                dados.add(numero)
                            } else {
                                dados[pos] = numero
                            }
                            guardar(dados)
                            ordens(id)
                        }
                    }
                    .setNegativeButton("Cancelar") { _, _ ->
                        ordens(id)
                    }

                if (!novo) {
                    dialogo.setNeutralButton("Excluir") { _, _ ->
                        dados.removeAt(pos)
                        guardar(dados)
                        ordens(id)
                    }
                }
                dialogo.show()
            }
            .setNegativeButton("Fechar", null)
            .show()
    }

    private fun importar() {
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
            var titulo = "Mapa.pdf"

            contentResolver.query(uri, null, null, null, null)?.use { c ->
                val n = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (n >= 0 && c.moveToFirst()) {
                    titulo = c.getString(n) ?: titulo
                }
            }

            val tmp = File(cacheDir, "importando.pdf")
            val hash = MessageDigest.getInstance("SHA-256")

            contentResolver.openInputStream(uri)!!.use { entrada ->
                FileOutputStream(tmp).use { saida ->
                    val buf = ByteArray(8192)
                    while (true) {
                        val n = entrada.read(buf)
                        if (n < 0) break
                        hash.update(buf, 0, n)
                        saida.write(buf, 0, n)
                    }
                }
            }

            val id = hash.digest().joinToString("") {
                "%02x".format(it)
            }

            val destino = File(pasta(), "$id.pdf")
            if (!destino.exists()) tmp.copyTo(destino)
            tmp.delete()

            prefs().edit().putString(id, titulo).apply()
            biblioteca()
        } catch (e: Exception) {
            Toast.makeText(
                this,
                "Erro ao importar: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun abrir(id: String) {
        try {
            val arquivo = File(pasta(), "$id.pdf")
            val fd = ParcelFileDescriptor.open(
                arquivo, ParcelFileDescriptor.MODE_READ_ONLY
            )
            val imagem: Bitmap
            val renderer = PdfRenderer(fd)

            try {
                val pagina: PdfRenderer.Page = renderer.openPage(0)
                try {
                    val fator = minOf(
                        3.0,
                        4000.0 / maxOf(pagina.width, pagina.height)
                    )
                    val limite = minOf(
                        1.0,
                        sqrt(
                            16000000.0 /
                                (
                                    pagina.width.toDouble() *
                                        pagina.height * fator * fator
                                    )
                        )
                    )

                    val w = maxOf(
                        1, (pagina.width * fator * limite).toInt()
                    )
                    val h = maxOf(
                        1, (pagina.height * fator * limite).toInt()
                    )

                    imagem = Bitmap.createBitmap(
                        w, h, Bitmap.Config.ARGB_8888
                    )
                    imagem.eraseColor(Color.WHITE)

                    pagina.render(
                        imagem, null, null,
                        PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                    )
                } finally {
                    pagina.close()
                }
            } finally {
                renderer.close()
            }

            chaveAtual = id
            nomeAtual = nome(id)
            mostrarMapa(imagem, id, GeoPdf.ler(arquivo))
        } catch (e: Exception) {
            chaveAtual = ""
            Toast.makeText(
                this,
                "Erro ao abrir: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun mostrarMapa(
        imagem: Bitmap,
        id: String,
        geo: GeoPdf?
    ) {
        val base = tela()
        val visual = MapaView(this).apply {
            carregar(imagem, id, geo)
        }
        mapa = visual

        base.addView(
            visual,
            FrameLayout.LayoutParams(-1, -1)
        )

        val cab = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(7), dp(10), dp(7))
            setBackgroundColor(escuro)
        }

        cab.addView(
            texto("‹", 34f, Color.WHITE).apply {
                gravity = Gravity.CENTER
                setOnClickListener { biblioteca() }
            },
            LinearLayout.LayoutParams(dp(48), dp(52))
        )

        val info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        info.addView(
            texto(nomeAtual, 16f, Color.WHITE).apply {
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }
        )

        status = texto(
            if (geo != null) {
                "GeoPDF reconhecido • conferir posição"
            } else {
                "Georreferência não reconhecida"
            },
            11f,
            0xFFB9D7C4.toInt()
        )
        info.addView(status)

        cab.addView(
            info,
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        cab.addView(
            texto("◎", 29f, Color.WHITE).apply {
                gravity = Gravity.CENTER
                setOnClickListener {
                    if (local == null) {
                        iniciarGps()
                    } else {
                        visual.centrarGps()
                    }
                }
            },
            LinearLayout.LayoutParams(dp(48), dp(52))
        )

        base.addView(
            cab,
            FrameLayout.LayoutParams(-1, -2, Gravity.TOP)
        )

        base.addView(
            redondo("+") { menu() },
            FrameLayout.LayoutParams(
                dp(62), dp(62), Gravity.BOTTOM or Gravity.END
            ).apply {
                bottomMargin = dp(22)
                rightMargin = dp(22)
            }
        )

        setContentView(base)
        base.requestApplyInsets()

        local?.let {
            visual.atualizarGps(it.latitude, it.longitude)
        }
        iniciarGps()
    }

    private fun menu() {
        AlertDialog.Builder(this)
            .setTitle("Ferramentas")
            .setItems(
                arrayOf(
                    "Pintar talhão",
                    "Medir área",
                    "Medir distância",
                    "Pincel",
                    "Borracha",
                    "Desfazer",
                    "O.S. de trabalho",
                    "GPS",
                    "Voltar aos mapas"
                )
            ) { _, i ->
                when (i) {
                    0 -> ferramenta(7)
                    1 -> ferramenta(8)
                    2 -> ferramenta(9)
                    3 -> ferramenta(1)
                    4 -> ferramenta(2)
                    5 -> mapa?.desfazer()
                    6 -> ordens(chaveAtual)
                    7 -> iniciarGps()
                    8 -> biblioteca()
                }
            }.show()
    }

    private fun ferramenta(tipo: Int) {
        val v = mapa ?: return
        v.iniciar(tipo)

        val base = raiz ?: return
        base.findViewWithTag<View>("painel")?.let {
            base.removeView(it)
        }

        val painel = LinearLayout(this).apply {
            tag = "painel"
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(escuro)
            setPadding(dp(5), dp(7), dp(5), dp(7))
        }

        val titulo = when (tipo) {
            7 -> "PINTAR TALHÃO"
            8 -> "MEDIR ÁREA"
            9 -> "MEDIR DISTÂNCIA"
            1 -> "PINCEL"
            else -> "BORRACHA"
        }

        painel.addView(
            texto(titulo, 13f, Color.WHITE).apply {
                gravity = Gravity.CENTER
            }
        )

        val linha = LinearLayout(this)

        fun adicionar(s: String, acao: () -> Unit) {
            linha.addView(
                botao(s, acao),
                LinearLayout.LayoutParams(0, dp(48), 1f)
            )
        }

        if (tipo in 7..9) {
            adicionar("+ Ponto") {
                v.adicionarPontoCentral()
            }
            adicionar("Desfazer") {
                v.desfazer()
            }
            adicionar(
                if (tipo == 9) "Concluir" else "Fechar"
            ) {
                if (tipo == 7) {
                    if (v.fecharArea()) escolherCor()
                } else {
                    v.concluirMedida()
                }
            }
        } else {
            adicionar("Cor") { escolherCor() }
            adicionar("Desfazer") { v.desfazer() }
        }

        adicionar("Sair") {
            v.cancelarFerramenta()
            base.removeView(painel)
        }

        painel.addView(linha)

        base.addView(
            painel,
            FrameLayout.LayoutParams(
                -1, -2, Gravity.BOTTOM
            ).apply {
                bottomMargin = dp(94)
            }
        )
    }

    private fun escolherCor() {
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
            .setTitle("Cor")
            .setItems(nomes) { _, i ->
                mapa?.corPintura = cores[i]

                if (
                    mapa?.modo == 7 &&
                    mapa?.areaFechada == true
                ) {
                    AlertDialog.Builder(this)
                        .setTitle("Transparência")
                        .setItems(
                            arrayOf(
                                "25%",
                                "40% (recomendado)",
                                "60%"
                            )
                        ) { _, a ->
                            mapa?.opacidade =
                                intArrayOf(64, 102, 153)[a]

                            if (mapa?.salvarArea() == true) {
                                raiz?.findViewWithTag<View>("painel")
                                    ?.let { raiz?.removeView(it) }
                                mapa?.cancelarFerramenta()
                            }
                        }.show()
                }
            }.show()
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
            status?.text = "Ative o GPS do celular"
            return
        }

        gpsListener?.let {
            gpsManager?.removeUpdates(it)
        }

        gpsListener = LocationListener { p: Location ->
            local = p
            mapa?.atualizarGps(p.latitude, p.longitude)

            status?.text = String.format(
                Locale("pt", "BR"),
                "GPS ±%.0f m • %.5f, %.5f",
                p.accuracy,
                p.latitude,
                p.longitude
            )
        }

        try {
            gpsManager?.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                2000L,
                1f,
                gpsListener!!
            )
            status?.text = "Buscando sinal GPS..."
        } catch (_: SecurityException) {
            status?.text = "Permissão de localização necessária"
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
        if (chaveAtual.isNotEmpty()) {
            biblioteca()
        } else {
            super.onBackPressed()
        }
    }

    override fun onPause() {
        super.onPause()
        mapa?.salvar()
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
    var areaFechada = false
        private set

    var corPintura = 0xFFFACC15.toInt()
    var opacidade = 102

    private var imagem: Bitmap? = null
    private var id = ""
    private var geo: GeoPdf? = null

    private var escala = 1f
    private var dx = 0f
    private var dy = 0f
    private var anteriorX = 0f
    private var anteriorY = 0f
    private var multitoque = false

    private var gpsLat: Double? = null
    private var gpsLon: Double? = null

    private val pontos = mutableListOf<PointF>()
    private val areas = mutableListOf<MutableList<PointF>>()
    private val coresAreas = mutableListOf<Int>()
    private val linhas = mutableListOf<MutableList<PointF>>()
    private val coresLinhas = mutableListOf<Int>()

    private var linhaAtual: MutableList<PointF>? = null

    private val detector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(d: ScaleGestureDetector): Boolean {
                val old = escala
                escala = (escala * d.scaleFactor).coerceIn(0.05f, 16f)
                val f = escala / old

                dx = d.focusX - (d.focusX - dx) * f
                dy = d.focusY - (d.focusY - dy) * f

                invalidate()
                return true
            }
        }
    )

    fun carregar(
        b: Bitmap,
        chave: String,
        referencia: GeoPdf?
    ) {
        imagem = b
        id = chave
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
                    width.toFloat() / b.width,
                    height.toFloat() / b.height
                )
                dx = (width - b.width * escala) / 2f
                dy = (height - b.height * escala) / 2f
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
        if (
            modo in 7..9 &&
            !areaFechada &&
            imagem != null
        ) {
            pontos.add(
                PointF(
                    (width / 2f - dx) / escala,
                    (height / 2f - dy) / escala
                )
            )
            invalidate()
        }
    }

    fun fecharArea(): Boolean {
        if (modo != 7 || pontos.size < 3) {
            aviso("Adicione pelo menos 3 pontos")
            return false
        }
        areaFechada = true
        invalidate()
        return true
    }

    private fun corAtual() =
        Color.argb(
            opacidade,
            Color.red(corPintura),
            Color.green(corPintura),
            Color.blue(corPintura)
        )

    fun salvarArea(): Boolean {
        if (
            modo != 7 ||
            !areaFechada ||
            pontos.size < 3
        ) return false

        areas.add(
            pontos.map {
                PointF(it.x, it.y)
            }.toMutableList()
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
        val g = geo
        val b = imagem

        if (g == null || b == null) {
            aviso(
                "Georreferência não reconhecida: não é possível medir em metros ou hectares."
            )
            return
        }

        val m = pontos.map {
            g.pixelParaMetros(it.x, it.y, b.width, b.height)
        }

        if (m.any { it == null }) {
            aviso("Pontos fora da região georreferenciada.")
            return
        }

        val xy = m.filterNotNull()

        if (modo == 8) {
            if (xy.size < 3) {
                aviso("Adicione 3 pontos ou mais")
                return
            }

            areaFechada = true
            invalidate()
            var soma = 0.0

            for (i in xy.indices) {
                val j = (i + 1) % xy.size
                soma +=
                    xy[i].first * xy[j].second -
                    xy[j].first * xy[i].second
            }

            aviso(
                String.format(
                    Locale("pt", "BR"),
                    "Área aproximada: %.4f ha",
                    abs(soma) / 20000.0
                )
            )
        } else if (modo == 9) {
            if (xy.size < 2) {
                aviso("Adicione pelo menos 2 pontos")
                return
            }

            var d = 0.0
            for (i in 1 until xy.size) {
                d += hypot(
                    xy[i].first - xy[i - 1].first,
                    xy[i].second - xy[i - 1].second
                )
            }

            aviso(
                String.format(
                    Locale("pt", "BR"),
                    "Distância aproximada: %.2f m",
                    d
                )
            )
        }
    }

    private fun aviso(s: String) {
        AlertDialog.Builder(context)
            .setMessage(s)
            .setPositiveButton("OK", null)
            .show()
    }

    fun atualizarGps(lat: Double, lon: Double) {
        gpsLat = lat
        gpsLon = lon
        invalidate()
    }

    private fun gpsPixel(): PointF? {
        val b = imagem ?: return null
        val lat = gpsLat ?: return null
        val lon = gpsLon ?: return null

        return geo?.gpsParaPixel(
            lat, lon, b.width, b.height
        )
    }

    fun centrarGps() {
        val p = gpsPixel()

        if (p == null) {
            aviso(
                "Posição fora do mapa ou georreferência não reconhecida."
            )
            return
        }

        dx = width / 2f - p.x * escala
        dy = height / 2f - p.y * escala
        invalidate()
    }

    private fun caminho(
        lista: List<PointF>,
        fechar: Boolean
    ): Path {
        val p = Path()
        if (lista.isEmpty()) return p

        p.moveTo(lista[0].x, lista[0].y)

        for (i in 1 until lista.size) {
            p.lineTo(lista[i].x, lista[i].y)
        }

        if (fechar) p.close()
        return p
    }

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        c.drawColor(0xFFE5EAE6.toInt())

        c.save()
        c.translate(dx, dy)
        c.scale(escala, escala)

        imagem?.let {
            c.drawBitmap(it, 0f, 0f, null)
        }

        for (i in areas.indices) {
            c.drawPath(
                caminho(areas[i], true),
                Paint(3).apply {
                    color = coresAreas.getOrElse(i) {
                        0x66FACC15
                    }
                    style = Paint.Style.FILL
                }
            )

            c.drawPath(
                caminho(areas[i], true),
                Paint(3).apply {
                    color = 0xFF17804D.toInt()
                    style = Paint.Style.STROKE
                    strokeWidth = 2f / escala
                }
            )
        }

        for (i in linhas.indices) {
            c.drawPath(
                caminho(linhas[i], false),
                Paint(3).apply {
                    color = coresLinhas.getOrElse(i) {
                        0x66FACC15
                    }
                    style = Paint.Style.STROKE
                    strokeWidth = 17f / escala
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                }
            )
        }

        if (pontos.isNotEmpty()) {
            c.drawPath(
                caminho(pontos, areaFechada),
                Paint(3).apply {
                    color = 0xFF087CDE.toInt()
                    style = Paint.Style.STROKE
                    strokeWidth = 3f / escala
                }
            )

            if (areaFechada) {
                c.drawPath(
                    caminho(pontos, true),
                    Paint(3).apply {
                        color = 0x3322C55E
                        style = Paint.Style.FILL
                    }
                )
            }

            for (p in pontos) {
                c.drawCircle(
                    p.x, p.y, 6f / escala,
                    Paint(3).apply {
                        color = Color.WHITE
                    }
                )

                c.drawCircle(
                    p.x, p.y, 6f / escala,
                    Paint(3).apply {
                        color = 0xFF087CDE.toInt()
                        style = Paint.Style.STROKE
                        strokeWidth = 2f / escala
                    }
                )
            }
        }

        gpsPixel()?.let { p ->
            c.drawCircle(
                p.x, p.y, 13f / escala,
                Paint(3).apply { color = Color.WHITE }
            )
            c.drawCircle(
                p.x, p.y, 9f / escala,
                Paint(3).apply {
                    color = 0xFF1674E8.toInt()
                }
            )
        }

        c.restore()

        if (modo in 7..9) {
            val x = width / 2f
            val y = height / 2f

            val p = Paint(3).apply {
                color = 0xFF087CDE.toInt()
                style = Paint.Style.STROKE
                strokeWidth = 3f
            }

            c.drawCircle(x, y, 10f, p)
            c.drawLine(x - 30, y, x - 14, y, p)
            c.drawLine(x + 14, y, x + 30, y, p)
            c.drawLine(x, y - 30, x, y - 14, p)
            c.drawLine(x, y + 14, x, y + 30, p)
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (imagem == null) return true

        if (
            e.actionMasked ==
            MotionEvent.ACTION_POINTER_DOWN
        ) {
            multitoque = true
            linhaAtual = null
        }

        detector.onTouchEvent(e)

        if (e.pointerCount > 1 || multitoque) {
            if (
                e.actionMasked == MotionEvent.ACTION_UP ||
                e.actionMasked == MotionEvent.ACTION_CANCEL
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
                anteriorX = e.x
                anteriorY = e.y

                if (modo == 1) {
                    linhaAtual = mutableListOf(PointF(x, y))
                    linhas.add(linhaAtual!!)
                    coresLinhas.add(corAtual())
                }

                if (modo == 2) apagar(x, y)
            }

            MotionEvent.ACTION_MOVE -> {
                when (modo) {
                    0, 7, 8, 9 -> {
                        dx += e.x - anteriorX
                        dy += e.y - anteriorY
                    }
                    1 -> linhaAtual?.add(PointF(x, y))
                    2 -> apagar(x, y)
                }
                anteriorX = e.x
                anteriorY = e.y
            }

            MotionEvent.ACTION_UP -> {
                linhaAtual = null
                if (modo == 1 || modo == 2) salvar()
            }
        }

        invalidate()
        return true
    }

    private fun apagar(x: Float, y: Float) {
        val raio = 25f / escala

        for (i in linhas.indices.reversed()) {
            if (
                linhas[i].any {
                    hypot(it.x - x, it.y - y) < raio
                }
            ) {
                linhas.removeAt(i)
                coresLinhas.removeAt(i)
            }
        }

        for (i in areas.indices.reversed()) {
            if (
                areas[i].any {
                    hypot(it.x - x, it.y - y) < raio
                }
            ) {
                areas.removeAt(i)
                coresAreas.removeAt(i)
            }
        }
    }

    fun salvar() {
        if (id.isEmpty()) return

        fun converter(v: List<List<PointF>>): JSONArray {
            val a = JSONArray()

            for (lista in v) {
                val p = JSONArray()
                for (t in lista) {
                    p.put(
                        JSONObject()
                            .put("x", t.x)
                            .put("y", t.y)
                    )
                }
                a.put(p)
            }
            return a
        }

        val j = JSONObject()
            .put("areas", converter(areas))
            .put("linhas", converter(linhas))
            .put("coresAreas", JSONArray(coresAreas))
            .put("coresLinhas", JSONArray(coresLinhas))

        context.getSharedPreferences("pinturas", 0)
            .edit()
            .putString(id, j.toString())
            .apply()
    }

    private fun restaurar() {
        try {
            val s = context.getSharedPreferences("pinturas", 0)
                .getString(id, "") ?: ""

            if (s.isEmpty()) return

            fun ler(
                j: JSONArray,
                d: MutableList<MutableList<PointF>>
            ) {
                for (i in 0 until j.length()) {
                    val arr = j.getJSONArray(i)
                    val pontos = mutableListOf<PointF>()

                    for (k in 0 until arr.length()) {
                        val p = arr.getJSONObject(k)
                        pontos.add(
                            PointF(
                                p.getDouble("x").toFloat(),
                                p.getDouble("y").toFloat()
                            )
                        )
                    }
                    d.add(pontos)
                }
            }

            if (s.trim().startsWith("[")) {
                ler(JSONArray(s), linhas)
                repeat(linhas.size) {
                    coresLinhas.add(0x66FACC15)
                }
            } else {
                val j = JSONObject(s)

                j.optJSONArray("areas")?.let {
                    ler(it, areas)
                }
                j.optJSONArray("linhas")?.let {
                    ler(it, linhas)
                }
                j.optJSONArray("coresAreas")?.let {
                    for (i in 0 until it.length()) {
                        coresAreas.add(it.getInt(i))
                    }
                }
                j.optJSONArray("coresLinhas")?.let {
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
        } catch (_: Exception) {}
    }
}

class GeoPdf private constructor(
    private val zonas: List<Zona>
) {
    private class Zona(
        val bbox: DoubleArray,
        val lpts: DoubleArray,
        val gpts: DoubleArray,
        val area: Double
    ) {
        val latitudeMedia =
            (0 until 4).map { gpts[2 * it] }.average()

        val r = 6378137.0

        val mx = DoubleArray(4) {
            r * Math.toRadians(gpts[2 * it + 1]) *
                cos(Math.toRadians(latitudeMedia))
        }

        val my = DoubleArray(4) {
            r * Math.toRadians(gpts[2 * it])
        }

        val lx = DoubleArray(4) { lpts[2 * it] }
        val ly = DoubleArray(4) { lpts[2 * it + 1] }

        private fun ajuste(
            xs: DoubleArray,
            ys: DoubleArray,
            valor: DoubleArray
        ): DoubleArray? {
            val a = Array(3) { DoubleArray(4) }

            for (i in 0 until 4) {
                val v = doubleArrayOf(1.0, xs[i], ys[i])

                for (j in 0..2) {
                    for (k in 0..2) {
                        a[j][k] += v[j] * v[k]
                    }
                    a[j][3] += v[j] * valor[i]
                }
            }

            for (j in 0..2) {
                var p = j

                for (k in j + 1..2) {
                    if (abs(a[k][j]) > abs(a[p][j])) {
                        p = k
                    }
                }

                if (abs(a[p][j]) < 1e-14) return null

                val t = a[p]
                a[p] = a[j]
                a[j] = t

                val z = a[j][j]
                for (k in j..3) {
                    a[j][k] /= z
                }

                for (i in 0..2) {
                    if (i != j) {
                        val f = a[i][j]
                        for (k in j..3) {
                            a[i][k] -= f * a[j][k]
                        }
                    }
                }
            }

            return DoubleArray(3) { a[it][3] }
        }

        private val ax = ajuste(lx, ly, mx)
        private val ay = ajuste(lx, ly, my)

        fun metrosParaPdf(
            lat: Double,
            lon: Double
        ): Pair<Double, Double>? {
            val a = ax ?: return null
            val b = ay ?: return null

            val x = r * Math.toRadians(lon) *
                cos(Math.toRadians(latitudeMedia))
            val y = r * Math.toRadians(lat)

            val det = a[1] * b[2] - a[2] * b[1]
            if (abs(det) < 1e-12) return null

            val u = (
                (x - a[0]) * b[2] -
                    (y - b[0]) * a[2]
                ) / det

            val v = (
                (y - b[0]) * a[1] -
                    (x - a[0]) * b[1]
                ) / det

            if (
                u < -0.01 || u > 1.01 ||
                v < -0.01 || v > 1.01
            ) return null

            return Pair(
                bbox[0] + u * (bbox[2] - bbox[0]),
                bbox[1] + v * (bbox[3] - bbox[1])
            )
        }

        fun pdfParaMetros(
            x: Double,
            y: Double
        ): Pair<Double, Double>? {
            if (
                x < bbox[0] || x > bbox[2] ||
                y < bbox[1] || y > bbox[3]
            ) return null

            val a = ax ?: return null
            val b = ay ?: return null

            val u = (x - bbox[0]) / (bbox[2] - bbox[0])
            val v = (y - bbox[1]) / (bbox[3] - bbox[1])

            return Pair(
                a[0] + a[1] * u + a[2] * v,
                b[0] + b[1] * u + b[2] * v
            )
        }
    }

    private var zLargura = 1.0
    private var zAltura = 1.0

    fun gpsParaPixel(
        lat: Double,
        lon: Double,
        w: Int,
        h: Int
    ): PointF? {
        for (z in zonas) {
            val p = z.metrosParaPdf(lat, lon) ?: continue
            return PointF(
                (p.first * w / zLargura).toFloat(),
                ((1.0 - p.second / zAltura) * h).toFloat()
            )
        }
        return null
    }

    fun pixelParaMetros(
        x: Float,
        y: Float,
        w: Int,
        h: Int
    ): Pair<Double, Double>? {
        val px = x / w * zLargura
        val py = (1.0 - y / h) * zAltura

        for (z in zonas) {
            z.pdfParaMetros(px, py)?.let {
                return it
            }
        }
        return null
    }

    companion object {
        private val nums = Regex(
            """[-+]?(?:\d+(?:\.\d*)?|\.\d+)(?:[eE][-+]?\d+)?"""
        )

        private fun array(
            s: String,
            chave: String
        ): DoubleArray? {
            val r = Regex(
                Regex.escape(chave) + "\\s*\\[([^]]+)]"
            ).find(s) ?: return null

            return nums.findAll(r.groupValues[1])
                .mapNotNull { it.value.toDoubleOrNull() }
                .toList()
                .toDoubleArray()
        }

        fun ler(f: File): GeoPdf? {
            try {
                if (f.length() > 60000000L) return null

                val pdf = f.readBytes().toString(
                    Charsets.ISO_8859_1
                )
                val zonas = mutableListOf<Zona>()

                val bboxRegex = Regex(
                    "/BBox\\s*\\[([^]]+)]"
                )

                for (m in bboxRegex.findAll(pdf)) {
                    val trecho = pdf.substring(
                        m.range.first,
                        minOf(pdf.length, m.range.first + 14000)
                    )

                    val prox = trecho.indexOf("/BBox", 6)
                    val bloco = if (prox > 0) {
                        trecho.substring(0, prox)
                    } else {
                        trecho
                    }

                    if (
                        !bloco.contains("/Measure") ||
                        !bloco.contains("/GPTS") ||
                        !bloco.contains("/LPTS")
                    ) continue

                    val bbox = array(bloco, "/BBox") ?: continue
                    val gpts = array(bloco, "/GPTS") ?: continue
                    val lpts = array(bloco, "/LPTS") ?: continue

                    if (
                        bbox.size != 4 ||
                        gpts.size != 8 ||
                        lpts.size != 8
                    ) continue

                    if (
                        bbox[2] <= bbox[0] ||
                        bbox[3] <= bbox[1]
                    ) continue

                    if (
                        (0 until 4).any {
                            abs(gpts[it * 2]) > 90 ||
                                abs(gpts[it * 2 + 1]) > 180
                        }
                    ) continue

                    val area = (
                        bbox[2] - bbox[0]
                        ) * (
                        bbox[3] - bbox[1]
                        )

                    zonas.add(
                        Zona(bbox, lpts, gpts, area)
                    )
                }

                if (zonas.isEmpty()) return null

                val tamanho = Regex(
                    "/MediaBox\\s*\\[([^]]+)]"
                ).find(pdf)?.groupValues?.get(1)

                val dimensoes = tamanho?.let { s ->
                    nums.findAll(s)
                        .mapNotNull {
                            it.value.toDoubleOrNull()
                        }
                        .toList()
                }

                val largura =
                    if (
                        dimensoes != null &&
                        dimensoes.size == 4
                    ) {
                        dimensoes[2] - dimensoes[0]
                    } else {
                        595.0
                    }

                val altura =
                    if (
                        dimensoes != null &&
                        dimensoes.size == 4
                    ) {
                        dimensoes[3] - dimensoes[1]
                    } else {
                        842.0
                    }

                return GeoPdf(
                    zonas.sortedByDescending { it.area }
                ).apply {
                    zLargura = largura
                    zAltura = altura
                }

            } catch (_: Exception) {
                return null
            }
        }
    }
}
