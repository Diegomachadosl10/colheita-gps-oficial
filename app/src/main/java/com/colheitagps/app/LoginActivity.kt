
package com.colheitagps.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue

class LoginActivity : Activity() {

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }
    private lateinit var painel: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        painel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(32, 40, 32, 40)
        }

        setContentView(ScrollView(this).apply {
            addView(painel)
        })

        if (auth.currentUser != null) {
            verificarAcesso()
        } else {
            formulario(false)
        }
    }

    private fun mensagem(texto: String) =
        TextView(this).apply {
            text = texto
            textSize = 19f
            setPadding(0, 12, 0, 12)
        }

    private fun aviso(texto: String) {
        AlertDialog.Builder(this)
            .setMessage(texto)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun formulario(cadastro: Boolean) {
        painel.removeAllViews()

        painel.addView(mensagem(
            if (cadastro) "Cadastro DM AGRO"
            else "Entrar no DM AGRO"
        ))

        val nome = EditText(this).apply {
            hint = "Nome completo"
            visibility = if (cadastro) View.VISIBLE else View.GONE
        }

        val email = EditText(this).apply {
            hint = "E-mail"
            inputType = 33
        }

        val senha = EditText(this).apply {
            hint = "Senha"
            inputType = 129
        }

        painel.addView(nome)
        painel.addView(email)
        painel.addView(senha)

        val entrar = Button(this).apply {
            text = if (cadastro) "Solicitar acesso" else "Entrar"
        }

        painel.addView(entrar)

        painel.addView(Button(this).apply {
            text = if (cadastro) "Já tenho conta" else "Criar conta"
            setOnClickListener { formulario(!cadastro) }
        })

        entrar.setOnClickListener {
            val e = email.text.toString().trim()
            val s = senha.text.toString()

            if (e.isBlank() || s.length < 6 ||
                (cadastro && nome.text.isBlank())) {
                aviso("Preencha os campos. A senha precisa ter pelo menos 6 caracteres.")
                return@setOnClickListener
            }

            entrar.isEnabled = false

            if (cadastro) {
                auth.createUserWithEmailAndPassword(e, s)
                    .addOnCompleteListener { resultado ->
                        if (!resultado.isSuccessful) {
                            entrar.isEnabled = true
                            aviso(resultado.exception?.localizedMessage
                                ?: "Erro no cadastro")
                        } else {
                            val uid = auth.currentUser!!.uid

                            val dados = hashMapOf(
                                "nome" to nome.text.toString().trim(),
                                "email" to e,
                                "papel" to "funcionario",
                                "status" to "pendente",
                                "criadoEm" to FieldValue.serverTimestamp()
                            )

                            db.collection("usuarios")
                                .document(uid)
                                .set(dados)
                                .addOnCompleteListener { gravacao ->
                                    if (gravacao.isSuccessful) {
                                        verificarAcesso()
                                    } else {
                                        auth.signOut()
                                        formulario(false)
                                        aviso("Não foi possível registrar a solicitação.")
                                    }
                                }
                        }
                    }
            } else {
                auth.signInWithEmailAndPassword(e, s)
                    .addOnCompleteListener { resultado ->
                        entrar.isEnabled = true
                        if (resultado.isSuccessful) {
                            verificarAcesso()
                        } else {
                            aviso(resultado.exception?.localizedMessage
                                ?: "Erro ao entrar")
                        }
                    }
            }
        }
    }

    private fun verificarAcesso() {
        val usuario = auth.currentUser ?: run {
            formulario(false)
            return
        }

        painel.removeAllViews()
        painel.addView(mensagem("Verificando autorização..."))

        db.collection("usuarios")
            .document(usuario.uid)
            .get()
            .addOnSuccessListener { documento ->
                when (documento.getString("status")) {
                    "aprovado" -> {
                        startActivity(Intent(this, MainActivity::class.java))
                        finish()
                    }
                    "bloqueado" -> {
                        aguardando("Seu acesso foi bloqueado.")
                    }
                    else -> {
                        aguardando("Seu cadastro aguarda aprovação do administrador.")
                    }
                }
            }
            .addOnFailureListener {
                aguardando("Não foi possível verificar sua autorização. Verifique a internet.")
            }
    }

    private fun aguardando(texto: String) {
        painel.removeAllViews()
        painel.addView(mensagem(texto))

        painel.addView(Button(this).apply {
            text = "Verificar novamente"
            setOnClickListener { verificarAcesso() }
        })

        painel.addView(Button(this).apply {
            text = "Sair da conta"
            setOnClickListener {
                auth.signOut()
                formulario(false)
            }
        })
    }
}
