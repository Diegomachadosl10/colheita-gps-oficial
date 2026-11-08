
package com.colheitagps.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

class LoginActivity : Activity() {
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }
    private lateinit var painel: LinearLayout
    private val verde = Color.rgb(16, 44, 37)
    private val destaque = Color.rgb(36, 107, 78)
    private val claro = Color.rgb(197, 232, 170)

    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    private fun fundo(cor: Int, raio: Int = 16): GradientDrawable =
        GradientDrawable().apply { setColor(cor); cornerRadius = dp(raio).toFloat() }
    private fun texto(valor: String, tamanho: Float, cor: Int = Color.BLACK, negrito: Boolean = false) =
        TextView(this).apply {
            text = valor; textSize = tamanho; setTextColor(cor)
            if (negrito) setTypeface(null, Typeface.BOLD)
        }
    private fun espaco(altura: Int) = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(altura)) }
    private fun botao(rotulo: String, cor: Int = destaque, textoCor: Int = Color.WHITE) =
        Button(this).apply {
            text = rotulo; isAllCaps = false; textSize = 15f
            setTextColor(textoCor); background = fundo(cor, 12)
            minHeight = dp(52)
        }
    private fun campo(dica: String, senha: Boolean = false) = EditText(this).apply {
        hint = dica; textSize = 16f; setSingleLine(true)
        setPadding(dp(16), dp(12), dp(16), dp(12))
        background = GradientDrawable().apply {
            setColor(Color.rgb(247, 249, 247)); cornerRadius = dp(12).toFloat()
            setStroke(dp(1), Color.rgb(218, 225, 219))
        }
        inputType = if (senha) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                    else InputType.TYPE_CLASS_TEXT
    }
    private fun aviso(m: String) { AlertDialog.Builder(this).setMessage(m).setPositiveButton("OK", null).show() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = verde
        window.navigationBarColor = verde
        painel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(32), dp(24), dp(28))
            setBackgroundColor(verde)
        }
        setContentView(ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(verde)
            addView(painel)
        })
        if (auth.currentUser != null) verificarAcesso() else formulario(false)
    }

    private fun estrutura(titulo: String, subtitulo: String): LinearLayout {
        painel.removeAllViews()
        painel.addView(texto("ðŸšœ", 36f, claro).apply { gravity = Gravity.CENTER })
        painel.addView(espaco(6))
        painel.addView(texto("DM AGRO", 30f, Color.WHITE, true).apply { gravity = Gravity.CENTER })
        painel.addView(texto("GESTÃƒO DE FRENTES DE SERVIÃ‡O", 11f, claro, true).apply { gravity = Gravity.CENTER })
        painel.addView(espaco(28))
        val cartao = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(24), dp(22), dp(24))
            background = fundo(Color.WHITE, 20)
        }
        cartao.addView(texto(titulo, 22f, verde, true))
        cartao.addView(espaco(5))
        cartao.addView(texto(subtitulo, 14f, Color.DKGRAY))
        cartao.addView(espaco(18))
        painel.addView(cartao, LinearLayout.LayoutParams(-1, -2))
        painel.addView(espaco(22))
        painel.addView(texto("Criado por Diego Machado", 13f, Color.WHITE).apply { gravity = Gravity.CENTER })
        return cartao
    }

    private fun formulario(cadastro: Boolean) {
        val cartao = estrutura(if (cadastro) "Solicitar cadastro" else "Bem-vindo de volta",
            if (cadastro) "Preencha seus dados para solicitar acesso." else "Acesse sua equipe e suas atividades.")
        val nome = campo("Nome completo").apply { visibility = if (cadastro) View.VISIBLE else View.GONE }
        val email = campo("E-mail")
        email.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        val senha = campo("Senha", true)
        cartao.addView(nome, LinearLayout.LayoutParams(-1, dp(52)))
        if (cadastro) cartao.addView(espaco(12))
        cartao.addView(email, LinearLayout.LayoutParams(-1, dp(52)))
        cartao.addView(espaco(12))
        cartao.addView(senha, LinearLayout.LayoutParams(-1, dp(52)))
        cartao.addView(espaco(18))
        val entrar = botao(if (cadastro) "Solicitar acesso" else "Entrar")
        cartao.addView(entrar, LinearLayout.LayoutParams(-1, dp(52)))
        cartao.addView(espaco(12))
        cartao.addView(botao(if (cadastro) "JÃ¡ tenho conta" else "Criar conta", Color.rgb(234, 242, 235), verde).apply {
            setOnClickListener { formulario(!cadastro) }
        }, LinearLayout.LayoutParams(-1, dp(48)))
        if (!cadastro) {
            cartao.addView(espaco(12))
            cartao.addView(texto("Esqueci minha senha", 14f, destaque, true).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(8), 0, dp(8))
                setOnClickListener {
                    val e = email.text.toString().trim()
                    if (e.isEmpty()) aviso("Informe seu e-mail no campo acima para recuperar a senha.")
                    else auth.sendPasswordResetEmail(e).addOnSuccessListener {
                        aviso("Se o e-mail estiver cadastrado, vocÃª receberÃ¡ instruÃ§Ãµes para redefinir a senha.")
                    }.addOnFailureListener { aviso("NÃ£o foi possÃ­vel enviar a recuperaÃ§Ã£o. Verifique o e-mail e a conexÃ£o.") }
                }
            })
        }
        entrar.setOnClickListener {
            val e = email.text.toString().trim()
            val s = senha.text.toString()
            if (e.isBlank() || s.length < 6 || (cadastro && nome.text.isBlank())) {
                aviso("Preencha os campos. A senha precisa ter pelo menos 6 caracteres."); return@setOnClickListener
            }
            entrar.isEnabled = false
            if (cadastro) {
                auth.createUserWithEmailAndPassword(e, s).addOnCompleteListener { resultado ->
                    if (!resultado.isSuccessful) {
                        entrar.isEnabled = true
                        aviso(resultado.exception?.localizedMessage ?: "Erro no cadastro")
                    } else {
                        val uid = auth.currentUser!!.uid
                        val dados = hashMapOf<String, Any>(
                            "nome" to nome.text.toString().trim(), "email" to e,
                            "papel" to "funcionario", "status" to "pendente",
                            "criadoEm" to FieldValue.serverTimestamp()
                        )
                        db.collection("usuarios").document(uid).set(dados).addOnCompleteListener { gravacao ->
                            if (gravacao.isSuccessful) verificarAcesso()
                            else { auth.signOut(); formulario(false); aviso("NÃ£o foi possÃ­vel registrar a solicitaÃ§Ã£o.") }
                        }
                    }
                }
            } else {
                auth.signInWithEmailAndPassword(e, s).addOnCompleteListener { resultado ->
                    entrar.isEnabled = true
                    if (resultado.isSuccessful) verificarAcesso()
                    else aviso(resultado.exception?.localizedMessage ?: "Erro ao entrar")
                }
            }
        }
    }

    private fun verificarAcesso() {
        val usuario = auth.currentUser ?: run { formulario(false); return }
        estrutura("Verificando autorizaÃ§Ã£o", "Aguarde enquanto confirmamos seu acesso.")
        db.collection("usuarios").document(usuario.uid).get()
            .addOnSuccessListener { documento ->
                when (documento.getString("status")) {
                    "aprovado" -> if (documento.getString("papel") == "administrador") painelAdministrador() else abrirAplicativo()
                    "bloqueado" -> aguardando("Seu acesso foi bloqueado.")
                    else -> aguardando("Seu cadastro aguarda aprovaÃ§Ã£o do administrador.")
                }
            }.addOnFailureListener { aguardando("NÃ£o foi possÃ­vel verificar sua autorizaÃ§Ã£o. Verifique a internet.") }
    }
    private fun abrirAplicativo() { startActivity(Intent(this, MainActivity::class.java)); finish() }
    private fun painelAdministrador() {
        val c = estrutura("Painel do administrador", "Bem-vindo! Gerencie os acessos ou abra o aplicativo.")
        c.addView(botao("Abrir aplicativo").apply { setOnClickListener { abrirAplicativo() } }, LinearLayout.LayoutParams(-1, dp(52)))
        c.addView(espaco(12))
        c.addView(botao("Gerenciar usuÃ¡rios").apply {
            setOnClickListener { startActivity(Intent(this@LoginActivity, AdminActivity::class.java)) }
        }, LinearLayout.LayoutParams(-1, dp(52)))
        c.addView(espaco(12))
        c.addView(botao("Sair da conta", Color.rgb(234, 242, 235), verde).apply {
            setOnClickListener { auth.signOut(); formulario(false) }
        }, LinearLayout.LayoutParams(-1, dp(48)))
    }
    private fun aguardando(m: String) {
        val c = estrutura("Status do cadastro", m)
        c.addView(botao("Verificar novamente").apply { setOnClickListener { verificarAcesso() } }, LinearLayout.LayoutParams(-1, dp(52)))
        c.addView(espaco(12))
        c.addView(botao("Sair da conta", Color.rgb(234, 242, 235), verde).apply {
            setOnClickListener { auth.signOut(); formulario(false) }
        }, LinearLayout.LayoutParams(-1, dp(48)))
    }
}