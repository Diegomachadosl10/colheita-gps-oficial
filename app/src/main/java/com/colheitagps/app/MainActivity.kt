
package com.colheitagps.app

import android.Manifest
import android.speech.RecognizerIntent
import android.net.Uri
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
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.security.MessageDigest
import java.util.Locale
import kotlin.math.*

class MainActivity : Activity() {
    private val verde = Color.rgb(20,139,81)
    private val escuro = Color.rgb(18,26,22)
    private var raiz: FrameLayout? = null
    private var mapa: MapaView? = null
    private var chaveAtual = ""
    private var nomeAtual = ""
    private var gpsManager: LocationManager? = null
    private var gpsListener: LocationListener? = null
    private var local: Location? = null
    private var status: TextView? = null
    private var telaAtual = "inicio"
    private var fotoPendente: String? = null
    private var fotosFormulario = mutableListOf<String>()
    private var atualizarFotosFormulario: (() -> Unit)? = null
    private var pdfPendente: File? = null
    private var imagemPendente: File? = null
    private var vozResultado: ((String) -> Unit)? = null
    private fun dados(key: String): JSONArray = try {
        JSONArray(getSharedPreferences("registros_v3", 0).getString(key, "[]"))
    } catch (_: Exception) { JSONArray() }
    private fun guardar(key: String, arr: JSONArray) {
        getSharedPreferences("registros_v3", 0).edit().putString(key, arr.toString()).apply()
    }
    private fun remover(key: String, index: Int, retorno: () -> Unit) {
        AlertDialog.Builder(this).setTitle("Excluir registro?")
            .setMessage("Esta ação não poderá ser desfeita.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Excluir") { _, _ ->
                val atual = dados(key)
                val novo = JSONArray()
                for (i in 0 until atual.length()) if (i != index) novo.put(atual.get(i))
                guardar(key, novo)
                retorno()
            }.show()
    }
    private fun campo(hintTexto: String, valor: String = "") = EditText(this).apply {
        hint = hintTexto
        setText(valor)
        setSingleLine(false)
    }
    private fun ouvir(resultado: (String) -> Unit) {
        try {
            vozResultado = resultado
            startActivityForResult(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Fale o número e horário do caminhão")
            }, 302)
        } catch (_: Exception) {
            Toast.makeText(this, "Reconhecimento de voz indisponível neste aparelho", Toast.LENGTH_LONG).show()
        }
    }
    private fun tratarResultadoVozEFoto(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode != RESULT_OK) return
        if (requestCode == 304) {
            val destino = data?.data ?: return
            val origem = imagemPendente ?: return
            try {
                contentResolver.openOutputStream(destino)?.use { out -> origem.inputStream().use { it.copyTo(out) } }
                Toast.makeText(this, "Imagem salva e pronta para compartilhar", Toast.LENGTH_LONG).show()
            } catch (_: Exception) { Toast.makeText(this, "Erro ao salvar imagem", Toast.LENGTH_LONG).show() }
            imagemPendente = null
            return
        }
        if (requestCode == 303) {
            val destino = data?.data ?: return
            val origem = pdfPendente ?: return
            try {
                contentResolver.openOutputStream(destino)?.use { out -> origem.inputStream().use { it.copyTo(out) } }
                Toast.makeText(this, "PDF salvo. Abra o arquivo para compartilhar.", Toast.LENGTH_LONG).show()
            } catch (e: Exception) { Toast.makeText(this, "Falha ao salvar PDF", Toast.LENGTH_LONG).show() }
            pdfPendente = null
            return
        }
        if (requestCode == 302) {
            val fala = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (fala != null) vozResultado?.invoke(fala)
            vozResultado = null
        }
        if (requestCode == 306) {
            val uri = data?.data ?: return
            try {
                val destino = File(filesDir, "foto_estoque_${System.currentTimeMillis()}.jpg")
                val bitmap = contentResolver.openInputStream(uri)?.use { entrada ->
                    BitmapFactory.decodeStream(entrada)
                } ?: throw IOException("Imagem indisponível")
                destino.outputStream().use { saida ->
                    if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 88, saida))
                        throw IOException("Falha ao gravar imagem")
                }
                bitmap.recycle()
                val nomeFoto = contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val col = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (col >= 0 && cursor.moveToFirst()) cursor.getString(col) else null
                } ?: "Foto do estoque.jpg"
                getSharedPreferences("controle_colheita", 0).edit()
                    .putString("nome_foto_estoque", nomeFoto).apply()
                val p = progresso().put("fotoEstoque", destino.absolutePath)
                salvarEstoqueAtual(p)
                estoque()
                Toast.makeText(this, "Foto anexada ao estoque", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Erro ao anexar foto: ${e.message}", Toast.LENGTH_LONG).show()
            }
            return
        }
        if (requestCode == 307 || requestCode == 308) {
            try {
                if (requestCode == 307) {
                    val uris = mutableListOf<android.net.Uri>()
                    data?.clipData?.let { clips -> for (i in 0 until clips.itemCount) uris.add(clips.getItemAt(i).uri) }
                    if (uris.isEmpty()) data?.data?.let { uris.add(it) }
                    for (uri in uris) {
                        val arquivo = File(filesDir, "pendencia_foto_${System.currentTimeMillis()}_${fotosFormulario.size}.jpg")
                        contentResolver.openInputStream(uri)?.use { entrada -> arquivo.outputStream().use { entrada.copyTo(it) } }
                        if (arquivo.length() > 0) fotosFormulario.add(arquivo.absolutePath)
                    }
                } else {
                    val miniatura = data?.extras?.get("data") as? Bitmap
                    if (miniatura != null) {
                        val arquivo = File(filesDir, "pendencia_camera_${System.currentTimeMillis()}.jpg")
                        arquivo.outputStream().use { miniatura.compress(Bitmap.CompressFormat.JPEG, 90, it) }
                        fotosFormulario.add(arquivo.absolutePath)
                    }
                }
                atualizarFotosFormulario?.invoke()
            } catch (e: Exception) { Toast.makeText(this, "Erro na foto: ${e.message}", Toast.LENGTH_LONG).show() }
            return
        }
        if (requestCode == 301) {
            val uri = data?.data ?: return
            val id = fotoPendente ?: return
            try {
                val arquivo = File(filesDir, "pendencia_${id}_${System.currentTimeMillis()}.jpg")
                contentResolver.openInputStream(uri)?.use { entrada -> arquivo.outputStream().use { entrada.copyTo(it) } }
                val arr = dados("pendencias")
                for (i in 0 until arr.length()) {
                    val item = arr.getJSONObject(i)
                    if (item.optString("id") == id) {
                        val fotos = item.optJSONArray("fotos") ?: JSONArray().also { lista ->
                            val anterior = item.optString("foto")
                            if (anterior.isNotBlank()) lista.put(anterior)
                        }
                        fotos.put(arquivo.absolutePath)
                        item.put("fotos", fotos)
                        item.put("foto", arquivo.absolutePath)
                        break
                    }
                }
                guardar("pendencias", arr)
                pendencias()
            } catch (_: Exception) { Toast.makeText(this, "Não foi possível salvar a foto", Toast.LENGTH_LONG).show() }
        }
    }


    private fun exportarBackup() {
        startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/zip"
            putExtra(Intent.EXTRA_TITLE, "ColheitaGPS_Backup.zip")
        }, 305)
    }

    private fun escreverBackup(destino: Uri) {
        val preferencias = listOf("registros_v3", "controle_colheita", "mapas", "ordens_servico")
        val indices = JSONObject()
        for (nome in preferencias) {
            val dados = JSONObject()
            for ((chave, valor) in getSharedPreferences(nome, 0).all) {
                dados.put(chave, valor?.toString() ?: "")
            }
            indices.put(nome, dados)
        }
        val saida = contentResolver.openOutputStream(destino)
            ?: throw IOException("Destino indisponível")
        ZipOutputStream(saida).use { zip ->
            zip.putNextEntry(ZipEntry("preferencias.json"))
            zip.write(indices.toString(2).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            val raiz = filesDir.canonicalFile
            raiz.walkTopDown().filter { it.isFile }.forEach { arquivo ->
                val relativo = arquivo.relativeTo(raiz).invariantSeparatorsPath
                zip.putNextEntry(ZipEntry("arquivos/" + relativo))
                arquivo.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }


    private fun solicitarRestauracaoBackup() {
        AlertDialog.Builder(this)
            .setTitle("Restaurar cópia de segurança")
            .setMessage("Os dados atuais poderão ser substituídos. Faça um backup antes de continuar.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Escolher ZIP") { _, _ ->
                startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/zip"
                }, 306)
            }.show()
    }

    private fun restaurarBackupZip(origem: Uri) {
        val preferenciasPermitidas = setOf("registros_v3", "controle_colheita", "mapas", "ordens_servico")
        val pastaTemp = File(cacheDir, "restauracao_${System.currentTimeMillis()}")
        if (!pastaTemp.mkdirs()) throw IOException("Não foi possível preparar restauração")
        try {
            var indices: JSONObject? = null
            var total = 0L
            val arquivos = mutableListOf<Pair<File, String>>()
            contentResolver.openInputStream(origem)?.use { entrada ->
                java.util.zip.ZipInputStream(entrada).use { zip ->
                    while (true) {
                        val item = zip.nextEntry ?: break
                        if (item.isDirectory) { zip.closeEntry(); continue }
                        val nome = item.name
                        if (nome != "preferencias.json" && !nome.startsWith("arquivos/"))
                            throw IOException("Arquivo ZIP incompatível")
                        if (nome.startsWith("arquivos/") && nome.removePrefix("arquivos/").isBlank())
                            throw IOException("Nome de arquivo inválido")
                        val destino = File(pastaTemp, nome).canonicalFile
                        if (!destino.path.startsWith(pastaTemp.canonicalPath + File.separator))
                            throw IOException("Caminho inválido no ZIP")
                        destino.parentFile?.mkdirs()
                        destino.outputStream().use { saida ->
                            val buffer = ByteArray(8192)
                            while (true) {
                                val lidos = zip.read(buffer)
                                if (lidos < 0) break
                                total += lidos
                                if (total > 300L * 1024 * 1024)
                                    throw IOException("Backup excede 300 MB")
                                saida.write(buffer, 0, lidos)
                            }
                        }
                        if (nome == "preferencias.json")
                            indices = JSONObject(destino.readText(Charsets.UTF_8))
                        else arquivos.add(destino to nome.removePrefix("arquivos/"))
                        zip.closeEntry()
                    }
                }
            } ?: throw IOException("Não foi possível abrir o backup")
            val json = indices ?: throw IOException("Preferências não encontradas no backup")
            for (nome in preferenciasPermitidas) {
                val objeto = json.optJSONObject(nome) ?: continue
                val editor = getSharedPreferences(nome, 0).edit()
                editor.clear()
                val chaves = objeto.keys()
                while (chaves.hasNext()) {
                    val chave = chaves.next()
                    val valor = objeto.optString(chave)
                    // Os backups anteriores guardam todos os valores como texto.
                    val atual = getSharedPreferences(nome, 0).all[chave]
                    when (atual) {
                        is Boolean -> editor.putBoolean(chave, valor.toBoolean())
                        is Int -> editor.putInt(chave, valor.toIntOrNull() ?: 0)
                        is Long -> editor.putLong(chave, valor.toLongOrNull() ?: 0L)
                        is Float -> editor.putFloat(chave, valor.toFloatOrNull() ?: 0f)
                        else -> editor.putString(chave, valor)
                    }
                }
                if (!editor.commit()) throw IOException("Falha ao restaurar preferências")
            }
            for ((arquivo, relativo) in arquivos) {
                val destino = File(filesDir, relativo).canonicalFile
                if (!destino.path.startsWith(filesDir.canonicalPath + File.separator))
                    throw IOException("Destino inválido")
                destino.parentFile?.mkdirs()
                arquivo.copyTo(destino, overwrite = true)
            }
        } finally {
            pastaTemp.deleteRecursively()
        }
    }

    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()

    private fun texto(s: String, size: Float = 15f, color: Int = Color.WHITE) =
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

    private fun botaoVoltar(acao: () -> Unit): View =
        TextView(this).apply {
            text = "‹"
            textSize = 34f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            contentDescription = "Voltar"
            background = GradientDrawable().apply {
                setColor(0xFF173F2D.toInt())
                cornerRadius = dp(14).toFloat()
                setStroke(dp(1), 0xFF279965.toInt())
            }
            elevation = dp(2).toFloat()
            setOnClickListener { acao() }
        }

    private fun apresentacao() {
        val base = tela()
        base.setBackgroundColor(0xFF071810.toInt())
        val imagem = ImageView(this).apply {
            setImageResource(R.drawable.splash_dm_agro)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "DM AGRO - Colheita GPS - Criado por Diego Machado"
        }
        base.addView(imagem, FrameLayout.LayoutParams(-1, -1))
        setContentView(base)
        base.requestApplyInsets()
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            if (!isFinishing && !isDestroyed) inicio()
        }, 2000L)
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
        if (savedInstanceState == null) apresentacao() else inicio()
    }

    private fun tela(): FrameLayout =
        FrameLayout(this).apply {
            setBackgroundColor(0xFF121A16.toInt())
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

    private fun cartao(titulo: String, subtitulo: String, acao: () -> Unit): View {
        val caixa = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            background = GradientDrawable().apply {
                setColor(0xFF223029.toInt())
                cornerRadius = dp(16).toFloat()
            }
            setOnClickListener { acao() }
        }
        caixa.addView(texto(titulo, 19f, Color.WHITE))
        caixa.addView(texto(subtitulo, 13f, 0xFFB6C9BD.toInt()))
        return caixa
    }

    private fun pagina(titulo: String): LinearLayout {
        telaAtual = if (titulo == "Colheita GPS 2.0") "inicio" else "pagina"
        val base = tela()
        val coluna = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(16))
        }
        val topo = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        if (telaAtual != "inicio") topo.addView(
            botaoVoltar { voltarTela() },
            LinearLayout.LayoutParams(dp(46), dp(46))
        )
        topo.addView(texto(titulo, 22f, Color.WHITE).apply {
            setPadding(dp(12), 0, 0, 0)
        })
        topo.addView(TextView(this).apply {
            text = "⋮"; textSize = 26f; setTextColor(Color.WHITE)
            setPadding(dp(12), 0, dp(8), 0)
            setOnClickListener { v -> PopupMenu(this@MainActivity, v).apply {
                menu.add("Início").setOnMenuItemClickListener { inicio(); true }
                menu.add("Mapas").setOnMenuItemClickListener { biblioteca(); true }
                menu.add("Pendências").setOnMenuItemClickListener { pendencias(); true }
                menu.add("Calendário").setOnMenuItemClickListener { calendario(); true }
                menu.add("Caminhões").setOnMenuItemClickListener { caminhoes(); true }
                menu.add("Relatórios de turno").setOnMenuItemClickListener { relatorios(); true }
                if (titulo == "Estoque de cana") {
                    menu.add("Compartilhar estoque").setOnMenuItemClickListener { escolherCompartilhamentoEstoque(); true }
                    
                }
                menu.add("Exportar cópia de segurança").setOnMenuItemClickListener { exportarBackup(); true }
                menu.add("Restaurar cópia de segurança").setOnMenuItemClickListener { solicitarRestauracaoBackup(); true }
                show()
            } }
        })
        coluna.addView(topo)
        val rolagem = ScrollView(this).apply { addView(coluna) }
        base.addView(rolagem, FrameLayout.LayoutParams(-1, -1))
        setContentView(base)
        base.requestApplyInsets()
        return coluna
    }

    private fun inicio() {
        mapa?.salvar()
        mapa = null
        status = null
        val coluna = pagina("Colheita GPS 2.0")
        val itens = listOf(
            Triple("MAPAS", "PDF georreferenciado, GPS e talhões", 0),
            Triple("ORDENS DE SERVIÇO", "Página exclusiva das O.S. por mapa", 1),
            Triple("ESTOQUE DE CANA", "Registrar entradas e saídas por turno", 2),
            Triple("RELATÓRIOS DE TURNO", "Resumo dos turnos A, B e C", 3),
            Triple("PENDÊNCIAS", "Fotos e acompanhamento de manutenção", 4),
            Triple("CAMINHÕES", "Chegada e saída por voz ou digitação", 5),
            Triple("📅 CALENDÁRIO", "Consultar estoques, pendências e relatórios por dia", 6),
        )
        for ((titulo, subtitulo, tipo) in itens) {
            val item = cartao(titulo, subtitulo) {
                when (tipo) {
                    0 -> biblioteca()
                    1 -> paginaOrdens()
                    2 -> estoque()
                    3 -> relatorios()
                    4 -> pendencias()
                    5 -> caminhoes()
                    6 -> calendario()
                    else -> relatoriosTurno()
                }
            }
            coluna.addView(item, LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = dp(16)
            })
        }

    }

    private fun paginaOrdens() {
        val coluna = pagina("Ordens de serviço")
        coluna.addView(texto("Selecione um mapa para consultar ou editar suas O.S.", 14f).apply {
            setPadding(0, dp(16), 0, dp(8))
        })
        val todos = arquivos()
        if (todos.isEmpty()) coluna.addView(texto("Nenhum mapa importado."))
        for (f in todos) {
            val id = f.nameWithoutExtension
            coluna.addView(cartao(nome(id), "Abrir ordens de serviço") { ordens(id) },
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        }
    }

    private fun registros(): JSONArray {
        val texto = getSharedPreferences("controle_colheita", 0).getString("movimentos", "[]") ?: "[]"
        return try { JSONArray(texto) } catch (_: Exception) { JSONArray() }
    }

    private fun listaEstoques(): JSONArray = dados("estoques_salvos")
    private fun salvarEstoqueAtual(obj: JSONObject) {
        val arr = listaEstoques()
        val id = obj.optString("id").ifBlank { System.currentTimeMillis().toString() }
        obj.put("id", id)
        if (obj.optString("dataCadastro").isBlank()) obj.put("dataCadastro", dataHoje())
        var pos = -1
        for (i in 0 until arr.length()) if (arr.optJSONObject(i)?.optString("id") == id) pos = i
        if (pos >= 0) arr.put(pos, obj) else arr.put(obj)
        guardar("estoques_salvos", arr)
        getSharedPreferences("controle_colheita", 0).edit().putString("progresso", obj.toString()).commit()
    }
    private fun abrirEstoqueSalvo(obj: JSONObject) {
        getSharedPreferences("controle_colheita", 0).edit()
            .putString("progresso", obj.toString()).commit()
        val lote = obj.optString("os")
        val detalhe = "Lote: $lote\nTalhões: ${obj.optString("talhoes")}\nÁrea: ${obj.optString("areaRestante")} ha\nProdutividade: ${obj.optString("produtividade")} t/ha"
        AlertDialog.Builder(this).setTitle("Estoque $lote")
            .setView(texto(detalhe, 15f))
            .setItems(arrayOf("Editar estoque", "Compartilhar estoque", "Excluir estoque")) { _, escolha ->
                when (escolha) {
                    0 -> editarProgressoColheita(false)
                    1 -> escolherCompartilhamentoEstoque()
                    2 -> AlertDialog.Builder(this).setTitle("Excluir estoque?")
                        .setPositiveButton("Excluir") { _, _ ->
                            val arr = listaEstoques(); val novo = JSONArray()
                            for (i in 0 until arr.length()) {
                                val item = arr.optJSONObject(i) ?: continue
                                if (item.optString("id") != obj.optString("id")) novo.put(item)
                            }
                            guardar("estoques_salvos", novo)
                            getSharedPreferences("controle_colheita", 0).edit().remove("progresso").commit()
                            estoque()
                        }.setNegativeButton("Cancelar", null).show()
                }
            }.show()
    }
    private fun estoque() {
        val coluna = pagina("Estoque de cana")
        coluna.addView(botao("📅 Abrir calendário") { calendario() })
        coluna.addView(botao("+ Novo estoque") { editarProgressoColheita(true) })
        coluna.addView(texto("ESTOQUES SALVOS", 15f))
        val arr = listaEstoques()
        if (arr.length() == 0) coluna.addView(texto("Nenhum estoque cadastrado ainda.", 14f))
        for (i in arr.length() - 1 downTo 0) {
            val item = arr.optJSONObject(i) ?: continue
            val lote = item.optString("os").ifBlank { "Sem lote" }
            val detalhe = "Talhões: ${item.optString("talhoes")} • Área: ${item.optString("areaRestante")} ha • Produtividade: ${item.optString("produtividade")} t/ha"
            coluna.addView(cartao(lote, detalhe) { abrirEstoqueSalvo(item) },
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        }
    }

    private var mapaEstoqueSelecionado: File? = null
    private fun fotoEstoque(): File? {
        val caminho = progresso().optString("fotoEstoque")
        return if (caminho.isNotBlank()) File(caminho).takeIf { it.exists() } else null
    }
    private fun escolherFotoEstoque() {
        try {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                type = "image/*"
                addCategory(Intent.CATEGORY_OPENABLE)
            }, 306)
        } catch (e: Exception) {
            Toast.makeText(this, "Não foi possível abrir a galeria", Toast.LENGTH_LONG).show()
        }
    }

    private fun mapasDisponiveisEstoque(): List<File> =
        (filesDir.listFiles() ?: emptyArray<File>()).filter {
            it.isFile && it.name.startsWith("mapa_relatorio_") && it.name.endsWith(".png")
        }.sortedBy { it.name }

    private fun escolherCompartilhamentoEstoque() {
        val p = progresso()
        if (p.optString("os").isBlank() || p.optString("talhoes").isBlank() ||
            p.optString("areaRestante").isBlank() || p.optString("produtividade").isBlank()) {
            AlertDialog.Builder(this).setTitle("Complete o estoque")
                .setMessage("Informe lote, talhões, área e produtividade antes de compartilhar.")
                .setPositiveButton("Preencher") { _, _ -> editarProgressoColheita() }
                .setNegativeButton("Cancelar", null).show()
            return
        }
        val foto = fotoEstoque()
        if (foto != null) {
            mapaEstoqueSelecionado = foto
            AlertDialog.Builder(this).setTitle("Compartilhar estoque")
                .setItems(arrayOf("Mensagem com foto (WhatsApp)", "PDF com foto")) { _, op ->
                    if (op == 0) compartilharEstoqueComMapa() else gerarPdfEstoque()
                }.show()
            return
        }
        val mapas = mapasDisponiveisEstoque()
        if (mapas.isEmpty()) {
            AlertDialog.Builder(this).setTitle("Compartilhar estoque")
                .setItems(arrayOf("Mensagem sem mapa", "PDF sem mapa")) { _, op ->
                    mapaEstoqueSelecionado = null
                    if (op == 0) compartilharTextoEstoque() else gerarPdfEstoque()
                }.show()
            return
        }
        val nomes = mapas.map { nomeOriginalMapaEstoque(it) }.toTypedArray()
        AlertDialog.Builder(this).setTitle("Selecione o mapa pintado")
            .setSingleChoiceItems(nomes, -1) { dialogo, indice ->
                mapaEstoqueSelecionado = mapas[indice]
                dialogo.dismiss()
                AlertDialog.Builder(this).setTitle("Compartilhar estoque")
                    .setItems(arrayOf("Mensagem e foto (WhatsApp)", "PDF com mapa")) { _, op ->
                        if (op == 0) compartilharEstoqueComMapa() else gerarPdfEstoque()
                    }.show()
            }.setNegativeButton("Cancelar", null).show()
    }

    private fun progresso(): JSONObject = try {
        JSONObject(getSharedPreferences("controle_colheita", 0).getString("progresso", "{}") ?: "{}")
    } catch (_: Exception) { JSONObject() }

    private fun editarProgressoColheita(novo: Boolean = false) {
        val anterior = progresso()
        val atual = if (novo) JSONObject() else anterior
        val corpo = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(8), dp(18), dp(8))
        }
        val os = campo("Lote (ex.: K/39)", atual.optString("os"))
        val talhoes = campo("Talhões (ex.: 1 a 10, 25, 301)", atual.optString("talhoes"))
        val area = campo("Área (ha)", atual.optString("areaRestante",
            atual.optString("areaTotal")))
        val produtividade = campo("Produtividade (toneladas/ha)", atual.optString("produtividade"))
        for (v in listOf(os, talhoes, area, produtividade)) corpo.addView(v)
        corpo.addView(botao("Seleção rápida de talhões (1 a 500)") {
            val entrada = campo("Ex.: 1-10, 15, 28-35, 500", talhoes.text.toString())
            AlertDialog.Builder(this).setTitle("Selecionar talhões")
                .setMessage("Digite números de 1 a 500 separados por vírgulas. Use hífen para intervalos.")
                .setView(entrada)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Aplicar") { _, _ ->
                    val selecionados = sortedSetOf<Int>()
                    var valido = true
                    for (parte in entrada.text.toString().split(",")) {
                        val pedaco = parte.trim()
                        if (pedaco.isEmpty()) continue
                        val faixa = pedaco.replace(Regex("\\s+[aA]\\s+"), "-")
                            .split("-").map { it.trim().toIntOrNull() }
                        if (faixa.size !in 1..2 || faixa.any { it == null }) {
                            valido = false; break
                        }
                        val primeiro = faixa.first()!!
                        val ultimo = faixa.last()!!
                        if (primeiro !in 1..500 || ultimo !in 1..500 || ultimo < primeiro) {
                            valido = false; break
                        }
                        for (n in primeiro..ultimo) selecionados.add(n)
                    }
                    if (valido && selecionados.isNotEmpty()) {
                        talhoes.setText(selecionados.joinToString(", "))
                    } else {
                        Toast.makeText(this, "Use talhões de 1 a 500. Ex.: 1-10, 25, 301", Toast.LENGTH_LONG).show()
                    }
                }.show()
        })
        corpo.addView(botao("Adicionar foto / mapa pintado") {
            val rascunho = JSONObject().put("os", os.text.toString())
                .put("talhoes", talhoes.text.toString())
                .put("areaRestante", area.text.toString())
                .put("produtividade", produtividade.text.toString())
            atual.optString("id").takeIf { it.isNotBlank() }?.let { rascunho.put("id", it) }
            atual.optString("fotoEstoque").takeIf { it.isNotBlank() }?.let { rascunho.put("fotoEstoque", it) }
            salvarEstoqueAtual(rascunho)
            atual.put("id", progresso().optString("id"))
            val mapas = mapasDisponiveisEstoque()
            val escolhas = if (mapas.isEmpty()) arrayOf("Escolher da galeria")
                else arrayOf("Escolher da galeria", "Usar mapa pintado salvo")
            AlertDialog.Builder(this).setTitle("Adicionar foto")
                .setItems(escolhas) { _, opcao ->
                    if (opcao == 0) escolherFotoEstoque()
                    else {
                        val nomes = mapas.map { nomeOriginalMapaEstoque(it) }.toTypedArray()
                        AlertDialog.Builder(this).setTitle("Mapa pintado salvo")
                            .setItems(nomes) { _, indice ->
                                val obj = progresso().put("fotoEstoque", mapas[indice].absolutePath)
                                salvarEstoqueAtual(obj)
                                estoque()
                            }.setNegativeButton("Cancelar", null).show()
                    }
                }.show()
        })
        fotoEstoque()?.let { corpo.addView(texto("Foto atual: ${it.name}", 14f)) }
        val rolagem = ScrollView(this).apply { addView(corpo) }
        AlertDialog.Builder(this).setTitle(if (novo) "Novo estoque" else "Editar estoque").setView(rolagem)
            .setNegativeButton("Cancelar", null).setPositiveButton("Salvar") { _, _ ->
                // Campos são texto livre. Os exemplos não são regras de validação.
                val obj = JSONObject().put("os", os.text.toString())
                    .put("talhoes", talhoes.text.toString())
                    .put("areaRestante", area.text.toString())
                    .put("produtividade", produtividade.text.toString())
                atual.optString("id").takeIf { it.isNotBlank() }?.let { obj.put("id", it) }
                atual.optString("fotoEstoque").takeIf { it.isNotBlank() }?.let { obj.put("fotoEstoque", it) }
                salvarEstoqueAtual(obj)
                estoque()
                AlertDialog.Builder(this).setTitle("Estoque salvo")
                    .setMessage("Deseja compartilhar este estoque agora?")
                    .setPositiveButton("Compartilhar") { _, _ -> escolherCompartilhamentoEstoque() }
                    .setNegativeButton("Agora não", null).show()
            }.show()
    }

    private fun historicoEstoque() {
        val coluna = pagina("Histórico do estoque")
        val arr = dados("historico_estoque")
        if (arr.length() == 0) coluna.addView(texto("Nenhum registro salvo ainda."))
        for (i in arr.length() - 1 downTo 0) {
            val o = arr.optJSONObject(i) ?: continue
            val resumo = "${o.optString("data")} • O.S. ${o.optString("os")}"
            val detalhe = "Talhões: ${o.optString("talhoes")} • Restante: ${o.optString("areaRestante")} ha • Produtividade: ${o.optString("produtividade")} t/ha"
            coluna.addView(cartao(resumo, detalhe) {},
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        }
    }

    private fun arquivoMapaAtual(): File? {
        val id = getSharedPreferences("controle_colheita", 0).getString("ultimo_mapa", "") ?: ""
        if (id.isBlank() && mapaEstoqueSelecionado == null && fotoEstoque() == null) return null
        return (mapaEstoqueSelecionado ?: fotoEstoque()
            ?: File(filesDir, "mapa_relatorio_$id.png")).takeIf { it.exists() }
    }

    private fun textoEstoque(): String {
        val p = progresso()
        val br = Locale("pt", "BR")
        fun numero(texto: String): Double? = Regex("""\d+(?:[.,]\d+)?""")
            .find(texto)?.value?.replace(',', '.')?.toDoubleOrNull()
        val restante = numero(p.optString("areaRestante", p.optString("areaTotal")))
        val produt = numero(p.optString("produtividade"))
        val sb = StringBuilder("ESTOQUE DE CANA - COLHEITA GPS\n")
        sb.append("Data: ").append(java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", br)
            .format(java.util.Date())).append("\n")
        sb.append("Lote: ").append(p.optString("os").ifBlank { "Não informada" }).append("\n")
        sb.append("Talhões: ").append(p.optString("talhoes").ifBlank { "Não informados" }).append("\n")
        sb.append("Área: ")
            .append(restante?.let { "%.2f ha".format(br, it) } ?: "Não informada").append("\n")
        sb.append("Produtividade: ")
            .append(produt?.let { "%.2f t/ha".format(br, it) } ?: "Não informada").append("\n")
        // Estoque não inclui movimentações de cana: somente o que falta colher.

        return sb.toString()
    }

    private fun compartilharTextoEstoque() {
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"; putExtra(Intent.EXTRA_TEXT, textoEstoque())
        }, "Compartilhar estoque"))
    }


    private fun nomeOriginalMapaEstoque(arquivo: File?): String {
        val selecionado = arquivo?.name?.takeIf {
            it.startsWith("mapa_relatorio_") && it.endsWith(".png")
        }?.removePrefix("mapa_relatorio_")?.removeSuffix(".png")
        val id = selecionado ?: getSharedPreferences("controle_colheita", 0)
            .getString("ultimo_mapa", "").orEmpty()
        return if (id.isNotBlank() && arquivo?.name?.startsWith("mapa_relatorio_") == true) {
            nome(id).substringBeforeLast('.', nome(id))
        } else if (arquivo?.name == "foto_estoque_atual.jpg") {
            getSharedPreferences("controle_colheita", 0)
                .getString("nome_foto_estoque", "Foto do estoque").orEmpty()
                .substringBeforeLast('.', "Foto do estoque")
        } else arquivo?.nameWithoutExtension ?: "Mapa"
    }

    private fun compartilharEstoqueComMapa() {
        val imagem = arquivoMapaAtual()
        if (imagem == null) {
            compartilharTextoEstoque()
            Toast.makeText(this, "Mapa pintado indisponível; enviando apenas texto", Toast.LENGTH_LONG).show()
            return
        }
        try {
            val original = nomeOriginalMapaEstoque(imagem)
            val extensao = if (imagem.extension.equals("png", true)) ".png" else ".jpg"
            val nome = original.replace(Regex("[^\\p{L}\\p{N} ._-]"), "_") + extensao
            val valores = android.content.ContentValues().apply {
                put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, nome)
                put(android.provider.MediaStore.Images.Media.MIME_TYPE,
                    if (extensao == ".png") "image/png" else "image/jpeg")
                if (Build.VERSION.SDK_INT >= 29) {
                    put(android.provider.MediaStore.Images.Media.RELATIVE_PATH,
                        android.os.Environment.DIRECTORY_PICTURES + "/ColheitaGPS")
                }
            }
            if (Build.VERSION.SDK_INT < 29) {
                Toast.makeText(this, "Use o PDF para compartilhar mapa neste Android", Toast.LENGTH_LONG).show()
                gerarPdfEstoque()
                return
            }
            val uri = contentResolver.insert(
                android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, valores
            ) ?: throw IOException("Não foi possível criar a imagem")
            contentResolver.openOutputStream(uri)?.use { saida ->
                imagem.inputStream().use { it.copyTo(saida) }
            } ?: throw IOException("Não foi possível copiar o mapa")
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = if (extensao == ".png") "image/png" else "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, textoEstoque())
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = ClipData.newUri(contentResolver, "Mapa pintado", uri)
            }, "Compartilhar estoque com mapa"))
        } catch (e: Exception) {
            Toast.makeText(this, "Falha ao compartilhar mapa: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun gerarPdfEstoque() {
        try {
            val doc = android.graphics.pdf.PdfDocument()
            val linhas = textoEstoque().lines()
            val paint = Paint(3).apply { color = Color.BLACK; textSize = 11f }
            var pagina = 0
            for (bloco in linhas.chunked(45)) {
                pagina++
                val pg = doc.startPage(android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, pagina).create())
                var y = 35f
                for (linha in bloco) { pg.canvas.drawText(linha.take(95), 28f, y, paint); y += 16f }
                doc.finishPage(pg)
            }
            arquivoMapaAtual()?.let { arquivo ->
                val bitmap = BitmapFactory.decodeFile(arquivo.absolutePath)
                if (bitmap != null) {
                    pagina++
                    val pg = doc.startPage(android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, pagina).create())
                    pg.canvas.drawText("MAPA: " + nomeOriginalMapaEstoque(arquivo).take(70), 28f, 35f, paint)
                    val escala = minOf(535f / bitmap.width, 750f / bitmap.height)
                    val destino = RectF(30f, 50f, 30f + bitmap.width * escala, 50f + bitmap.height * escala)
                    pg.canvas.drawBitmap(bitmap, null, destino, Paint(3))
                    doc.finishPage(pg)
                    bitmap.recycle()
                }
            }
            val arquivo = File(cacheDir, "estoque_${System.currentTimeMillis()}.pdf")
            arquivo.outputStream().use { doc.writeTo(it) }; doc.close()
            pdfPendente = arquivo
            startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE); type = "application/pdf"
                putExtra(Intent.EXTRA_TITLE, "Relatorio_Estoque.pdf")
            }, 303)
        } catch (e: Exception) { Toast.makeText(this, "Erro no PDF: ${e.message}", Toast.LENGTH_LONG).show() }
    }

    private fun salvarImagemMapa() {
        val arquivo = arquivoMapaAtual()
        if (arquivo == null) { Toast.makeText(this, "Abra e pinte um mapa primeiro", Toast.LENGTH_LONG).show(); return }
        imagemPendente = arquivo
        startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); type = "image/png"
            putExtra(Intent.EXTRA_TITLE, nomeOriginalMapaEstoque(arquivo) + ".png")
        }, 304)
    }

    private fun novaMovimentacao() {
        val corpo = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(8))
        }
        val tipo = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item,
                arrayOf("Entrada", "Saída"))
        }
        val turno = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item,
                arrayOf("A", "B", "C"))
        }
        val quantidade = EditText(this).apply {
            hint = "Quantidade em toneladas"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val os = EditText(this).apply { hint = "Número da O.S. (opcional)" }
        val obs = EditText(this).apply { hint = "Talhão / observação" }
        corpo.addView(TextView(this).apply { text = "Tipo de movimentação" })
        corpo.addView(tipo)
        corpo.addView(TextView(this).apply { text = "Turno" })
        corpo.addView(turno)
        corpo.addView(quantidade)
        corpo.addView(os)
        corpo.addView(obs)
        AlertDialog.Builder(this).setTitle("Nova movimentação").setView(corpo)
            .setPositiveButton("Salvar") { _, _ ->
                val valor = quantidade.text.toString().replace(',', '.').toDoubleOrNull()
                if (valor == null || valor <= 0) {
                    Toast.makeText(this, "Informe uma quantidade válida", Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }
                val objeto = JSONObject().apply {
                    put("tipo", tipo.selectedItem.toString())
                    put("turno", turno.selectedItem.toString())
                    put("quantidade", valor)
                    put("os", os.text.toString())
                    put("observacao", obs.text.toString())
                    put("data", java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR")).format(java.util.Date()))
                }
                val dados = registros()
                dados.put(objeto)
                getSharedPreferences("controle_colheita", 0).edit()
                    .putString("movimentos", dados.toString()).apply()
                estoque()
            }.setNegativeButton("Cancelar", null).show()
    }

    private fun relatorios() {
        val coluna = pagina("Relatório diário de turno")
        coluna.addView(texto("Registre o que aconteceu durante o turno, por escrita ou voz.", 14f))
        for (turno in listOf("A", "B", "C")) {
            coluna.addView(cartao("Turno $turno", "Relato, serviços e pendências") { detalheTurno(turno) },
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        }
    }

    private fun textoRelatorio(turno: String): String {
        val sb = StringBuilder("COLHEITA GPS - RELATÓRIO DO TURNO $turno\n")
        sb.append("Data: ").append(java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR")).format(java.util.Date())).append("\n")
        val ocorr = dados("relatorios_turno")
        for (i in 0 until ocorr.length()) {
            val o = ocorr.optJSONObject(i) ?: continue
            if (o.optString("turno").trim().uppercase() != turno) continue
            sb.append("\nO.S.: ").append(o.optString("os")).append("\n")
            sb.append("Relato: ").append(o.optString("descricao")).append("\n")
            sb.append("Pendências: ").append(o.optString("pendencias")).append("\n")
        }
        return sb.toString()
    }

    private fun detalheTurno(turno: String) {
        val coluna = pagina("Relatório do turno $turno")
        coluna.addView(texto(textoRelatorio(turno), 14f).apply { setPadding(0, dp(15), 0, dp(15)) })
        coluna.addView(botao("+ Registrar ocorrência") { editarRegistro("relatorios_turno", -1, false, turno) })
        coluna.addView(botao("Compartilhar relatório") {
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, textoRelatorio(turno))
            }, "Compartilhar turno $turno"))
        })
        coluna.addView(botao("Gerar PDF") { gerarPdfTurno(turno) })
    }

    private fun gerarPdfTurno(turno: String) {
        try {
            val documento = android.graphics.pdf.PdfDocument()
            val linhas = textoRelatorio(turno).lines()
            val pincel = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 11f }
            val alturaUtil = 780
            val linhasPorPagina = alturaUtil / 16
            var paginaNumero = 0
            for (bloco in linhas.chunked(linhasPorPagina)) {
                paginaNumero++
                val info = android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, paginaNumero).create()
                val pagina = documento.startPage(info)
                val canvas = pagina.canvas
                var y = 40f
                for (linha in bloco) {
                    canvas.drawText(linha.take(95), 32f, y, pincel)
                    y += 16f
                }
                documento.finishPage(pagina)
            }
            if (paginaNumero == 0) {
                val pagina = documento.startPage(android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, 1).create())
                documento.finishPage(pagina)
            }
            val arquivo = File(cacheDir, "relatorio_turno_${turno}_${System.currentTimeMillis()}.pdf")
            arquivo.outputStream().use { documento.writeTo(it) }
            documento.close()
            // Salvar PDF pelo seletor de documentos do Android.
            pdfPendente = arquivo
            startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/pdf"
                putExtra(Intent.EXTRA_TITLE, "Relatorio_Turno_${turno}.pdf")
            }, 303)
        } catch (e: Exception) {
            Toast.makeText(this, "Erro ao gerar PDF: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun dataHoje(): String =
        java.text.SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")).format(java.util.Date())

    private fun dataRegistro(obj: JSONObject): String {
        val cadastrada = obj.optString("dataCadastro")
        if (cadastrada.isNotBlank()) return cadastrada.take(10)
        // Compatibilidade com registros anteriores: usar a data do identificador,
        // quando ele representa o instante de criação em milissegundos.
        val timestamp = obj.optString("id").toLongOrNull()
        return if (timestamp != null && timestamp > 946684800000L && timestamp < 4102444800000L)
            java.text.SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")).format(java.util.Date(timestamp))
        else obj.optString("data").take(10).ifBlank { "Sem data" }
    }

    private fun calendario(dataInicial: String = dataHoje()) {
        val coluna = pagina("Calendário")
        coluna.addView(botao("📅 Escolher data: $dataInicial") {
            val partes = dataInicial.split("/")
            val hoje = java.util.Calendar.getInstance()
            val dia = partes.getOrNull(0)?.toIntOrNull() ?: hoje.get(java.util.Calendar.DAY_OF_MONTH)
            val mes = partes.getOrNull(1)?.toIntOrNull()?.minus(1) ?: hoje.get(java.util.Calendar.MONTH)
            val ano = partes.getOrNull(2)?.toIntOrNull() ?: hoje.get(java.util.Calendar.YEAR)
            android.app.DatePickerDialog(this, { _, a, m, d ->
                calendario(String.format(Locale("pt", "BR"), "%02d/%02d/%04d", d, m + 1, a))
            }, ano, mes, dia).show()
        })
        coluna.addView(texto("Registros de $dataInicial", 18f, Color.WHITE))
        val estoques = listaEstoques()
        var encontrados = 0
        coluna.addView(texto("ESTOQUES", 16f, 0xFF80DDAA.toInt()))
        for (i in estoques.length() - 1 downTo 0) {
            val item = estoques.optJSONObject(i) ?: continue
            if (dataRegistro(item) != dataInicial) continue
            encontrados++
            val lote = item.optString("os").ifBlank { "Sem lote" }
            coluna.addView(cartao(lote, "Talhões: ${item.optString("talhoes")} • Área: ${item.optString("areaRestante")} ha") {
                abrirEstoqueSalvo(item)
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        }
        coluna.addView(texto("PENDÊNCIAS", 16f, 0xFF80DDAA.toInt()))
        val pend = dados("pendencias")
        for (i in pend.length() - 1 downTo 0) {
            val item = pend.optJSONObject(i) ?: continue
            if (dataRegistro(item) != dataInicial) continue
            encontrados++
            val idx = i
            coluna.addView(cartao(item.optString("equipamento", "Pendência"), item.optString("descricao")) {
                AlertDialog.Builder(this).setTitle("Pendência")
                    .setMessage(item.optString("descricao"))
                    .setPositiveButton("Editar") { _, _ -> editarRegistro("pendencias", idx, true) }
                    .setNegativeButton("Fechar", null).show()
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        }
        coluna.addView(texto("RELATÓRIOS DE TURNO", 16f, 0xFF80DDAA.toInt()))
        val rel = dados("relatorios_turno")
        for (i in rel.length() - 1 downTo 0) {
            val item = rel.optJSONObject(i) ?: continue
            if (dataRegistro(item) != dataInicial) continue
            encontrados++
            coluna.addView(cartao("Turno ${item.optString("turno")}", item.optString("descricao")) {
                relatorios()
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        }
        if (encontrados == 0) coluna.addView(texto("Nenhum registro nesta data.", 14f))
    }

    private fun pendencias() = listarRegistros("pendencias", "Pendências de manutenção", true)
    private fun relatoriosTurno() = relatorios()
    private fun listarRegistros(key: String, titulo: String, foto: Boolean) {
        val coluna = pagina(titulo)
        coluna.addView(botao("+ Novo registro") { editarRegistro(key, -1, foto) })
        val arr = dados(key)
        for (i in arr.length() - 1 downTo 0) {
            val obj = arr.optJSONObject(i) ?: continue
            val idx = i
            val tituloItem = obj.optString("equipamento", obj.optString("turno", "Registro"))
            coluna.addView(cartao(tituloItem + (if (foto && obj.optBoolean("concluida", false)) " • Concluída" else ""), obj.optString("descricao")) {
                val opcoes = if (foto) arrayOf("Editar", "Adicionar foto", "Ver fotos", "Concluir / reabrir", "Excluir")
                              else arrayOf("Editar", "Excluir")
                AlertDialog.Builder(this).setTitle(tituloItem).setItems(opcoes) { _, escolha ->
                    when {
                        escolha == 0 -> editarRegistro(key, idx, foto)
                        foto && escolha == 1 -> {
                            fotoPendente = obj.optString("id")
                            startActivityForResult(Intent(Intent.ACTION_GET_CONTENT).apply { type = "image/*" }, 301)
                        }
                        foto && escolha == 2 -> {
                            val fotos = obj.optJSONArray("fotos")
                            val caminhos = if (fotos != null && fotos.length() > 0) (0 until fotos.length()).map { fotos.optString(it) }
                                else listOf(obj.optString("foto"))
                            val existentes = caminhos.filter { it.isNotBlank() && File(it).exists() }
                            if (existentes.isEmpty()) Toast.makeText(this, "Sem foto", Toast.LENGTH_SHORT).show()
                            else AlertDialog.Builder(this).setTitle("Fotos da pendência")
                                .setItems(existentes.mapIndexed { i, _ -> "Foto ${i + 1}" }.toTypedArray()) { _, fotoIdx ->
                                    AlertDialog.Builder(this).setView(ImageView(this).apply {
                                        setImageBitmap(BitmapFactory.decodeFile(existentes[fotoIdx])); adjustViewBounds = true
                                    }).setPositiveButton("Fechar", null).show()
                                }.show()
                        }
                        foto && escolha == 3 -> {
                            obj.put("concluida", !obj.optBoolean("concluida", false))
                            guardar(key, arr)
                            pendencias()
                        }
                        else -> remover(key, idx) { listarRegistros(key, titulo, foto) }
                    }
                }.show()
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        }
    }
    private fun editarRegistro(key: String, idx: Int, foto: Boolean, turnoInicial: String = "A") {
        val atual = if (idx >= 0) dados(key).optJSONObject(idx) else null
        val corpo = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(8), dp(18), dp(8)) }
        val equipamento = campo(if (foto) "Equipamento / O.S." else "Turno (A, B ou C)", (if (foto) atual?.optString("equipamento") else atual?.optString("turno") ?: turnoInicial) ?: "")
        val descricao = campo(if (foto) "Defeito e localização" else "Ocorrências, serviços e pendências", atual?.optString("descricao") ?: "")
        corpo.addView(equipamento); corpo.addView(descricao)
        val osRelatorio = campo("O.S. / lote (opcional)", atual?.optString("os") ?: "")
        val pendenciasRelatorio = campo("Pendências para o próximo turno", atual?.optString("pendencias") ?: "")
        if (!foto) { corpo.addView(osRelatorio); corpo.addView(pendenciasRelatorio) }
        if (foto) {
            fotosFormulario = mutableListOf<String>().apply {
                atual?.optJSONArray("fotos")?.let { arr -> for (i in 0 until arr.length()) add(arr.optString(i)) }
                if (isEmpty()) atual?.optString("foto")?.takeIf { it.isNotBlank() }?.let { add(it) }
            }
            val resumo = texto("Fotos anexadas: ${fotosFormulario.size}", 14f)
            corpo.addView(resumo)
            atualizarFotosFormulario = { resumo.text = "Fotos anexadas: ${fotosFormulario.size}" }
            corpo.addView(botao("📷 Tirar foto") {
                startActivityForResult(Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE), 308)
            })
            corpo.addView(botao("🖼 Adicionar fotos da galeria") {
                startActivityForResult(Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = "image/*"; putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                }, 307)
            })
            corpo.addView(botao("Remover foto selecionada") {
                if (fotosFormulario.isNotEmpty()) {
                    AlertDialog.Builder(this).setTitle("Remover foto")
                        .setItems(fotosFormulario.mapIndexed { i, _ -> "Foto ${i + 1}" }.toTypedArray()) { _, pos ->
                            fotosFormulario.removeAt(pos); atualizarFotosFormulario?.invoke()
                        }.show()
                }
            })
        }
        if (!foto) corpo.addView(botao("🎤 Ditar ocorrência") { ouvir { fala ->
            descricao.setText((descricao.text.toString() + " " + fala).trim())
            Toast.makeText(this, "Confira o texto e salve o registro", Toast.LENGTH_SHORT).show()
        } })
        AlertDialog.Builder(this).setTitle(if (idx < 0) "Novo registro" else "Editar registro")
            .setView(corpo).setNegativeButton("Cancelar", null).setPositiveButton("Salvar") { _, _ ->
                val arr = dados(key)
                val obj = atual ?: JSONObject().apply { put("id", System.currentTimeMillis().toString()) }
                obj.put(if (foto) "equipamento" else "turno", equipamento.text.toString())
                obj.put("descricao", descricao.text.toString())
                if (!foto) {
                    obj.put("os", osRelatorio.text.toString())
                    obj.put("pendencias", pendenciasRelatorio.text.toString())
                }
                if (foto) {
                    val listaFotos = JSONArray()
                    fotosFormulario.filter { File(it).exists() }.forEach { listaFotos.put(it) }
                    obj.put("fotos", listaFotos)
                    obj.put("foto", if (listaFotos.length() > 0) listaFotos.optString(0) else "")
                }
                if (obj.optString("dataCadastro").isBlank()) obj.put("dataCadastro", dataHoje())
                if (idx < 0) arr.put(obj)
                guardar(key, arr)
                if (foto) {
                    atualizarFotosFormulario = null
                    pendencias()
                } else relatorios()
            }.show()
    }
    private fun caminhoes() {
        val coluna = pagina("Controle de caminhões")
        coluna.addView(botao("+ Registrar caminhão") { editarCaminhao(-1) })
        val arr = dados("caminhoes")
        for (i in arr.length() - 1 downTo 0) {
            val idx = i
            val o = arr.optJSONObject(i) ?: continue
            coluna.addView(cartao("Caminhão ${o.optString("numero")}", "Turno ${o.optString("turno")} • Chegada ${o.optString("chegada")} • Saída ${o.optString("saida", "Pendente")}") {
                AlertDialog.Builder(this).setItems(arrayOf("Editar chegada/saída", "Excluir")) { _, pos ->
                    if (pos == 0) editarCaminhao(idx) else remover("caminhoes", idx) { caminhoes() }
                }.show()
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        }
    }
    private fun editarCaminhao(idx: Int) {
        val atual = if (idx >= 0) dados("caminhoes").optJSONObject(idx) else null
        val corpo = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(8), dp(18), dp(8)) }
        val numero = campo("Número do caminhão", atual?.optString("numero") ?: "")
        val chegada = campo("Chegada (HH:mm)", atual?.optString("chegada") ?: "")
        val saida = campo("Saída (HH:mm), opcional", atual?.optString("saida") ?: "")
        val turno = Spinner(this).apply { adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, arrayOf("A", "B", "C")) }
        if (atual != null) turno.setSelection(listOf("A", "B", "C").indexOf(atual.optString("turno")).coerceAtLeast(0))
        corpo.addView(numero); corpo.addView(chegada); corpo.addView(saida); corpo.addView(turno)
        corpo.addView(botao("🎤 Ditar informações") { ouvir { fala ->
            val numeroFalado = Regex("(?:caminhão|caminhao)\\s*(?:número\\s*)?(\\d+)", RegexOption.IGNORE_CASE)
                .find(fala)?.groupValues?.get(1)
            if (numeroFalado != null) numero.setText(numeroFalado)
            val hora = Regex("\\b([01]?\\d|2[0-3])[:h]([0-5]\\d)\\b").find(fala)?.let {
                it.groupValues[1].padStart(2, '0') + ":" + it.groupValues[2]
            } ?: Regex("\\b([01]?\\d|2[0-3])\\s*horas?\\b", RegexOption.IGNORE_CASE).find(fala)?.let {
                it.groupValues[1].padStart(2, '0') + ":00"
            }
            val saidaFalou = Regex("sa[ií]da|saiu|partiu", RegexOption.IGNORE_CASE).containsMatchIn(fala)
            if (hora != null) {
                if (saidaFalou) saida.setText(hora) else chegada.setText(hora)
            }
            Toast.makeText(this, "Confira os campos e toque em Salvar", Toast.LENGTH_LONG).show()
        } })
        AlertDialog.Builder(this).setTitle("Caminhão").setView(corpo)
            .setNegativeButton("Cancelar", null).setPositiveButton("Salvar") { _, _ ->
                if (numero.text.isBlank()) { Toast.makeText(this, "Informe o caminhão", Toast.LENGTH_SHORT).show(); return@setPositiveButton }
                val arr = dados("caminhoes")
                val obj = atual ?: JSONObject()
                obj.put("numero", numero.text.toString()); obj.put("chegada", chegada.text.toString())
                obj.put("saida", saida.text.toString()); obj.put("turno", turno.selectedItem.toString())
                if (idx < 0) arr.put(obj)
                guardar("caminhoes", arr); caminhoes()
            }.show()
    }

    private fun biblioteca() {
        telaAtual = "biblioteca"
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
        cab.addView(texto("COLHEITA GPS 2.0", 23f, Color.WHITE))
        cab.addView(botao("←") { inicio() })
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
                    setColor(0xFF223029.toInt())
                    cornerRadius = dp(14).toFloat()
                }
                elevation = dp(2).toFloat()
            }

            item.addView(
                texto("PDF", 17f, verde),
                LinearLayout.LayoutParams(dp(48), dp(58))
            )

            val info = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
            }
            info.addView(texto(nome(id), 16f).apply { maxLines = 2 })
            info.addView(texto("Mapa PDF • Abrir", 12f, 0xFFB6C9BD.toInt()))

            item.addView(info, LinearLayout.LayoutParams(0, -2, 1f))

            item.addView(
                texto("Opções", 12f).apply {
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

    private fun validarTalhoes(entrada: String): String? {
        val escolhidos = sortedSetOf<Int>()
        if (entrada.isBlank()) return null
        for (parte in entrada.split(",")) {
            val limites = parte.trim().split("-").map { it.trim().toIntOrNull() }
            if (limites.size !in 1..2 || limites.any { it == null }) return null
            val primeiro = limites.first()!!
            val ultimo = limites.last()!!
            if (primeiro !in 1..500 || ultimo !in 1..500 || ultimo < primeiro) return null
            for (n in primeiro..ultimo) escolhidos.add(n)
        }
        return if (escolhidos.isEmpty()) null else escolhidos.joinToString(", ")
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
        val prefTalhoes = getSharedPreferences("talhoes_por_os", 0)
        val itens = (
            dados.map { "O.S. $it • Talhões: ${prefTalhoes.getString(id + "_" + it, "Não informados")}" } +
                "Adicionar número"
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

                val corpoOs = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(18), dp(8), dp(18), dp(8))
                }
                val talhoesOs = EditText(this).apply {
                    hint = "Talhões (ex.: 1-10, 25, 301)"
                    setText(if (novo) "" else prefTalhoes.getString(id + "_" + dados[pos], "") ?: "")
                }
                corpoOs.addView(campo)
                corpoOs.addView(talhoesOs)
                val dialogo = AlertDialog.Builder(this)
                    .setTitle(if (novo) "Nova O.S." else "Editar O.S.")
                    .setView(corpoOs)
                    .setPositiveButton("Salvar") { _, _ ->
                        val numero = campo.text.toString().trim()
                        val selecao = validarTalhoes(talhoesOs.text.toString())
                        if (numero.isNotEmpty() && selecao != null) {
                            if (!novo) prefTalhoes.edit().remove(id + "_" + dados[pos]).apply()
                            prefTalhoes.edit().putString(id + "_" + numero, selecao).apply()
                            if (novo) {
                                dados.add(numero)
                            } else {
                                dados[pos] = numero
                            }
                            guardar(dados)
                            ordens(id)
                        } else Toast.makeText(this, "Informe O.S. e talhões válidos de 1 a 500", Toast.LENGTH_LONG).show()
                    }
                    .setNegativeButton("Cancelar") { _, _ ->
                        ordens(id)
                    }

                if (!novo) {
                    dialogo.setNeutralButton("Excluir") { _, _ ->
                        prefTalhoes.edit().remove(id + "_" + dados[pos]).apply()
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
        tratarResultadoVozEFoto(requestCode, resultCode, data)
        if (requestCode == 306) {
            if (resultCode == RESULT_OK) {
                try {
                    val origem = data?.data ?: throw IOException("Backup não selecionado")
                    restaurarBackupZip(origem)
                    Toast.makeText(this, "Backup restaurado", Toast.LENGTH_LONG).show()
                    inicio()
                } catch (e: Exception) {
                    Toast.makeText(this, "Erro ao restaurar: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
            return
        }
        if (requestCode == 305) {
            if (resultCode == RESULT_OK) {
                try {
                    val destino = data?.data ?: throw IOException("Destino não selecionado")
                    escreverBackup(destino)
                    Toast.makeText(this, "Backup exportado", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Toast.makeText(this, "Falha no backup: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
            return
        }
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
            getSharedPreferences("controle_colheita", 0).edit().putString("ultimo_mapa", id).apply()
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

    private var atualizarControlesMapa: (() -> Unit)? = null

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
        visual.aoEditarArea = { ferramenta(7, manterPontos = true) }

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
            botaoVoltar { biblioteca() },
            LinearLayout.LayoutParams(dp(46), dp(46)).apply {
                rightMargin = dp(8)
            }
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
            texto("GPS", 15f, Color.WHITE).apply {
                gravity = Gravity.CENTER
                setOnClickListener {
                    if (local == null) {
                        iniciarGps()
                    } else {
                        visual.centrarGps()
                    }
                }
            },
            LinearLayout.LayoutParams(dp(62), dp(52))
        )

        val resumoArea = texto("Destaque: —    •    Total: —", 14f, Color.WHITE).apply {
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setBackgroundColor(0xEE173A2A.toInt())
            gravity = Gravity.CENTER_VERTICAL
        }
        val atualizarArea = {
            val destaque = visual.hectaresEmDestaque()?.let {
                String.format(Locale("pt", "BR"), "%.2f ha", it)
            } ?: "—"
            val total = visual.hectaresPintados()?.let {
                String.format(Locale("pt", "BR"), "%.2f ha", it)
            } ?: "—"
            resumoArea.text = "Destaque: $destaque    •    Total: $total"
        }
        visual.aoAtualizarArea = atualizarArea
        // Recalcular após a criação da tela e após restaurar as pinturas.
        resumoArea.post { atualizarArea() }
        base.addView(resumoArea, FrameLayout.LayoutParams(-1, dp(40), Gravity.TOP).apply {
            topMargin = dp(69)
        })
        atualizarArea()

        val controles = LinearLayout(this).apply {
            tag = "controlesPintura"
            gravity = Gravity.CENTER
            setPadding(dp(5), dp(5), dp(5), dp(5))
            setBackgroundColor(0xE8182B21.toInt())
            visibility = View.GONE
        }
        fun controle(nome: String, acao: () -> Unit) {
            controles.addView(botao(nome, acao), LinearLayout.LayoutParams(0, dp(48), 1f).apply {
                marginStart = dp(3); marginEnd = dp(3)
            })
        }
        controle("+ Ponto") { visual.adicionarPontoCentral() }
        controle("Desfazer") { visual.desfazer() }
        controle("Concluir") {
            if (visual.modo == 7) {
                if (visual.fecharArea()) escolherCor()
            } else visual.concluirMedida()
        }
        controle("Sair") { visual.cancelarFerramenta() }
        base.addView(controles, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply {
            bottomMargin = dp(94)
        })
        atualizarControlesMapa = {
            controles.visibility = if (visual.modo in 7..9) View.VISIBLE else View.GONE
        }
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
        val v = mapa ?: return
        val ativo = v.modo in 7..9
        val opcoes = if (ativo) arrayOf(
            "Adicionar ponto", "Desfazer ponto", "Concluir",
            "Cancelar ferramenta", "Pintar talhão", "Medir área",
            "Medir distância", "Pincel", "Borracha", "O.S. de trabalho", "GPS", "Salvar mapa pintado"
        ) else arrayOf(
            "Pintar talhão", "Medir área", "Medir distância", "Pincel",
            "Borracha", "Desfazer", "Excluir última pintura",
            "O.S. de trabalho", "GPS", "Salvar mapa pintado", "Voltar aos mapas"
        )
        AlertDialog.Builder(this).setTitle("Ferramentas")
            .setItems(opcoes) { _, i ->
                if (ativo) when (i) {
                    0 -> v.adicionarPontoCentral()
                    1 -> v.desfazer()
                    2 -> if (v.modo == 7) {
                        if (v.fecharArea()) escolherCor()
                    } else v.concluirMedida()
                    3 -> { v.cancelarFerramenta(); atualizarControlesMapa?.invoke() }
                    4 -> ferramenta(7)
                    5 -> ferramenta(8)
                    6 -> ferramenta(9)
                    7 -> ferramenta(1)
                    8 -> ferramenta(2)
                    9 -> ordens(chaveAtual)
                    10 -> iniciarGps()
                    11 -> { v.salvar(); salvarImagemMapa() }
                } else when (i) {
                    0 -> ferramenta(7)
                    1 -> ferramenta(8)
                    2 -> ferramenta(9)
                    3 -> ferramenta(1)
                    4 -> ferramenta(2)
                    5 -> v.desfazer()
                    6 -> AlertDialog.Builder(this).setTitle("Excluir última pintura?")
                        .setPositiveButton("Excluir") { _, _ -> v.desfazer() }
                        .setNegativeButton("Cancelar", null).show()
                    7 -> ordens(chaveAtual)
                    8 -> iniciarGps()
                    9 -> { v.salvar(); salvarImagemMapa() }
                    10 -> biblioteca()
                }
            }.show()
    }

    private fun ferramenta(tipo: Int, manterPontos: Boolean = false) {
        val v = mapa ?: return
        if (!manterPontos) v.iniciar(tipo)
        raiz?.findViewWithTag<View>("painel")?.let { raiz?.removeView(it) }
        atualizarControlesMapa?.invoke()
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
                                atualizarControlesMapa?.invoke()
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

    private fun voltarTela() {
        when {
            chaveAtual.isNotEmpty() -> biblioteca()
            telaAtual == "inicio" -> AlertDialog.Builder(this)
                .setTitle("Sair do Colheita GPS?")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Sair") { _, _ -> finish() }.show()
            else -> inicio()
        }
    }
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { voltarTela() }

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

    var aoEditarArea: (() -> Unit)? = null
    var aoAtualizarArea: (() -> Unit)? = null
    private var inicioToqueX = 0f
    private var inicioToqueY = 0f
    private var arrastou = false
    private var verticeSelecionado = -1
    private var areaEmEdicao = -1
    private var originalEdicao: List<PointF>? = null
    private var corOriginalEdicao = 0
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
        aoAtualizarArea?.invoke()
        post { aoAtualizarArea?.invoke() }

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
        aoAtualizarArea?.invoke()
    }

    fun cancelarFerramenta() {
        if (areaEmEdicao >= 0) {
            // Restaura a pintura anterior caso o usuário cancele a edição.
            originalEdicao?.let { original ->
                areas.add(areaEmEdicao.coerceIn(0, areas.size), original.map { PointF(it.x, it.y) }.toMutableList())
                coresAreas.add((areaEmEdicao).coerceIn(0, coresAreas.size), corOriginalEdicao)
                salvar()
            }
            areaEmEdicao = -1
            originalEdicao = null
        }
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
            aoAtualizarArea?.invoke()
        }
    }

    fun fecharArea(): Boolean {
        if (modo != 7 || pontos.size < 3) {
            aviso("Adicione pelo menos 3 pontos")
            return false
        }
        areaFechada = true
        invalidate()
        aoAtualizarArea?.invoke()
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

        if (areaEmEdicao in areas.indices) {
            areas.removeAt(areaEmEdicao)
            coresAreas.removeAt(areaEmEdicao)
        }
        areaEmEdicao = -1
        originalEdicao = null
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
        aoAtualizarArea?.invoke()
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
        c.drawColor(0xFF18231D.toInt())

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


        for (i in areas.indices) {
            val ha = hectaresDaArea(areas[i]) ?: continue
            val p = areas[i]
            if (p.isEmpty()) continue
            val centroX = p.map { it.x }.average().toFloat()
            val centroY = p.map { it.y }.average().toFloat()
            val legenda = String.format(Locale("pt", "BR"), "%.2f ha", ha)
            val tinta = Paint(3).apply {
                color = Color.WHITE
                textAlign = Paint.Align.CENTER
                textSize = 25f / escala
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                setShadowLayer(3f / escala, 1f / escala, 1f / escala, Color.BLACK)
            }
            c.drawText(legenda, centroX, centroY, tinta)
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

            if (!areaFechada && pontos.isNotEmpty() && modo in 7..9) {
                val ultimo = pontos.last()
                val centro = PointF((width / 2f - dx) / escala, (height / 2f - dy) / escala)
                val referencia = Paint(3).apply {
                    color = 0xFF22A8FF.toInt()
                    style = Paint.Style.STROKE
                    strokeWidth = 2.5f / escala
                    pathEffect = DashPathEffect(floatArrayOf(12f / escala, 7f / escala), 0f)
                }
                c.drawLine(ultimo.x, ultimo.y, centro.x, centro.y, referencia)
                // Sem fechamento antecipado: apenas a linha guia até o próximo ponto.
            }
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

        /* Indicador duplicado removido; a faixa única fica acima do mapa.
        hectaresPintados()?.let { ha ->
            val legenda = String.format(Locale("pt", "BR"), "TOTAL PINTADO: %.2f ha", ha)
            val fundo = Paint(3).apply { color = 0xDD143C2A.toInt() }
            c.drawRoundRect(RectF(12f, 94f * resources.displayMetrics.density, minOf(width.toFloat() - 12f, 330f * resources.displayMetrics.density), 142f * resources.displayMetrics.density), 12f, 12f, fundo)
            c.drawText(legenda, 24f, 124f * resources.displayMetrics.density, Paint(3).apply {
                color = Color.WHITE; textSize = 19f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            })
        }

        */

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
                inicioToqueX = e.x
                inicioToqueY = e.y
                arrastou = false
                verticeSelecionado = -1
                if (modo == 7 && areaEmEdicao >= 0) {
                    val raio = 28f * resources.displayMetrics.density / escala
                    verticeSelecionado = pontos.indexOfFirst { hypot(it.x - x, it.y - y) <= raio }
                }

                if (modo == 1) {
                    linhaAtual = mutableListOf(PointF(x, y))
                    linhas.add(linhaAtual!!)
                    coresLinhas.add(corAtual())
                }

                if (modo == 2) apagar(x, y)
            }

            MotionEvent.ACTION_MOVE -> {
                if (hypot(e.x - inicioToqueX, e.y - inicioToqueY) > 12f * resources.displayMetrics.density) arrastou = true
                if (modo == 7 && verticeSelecionado in pontos.indices) {
                    pontos[verticeSelecionado] = PointF(x, y)
                    anteriorX = e.x
                    anteriorY = e.y
                    invalidate()
                    return true
                }
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
                if (modo == 0 && !arrastou && hypot(e.x - inicioToqueX, e.y - inicioToqueY) < 14f * resources.displayMetrics.density) {
                    abrirAreaPintada(x, y)
                }
                verticeSelecionado = -1
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


    private fun hectaresDaArea(poligono: List<PointF>): Double? {
        val g = geo ?: return null
        val b = imagem ?: return null
        if (poligono.size < 3) return null
        val xy = poligono.map { g.pixelParaMetros(it.x, it.y, b.width, b.height) }
        if (xy.any { it == null }) return null
        val metros = xy.filterNotNull()
        var soma = 0.0
        for (i in metros.indices) {
            val j = (i + 1) % metros.size
            soma += metros[i].first * metros[j].second - metros[j].first * metros[i].second
        }
        return abs(soma) / 20000.0
    }

    private fun areaSelecionada(x: Float, y: Float): Int {
        for (i in areas.indices.reversed()) {
            val poligono = areas[i]
            if (poligono.size < 3) continue
            val p = caminho(poligono, true)
            val limites = RectF()
            p.computeBounds(limites, true)
            val regiao = Region()
            regiao.setPath(p, Region(
                kotlin.math.floor(limites.left).toInt() - 1,
                kotlin.math.floor(limites.top).toInt() - 1,
                kotlin.math.ceil(limites.right).toInt() + 1,
                kotlin.math.ceil(limites.bottom).toInt() + 1
            ))
            if (regiao.contains(x.toInt(), y.toInt())) return i
        }
        return -1
    }

    private fun abrirAreaPintada(x: Float, y: Float) {
        val i = areaSelecionada(x, y)
        if (i < 0) return
        val ha = hectaresDaArea(areas[i])
        val descricao = if (ha != null)
            String.format(Locale("pt", "BR"), "Área pintada: %.4f ha", ha)
        else "Área não disponível: PDF sem georreferência reconhecida"
        AlertDialog.Builder(context)
            .setTitle("Talhão pintado")
            .setMessage(descricao)
            .setPositiveButton("Editar") { _, _ ->
                val vertices = areas.removeAt(i)
                val cor = coresAreas.removeAt(i)
                areaEmEdicao = i
                originalEdicao = vertices.map { PointF(it.x, it.y) }
                corOriginalEdicao = cor
                pontos.clear()
                pontos.addAll(vertices.map { PointF(it.x, it.y) })
                corPintura = Color.rgb(Color.red(cor), Color.green(cor), Color.blue(cor))
                opacidade = Color.alpha(cor)
                modo = 7
                areaFechada = true
                invalidate()
                aoEditarArea?.invoke()
            }
            .setNeutralButton("Apagar") { _, _ ->
                AlertDialog.Builder(context)
                    .setTitle("Apagar esta pintura?")
                    .setMessage(descricao)
                    .setPositiveButton("Apagar") { _, _ ->
                        areas.removeAt(i)
                        coresAreas.removeAt(i)
                        salvar()
                        invalidate()
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // Soma a união das pinturas usando a escala de área já validada
    // para os polígonos. Não depende dos quatro cantos do PDF.
    fun hectaresPintados(): Double? {
        if (areas.isEmpty()) return 0.0
        if (imagem == null || geo == null) return null
        val validas = areas.filter { it.size >= 3 }
        if (validas.isEmpty()) return 0.0
        val medidas = validas.map { hectaresDaArea(it) }
        // Use as mesmas medições que aparecem dentro das pinturas.
        // Se a união não puder ser estimada, exiba ao menos a soma das medidas.
        val somaMedidas = medidas.filterNotNull().sum()
        val referencia = validas.firstOrNull { hectaresDaArea(it) != null }
            ?: return if (medidas.isEmpty()) 0.0 else null
        val hectaresReferencia = hectaresDaArea(referencia) ?: return somaMedidas
        val pixelsReferencia = areaPixels(referencia)
        if (pixelsReferencia <= 0.0) return somaMedidas
        // União rasterizada com resolução constante para limitar memória.
        val b = imagem ?: return null
        val w = minOf(1600, b.width)
        val h = maxOf(1, (b.height.toDouble() * w / b.width).toInt())
        val mascara = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        try {
            val c = Canvas(mascara)
            c.scale(w.toFloat() / b.width, h.toFloat() / b.height)
            val tinta = Paint(3).apply { color = Color.WHITE; style = Paint.Style.FILL }
            validas.forEach { c.drawPath(caminho(it, true), tinta) }
            val pixels = IntArray(w * h)
            mascara.getPixels(pixels, 0, w, 0, 0, w, h)
            val preenchidos = pixels.count { Color.alpha(it) != 0 }
            val pixelsUniao = preenchidos.toDouble() * b.width / w * b.height / h
            return hectaresReferencia * pixelsUniao / pixelsReferencia
        } catch (_: Exception) {
            return somaMedidas
        } finally { mascara.recycle() }
    }

    private fun areaPixels(poligono: List<PointF>): Double {
        var soma = 0.0
        for (i in poligono.indices) {
            val j = (i + 1) % poligono.size
            soma += poligono[i].x.toDouble() * poligono[j].y -
                poligono[j].x.toDouble() * poligono[i].y
        }
        return kotlin.math.abs(soma) / 2.0
    }

    fun hectaresEmDestaque(): Double? = when {
        pontos.size >= 3 -> hectaresDaArea(pontos)
        areas.isNotEmpty() -> hectaresDaArea(areas.last())
        else -> null
    }

    private fun atualizarResumoMapa() {
        if (id.isEmpty()) return
        val pref = context.getSharedPreferences("controle_colheita", 0).edit()
        val ha = hectaresPintados()
        if (ha != null) pref.putFloat("ultima_area_pintada_ha", ha.toFloat())
        else pref.remove("ultima_area_pintada_ha")
        pref.putString("ultimo_mapa", id).apply()
        val b = imagem ?: return
        try {
            val escalaImagem = minOf(1f, 2400f / maxOf(b.width, b.height))
            val w = maxOf(1, (b.width * escalaImagem).toInt())
            val h = maxOf(1, (b.height * escalaImagem).toInt())
            val saida = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(saida)
            c.scale(w.toFloat() / b.width, h.toFloat() / b.height)
            c.drawBitmap(b, 0f, 0f, null)
            for (i in areas.indices) {
                c.drawPath(caminho(areas[i], true), Paint(3).apply { color = coresAreas.getOrElse(i) { 0x66FACC15 }; style = Paint.Style.FILL })
            }
            for (i in linhas.indices) {
                c.drawPath(caminho(linhas[i], false), Paint(3).apply { color = coresLinhas.getOrElse(i) { 0x66FACC15 }; style = Paint.Style.STROKE; strokeWidth = 17f; strokeCap = Paint.Cap.ROUND })
            }
            File(context.filesDir, "mapa_relatorio_$id.png").outputStream().use { saida.compress(Bitmap.CompressFormat.PNG, 95, it) }
            saida.recycle()
        } catch (_: Exception) { /* A pintura permanece salva mesmo se a imagem falhar */ }
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
        atualizarResumoMapa()
        aoAtualizarArea?.invoke()
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
