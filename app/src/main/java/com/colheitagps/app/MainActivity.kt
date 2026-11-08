
package com.colheitagps.app

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.graphics.pdf.PdfRenderer
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
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
    private val escuro = Color.rgb(18,26,22)
    private var raiz: FrameLayout? = null
    private var mapa: MapaView? = null
    private var chaveAtual = ""
    private var nomeAtual = ""
    private var gpsManager: LocationManager? = null
    private var gpsListener: LocationListener? = null
    private var local: Location? = null
    private var status: TextView? = null

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
        inicio()
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
        val base = tela()
        val coluna = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(16))
        }
        val topo = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        topo.addView(botao("Voltar") { inicio() })
        topo.addView(texto(titulo, 20f, Color.WHITE).apply {
            setPadding(dp(12), 0, 0, 0)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        topo.addView(TextView(this).apply {
            text = "⋮"
            textSize = 28f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(dp(10), 0, dp(10), 0)
            setOnClickListener { v ->
                PopupMenu(this@MainActivity, v).apply {
                    menu.add("Início").setOnMenuItemClickListener { inicio(); true }
                    menu.add("Pendências").setOnMenuItemClickListener { pendencias(); true }
                    menu.add("Relatórios").setOnMenuItemClickListener { relatorios(); true }
                    menu.add("Estoque • Turno C").setOnMenuItemClickListener { estoque(); true }
                    show()
                }
            }
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
            Triple("ESTOQUE DE CANA • TURNO C", "Controle separado do estoque", 2),
            Triple("RELATÓRIOS", "Ocorrências, serviços e pendências por turno", 3),
            Triple("PENDÊNCIAS DE MANUTENÇÃO", "Registrar e acompanhar serviços pendentes", 4)
        )
        for ((titulo, subtitulo, tipo) in itens) {
            val item = cartao(titulo, subtitulo) {
                when (tipo) {
                    0 -> biblioteca()
                    1 -> paginaOrdens()
                    2 -> estoque()
                    3 -> relatorios()
                    else -> pendencias()
                }
            }
            coluna.addView(item, LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = dp(16)
            })
        }
        val frases = listOf(
            "Segurança em primeiro lugar. Um bom turno começa com atenção.",
            "Cada tarefa bem-feita fortalece toda a equipe.",
            "Planejamento e cuidado fazem a diferença na colheita.",
            "Trabalho em equipe transforma desafios em resultados.",
            "Cuide de você e dos colegas. Voltar bem para casa é prioridade.",
            "Organização hoje significa menos pendências amanhã.",
            "Faça com atenção, registre com clareza e siga em frente."
        )
        val dia = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_YEAR)
        coluna.addView(texto("Mensagem do dia: ${frases[dia % frases.size]}", 14f, 0xFFB7E5C9.toInt()).apply {
            setPadding(0, dp(22), 0, dp(4))
        })
        coluna.addView(texto("Dados salvos neste aparelho. Sincronização online ainda não disponível.", 12f, 0xFFB6C9BD.toInt()).apply {
            setPadding(0, dp(22), 0, 0)
        })
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

    private fun estoque() {
        val coluna = pagina("Estoque de cana • Turno C")
        coluna.addView(texto("Entradas e saídas de cana", 15f).apply {
            setPadding(0, dp(14), 0, dp(8))
        })
        coluna.addView(botao("Adicionar movimentação") { novaMovimentacao() })
        val dados = registros()
        var saldo = 0.0
        for (i in 0 until dados.length()) {
            val o = dados.optJSONObject(i) ?: continue
            val valor = o.optDouble("quantidade", 0.0)
            saldo += if (o.optString("tipo") == "Entrada") valor else -valor
        }
        coluna.addView(texto("Saldo registrado: %.2f toneladas".format(Locale("pt", "BR"), saldo), 19f).apply {
            setPadding(0, dp(18), 0, dp(12))
        })
        for (i in dados.length() - 1 downTo 0) {
            val o = dados.optJSONObject(i) ?: continue
            val resumo = "${o.optString("tipo")} • ${o.optDouble("quantidade")} t • Turno ${o.optString("turno")}"
            val detalhe = "${o.optString("data")} • ${o.optString("os")} • ${o.optString("observacao")}"
            coluna.addView(cartao(resumo, detalhe) {},
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(9) })
        }
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
                arrayOf("C"))
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

    private fun pendenciasDados(): JSONArray = try {
        JSONArray(getSharedPreferences("controle_colheita", 0).getString("pendencias_v2", "[]"))
    } catch (_: Exception) { JSONArray() }

    private fun relatoriosDados(): JSONArray = try {
        JSONArray(getSharedPreferences("controle_colheita", 0).getString("relatorios_v2", "[]"))
    } catch (_: Exception) { JSONArray() }

    private fun salvarDados(chave: String, dados: JSONArray) {
        getSharedPreferences("controle_colheita", 0).edit().putString(chave, dados.toString()).apply()
    }

    private fun campo(hintTexto: String, linhas: Int = 1): EditText = EditText(this).apply {
        hint = hintTexto
        setSingleLine(linhas == 1)
        if (linhas > 1) { minLines = linhas; gravity = Gravity.TOP }
    }

    private fun pendencias() {
        val coluna = pagina("Pendências de manutenção")
        coluna.addView(botao("+ Nova pendência") { editarPendencia(-1) })
        val dados = pendenciasDados()
        if (dados.length() == 0) coluna.addView(texto("Nenhuma pendência registrada.").apply {
            setPadding(0, dp(16), 0, 0)
        })
        for (i in dados.length() - 1 downTo 0) {
            val o = dados.optJSONObject(i) ?: continue
            val titulo = "${if (o.optBoolean("concluida")) "✓ Concluída" else "● Pendente"} • ${o.optString("equipamento")}"
            val detalhe = "O.S.: ${o.optString("os")} • ${o.optString("descricao")} • ${o.optString("data")}"
            coluna.addView(cartao(titulo, detalhe) { editarPendencia(i) },
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        }
    }

    private fun editarPendencia(indice: Int) {
        val dados = pendenciasDados()
        val anterior = if (indice >= 0) dados.optJSONObject(indice) else null
        val corpo = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(8), dp(18), dp(8))
        }
        val equipamento = campo("Equipamento / máquina").apply { setText(anterior?.optString("equipamento") ?: "") }
        val os = campo("Número da O.S.").apply { setText(anterior?.optString("os") ?: "") }
        val descricao = campo("Descreva a pendência", 3).apply { setText(anterior?.optString("descricao") ?: "") }
        val concluida = CheckBox(this).apply {
            text = "Serviço concluído"
            isChecked = anterior?.optBoolean("concluida") ?: false
        }
        listOf(equipamento, os, descricao, concluida).forEach { corpo.addView(it) }
        val builder = AlertDialog.Builder(this).setTitle(if (indice < 0) "Nova pendência" else "Editar pendência")
            .setView(ScrollView(this).apply { addView(corpo) })
            .setPositiveButton("Salvar") { _, _ ->
                if (equipamento.text.isBlank() || descricao.text.isBlank()) {
                    Toast.makeText(this, "Informe equipamento e descrição", Toast.LENGTH_LONG).show()
                } else {
                    val objeto = JSONObject().apply {
                        put("equipamento", equipamento.text.toString().trim())
                        put("os", os.text.toString().trim())
                        put("descricao", descricao.text.toString().trim())
                        put("concluida", concluida.isChecked)
                        put("data", anterior?.optString("data")?.takeIf { it.isNotBlank() } ?: agora())
                    }
                    if (indice < 0) dados.put(objeto) else dados.put(indice, objeto)
                    salvarDados("pendencias_v2", dados)
                }
                pendencias()
            }.setNegativeButton("Cancelar", null)
        if (indice >= 0) builder.setNeutralButton("Excluir") { _, _ ->
            val novos = JSONArray()
            for (i in 0 until dados.length()) if (i != indice) novos.put(dados.get(i))
            salvarDados("pendencias_v2", novos)
            pendencias()
        }
        builder.show()
    }

    private fun agora() = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR"))
        .format(java.util.Date())

    private fun relatorios() {
        val coluna = pagina("Relatórios de turno")
        coluna.addView(botao("+ Novo relatório de turno") { editarRelatorio() })
        val dados = relatoriosDados()
        if (dados.length() == 0) coluna.addView(texto("Nenhum relatório de turno criado.").apply {
            setPadding(0, dp(16), 0, 0)
        })
        for (i in dados.length() - 1 downTo 0) {
            val o = dados.optJSONObject(i) ?: continue
            coluna.addView(cartao("Turno ${o.optString("turno")} • ${o.optString("data")}",
                "O.S. ${o.optString("os")} • ${o.optString("acontecimentos")}") {
                AlertDialog.Builder(this).setTitle("Relatório do turno ${o.optString("turno")}")
                    .setMessage(textoRelatorio(o))
                    .setPositiveButton("Compartilhar PDF") { _, _ -> compartilharPdf(o) }
                    .setNeutralButton("Editar") { _, _ -> editarRelatorio(i) }
                    .setNegativeButton("Fechar", null).show()
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        }
    }

    private fun editarRelatorio(indice: Int = -1) {
        val dados = relatoriosDados()
        val anterior = if (indice >= 0) dados.optJSONObject(indice) else null
        val corpo = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(8), dp(18), dp(8))
        }
        val turno = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item,
                arrayOf("A", "B", "C"))
            setSelection(listOf("A", "B", "C").indexOf(anterior?.optString("turno") ?: "A").coerceAtLeast(0))
        }
        val os = campo("O.S. em andamento").apply { setText(anterior?.optString("os") ?: "") }
        val acontecimentos = campo("Tudo o que aconteceu no turno", 3).apply { setText(anterior?.optString("acontecimentos") ?: "") }
        val manutencoes = campo("Manutenções realizadas", 3).apply { setText(anterior?.optString("manutencoes") ?: "") }
        val pendentes = campo("Pendências para o próximo turno", 3).apply { setText(anterior?.optString("pendentes") ?: "") }
        corpo.addView(texto("Turno", 15f, Color.DKGRAY))
        listOf(turno, os, acontecimentos, manutencoes, pendentes).forEach { corpo.addView(it) }
        AlertDialog.Builder(this).setTitle("Relatório de turno")
            .setView(ScrollView(this).apply { addView(corpo) })
            .setPositiveButton("Salvar") { _, _ ->
                val objeto = JSONObject().apply {
                    put("turno", turno.selectedItem.toString())
                    put("os", os.text.toString())
                    put("acontecimentos", acontecimentos.text.toString())
                    put("manutencoes", manutencoes.text.toString())
                    put("pendentes", pendentes.text.toString())
                    put("data", anterior?.optString("data")?.takeIf { it.isNotBlank() } ?: agora())
                }
                if (indice < 0) dados.put(objeto) else dados.put(indice, objeto)
                salvarDados("relatorios_v2", dados)
                relatorios()
            }.setNegativeButton("Cancelar", null).show()
    }

    private fun textoRelatorio(o: JSONObject): String {
        val abertas = pendenciasDados()
        val pendenciasAtuais = mutableListOf<String>()
        for (i in 0 until abertas.length()) {
            val p = abertas.optJSONObject(i) ?: continue
            if (!p.optBoolean("concluida")) pendenciasAtuais.add(
                "${p.optString("equipamento")} / O.S. ${p.optString("os")}: ${p.optString("descricao")}")
        }
        return """COLHEITA GPS - RELATÓRIO DE TURNO
Data: ${o.optString("data")}
Turno: ${o.optString("turno")}
O.S. em andamento: ${o.optString("os")}

ACONTECIMENTOS DO TURNO
${o.optString("acontecimentos")}

MANUTENÇÕES REALIZADAS
${o.optString("manutencoes")}

PENDÊNCIAS INFORMADAS
${o.optString("pendentes")}

PENDÊNCIAS ABERTAS NO CONTROLE
${if (pendenciasAtuais.isEmpty()) "Nenhuma" else pendenciasAtuais.joinToString("\n")}
""".trimIndent()
    }

    private fun compartilharPdf(o: JSONObject) {
        try {
            val doc = PdfDocument()
            val linhas = mutableListOf<String>()
            val tinta = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK; textSize = 12f
            }
            val largura = 510f
            for (linha in textoRelatorio(o).lines()) {
                if (linha.isEmpty()) { linhas.add(""); continue }
                var restante = linha
                while (restante.isNotEmpty()) {
                    val quantidade = tinta.breakText(restante, true, largura, null).coerceAtLeast(1)
                    linhas.add(restante.take(quantidade))
                    restante = restante.drop(quantidade)
                }
            }
            val porPagina = 48
            for (pagina in 0 until ((linhas.size + porPagina - 1) / porPagina).coerceAtLeast(1)) {
                val info = PdfDocument.PageInfo.Builder(595, 842, pagina + 1).create()
                val pg = doc.startPage(info)
                val canvas = pg.canvas
                for ((i, linha) in linhas.drop(pagina * porPagina).take(porPagina).withIndex()) {
                    canvas.drawText(linha, 42f, 52f + i * 16f, tinta)
                }
                doc.finishPage(pg)
            }
            val arquivo = File(cacheDir, "relatorio_turno_${System.currentTimeMillis()}.pdf")
            FileOutputStream(arquivo).use { doc.writeTo(it) }
            doc.close()
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", arquivo)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Compartilhar relatório em PDF"))
        } catch (e: Exception) {
            Toast.makeText(this, "Não foi possível criar o PDF: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

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
        cab.addView(texto("COLHEITA GPS 2.0", 23f, Color.WHITE))
        cab.addView(botao("Menu principal") { inicio() })
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
        // Reaproveita a ultima posicao conhecida ao trocar de mapa.
        local?.let { visual.atualizarGps(it.latitude, it.longitude) }

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
            texto("Voltar", 14f, Color.WHITE).apply {
                gravity = Gravity.CENTER
                setOnClickListener { biblioteca() }
            },
            LinearLayout.LayoutParams(dp(66), dp(52))
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

            val dentroDoMapa = mapa?.posicaoGpsNoMapa() == true
            status?.text = String.format(
                Locale("pt", "BR"),
                "%s • GPS ±%.0f m • %.5f, %.5f",
                if (dentroDoMapa) "No mapa" else "Fora do mapa / sem georreferencia",
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

    fun posicaoGpsNoMapa(): Boolean = gpsPixel() != null

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
                        bbox[3] <= bbox[1] ||
                        !(bbox + gpts + lpts).all { it.isFinite() }
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
