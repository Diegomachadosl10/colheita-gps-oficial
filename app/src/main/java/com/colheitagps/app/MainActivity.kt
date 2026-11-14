
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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : Activity() {
    // Permissões obtidas do cadastro aprovado no Firestore.
    // Falha de rede ou perfil desconhecido: acesso bloqueado.
    private var papelAtual = ""
    private var acessoConfirmado = false
    private var podeAprovarUsuarios = false
    private var teamAccess: TeamAccess? = null
    private var teamStore: TeamStore? = null
    private var teamSync: TeamSync? = null
    private var profileWatch: com.google.firebase.firestore.ListenerRegistration? = null
    private var syncLabel: TextView? = null
    private var syncMessage = "Conectando à equipe…"
    private var sessionName = ""
    private var refreshList: (() -> Unit)? = null
    private var returnToParent: (() -> Unit)? = null
    private val syncUiHandler = Handler(Looper.getMainLooper())
    private var refreshPending = false
    private var relatorioAtual: (() -> FieldReport?)? = null
    private var buscaEstoque=""
    private var buscaEquipamento=""
    private var buscaMapas=""

    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
        if (name in setOf("mapas", "pinturas") && teamStore != null && teamAccess?.manages == false)
            teamStore!!.privateMapPrefs(name, teamAccess!!.uid)
        else if (name in TeamPolicy.preferences && teamStore != null) teamStore!!.prefs(name)
        else super.getSharedPreferences(name, mode)
    override fun getFilesDir(): File = teamStore?.files ?: super.getFilesDir()
    override fun getCacheDir(): File = teamStore?.cache ?: super.getCacheDir()

    private fun chooseTeam(access: TeamAccess) {
        val alertTeam=intent.getStringExtra("equipeAlerta")
        intent.removeExtra("equipeAlerta")
        if(alertTeam!=null && alertTeam.substringBeforeLast('_') in TeamPolicy.fronts && alertTeam.substringAfterLast('_') in TeamPolicy.shifts &&
            (access.primary||access.team==alertTeam)) { openTeam(access,alertTeam); return }
        if (!access.primary) { openTeam(access, access.team!!); return }
        val ids = TeamPolicy.fronts.keys.flatMap { f -> TeamPolicy.shifts.map { "${f}_$it" } }
        AlertDialog.Builder(this).setTitle("Qual equipe deseja acompanhar?")
            .setItems(ids.map(TeamPolicy::label).toTypedArray()) { _, index -> openTeam(access, ids[index]) }
            .setNegativeButton("Voltar") { _, _ -> moveTaskToBack(true) }.show()
    }
    private fun openTeam(access: TeamAccess, id: String) {
        if(teamStore?.team!=id||teamAccess?.uid!=access.uid) {buscaEstoque="";buscaEquipamento="";buscaMapas=""}
        mapa?.salvar(); mapa = null; chaveAtual = ""
        teamSync?.stop()
        profileWatch?.remove()
        teamStore = TeamStore(applicationContext, id)
        teamAccess = access
        if (!access.manages) teamStore!!.preservePreviousMaps(access.uid)
        papelAtual = access.role
        podeAprovarUsuarios = access.manages
        acessoConfirmado = true
        syncMessage = "Conectando à equipe…"
        teamSync = TeamSync(applicationContext, access, teamStore!!, {
            if (!refreshPending) {
                refreshPending = true
                syncUiHandler.postDelayed({
                    refreshPending = false
                    if (!isFinishing && !isDestroyed && hasWindowFocus() && mapa == null) refreshList?.invoke()
                }, 300)
            }
        }, { message -> syncMessage = message; syncLabel?.text = "● ${DashboardUi.status(message)}" })
        teamSync!!.start()
        if (!access.primary) profileWatch = FirebaseFirestore.getInstance().collection("usuarios").document(access.uid)
            .addSnapshotListener { document, _ ->
                if (document != null && !document.metadata.isFromCache && !isFinishing && !isDestroyed &&
                    (!TeamPolicy.allowed(access.uid, document.data) || TeamPolicy.team(document.data) != access.team || AccessPolicy.role(document.data) != access.role)) {
                    profileWatch?.remove(); profileWatch = null
                    mapa?.salvar(); mapa = null
                    teamSync?.stop(); teamSync = null
                    acessoConfirmado = false
                    startActivity(Intent(this, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    })
                    finish()
                }
            }
        inicio()
        AgroNotifications.offer(this, access)
    }
    private fun syncDetails() {
        val sync = teamSync ?: return
        AlertDialog.Builder(this).setTitle("Sincronização da equipe").setMessage(sync.diagnostics())
            .setPositiveButton("Tentar novamente") { _, _ -> sync.retry() }
            .setNeutralButton("Usar versões compartilhadas") { _, _ ->
                AlertDialog.Builder(this).setMessage("Suas cópias conflitantes ficam preservadas nos arquivos da equipe. Recarregar as versões compartilhadas?")
                    .setNegativeButton("Cancelar", null).setPositiveButton("Recarregar") { _, _ -> sync.useSharedCopies() }.show()
            }.setNegativeButton("Fechar", null).show()
    }
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if(hasFocus) {
            StartupUpdates.focused(this)
            if(telaAtual=="caminhoes"&&diaCaminhoes.isBlank()) atualizarTurnoCaminhoes.run()
        }
        teamSync?.available(hasFocus && mapa == null)
    }
    private fun pausarSincronizacao() {
        teamSync?.available(false)
        synchronized(teamStore?.lock ?: this) { /* Finish any incoming cache write before opening a form. */ }
    }
    private fun funcionarioRestrito() = papelAtual in TeamPolicy.leafRoles
    private fun podeGerenciar() = acessoConfirmado &&
        (papelAtual == "administrador" || papelAtual == "supervisor")
    private fun exigirGestor(): Boolean {
        if (podeGerenciar()) return true
        Toast.makeText(this, "Função exclusiva de supervisor ou administrador", Toast.LENGTH_LONG).show()
        return false
    }
    private fun carregarPermissoes() {
        val usuario = FirebaseAuth.getInstance().currentUser
        if (usuario == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }
        sessionName = usuario.displayName.orEmpty()
        val espera = tela()
        espera.addView(texto("Verificando permissões...", 18f).apply {
            gravity = Gravity.CENTER
        }, FrameLayout.LayoutParams(-1, -1))
        setContentView(espera)
        AccessRepository.loadTeamAccess(this, { access ->
            if (TeamPolicy.allowed(access.uid, access.profile)) {
                chooseTeam(access)
            } else {
                acessoConfirmado = false
                AlertDialog.Builder(this).setTitle("Acesso não autorizado")
                    .setMessage("Sua conta precisa de aprovação e de um perfil válido. Procure o administrador principal.")
                    .setCancelable(false).setPositiveButton("Voltar ao login") { _, _ ->
                        AccessRepository.logout(this)
                    }.show()
            }
        }, {error->
            AlertDialog.Builder(this).setTitle("Sem verificação de acesso")
                .setMessage(AccessFailures.description(error))
                .setCancelable(false).setPositiveButton("Tentar novamente") { _, _ ->
                    carregarPermissoes()
                }.setNegativeButton("Voltar ao login") { _, _ ->
                    AccessRepository.logout(this)
                }.show()
        })
    }

    private val palette get()=AppTheme.colors(this)
    private val verde get()=palette.primary
    private val escuro get()=palette.header
    private var themeScreen:()->Unit={inicio()}
    private var renderedNight:Boolean?=null
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
    private var imagemPendente: File? = null
    private var pecaRascunho: JSONObject? = null
    private var atualizarFotosPeca: (() -> Unit)? = null
    private var fotoCameraPeca: File? = null
    private var vozResultado: ((String) -> Unit)? = null
    private fun todosRegistros(key:String):JSONArray = synchronized(teamStore?.lock ?: this) {
        val prefs=getSharedPreferences(if(key=="movimentos") "controle_colheita" else "registros_v3",0)
        val array = try { JSONArray(prefs.getString(key,"[]")) } catch(_:Exception) {JSONArray()}
        var assigned = false
        for (i in 0 until array.length()) {
            val record = array.optJSONObject(i) ?: continue
            if (record.optString("id").isBlank()) { record.put("id", if(MaintenanceRecords.isEquipment(record)) MaintenanceRecords.equipmentId(record.optString("equipamento")) else java.util.UUID.randomUUID().toString()); assigned = true }
        }
        if (assigned) prefs.edit().putString(key,array.toString()).commit()
        for (i in 0 until array.length()) array.optJSONObject(i)?.let { record ->
            record.put("__syncBase", teamSync?.baseRevision(key, record.optString("id")) ?: 0L)
        }
        array
    }
    private fun dados(key:String):JSONArray {
        val all=todosRegistros(key)
        return if(key=="pendencias") MaintenanceRecords.issues(all) else RecordArchive.active(all)
    }
    private fun colocarRegistro(array: JSONArray, index: Int, record: JSONObject): Boolean {
        if (record.optString("id").isBlank()) record.put("id", java.util.UUID.randomUUID().toString())
        if (index < 0) { array.put(record); return true }
        for (i in 0 until array.length()) if (array.optJSONObject(i)?.optString("id") == record.optString("id")) {
            array.put(i, record); return true
        }
        val copy = File(filesDir, "conflitos/rascunho_${System.currentTimeMillis()}.json")
        copy.parentFile?.mkdirs(); copy.writeText(record.toString(2))
        Toast.makeText(this, "Este registro foi removido em outro aparelho. Seu rascunho foi preservado.", Toast.LENGTH_LONG).show()
        return false
    }
    private fun guardar(key: String, arr: JSONArray) {
        synchronized(teamStore?.lock ?: this) {
            val prefs = getSharedPreferences(if(key=="movimentos") "controle_colheita" else "registros_v3", 0)
            val old = try { JSONArray(prefs.getString(key, "[]")) } catch (_: Exception) { JSONArray() }
            val value = if (key == "pendencias") {
                MaintenanceRecords.merge(arr, old)
            } else RecordArchive.merge(arr,old)
            prefs.edit().putString(key, value.toString()).apply()
        }
    }
    private fun comRegistroAtual(group: String, id: String, action: (Int) -> Unit) {
        pausarSincronizacao()
        val current = dados(group)
        val index = (0 until current.length()).firstOrNull { current.optJSONObject(it)?.optString("id") == id }
        if (index != null) action(index) else {
            teamSync?.available(hasWindowFocus() && mapa == null)
            Toast.makeText(this, "Este registro foi removido em outro aparelho.", Toast.LENGTH_LONG).show()
            refreshList?.invoke()
        }
    }
    private fun exigirEdicao(group: String, record: JSONObject?): Boolean {
        if (record != null && RecordArchive.archived(record)) {
            Toast.makeText(this, "Registro excluído: restaure antes de editar. Somente o gestor pode restaurar.", Toast.LENGTH_LONG).show()
            return false
        }
        if (record == null || teamSync?.canEdit(group, record.optString("id")) != false) return true
        teamSync?.available(hasWindowFocus() && mapa == null)
        Toast.makeText(this, "Somente o autor ou o supervisor pode alterar este registro.", Toast.LENGTH_LONG).show()
        return false
    }
    private fun mostrarFotos(record: JSONObject) {
        val photos = fotosPendencia(record)
        if (photos.isEmpty()) { Toast.makeText(this, "As fotos são compartilhadas separadamente. Peça ao autor para enviá-las.", Toast.LENGTH_LONG).show(); return }
        AlertDialog.Builder(this).setTitle("Fotos").setItems(photos.indices.map { "Foto ${it + 1}" }.toTypedArray()) { _, index ->
            AlertDialog.Builder(this).setView(ImageView(this).apply {
                setImageBitmap(BitmapFactory.decodeFile(photos[index].absolutePath)); adjustViewBounds = true
            }).setPositiveButton("Fechar", null).show()
        }.show()
    }
    private fun remover(key: String, index: Int, retorno: () -> Unit) {
        pausarSincronizacao()
        if (!exigirGestor()) return
        val selected = dados(key).optJSONObject(index) ?: return
        if (!exigirEdicao(key, selected)) return
        AlertDialog.Builder(this).setTitle(if (key == "pendencias") "Excluir pendência?" else "Excluir registro?")
            .setMessage(if (key == "pendencias")
                "Equipamento ${selected.optString("equipamento")}\n${selected.optString("descricao")}\n\nA pendência sairá da lista ativa e permanecerá no histórico, com suas informações e fotos deste aparelho. O equipamento continuará cadastrado."
                else "O registro sairá da lista ativa e permanecerá em Registros excluídos, com seus dados e fotos deste aparelho. Somente o gestor pode restaurar.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Excluir") { _, _ ->
                if(!exigirGestor()) return@setPositiveButton
                val atual = dados(key)
                val registro = (0 until atual.length()).mapNotNull { atual.optJSONObject(it) }
                    .firstOrNull { it.optString("id") == selected.optString("id") }
                if (registro == null) { retorno(); return@setPositiveButton }
                if (!exigirEdicao(key, registro)) return@setPositiveButton
                val novo = JSONArray()
                for (i in 0 until atual.length()) {
                    val item = atual.optJSONObject(i) ?: continue
                    if (item.optString("id") != selected.optString("id")) novo.put(item)
                    else novo.put(arquivarManutencao(item))
                }
                guardar(key, novo)
                retorno()
            }.show()
    }
    private fun campo(hintTexto: String, valor: String = "") = EditText(this).apply {AppTheme.field(this)

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
        if (requestCode == 309) {
            val uri = data?.data ?: return
            val id = fotoPendente ?: return
            try {
                val arquivo = File(filesDir, "campo_${System.currentTimeMillis()}.jpg")
                contentResolver.openInputStream(uri)?.use { input -> arquivo.outputStream().use { input.copyTo(it) } }
                val arr = dados("ocorrencias_campo")
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    if (obj.optString("id") == id) {
                        val fotos = obj.optJSONArray("fotos") ?: JSONArray()
                        fotos.put(arquivo.absolutePath)
                        obj.put("fotos", fotos)
                        break
                    }
                }
                guardar("ocorrencias_campo", arr)
                ocorrenciasCampo()
            } catch (e: Exception) { Toast.makeText(this, "Erro ao anexar foto: ${e.message}", Toast.LENGTH_LONG).show() }
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
                        if (!exigirEdicao("pendencias", item)) { fotoPendente = null; pendencias(); return }
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
        if (!exigirGestor()) return
        startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/zip"
            putExtra(Intent.EXTRA_TITLE, "ColheitaGPS_Backup.zip")
        }, 305)
    }

    private fun escreverBackup(destino: Uri) {
        val preferencias = TeamPolicy.preferences.toList()
        val indices = JSONObject().put("_equipeId", teamStore!!.team)
        val tipos = JSONObject()
        for (nome in preferencias) {
            val dados = JSONObject()
            val tiposGrupo = JSONObject()
            for ((chave, valor) in getSharedPreferences(nome, 0).all) {
                dados.put(chave, teamStore!!.encode(valor?.toString() ?: ""))
                tiposGrupo.put(chave, when (valor) { is Boolean -> "Boolean"; is Int -> "Int"; is Long -> "Long"; is Float -> "Float"; else -> "String" })
            }
            indices.put(nome, dados)
            tipos.put(nome, tiposGrupo)
        }
        indices.put("_tipos", tipos)
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
        if (!exigirGestor()) return
        AlertDialog.Builder(this)
            .setTitle("Restaurar cópia de segurança")
            .setMessage("Os dados atuais poderão ser substituídos. Faça um backup antes de continuar.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Escolher ZIP") { _, _ ->
                startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/zip"
                }, 310)
            }.show()
    }

    private fun restaurarBackupZip(origem: Uri) {
        val preferenciasPermitidas = TeamPolicy.preferences
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
            val originalTeam = json.optString("_equipeId")
            if (originalTeam.isNotBlank() && originalTeam != teamStore!!.team) throw IOException("Este backup pertence a outra frente e turno.")
            if (originalTeam.isBlank() && teamAccess?.primary != true) throw IOException("Somente o administrador pode atribuir dados antigos sem equipe.")
            mapa?.salvar(); mapa = null
            teamSync?.stop(); teamSync = null
            for (nome in preferenciasPermitidas) {
                val objeto = json.optJSONObject(nome) ?: continue
                val editor = getSharedPreferences(nome, 0).edit()
                editor.clear()
                val chaves = objeto.keys()
                while (chaves.hasNext()) {
                    val chave = chaves.next()
                    val valor = if (originalTeam.isBlank()) teamStore!!.decodeLegacy(objeto.optString(chave)) else teamStore!!.decode(objeto.optString(chave))
                    // Os backups anteriores guardam todos os valores como texto.
                    val atual = getSharedPreferences(nome, 0).all[chave]
                    val type = json.optJSONObject("_tipos")?.optJSONObject(nome)?.optString(chave).orEmpty().ifBlank {
                        when (atual) { is Boolean -> "Boolean"; is Int -> "Int"; is Long -> "Long"; is Float -> "Float"; else -> "String" }
                    }
                    when (type) {
                        "Boolean" -> editor.putBoolean(chave, valor.toBoolean())
                        "Int" -> editor.putInt(chave, valor.toIntOrNull() ?: 0)
                        "Long" -> editor.putLong(chave, valor.toLongOrNull() ?: 0L)
                        "Float" -> editor.putFloat(chave, valor.toFloatOrNull() ?: 0f)
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
            if (teamSync == null && teamAccess != null && teamStore != null) openTeam(teamAccess!!, teamStore!!.team)
        }
    }

    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()

    private fun texto(s: String, size: Float = 15f, color:Int=palette.text) =
        TextView(this).apply {
            text = s
            textSize = size
            setTextColor(color)
            gravity = Gravity.CENTER_VERTICAL
        }

    private fun botao(s: String, acao: () -> Unit) =
        Button(this).apply {
            text = s
            textSize=15f;minimumHeight=dp(48);isAllCaps=false
            setTextColor(Color.WHITE);backgroundTintList=null
            background=GradientDrawable().apply {setColor(verde);cornerRadius=dp(14).toFloat()}
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
        AppTheme.apply(this)
        renderedNight=AppTheme.night(this)
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
        }
        carregarPermissoes()
    }
    override fun onResume() {
        super.onResume()
        StartupUpdates.resume(this)
        if(acessoConfirmado&&teamStore!=null) {
            if(renderedNight!=AppTheme.night(this)) {AppTheme.apply(this);renderedNight=AppTheme.night(this);themeScreen();return}
            if(telaAtual=="inicio") inicio() else if(telaAtual=="caminhoes") caminhoes()
        }
    }

    private fun tela(): FrameLayout =
        FrameLayout(this).apply {
            setBackgroundColor(palette.background)
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
            ?.filter { it.extension.equals("pdf", true) && prefs().contains(it.nameWithoutExtension) }
            ?.sortedBy { it.name }
            ?: emptyList()

    private fun nome(id: String) =
        prefs().getString(id, "Mapa PDF") ?: "Mapa PDF"

    private fun escolherTema() {
        AppTheme.choose(this) {renderedNight=AppTheme.night(this);themeScreen()}
    }

    private fun campoBusca(hintText:String,value:String,onChange:(String)->Unit):EditText = EditText(this).apply {AppTheme.field(this)

        hint=hintText;contentDescription=hintText;setSingleLine(true);textSize=16f
        setTextColor(palette.text);setHintTextColor(palette.muted);setPadding(dp(14),dp(12),dp(14),dp(12));minimumHeight=dp(48)
        background=GradientDrawable().apply {setColor(palette.surface);cornerRadius=dp(12).toFloat();setStroke(dp(1),palette.border)}
        inputType=android.text.InputType.TYPE_CLASS_TEXT;imeOptions=android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
        setText(value)
        addTextChangedListener(object:android.text.TextWatcher {
            override fun beforeTextChanged(text:CharSequence?,start:Int,count:Int,after:Int) {}
            override fun afterTextChanged(text:android.text.Editable?) {}
            override fun onTextChanged(text:CharSequence?,start:Int,before:Int,count:Int) {onChange(text.toString())}
        })
    }

    private fun cartao(titulo: String, subtitulo: String, acao: () -> Unit): View {
        val caixa = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            background = GradientDrawable().apply {
                setColor(palette.surface)
                cornerRadius = dp(16).toFloat()
            }
            setOnClickListener { acao() }
        }
        caixa.addView(texto(titulo,19f,palette.text))
        caixa.addView(texto(subtitulo, 13f, palette.muted))
        return caixa
    }

    private fun compartilharRelatorio(report:FieldReport) {
        val store=teamStore?:return;val uid=teamAccess?.uid?:return
        FieldReportSharing.share(this,report,store.cache) {
            acessoConfirmado&&teamStore?.team==store.team&&teamAccess?.uid==uid
        }
    }
    private fun relatorioGrupo(title:String,group:String,records:List<JSONObject>,period:String=""):FieldReport {
        val root=filesDir.canonicalPath+File.separator
        val pictures=records.flatMap {item->
            val paths=mutableListOf<String>()
            item.optJSONArray("fotos")?.let {photos->for(i in 0 until photos.length()) paths.add(photos.optString(i))}
            for(key in listOf("foto","fotoEstoque")) item.optString(key).takeIf {it.isNotBlank()}?.let(paths::add)
            paths.distinct().mapNotNull {path->
                val file=File(path)
                if(file.isFile&&file.canonicalPath.startsWith(root)) ReportPicture(file,RecordArchive.title(group,item)) else null
            }
        }
        return FieldReport(title,TeamPolicy.label(teamStore!!.team),period,listOf(FieldReports.group(title,group,records)),pictures)
    }
    private fun secoesOrdens(mapId:String?=null):ReportSection {
        val orders=getSharedPreferences("ordens_servico",0);val plots=getSharedPreferences("talhoes_por_os",0)
        val rows=orders.all.keys.filter {mapId==null||it==mapId}.sortedBy {nome(it)}.flatMap {id->
            val values=try {JSONArray(orders.getString(id,"[]"))} catch(_:Exception) {JSONArray()}
            (0 until values.length()).map {i->val number=values.optString(i);listOf(nome(id),number,plots.getString(id+"_"+number,"").orEmpty())}
        }
        return ReportSection("Ordens de serviço",listOf("Mapa","O.S.","Talhões"),rows)
    }
    private fun relatorioPadrao(title:String):FieldReport? {
        fun group(key:String,filter:(JSONObject)->Boolean={true})=relatorioGrupo(title,key,FieldReports.rows(dados(key)).filter(filter))
        return when(title) {
            "Pendências de manutenção" -> {
                val catalog=maintenanceCatalog()
                val visible=MaintenanceRecords.search(catalog,buscaEquipamento)
                val numbers=visible.map {it.number}.toSet()
                val summary=visible.map {listOf(it.number,it.open.toString(),it.completed.toString())}
                FieldReport(title,TeamPolicy.label(teamStore!!.team),sections=listOf(
                    ReportSection("Equipamentos",listOf("Equipamento","Pendências abertas","Concluídas"),summary),
                    FieldReports.group("Pendências abertas","pendencias",FieldReports.rows(dados("pendencias")).filter {!RecordArchive.archived(it)&&!it.optBoolean("concluida")&&MaintenanceRecords.number(it.optString("equipamento")) in numbers})))
            }
            "Histórico de pendências" -> group("pendencias") {RecordArchive.archived(it)||it.optBoolean("concluida")}
            "Peças a pedir" -> relatorioGrupo(title,"pendencias",FieldReports.rows(dadosPecas()).filter {!PartsRecords.historical(it)})
            "Histórico de peças" -> relatorioGrupo(title,"pendencias",FieldReports.rows(dadosPecas()).filter(PartsRecords::historical))
            "Registro de chuva" -> group("ocorrencias_campo",RainRecords::isRain)
            "Ocorrências de campo" -> group("ocorrencias_campo") {!RainRecords.isRain(it)&&!it.optBoolean("concluida")}
            "Histórico de ocorrências" -> group("ocorrencias_campo") {!RainRecords.isRain(it)&&it.optBoolean("concluida")}
            "Estoque de cana" -> group("estoques_salvos")
            "Histórico do estoque" -> group("historico_estoque")
            "Ordens de serviço" -> if(podeGerenciar()) FieldReport(title,TeamPolicy.label(teamStore!!.team),sections=listOf(secoesOrdens())) else null
            "Registros excluídos" -> if(podeGerenciar()) FieldReport(title,TeamPolicy.label(teamStore!!.team),sections=RecordArchive.labels.map {(key,label)->FieldReports.group(label,key,FieldReports.rows(todosRegistros(key)).filter(RecordArchive::archived))}) else null
            "Equipamentos excluídos" -> relatorioGrupo(title,"pendencias",FieldReports.rows(maintenanceCatalog()).filter {MaintenanceRecords.isEquipment(it)&&RecordArchive.archived(it)})
            else -> null
        }
    }

    private fun pagina(titulo: String, parent: () -> Unit = { inicio() }): LinearLayout {
        syncUiHandler.removeCallbacks(atualizarTurnoCaminhoes)
        renderedNight=AppTheme.night(this)
        themeScreen={inicio()}
        relatorioAtual = {relatorioPadrao(titulo)}
        if(titulo=="DM AGRO") relatorioAtual=null
        returnToParent = if (titulo == "DM AGRO") null else parent
        telaAtual = if (titulo == "DM AGRO") "inicio" else "pagina"
        teamSync?.available(hasWindowFocus() && mapa == null)
        refreshList = when (titulo) {
            "DM AGRO" -> ({ inicio() })
            "Pendências de manutenção" -> ({ pendencias() })
            "Pendências concluídas" -> ({ historicoPendencias() })
            "Ocorrências de campo" -> ({ ocorrenciasCampo() })
            "Histórico de ocorrências" -> ({ ocorrenciasCampo(true) })
            "Estoque de cana" -> ({ estoque() })
            else -> null
        }
        val base = tela()
        val coluna = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(16))
        }
        val topo = LinearLayout(this).apply {
            orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL
            setPadding(dp(12),dp(8),dp(8),dp(8))
            background=GradientDrawable().apply {setColor(palette.header);cornerRadius=dp(16).toFloat()}
        }
        if (telaAtual != "inicio") topo.addView(
            botaoVoltar { voltarTela() },
            LinearLayout.LayoutParams(dp(46), dp(46))
        )
        if (telaAtual == "inicio") topo.addView(DashboardUi(this).brand(), LinearLayout.LayoutParams(0, -2, 1f))
        else topo.addView(texto(titulo, 22f, Color.WHITE).apply {
            setPadding(dp(12), 0, 0, 0)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        topo.addView(TextView(this).apply {
            text = "⋮"; textSize = 26f; setTextColor(Color.WHITE)
            setPadding(dp(12), 0, dp(8), 0)
            contentDescription = "Mais opções"; minWidth = dp(48); minHeight = dp(48); gravity = Gravity.CENTER
            setOnClickListener { v -> PopupMenu(this@MainActivity, v).apply {
                if(relatorioAtual!=null&&titulo!="Estoque de cana") menu.add("Compartilhar imagem").setOnMenuItemClickListener {relatorioAtual?.invoke()?.let(::compartilharRelatorio);true}
                menu.add("Sair da conta").setOnMenuItemClickListener {
                    sairDaConta()
                    true
                }
                menu.add("Tema do aplicativo").setOnMenuItemClickListener {
                    escolherTema();true
                }
                menu.add("Verificar atualização").setOnMenuItemClickListener { startActivity(Intent(this@MainActivity, UpdateActivity::class.java)); true }
                menu.add("Sincronização").setOnMenuItemClickListener { syncDetails(); true }
                if(podeGerenciar()) menu.add("Registros excluídos").setOnMenuItemClickListener {registrosExcluidos();true}
                if (podeGerenciar()) menu.add("Notificações").setOnMenuItemClickListener { startActivity(Intent(this@MainActivity, NotificationSettingsActivity::class.java)); true }
                if (teamAccess?.primary == true) menu.add("Trocar equipe").setOnMenuItemClickListener { chooseTeam(teamAccess!!); true }
                menu.add("Início").setOnMenuItemClickListener { inicio(); true }
                menu.add("Mapas").setOnMenuItemClickListener { biblioteca(); true }
                menu.add("Pendências").setOnMenuItemClickListener { pendencias(); true }
                menu.add("Peças a pedir").setOnMenuItemClickListener { pecas(); true }
                menu.add("Ocorrências de campo").setOnMenuItemClickListener { ocorrenciasCampo(); true }
                menu.add("Registro de chuva").setOnMenuItemClickListener { chuvas(); true }
                if (podeGerenciar()) menu.add("Calendário").setOnMenuItemClickListener { calendario(); true }
                if (podeGerenciar()) menu.add("Caminhões").setOnMenuItemClickListener { abrirCaminhoes(); true }
                if (podeGerenciar() && titulo == "Estoque de cana") {
                    menu.add("Compartilhar estoque").setOnMenuItemClickListener { escolherCompartilhamentoEstoque(); true }
                    
                }
                if (podeGerenciar()) menu.add("Exportar cópia de segurança").setOnMenuItemClickListener { exportarBackup(); true }
                if (podeGerenciar()) menu.add("Restaurar cópia de segurança").setOnMenuItemClickListener { solicitarRestauracaoBackup(); true }
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
        mapa?.salvar(); mapa = null; status = null
        val access = teamAccess ?: return
        val store = teamStore ?: return
        val ui = DashboardUi(this)
        val coluna = pagina("DM AGRO")
        themeScreen={inicio()}
        raiz?.setBackgroundColor(palette.background)
        coluna.addView(ui.text("Olá, ${DashboardUi.name(access, sessionName)}", 28f, bold = true).apply {
            setPadding(0, dp(26), 0, dp(3))
        })

        val teamPanel=LinearLayout(this).apply {orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(0,dp(8),0,dp(8))}
        val teamInfo = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        teamInfo.addView(ui.text(TeamPolicy.label(store.team), 16f, bold = true))
        syncLabel = ui.text("● ${DashboardUi.status(syncMessage)}", 13f, ui.mint).also {
            it.setPadding(0, dp(7), 0, 0); it.setOnClickListener { syncDetails() }; teamInfo.addView(it)
        }
        teamPanel.addView(teamInfo, LinearLayout.LayoutParams(0, -2, 1f))
        if (access.primary) teamPanel.addView(ui.text("Trocar", 14f, ui.mint, true).apply {
            gravity = Gravity.CENTER; minHeight = dp(48); setPadding(dp(12), 0, 0, 0)
            contentDescription = "Trocar equipe"; setOnClickListener { chooseTeam(access) }
        })
        coluna.addView(teamPanel, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        if (access.primary && access.profile["nome"]?.toString().isNullOrBlank()) coluna.addView(botao("Definir meu nome") { definirNome() })
        val verse=DailyVerse.forDate()
        val overview=LinearLayout(this).apply {orientation=if(resources.configuration.fontScale>=1.45f||resources.configuration.screenWidthDp<360) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL;isBaselineAligned=false}
        val weather=WeatherActivity.homeCard(this,store.team)
        val reading=ui.panel {
            AlertDialog.Builder(this).setTitle("Versículo do dia • ${verse.reference}").setMessage(verse.text).setPositiveButton("Fechar",null).show()
        }.apply {
            orientation=LinearLayout.VERTICAL
            addView(ui.text("Versículo do dia",12f,palette.accent,true))
            addView(ui.text(verse.text,14f).apply {setPadding(0,dp(6),0,dp(4));maxLines=4;ellipsize=android.text.TextUtils.TruncateAt.END})
            addView(ui.text(verse.reference,12f,ui.mint))
        }
        if(overview.orientation==LinearLayout.HORIZONTAL) {
            overview.addView(weather,LinearLayout.LayoutParams(0,-1,1f))
            overview.addView(reading,LinearLayout.LayoutParams(0,-1,1f).apply {marginStart=dp(10)})
        } else {
            overview.addView(weather,LinearLayout.LayoutParams(-1,-2))
            overview.addView(reading,LinearLayout.LayoutParams(-1,-2).apply {topMargin=dp(10)})
        }
        coluna.addView(overview,LinearLayout.LayoutParams(-1,-2).apply {topMargin=dp(12)})
        coluna.addView(ui.text("Ferramentas", 23f, bold = true).apply { setPadding(0, dp(24), 0, dp(3)) })
        val open = MaintenanceRecords.summaries(maintenanceCatalog()).sumOf { it.open }
        val tiles = mutableListOf(
            DashboardUi.Tile("Mapas", FieldIcon.MAP, { biblioteca() }),
            DashboardUi.Tile("Pendências", FieldIcon.PENDING, { pendencias() }, open),
            DashboardUi.Tile("Peças a pedir", FieldIcon.PART, { pecas() }),
            DashboardUi.Tile("Chuva por turno", FieldIcon.RAIN, { chuvas() })
        )
        if (access.manages) tiles.addAll(listOf(
            DashboardUi.Tile("Estoque", FieldIcon.STOCK, { estoque() }),
            DashboardUi.Tile("Ordens de serviço", FieldIcon.ORDER, { paginaOrdens() })
        ))
        tiles.add(DashboardUi.Tile("Ocorrências", FieldIcon.OCCURRENCE, { ocorrenciasCampo() }))
        if (access.manages) tiles.addAll(listOf(
            DashboardUi.Tile("Caminhões", FieldIcon.TRUCK, { abrirCaminhoes() }),
            DashboardUi.Tile("Calendário", FieldIcon.CALENDAR, { calendario() })
        ))
        ui.grid(coluna, tiles)
        if (access.manages) coluna.addView(ui.panel { startActivity(Intent(this, AdminActivity::class.java)) }.apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            addView(FieldIconView(this@MainActivity, FieldIcon.PEOPLE), LinearLayout.LayoutParams(dp(36), dp(36)))
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL; setPadding(dp(14), 0, 0, 0)
                addView(ui.text(if (access.primary) "Gerenciar usuários" else "Aprovar minha equipe", 17f, bold = true))
                addView(ui.text(if (access.primary) "Administrador" else "Supervisor da equipe", 13f, ui.mint))
            })
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(18) })
        if (access.primary && store.hasLegacy()) coluna.addView(botao("Dados antigos deste aparelho") {
            AlertDialog.Builder(this).setTitle("Preservar e importar dados anteriores")
                .setMessage("Importe somente registros de ${TeamPolicy.label(store.team)}. Os originais e registros atuais serão preservados.")
                .setNegativeButton("Cancelar", null).setPositiveButton("Importar") { _, _ ->
                    try { store.importLegacy(); teamSync?.retry(); inicio() }
                    catch (e: Exception) { Toast.makeText(this, e.message ?: "Falha na importação", Toast.LENGTH_LONG).show() }
                }.show()
        })
    }

    private fun definirNome() {
        if (teamAccess?.primary != true) return
        val input = campo("Seu nome")
        AlertDialog.Builder(this).setTitle("Como deseja ser chamado?").setView(input)
            .setNegativeButton("Cancelar", null).setPositiveButton("Salvar") { _, _ ->
                val name = input.text.toString().trim()
                if (name.length !in 3..100) { Toast.makeText(this, "Informe seu nome, de 3 a 100 caracteres.", Toast.LENGTH_LONG).show(); return@setPositiveButton }
                FirebaseFirestore.getInstance().document("usuarios/${teamAccess!!.uid}")
                    .set(mapOf("nome" to name), com.google.firebase.firestore.SetOptions.merge())
                    .addOnSuccessListener { if (!isDestroyed) { teamAccess = teamAccess!!.copy(profile = teamAccess!!.profile + ("nome" to name)); inicio() } }
                    .addOnFailureListener { Toast.makeText(this, "Não foi possível salvar. Confira as regras desta atualização e a conexão.", Toast.LENGTH_LONG).show() }
            }.show()
    }

    private fun paginaOrdens() {
        if (!exigirGestor()) return
        val coluna = pagina("Ordens de serviço")
        themeScreen={paginaOrdens()}
        coluna.addView(texto("Selecione um mapa para consultar ou editar suas O.S.", 14f).apply {
            setPadding(0, dp(16), 0, dp(8))
        })
        coluna.addView(botao("O.S. encerradas / histórico") {historicoOrdensEncerradas()})
        val todos = arquivos()
        if (todos.isEmpty()) coluna.addView(texto("Nenhum mapa importado."))
        for (f in todos) {
            val id = f.nameWithoutExtension
            coluna.addView(cartao(nome(id), "Abrir ordens de serviço") { ordens(id) },
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        }
    }

    private fun registros(): JSONArray = dados("movimentos")

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
        pausarSincronizacao()
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
                    2 -> comRegistroAtual("estoques_salvos",obj.optString("id")) { current->remover("estoques_salvos",current) {
                        if(progresso().optString("id")==obj.optString("id")) getSharedPreferences("controle_colheita",0).edit().remove("progresso").commit()
                        estoque()
                    } }
                }
            }.show()
    }
    private fun estoque() {
        if (!exigirGestor()) return
        val coluna=pagina("Estoque de cana")
        themeScreen={estoque()}
        coluna.addView(botao("📅 Abrir calendário") {calendario(parent={estoque()})})
        coluna.addView(botao("+ Novo estoque") {editarProgressoColheita(true)})
        coluna.addView(botao("+ Cadastrar vários lotes") {cadastrarLotes()})
        coluna.addView(botao("Compartilhar vários estoques") {selecionarEstoques()})
        coluna.addView(texto("ESTOQUES SALVOS",15f))
        val results=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;tag="resultadosEstoque"}
        fun render() {
            results.removeAllViews()
            val all=listaEstoques()
            val rows=(all.length()-1 downTo 0).mapNotNull {all.optJSONObject(it)}.filter {
                QuickFind.matches(buscaEstoque,it.optString("os"),it.optString("talhoes"))
            }
            for(item in rows) {
                val lote=item.optString("os").ifBlank {"Sem lote"}
                val detalhe="Talhões: ${item.optString("talhoes")} • Área: ${item.optString("areaRestante")} ha • Produtividade: ${item.optString("produtividade")} t/ha"
                results.addView(cartao(lote,detalhe) {abrirEstoqueSalvo(item)},LinearLayout.LayoutParams(-1,-2).apply {topMargin=dp(10)})
            }
            if(rows.isEmpty()) results.addView(texto(if(all.length()==0) "Nenhum estoque cadastrado ainda." else "Nenhum lote encontrado. Altere ou apague a busca.",14f))
        }
        coluna.addView(campoBusca("Buscar lote ou talhão",buscaEstoque) {buscaEstoque=it;render()},LinearLayout.LayoutParams(-1,-2).apply {topMargin=dp(10)})
        coluna.addView(results)
        refreshList={render()};render()
    }

    private var mapaEstoqueSelecionado: File? = null
    private fun cadastrarLotes() {
        if(!exigirGestor()) return
        pausarSincronizacao()
        val ui=AccessUi(this,attach=false)
        val content=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setPadding(dp(16),dp(8),dp(16),dp(16)) }
        val rows=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        content.addView(texto("Preencha um cartão por lote. Até 20 lotes por cadastro.",14f,palette.text));content.addView(rows)
        val inputs=mutableListOf<Pair<LinearLayout,List<EditText>>>()
        fun addLot() {
            if(inputs.size>=20) { Toast.makeText(this,"Salve estes lotes antes de adicionar outros.",Toast.LENGTH_LONG).show();return }
            val card=ui.card().apply { elevation=0f;background=ui.background(0xFFF1F6F1.toInt()) }
            rows.addView(card,LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(12) })
            val fields=listOf("Lote","Talhões","Área (ha)","Produtividade (t/ha)").map { ui.field(card,it) }
            ui.button(card,"Remover este lote",true) { rows.removeView(card);inputs.removeAll { it.first===card } }
            inputs.add(card to fields)
        }
        addLot()
        ui.button(content,"+ Adicionar outro lote",true) { addLot() }
        val dialog=AlertDialog.Builder(this).setTitle("Cadastrar vários lotes").setView(ScrollView(this).apply { addView(content) })
            .setNegativeButton("Cancelar",null).setPositiveButton("Salvar todos",null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val drafts=inputs.map { (_,fields)->StockBatch.Draft(fields[0].text.toString(),fields[1].text.toString(),fields[2].text.toString(),fields[3].text.toString()) }
                val records=try { StockBatch.records(drafts,dataHoje()) } catch(error:IllegalArgumentException) {
                    Toast.makeText(this,error.message,Toast.LENGTH_LONG).show();return@setOnClickListener
                }
                synchronized(teamStore!!.lock) { val all=listaEstoques();records.forEach { all.put(it) };guardar("estoques_salvos",all) }
                dialog.dismiss();estoque()
                AlertDialog.Builder(this).setTitle("${records.size} lote(s) salvo(s)").setMessage("Deseja compartilhar esses lotes juntos?")
                    .setNegativeButton("Agora não",null).setPositiveButton("Compartilhar") { _,_->compartilharLotes(records) }.show()
            }
        }
        dialog.show()
    }
    private fun selecionarEstoques() {
        if(!exigirGestor()) return
        val all=listaEstoques()
        if(all.length()==0) { Toast.makeText(this,"Cadastre um estoque antes de compartilhar.",Toast.LENGTH_LONG).show();return }
        val records=(all.length()-1 downTo 0).mapNotNull { all.optJSONObject(it) }
        val selected=BooleanArray(records.size)
        val labels=records.map { "Lote ${it.optString("os").ifBlank { "não informado" }} • ${it.optString("areaRestante")} ha\nTalhões: ${it.optString("talhoes")}" }.toTypedArray()
        val dialog=AlertDialog.Builder(this).setTitle("Selecione os estoques")
            .setMultiChoiceItems(labels,selected) { _,index,value->selected[index]=value }
            .setNegativeButton("Cancelar",null).setNeutralButton("Selecionar todos",null).setPositiveButton("Continuar",null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener { for(i in selected.indices) {selected[i]=true;dialog.listView.setItemChecked(i,true)} }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val ids=records.filterIndexed { index,_->selected[index] }.map { it.optString("id") }.toSet()
                val chosen=StockBatch.selected(all,ids)
                if(chosen.isEmpty()) { Toast.makeText(this,"Selecione pelo menos um lote.",Toast.LENGTH_LONG).show();return@setOnClickListener }
                if(chosen.any { !StockBatch.complete(it) }) { Toast.makeText(this,"Complete lote, talhões, área e produtividade dos estoques selecionados antes de compartilhar.",Toast.LENGTH_LONG).show();return@setOnClickListener }
                dialog.dismiss();compartilharLotes(chosen)
            }
        };dialog.show()
    }
    private fun compartilharLotes(records:List<JSONObject>) {
        if(!exigirGestor()) return
        startActivity(Intent(this,StockShareActivity::class.java)
            .putExtra("team",teamStore!!.team)
            .putStringArrayListExtra("ids",ArrayList(records.map { it.optString("id") })))
    }
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

    private fun mapasDisponiveisEstoque():List<PaintedMapPicker.Item> =
        teamStore?.let(PaintedMapPicker::items).orEmpty()

    private fun escolherCompartilhamentoEstoque() {
        if (!exigirGestor()) return
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
            compartilharEstoqueMensagem()
            return
        }
        val mapas = mapasDisponiveisEstoque()
        if (mapas.isEmpty()) {
            mapaEstoqueSelecionado = null
            compartilharEstoqueMensagem()
            return
        }
        val nomes = mapas.map { it.title } + "Sem foto do mapa"
        AlertDialog.Builder(this).setTitle("Foto para o estoque")
            .setItems(nomes.toTypedArray()) { _, indice ->
                mapaEstoqueSelecionado = mapas.getOrNull(indice)?.file
                compartilharEstoqueMensagem()
            }.setNegativeButton("Cancelar", null).show()
    }

    private fun progresso(): JSONObject = try {
        JSONObject(getSharedPreferences("controle_colheita", 0).getString("progresso", "{}") ?: "{}")
    } catch (_: Exception) { JSONObject() }

    private fun editarProgressoColheita(novo: Boolean = false) {
        pausarSincronizacao()
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
            val rascunho = JSONObject(atual.toString()).put("os", os.text.toString())
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
                        val nomes = mapas.map { it.title }.toTypedArray()
                        AlertDialog.Builder(this).setTitle("Mapa pintado salvo")
                            .setItems(nomes) { _, indice ->
                                val obj = progresso().put("fotoEstoque", mapas[indice].file.absolutePath)
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
                val obj = JSONObject(atual.toString()).put("os", os.text.toString())
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
        themeScreen={historicoEstoque()}
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

    private fun compartilharEstoqueMensagem() {
        if(!exigirGestor()) return
        val store=teamStore?:return;val uid=teamAccess?.uid?:return
        val record=JSONObject(progresso().toString()).apply {
            remove("fotoEstoque")
            mapaEstoqueSelecionado?.takeIf {it.isFile}?.let {put("fotoEstoque",it.path)}
        }
        StockSharing.share(this,store,listOf(record),textoEstoque()) {
            acessoConfirmado&&podeGerenciar()&&teamStore?.team==store.team&&teamAccess?.uid==uid
        }
    }

    private fun salvarImagemMapa() {
        val arquivo = if(chaveAtual.isNotBlank()) File(filesDir,"mapa_relatorio_$chaveAtual.png").takeIf {it.isFile} else arquivoMapaAtual()
        if (arquivo == null) { Toast.makeText(this, "Abra e pinte um mapa primeiro", Toast.LENGTH_LONG).show(); return }
        imagemPendente = arquivo
        startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); type = "image/png"
            putExtra(Intent.EXTRA_TITLE, nomeOriginalMapaEstoque(arquivo) + ".png")
        }, 304)
    }

    private fun novaMovimentacao() {
        pausarSincronizacao()
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
        turno.setSelection(TeamPolicy.shifts.indexOf(teamStore!!.team.substringAfterLast('_')))
        turno.isEnabled = false
        val quantidade = EditText(this).apply {AppTheme.field(this)

            hint = "Quantidade em toneladas"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val os = EditText(this).apply {AppTheme.field(this)
 hint = "Número da O.S. (opcional)" }
        val obs = EditText(this).apply {AppTheme.field(this)
 hint = "Talhão / observação" }
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
                    put("turno", teamStore!!.team.substringAfterLast('_'))
                    put("quantidade", valor)
                    put("os", os.text.toString())
                    put("observacao", obs.text.toString())
                    put("data", java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR")).format(java.util.Date()))
                }
                val dados = registros()
                dados.put(objeto)
                guardar("movimentos",dados)
                estoque()
            }.setNegativeButton("Cancelar", null).show()
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

    private fun calendario(dataInicial: String = dataHoje(), parent: () -> Unit = { inicio() }) {
        if (!exigirGestor()) return
        val coluna = pagina("Calendário", parent)
        themeScreen={calendario(dataInicial,parent)}
        refreshList = { calendario(dataInicial, parent) }
        coluna.addView(botao("📅 Escolher data: $dataInicial") {
            val partes = dataInicial.split("/")
            val hoje = java.util.Calendar.getInstance()
            val dia = partes.getOrNull(0)?.toIntOrNull() ?: hoje.get(java.util.Calendar.DAY_OF_MONTH)
            val mes = partes.getOrNull(1)?.toIntOrNull()?.minus(1) ?: hoje.get(java.util.Calendar.MONTH)
            val ano = partes.getOrNull(2)?.toIntOrNull() ?: hoje.get(java.util.Calendar.YEAR)
            android.app.DatePickerDialog(this, { _, a, m, d ->
                calendario(String.format(Locale("pt", "BR"), "%02d/%02d/%04d", d, m + 1, a), parent)
            }, ano, mes, dia).show()
        })
        coluna.addView(texto("Registros de $dataInicial",18f,palette.text))
        val estoques = listaEstoques()
        var encontrados = 0
        coluna.addView(texto("ESTOQUES", 16f, palette.accent))
        for (i in estoques.length() - 1 downTo 0) {
            val item = estoques.optJSONObject(i) ?: continue
            if (dataRegistro(item) != dataInicial) continue
            encontrados++
            val lote = item.optString("os").ifBlank { "Sem lote" }
            coluna.addView(cartao(lote, "Talhões: ${item.optString("talhoes")} • Área: ${item.optString("areaRestante")} ha") {
                abrirEstoqueSalvo(item)
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        }
        coluna.addView(texto("PENDÊNCIAS", 16f, palette.accent))
        val pend = dados("pendencias")
        for (i in pend.length() - 1 downTo 0) {
            val item = pend.optJSONObject(i) ?: continue
            if (dataRegistro(item) != dataInicial) continue
            encontrados++
            coluna.addView(cartao(item.optString("equipamento", "Pendência") +
                if (MaintenanceRecords.isArchived(item)) " • Excluída" else "", item.optString("descricao")) {
                comRegistroAtual("pendencias", item.optString("id")) { opcoesPendencia(it) }
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        }
        coluna.addView(texto("OCORRÊNCIAS DE CAMPO", 16f, palette.accent))
        val ocorrencias = dados("ocorrencias_campo")
        for (i in ocorrencias.length() - 1 downTo 0) {
            val item = ocorrencias.optJSONObject(i) ?: continue
            if (RainRecords.isRain(item)) continue
            if (dataRegistro(item) != dataInicial) continue
            encontrados++
            val idx = i
            coluna.addView(cartao(item.optString("tipo"), item.optString("localizacao")) { comRegistroAtual("ocorrencias_campo", item.optString("id")) { opcoesOcorrencia(it) } },
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        }
        coluna.addView(texto("CHUVA DO TURNO", 16f, palette.accent))
        for (item in RainRecords.readings(ocorrencias).filter { it.optString("dataCadastro") == dataInicial }) {
            encontrados++
            coluna.addView(cartao("Chuva • ${RainRecords.label(item)}", item.optString("observacaoChuva")) {
                comRegistroAtual(RainRecords.GROUP, item.optString("id")) { index ->
                    dados(RainRecords.GROUP).optJSONObject(index)?.let { detalhesChuva(it) }
                }
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        }
        val day=RainRecords.date(dataInicial)
        val calendarTrucks=if(day==null) dados("caminhoes") else TruckRecords.until(dados("caminhoes"),java.time.LocalDateTime.of(day,java.time.LocalTime.MAX))
        val trucks=if(day==null) emptyList() else TruckRecords.rows(calendarTrucks,day)
        coluna.addView(texto("CAMINHÕES",16f,palette.accent))
        for(item in trucks) {
            encontrados++
            coluna.addView(cartao("Caminhão ${item.optString("numero")}",TruckRecords.details(item)) {
                comRegistroAtual("caminhoes",item.optString("id")) {editarCaminhao(it)}
            },LinearLayout.LayoutParams(-1,-2).apply {topMargin=dp(8)})
        }
        relatorioAtual={
            val groups=listOf("estoques_salvos","pendencias","ocorrencias_campo","movimentos")
            val sections=groups.map {group->FieldReports.group(RecordArchive.labels[group]?:group,group,FieldReports.rows(dados(group)).filter {dataRegistro(it)==dataInicial})}+
                FieldReports.trucks(TeamPolicy.label(teamStore!!.team),dataInicial,trucks,TruckRecords.summary(calendarTrucks,day)).sections
            FieldReport("Registros do dia",TeamPolicy.label(teamStore!!.team),dataInicial,sections)
        }
        coluna.addView(botao("Compartilhar o dia por imagem") {relatorioAtual?.invoke()?.let(::compartilharRelatorio)})
        if (encontrados == 0) coluna.addView(texto("Nenhum registro nesta data.", 14f))
    }

    private fun textoPendencia(obj: JSONObject): String = buildString {
        append("PENDÊNCIA DE MANUTENÇÃO - COLHEITA GPS\n")
        append("Data: ").append(dataRegistro(obj)).append("\n")
        append("O.S.: ").append(obj.optString("osPendencia").ifBlank { "Não informada" }).append("\n")
        append("Equipamento: ").append(obj.optString("equipamento")).append("\n")
        append("Pendência: ").append(obj.optString("descricao")).append("\n")
        append("Situação: ").append(if (MaintenanceRecords.isArchived(obj)) "Excluída, preservada no histórico" else if (obj.optBoolean("concluida", false)) "Concluída" else "Pendente")
        if (MaintenanceRecords.isArchived(obj)) append("\nExcluída em: ${obj.optString("arquivadoEm", "data não registrada")}\nPor: ${obj.optString("arquivadoPorNome", "gestor da equipe")}")
        if (obj.optBoolean("concluida", false) && obj.optString("dataConclusao").isNotBlank()) {
            append("\nConcluída em: ").append(obj.optString("dataConclusao"))
        }
    }

    private fun fotosPendencia(obj: JSONObject): List<File> {
        val arr = obj.optJSONArray("fotos")
        val caminhos = if (arr != null && arr.length() > 0)
            (0 until arr.length()).map { arr.optString(it) }
        else listOf(obj.optString("foto"))
        return caminhos.filter { it.isNotBlank() }.map { File(it) }.filter { it.isFile }
    }

    private fun compartilharPendenciaMenu(obj: JSONObject) {
        compartilharRelatorio(relatorioGrupo("Pendência de manutenção","pendencias",listOf(obj)))
    }

    private fun maintenanceCatalog(): JSONArray = synchronized(teamStore?.lock ?: this) {
        val prefs = getSharedPreferences("registros_v3", 0)
        val raw = try { JSONArray(prefs.getString("pendencias", "[]")) } catch (_: Exception) { JSONArray() }
        val merged = MaintenanceRecords.merge(MaintenanceRecords.issues(raw), raw)
        if (merged.toString() != raw.toString()) prefs.edit().putString("pendencias", merged.toString()).apply()
        merged
    }
    private fun arquivarManutencao(item: JSONObject) = MaintenanceRecords.archive(item,
        teamAccess!!.uid, teamAccess!!.profile["nome"]?.toString().orEmpty(),
        java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR")).format(java.util.Date()))
    private fun archiveDate()=java.text.SimpleDateFormat("dd/MM/yyyy HH:mm",Locale("pt","BR")).format(java.util.Date())
    private fun restaurarEquipamento(number:String) {
        if(!exigirGestor()) return
        pausarSincronizacao()
        val all=maintenanceCatalog()
        val catalog=(0 until all.length()).mapNotNull {all.optJSONObject(it)}.firstOrNull {MaintenanceRecords.isEquipment(it)&&MaintenanceRecords.number(it.optString("equipamento"))==number&&RecordArchive.archived(it)}?:return
        val legacy=catalog.optString("exclusaoEquipamentoId").isBlank()
        val options=CheckBox(this).apply {
            text=if(legacy) "Restaurar também todas as pendências excluídas deste equipamento" else "Restaurar também as pendências excluídas junto com ele"
            isChecked=!legacy;setPadding(dp(18),dp(8),dp(18),dp(12))
        }
        AlertDialog.Builder(this).setTitle("Restaurar equipamento $number?")
            .setMessage("O equipamento voltará à lista ativa desta equipe. Informações, fotos locais e histórico serão preservados. Pendências já concluídas continuarão concluídas."+
                if(legacy) "\n\nNesta exclusão antiga, não é possível separar quais pendências foram excluídas junto. Marque a opção somente se quiser trazer todas as excluídas." else "")
            .setView(options).setNegativeButton("Cancelar",null).setPositiveButton("Restaurar") { _,_->
                if(!exigirGestor()) return@setPositiveButton
                try {
                    synchronized(teamStore!!.lock) {
                        val current=todosRegistros("pendencias")
                        val restored=MaintenanceRecords.restoreEquipment(current,number,options.isChecked,teamAccess!!.uid,teamAccess!!.profile["nome"]?.toString().orEmpty(),archiveDate())
                        getSharedPreferences("registros_v3",0).edit().putString("pendencias",restored.toString()).commit()
                    }
                    pendenciasEquipamento(number)
                } catch(error:Exception) {Toast.makeText(this,error.message?:"Não foi possível restaurar. Atualize a lista.",Toast.LENGTH_LONG).show()}
            }.show()
    }
    private fun registrosExcluidos() {
        if(!exigirGestor()) return
        val column=pagina("Registros excluídos")
        themeScreen={registrosExcluidos()}
        refreshList={registrosExcluidos()}
        column.addView(texto("${TeamPolicy.label(teamStore!!.team)}\nRestaure um item para voltar a editá-lo. Dados e fotos locais permanecem preservados.",14f))
        var count=0
        for((group,label) in RecordArchive.labels) {
            val all=todosRegistros(group)
            for(i in all.length()-1 downTo 0) {
                val record=all.optJSONObject(i)?:continue
                if(!RecordArchive.archived(record)) continue
                count++
                column.addView(cartao(RecordArchive.title(group,record),"$label • ${record.optString("arquivadoEm","Data não registrada")}\nToque para consultar e restaurar") {
                    detalhesExcluido(group,record.optString("id"))
                },LinearLayout.LayoutParams(-1,-2).apply {topMargin=dp(10)})
            }
        }
        if(count==0) column.addView(texto("Nenhum registro excluído preservado nesta equipe.",15f))
    }
    private fun detalhesExcluido(group:String,id:String) {
        if(!exigirGestor()) return
        pausarSincronizacao()
        val all=todosRegistros(group)
        val record=(0 until all.length()).mapNotNull {all.optJSONObject(it)}.firstOrNull {it.optString("id")==id&&RecordArchive.archived(it)}?:return
        AlertDialog.Builder(this).setTitle(RecordArchive.title(group,record)).setMessage(RecordArchive.description(record).ifBlank {"Registro preservado no histórico."})
            .setNegativeButton("Fechar",null)
            .setNeutralButton("Ver fotos") { _,_->
                val photo=StockShareFiles.photo(filesDir,record)
                if(photo!=null) AlertDialog.Builder(this).setView(ImageView(this).apply {setImageBitmap(StockShareFiles.decode(photo,1200));adjustViewBounds=true}).setPositiveButton("Fechar",null).show()
                else mostrarFotos(record)
            }
            .setPositiveButton("Restaurar") { _,_->
                if(group=="pendencias"&&MaintenanceRecords.isEquipment(record)) restaurarEquipamento(MaintenanceRecords.number(record.optString("equipamento")))
                else if(group=="historico_os") restaurarOrdemServico(id)
                else confirmarRestauracao(group,id)
            }.show()
    }
    private fun confirmarRestauracao(group:String,id:String) {
        if(!exigirGestor()) return
        AlertDialog.Builder(this).setTitle("Restaurar registro?").setMessage("O item voltará à lista correspondente desta equipe. O estado de conclusão e o histórico serão preservados. Se for uma pendência de equipamento excluído, o equipamento também voltará à lista.")
            .setNegativeButton("Cancelar",null).setPositiveButton("Restaurar") { _,_->
                if(!exigirGestor()) return@setPositiveButton
                synchronized(teamStore!!.lock) {
                    var all=todosRegistros(group)
                    val target=(0 until all.length()).mapNotNull {all.optJSONObject(it)}.firstOrNull {it.optString("id")==id&&RecordArchive.archived(it)}?:return@setPositiveButton
                    if(group=="pendencias"&&!PartsRecords.isPart(target)) {
                        val number=MaintenanceRecords.number(target.optString("equipamento"))
                        if(MaintenanceRecords.archivedEquipment(all).contains(number)) all=MaintenanceRecords.restoreEquipment(all,number,false,teamAccess!!.uid,teamAccess!!.profile["nome"]?.toString().orEmpty(),archiveDate())
                    }
                    for(i in 0 until all.length()) all.optJSONObject(i)?.takeIf {it.optString("id")==id}?.let {
                        all.put(i,RecordArchive.restore(it,teamAccess!!.uid,teamAccess!!.profile["nome"]?.toString().orEmpty(),archiveDate()))
                    }
                    getSharedPreferences(if(group=="movimentos") "controle_colheita" else "registros_v3",0).edit().putString(group,all.toString()).commit()
                }
                registrosExcluidos()
            }.show()
    }

    private fun opcoesEquipamento(equipamento: String, historico: Boolean = false) {
        val number = MaintenanceRecords.number(equipamento)
        val opcoes = mutableListOf(if (historico) "Ver histórico do equipamento" else "Abrir pendências", "Histórico de pendências")
        if (podeGerenciar() && !historico) { opcoes.add("Corrigir número do equipamento"); opcoes.add("Excluir equipamento e suas pendências") }
        if(podeGerenciar()&&historico) opcoes.add("Restaurar equipamento")
        AlertDialog.Builder(this).setTitle("Equipamento $number").setItems(opcoes.toTypedArray()) { _, escolha ->
            when (escolha) {
                0 -> pendenciasEquipamento(number, historico)
                1 -> pendenciasEquipamento(number, true)
                2 -> if(historico) restaurarEquipamento(number) else corrigirEquipamento(number)
                3 -> excluirEquipamento(number)
            }
        }.show()
    }

    private fun corrigirEquipamento(number: String) {
        if (!exigirGestor()) return
        pausarSincronizacao()
        val input = campo("Número correto do equipamento", number)
        val dialog = AlertDialog.Builder(this).setTitle("Corrigir equipamento $number").setView(input)
            .setMessage("As pendências e fotos continuarão vinculadas ao equipamento. A correção ficará registrada no histórico.")
            .setNegativeButton("Cancelar", null).setPositiveButton("Salvar correção", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                try {
                    synchronized(teamStore!!.lock) {
                        val all = maintenanceCatalog()
                        for (i in 0 until all.length()) all.optJSONObject(i)?.let { item ->
                            item.put("__syncBase", teamSync?.baseRevision("pendencias", item.optString("id")) ?: 0L)
                        }
                        val updated = MaintenanceRecords.correctNumber(all, number, input.text.toString(), teamAccess!!.uid,
                            teamAccess!!.profile["nome"]?.toString().orEmpty(),
                            java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR")).format(java.util.Date()))
                        getSharedPreferences("registros_v3", 0).edit().putString("pendencias", updated.toString()).apply()
                    }
                    dialog.dismiss(); pendenciasEquipamento(MaintenanceRecords.number(input.text.toString()))
                } catch (error: IllegalArgumentException) { input.error = error.message }
            }
        }
        dialog.show()
    }

    private fun excluirEquipamento(equipamento: String) {
        if (!exigirGestor()) return
        pausarSincronizacao()
        val number = MaintenanceRecords.number(equipamento)
        val related = MaintenanceRecords.issues(maintenanceCatalog())
        val count = (0 until related.length()).mapNotNull { related.optJSONObject(it) }
            .count { MaintenanceRecords.number(it.optString("equipamento")) == number && !MaintenanceRecords.isArchived(it) }
        AlertDialog.Builder(this).setTitle("Excluir equipamento $number?")
            .setMessage("O equipamento e suas $count pendência(s) sairão da lista ativa de ${TeamPolicy.label(teamStore!!.team)}.\n\nTudo permanecerá disponível no histórico, com as informações e fotos deste aparelho.")
            .setNegativeButton("Cancelar", null).setPositiveButton("Excluir e manter no histórico") { _, _ ->
                if (!exigirGestor()) return@setPositiveButton
                synchronized(teamStore!!.lock) {
                    val all = maintenanceCatalog()
                    for (i in 0 until all.length()) all.optJSONObject(i)?.let { item ->
                        if (MaintenanceRecords.number(item.optString("equipamento")) == number)
                            item.put("__syncBase", teamSync?.baseRevision("pendencias", item.optString("id")) ?: 0L)
                    }
                    val updated = MaintenanceRecords.archiveEquipment(all, number, teamAccess!!.uid,
                        teamAccess!!.profile["nome"]?.toString().orEmpty(),
                        java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR")).format(java.util.Date()))
                    getSharedPreferences("registros_v3", 0).edit().putString("pendencias", updated.toString()).apply()
                }
                pendencias()
            }.show()
    }

    private fun historicoEquipamentos() {
        val coluna = pagina("Equipamentos excluídos", parent = { pendencias() })
        themeScreen={historicoEquipamentos()}
        refreshList = { historicoEquipamentos() }
        val equipment = MaintenanceRecords.archivedEquipment(maintenanceCatalog())
        for (number in equipment) coluna.addView(cartao("Equipamento $number", if(podeGerenciar()) "Excluído • consultar ou restaurar" else "Excluído • toque para consultar o histórico") {
            opcoesEquipamento(number, true)
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        if (equipment.isEmpty()) coluna.addView(texto("Nenhum equipamento excluído nesta equipe.", 15f))
    }
    private fun cadastrarEquipamento() {
        pausarSincronizacao()
        val input = campo("Número do equipamento (ex.: 21569)")
        AlertDialog.Builder(this).setTitle("Cadastrar equipamento da equipe").setView(input)
            .setNegativeButton("Cancelar", null).setPositiveButton("Salvar") { _, _ ->
                val number = MaintenanceRecords.number(input.text.toString())
                if (number.isBlank() || number.length > 80) {
                    Toast.makeText(this, "Informe um equipamento com até 80 caracteres.", Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }
                synchronized(teamStore?.lock ?: this) {
                    val all = maintenanceCatalog()
                    if(MaintenanceRecords.archivedEquipment(all).contains(number)) {
                        if(podeGerenciar()) restaurarEquipamento(number) else Toast.makeText(this,"Equipamento excluído. Peça ao supervisor para restaurar.",Toast.LENGTH_LONG).show()
                        return@setPositiveButton
                    }
                    if (MaintenanceRecords.ensureEquipment(all, number)) getSharedPreferences("registros_v3", 0).edit().putString("pendencias", all.toString()).apply()
                }
                pendenciasEquipamento(number)
            }.show()
    }
    private fun pendencias() {
        val coluna=pagina("Pendências de manutenção")
        themeScreen={pendencias()}
        coluna.addView(botao("+ Cadastrar equipamento") {cadastrarEquipamento()})
        coluna.addView(botao("+ Nova pendência") {editarPendencia(-1)})
        coluna.addView(texto("EQUIPAMENTOS DA EQUIPE",16f,palette.text))
        val results=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;tag="resultadosEquipamentos"}
        fun render() {
            results.removeAllViews()
            val all=maintenanceCatalog()
            val equipment=MaintenanceRecords.search(all,buscaEquipamento)
            for(item in equipment) {
                val description=if(item.open==0) "Sem pendências abertas • ${item.completed} concluídas" else "${item.open} em aberto • ${item.completed} concluídas"
                val card=cartao("Equipamento ${item.number}","$description\nToque para abrir opções") {opcoesEquipamento(item.number)} as LinearLayout
                card.addView(TextView(this).apply {
                    text=if(item.urgent>0) "● URGENTE • ${item.urgent} pendência(s)" else if(item.open>0) "● ${item.open} pendência(s) aberta(s)" else "✓ Sem pendências abertas"
                    contentDescription=if(item.urgent>0) "Alerta: equipamento ${item.number} tem ${item.urgent} pendências urgentes" else if(item.open>0) "Alerta: equipamento ${item.number} tem ${item.open} pendências abertas" else "Equipamento ${item.number} sem pendências abertas"
                    textSize=14f;setTypeface(null,Typeface.BOLD);setTextColor(if(item.open>0) palette.danger else palette.accent);setPadding(0,dp(10),0,0)
                },0)
                results.addView(card,LinearLayout.LayoutParams(-1,-2).apply {topMargin=dp(10)})
            }
            if(equipment.isEmpty()) results.addView(texto(if(MaintenanceRecords.summaries(all).isEmpty()) "Cadastre um equipamento para acompanhar as pendências desta equipe." else "Nenhum equipamento encontrado. Altere ou apague a busca.",14f))
        }
        coluna.addView(campoBusca("Buscar equipamento, pendência ou O.S.",buscaEquipamento) {buscaEquipamento=it;render()},LinearLayout.LayoutParams(-1,-2).apply {topMargin=dp(10)})
        coluna.addView(results)
        coluna.addView(botao("✓ Histórico de concluídas e excluídas") {historicoPendencias()})
        coluna.addView(botao("Equipamentos excluídos") {historicoEquipamentos()})
        refreshList={render()};render()
    }
    private fun pendenciasEquipamento(equipamento: String, concluidas: Boolean = false) {
        val number = MaintenanceRecords.number(equipamento)
        val catalog = maintenanceCatalog()
        val archivedEquipment = (0 until catalog.length()).mapNotNull { catalog.optJSONObject(it) }
            .any { MaintenanceRecords.isEquipment(it) && MaintenanceRecords.number(it.optString("equipamento")) == number && MaintenanceRecords.isArchived(it) }
        val coluna = pagina(if (concluidas) "Histórico $number" else "Equipamento $number",
            parent = { if (concluidas && archivedEquipment) historicoEquipamentos() else if (concluidas) pendenciasEquipamento(number) else pendencias() })
        themeScreen={pendenciasEquipamento(number,concluidas)}
        refreshList = { pendenciasEquipamento(number, concluidas) }
        relatorioAtual={relatorioGrupo(if(concluidas) "Histórico do equipamento $number" else "Pendências do equipamento $number","pendencias",FieldReports.rows(dados("pendencias")).filter {
            MaintenanceRecords.number(it.optString("equipamento"))==number&&(if(concluidas) RecordArchive.archived(it)||it.optBoolean("concluida") else !RecordArchive.archived(it)&&!it.optBoolean("concluida"))
        })}
        if (archivedEquipment) coluna.addView(texto("Equipamento excluído • registros preservados no histórico", 15f, palette.muted))
        if(archivedEquipment&&podeGerenciar()) coluna.addView(botao("Restaurar equipamento") {restaurarEquipamento(number)})
        if (!concluidas && !archivedEquipment) coluna.addView(botao("+ Adicionar pendência") {
            editarPendencia(-1,equipamentoInicial=number)
        })
        val arr = dados("pendencias")
        var total = 0
        val ordered=(arr.length()-1 downTo 0).mapNotNull {arr.optJSONObject(it)}.sortedByDescending {MaintenanceRecords.urgent(it)}
        for (obj in ordered) {
            val archived = MaintenanceRecords.isArchived(obj)
            if ((if (concluidas) !obj.optBoolean("concluida", false) && !archived else obj.optBoolean("concluida", false) || archived) ||
                MaintenanceRecords.number(obj.optString("equipamento")) != number) continue
            total++
            coluna.addView(cartao((if(MaintenanceRecords.urgent(obj)) "🔴 URGENTE • " else "• ") + obj.optString("descricao").ifBlank { "Sem descrição" },
                if (archived) "Excluída • toque para consultar" else if (concluidas) "Toque para consultar ou reabrir" else "Toque para ver opções") {
                comRegistroAtual("pendencias", obj.optString("id")) { opcoesPendencia(it) }
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        }
        if (total == 0) coluna.addView(texto(if (concluidas) "Nenhuma pendência concluída ou excluída neste equipamento." else if (archivedEquipment) "Nenhuma pendência ativa neste equipamento excluído." else "Nenhuma pendência aberta. O equipamento continua salvo para novos registros.", 14f))
        coluna.addView(botao(if (concluidas) "Voltar às pendências abertas" else "✓ Histórico deste equipamento") {
            pendenciasEquipamento(number, !concluidas)
        })
        if (!concluidas && !archivedEquipment && podeGerenciar()) coluna.addView(botao("Excluir equipamento e suas pendências") { excluirEquipamento(number) })
    }
    private fun historicoPendencias() {
        val coluna = pagina("Histórico de pendências", parent = { pendencias() })
        themeScreen={historicoPendencias()}
        refreshList = { historicoPendencias() }
        val arr = dados("pendencias")
        var total = 0
        for (i in arr.length() - 1 downTo 0) {
            val obj = arr.optJSONObject(i) ?: continue
            if (!obj.optBoolean("concluida", false) && !MaintenanceRecords.isArchived(obj)) continue
            total++
            coluna.addView(cartao(obj.optString("equipamento", "Equipamento"),
                obj.optString("descricao") + if (MaintenanceRecords.isArchived(obj)) " • Excluída em ${obj.optString("arquivadoEm", "data não registrada")}" else " • Concluída em " + obj.optString("dataConclusao", "data não registrada")) {
                comRegistroAtual("pendencias", obj.optString("id")) { opcoesPendencia(it) }
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        }
        if (total == 0) coluna.addView(texto("Nenhuma pendência concluída ou excluída.", 14f))
    }

    private fun mudarSituacaoPendencia(idx: Int) {
        pausarSincronizacao()
        val arr = dados("pendencias")
        val obj = arr.optJSONObject(idx) ?: return
        if (!exigirEdicao("pendencias", obj)) return
        val concluida = obj.optBoolean("concluida", false)
        AlertDialog.Builder(this)
            .setTitle(if (concluida) "Reabrir pendência?" else "Concluir pendência?")
            .setMessage(if (concluida) "A pendência voltará à lista de pendentes. Fotos e informações serão preservadas."
                else "Confirmar que a manutenção foi concluída? O registro ficará no histórico, com as fotos e informações.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton(if (concluida) "Reabrir" else "Concluir") { _, _ ->
                obj.put("concluida", !concluida)
                if (concluida) obj.remove("dataConclusao")
                else obj.put("dataConclusao", java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR")).format(java.util.Date()))
                guardar("pendencias", arr)
                pendenciasEquipamento(obj.optString("equipamento"))
            }.show()
    }

    private fun opcoesPendencia(idx: Int) {
        pausarSincronizacao()
        val arr = dados("pendencias")
        val obj = arr.optJSONObject(idx) ?: return
        val concluida = obj.optBoolean("concluida", false)
        if (MaintenanceRecords.isArchived(obj)) {
            val dialog=AlertDialog.Builder(this).setTitle("Pendência excluída • ${obj.optString("equipamento")}")
                .setMessage("${obj.optString("descricao")}\n\nExcluída em: ${obj.optString("arquivadoEm", "data não registrada")}\nPor: ${obj.optString("arquivadoPorNome", "gestor da equipe")}")
                .setNegativeButton("Fechar",null)
            if(podeGerenciar()) dialog.setPositiveButton("Restaurar pendência") { _,_->confirmarRestauracao("pendencias",obj.optString("id")) }
                .setNeutralButton("Compartilhar / fotos") { _,_->AlertDialog.Builder(this).setItems(arrayOf("Compartilhar","Ver fotos")) { _,index->if(index==0) compartilharPendenciaMenu(obj) else mostrarFotos(obj)}.show()}
            else dialog.setPositiveButton("Compartilhar") { _,_->compartilharPendenciaMenu(obj)}.setNeutralButton("Ver fotos") { _,_->mostrarFotos(obj)}
            dialog.show()
            return
        }
        if (teamSync?.canEdit("pendencias", obj.optString("id")) == false) {
            AlertDialog.Builder(this).setTitle(obj.optString("equipamento", "Pendência"))
                .setItems(arrayOf("Compartilhar", "Ver fotos")) { _, option ->
                    if (option == 0) compartilharPendenciaMenu(obj) else mostrarFotos(obj)
                }.show()
            return
        }
        val opcoes = listOf("Editar pendência", if (concluida) "↩ Reabrir pendência" else "✓ Concluir pendência",
            "Excluir pendência", "Compartilhar imagem", "Adicionar foto", "Ver fotos")
            .mapIndexed { index, label -> label to index }.filter { it.second != 2 || podeGerenciar() }
        AlertDialog.Builder(this).setTitle(obj.optString("equipamento", "Pendência"))
            .setItems(opcoes.map { it.first }.toTypedArray()) { _, escolha ->
                when (opcoes[escolha].second) {
                    0 -> editarPendencia(idx)
                    1 -> mudarSituacaoPendencia(idx)
                    2 -> remover("pendencias", idx) { pendenciasEquipamento(obj.optString("equipamento"), concluida) }
                    3 -> compartilharPendenciaMenu(obj)
                    4 -> {
                        fotoPendente = obj.optString("id")
                        startActivityForResult(Intent(Intent.ACTION_GET_CONTENT).apply { type = "image/*" }, 301)
                    }
                    5 -> {
                        val existentes = fotosPendencia(obj)
                        if (existentes.isEmpty()) Toast.makeText(this, "Sem foto", Toast.LENGTH_SHORT).show()
                        else AlertDialog.Builder(this).setTitle("Fotos da pendência")
                            .setItems(existentes.mapIndexed { i, _ -> "Foto ${i + 1}" }.toTypedArray()) { _, fotoIdx ->
                                AlertDialog.Builder(this).setView(ImageView(this).apply {
                                    setImageBitmap(BitmapFactory.decodeFile(existentes[fotoIdx].absolutePath))
                                    adjustViewBounds = true
                                }).setPositiveButton("Fechar", null).show()
                            }.show()
                    }
                }
            }.show()
    }
    private fun dadosPecas():JSONArray = synchronized(teamStore?.lock ?: this) {
        PartsRecords.rows(maintenanceCatalog()).apply {
            for(i in 0 until length()) optJSONObject(i)?.let { it.put("__syncBase",teamSync?.baseRevision("pendencias",it.optString("id")) ?: 0L) }
        }
    }
    private fun guardarPecas(parts:JSONArray) = synchronized(teamStore?.lock ?: this) {
        getSharedPreferences("registros_v3",0).edit().putString("pendencias",PartsRecords.merge(parts,maintenanceCatalog()).toString()).apply()
    }
    private fun rascunhoPecaPrefs()=getSharedPreferences("rascunho_peca_${teamStore!!.team}_${teamAccess!!.uid}",0)
    private fun lerRascunhoPeca():JSONObject?=try { rascunhoPecaPrefs().getString("json",null)?.let(::JSONObject)?.also { draft ->
        val pending=draft.optString("cameraPeca")
        val file=File(pending)
        if(pending.isNotBlank()&&file.isFile&&file.length()>0&&file.canonicalPath.startsWith(filesDir.canonicalPath+File.separator)) {
            val photos=draft.optJSONArray("fotos") ?: JSONArray()
            if((0 until photos.length()).none { photos.optString(it)==pending }) photos.put(pending)
            draft.put("fotos",photos); draft.remove("cameraPeca")
        }
    } } catch(_:Exception) { null }
    private fun pecas(historico:Boolean=false) {
        val coluna=pagina(if(historico) "Histórico de peças" else "Peças a pedir",parent={if(historico) pecas() else inicio()})
        themeScreen={pecas(historico)}
        refreshList={pecas(historico)}
        coluna.addView(texto(TeamPolicy.label(teamStore!!.team),16f,palette.accent))
        if(!historico) {
            coluna.addView(botao("+ Cadastrar peça a pedir") { editarPeca() })
            if(lerRascunhoPeca()!=null) coluna.addView(botao("Continuar rascunho de peça") { editarPeca(retomar=true) })
        }
        coluna.addView(texto("Códigos e pedidos são compartilhados com a equipe. As fotos ficam no celular e podem ser enviadas pelo botão Compartilhar.",14f,palette.muted))
        val all=dadosPecas(); var count=0
        for(i in all.length()-1 downTo 0) {
            val item=all.optJSONObject(i) ?: continue
            if(PartsRecords.historical(item)!=historico) continue
            count++
            val title=item.optString("codigoPeca").ifBlank { "Peça sem código" }+" • ${item.optInt("quantidadePeca",1)} un."
            val state=if(MaintenanceRecords.isArchived(item)) "Excluída, mantida no histórico" else PartsRecords.states[item.optString("situacaoPeca")] ?: "A pedir"
            val reference=item.optString("equipamentoReferencia").takeIf { it.isNotBlank() }?.let { "\nEquipamento: $it" }.orEmpty()
            coluna.addView(cartao(title,item.optString("nomePeca",item.optString("descricao"))+reference+"\n$state") {
                pausarSincronizacao()
                val current=dadosPecas(); val selected=(0 until current.length()).mapNotNull { current.optJSONObject(it) }.firstOrNull { it.optString("id")==item.optString("id") }
                if(selected!=null) opcoesPeca(selected,historico) else pecas(historico)
            },LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(12) })
        }
        if(count==0) coluna.addView(texto(if(historico) "Nenhuma peça recebida ou excluída." else "Nenhuma peça a pedir nesta equipe.",15f))
        if(!historico) coluna.addView(botao("Histórico de peças recebidas e excluídas") { pecas(true) })
    }
    private fun podeEditarPeca(item:JSONObject)= !MaintenanceRecords.isArchived(item) &&
        (podeGerenciar()||item.optString("autorPecaUid")==teamAccess?.uid) && teamSync?.canEdit("pendencias",item.optString("id"))!=false
    private fun opcoesPeca(item:JSONObject,historico:Boolean) {
        val actions=mutableListOf<Pair<String,()->Unit>>()
        if(podeGerenciar()&&MaintenanceRecords.isArchived(item)) actions.add("Restaurar pedido de peça" to {confirmarRestauracao("pendencias",item.optString("id"))})
        if(podeEditarPeca(item)) actions.add("Editar peça / situação" to { editarPeca(item.optString("id")) })
        actions.add("Ver fotos" to { verFotosPeca(item) })
        actions.add("Compartilhar pedido por imagem" to { compartilharPeca(item) })
        if(podeGerenciar()&&!MaintenanceRecords.isArchived(item)) actions.add("Excluir e manter no histórico" to {
            AlertDialog.Builder(this).setTitle("Excluir pedido de peça?").setMessage("A peça sairá da lista ativa e permanecerá no histórico com suas informações e fotos locais.")
                .setNegativeButton("Cancelar",null).setPositiveButton("Excluir") { _,_->
                    if(!exigirGestor()) return@setPositiveButton
                    val parts=dadosPecas()
                    for(i in 0 until parts.length()) parts.optJSONObject(i)?.let { if(it.optString("id")==item.optString("id")) parts.put(i,arquivarManutencao(it)) }
                    guardarPecas(parts); pecas(historico)
                }.show()
        })
        AlertDialog.Builder(this).setTitle(item.optString("codigoPeca").ifBlank { "Pedido de peça" })
            .setItems(actions.map { it.first }.toTypedArray()) { _,index->actions[index].second() }.show()
    }
    private fun editarPeca(id:String?=null,retomar:Boolean=false) {
        pausarSincronizacao()
        val all=dadosPecas()
        val existing=id?.let { key->(0 until all.length()).mapNotNull { all.optJSONObject(it) }.firstOrNull { it.optString("id")==key } }
        if(id!=null&&(existing==null||!podeEditarPeca(existing))) { Toast.makeText(this,"Somente o autor ou o gestor pode editar esta peça ativa.",Toast.LENGTH_LONG).show(); return }
        if(!retomar&&id==null&&lerRascunhoPeca()!=null) {
            AlertDialog.Builder(this).setTitle("Existe um rascunho de peça")
                .setPositiveButton("Continuar rascunho") { _,_->editarPeca(retomar=true) }
                .setNegativeButton("Descartar e criar outro") { _,_->rascunhoPecaPrefs().edit().clear().apply(); editarPeca() }.show(); return
        }
        val draft=if(retomar) lerRascunhoPeca() ?: return else existing?.let { JSONObject(it.toString()).put("_novo",false) }
            ?: JSONObject().put("id",java.util.UUID.randomUUID().toString()).put("categoriaRegistro","peca").put("_novo",true)
                .put("autorPecaUid",teamAccess!!.uid).put("autorPecaNome",teamAccess!!.profile["nome"]?.toString().orEmpty()).put("dataCadastro",dataHoje())
        if(MaintenanceRecords.isArchived(draft)) return
        pecaRascunho=draft
        val body=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(20),dp(8),dp(20),dp(10)) }
        fun input(label:String,value:String,multi:Boolean=false):EditText {
            body.addView(texto(label,14f,0xFF153F28.toInt()).apply { setPadding(0,dp(10),0,dp(4)) })
            return campo(label,value).apply {
                setSingleLine(!multi); setTextColor(0xFF153F28.toInt()); setHintTextColor(0xFF64756B.toInt()); textSize=16f
                backgroundTintList=ColorStateList.valueOf(verde)
                body.addView(this)
            }
        }
        val code=input("Código / número da peça",draft.optString("codigoPeca"))
        val description=input("Nome ou descrição da peça",draft.optString("nomePeca",draft.optString("descricao")),true)
        val quantity=input("Quantidade",draft.optString("quantidadePeca","1")).apply { inputType=android.text.InputType.TYPE_CLASS_NUMBER }
        val reference=input("Equipamento de referência (opcional)",draft.optString("equipamentoReferencia"))
        val state=if(podeGerenciar()) Spinner(this).apply {
            adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,PartsRecords.states.values.toList())
            setSelection(PartsRecords.states.keys.indexOf(draft.optString("situacaoPeca","a_pedir")).coerceAtLeast(0))
            body.addView(texto("Situação do pedido",14f,0xFF153F28.toInt())); body.addView(this)
        } else null
        val photoCount=texto("",14f,0xFF153F28.toInt()); body.addView(photoCount)
        fun capture() {
            draft.put("codigoPeca",code.text.toString()).put("nomePeca",description.text.toString()).put("descricao",description.text.toString()).put("quantidadePeca",quantity.text.toString()).put("equipamentoReferencia",reference.text.toString())
                .put("situacaoPeca",state?.let { PartsRecords.states.keys.elementAt(it.selectedItemPosition) } ?: draft.optString("situacaoPeca","a_pedir"))
            pecaRascunho=draft; rascunhoPecaPrefs().edit().putString("json",draft.toString()).apply()
        }
        atualizarFotosPeca={photoCount.text="Fotos neste celular: ${draft.optJSONArray("fotos")?.length() ?: 0} (até 6)"}; atualizarFotosPeca?.invoke()
        body.addView(botao("Escolher fotos no celular") {
            capture()
            if((draft.optJSONArray("fotos")?.length() ?: 0)>=6) { Toast.makeText(this,"Limite de 6 fotos por peça.",Toast.LENGTH_LONG).show(); return@botao }
            startActivityForResult(Intent(Intent.ACTION_GET_CONTENT).apply { type="image/*"; putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true) },8801)
        })
        body.addView(botao("Tirar foto da peça") {
            capture()
            if((draft.optJSONArray("fotos")?.length() ?: 0)>=6) { Toast.makeText(this,"Limite de 6 fotos por peça.",Toast.LENGTH_LONG).show(); return@botao }
            val file=File(filesDir,"pecas/${java.util.UUID.randomUUID()}.jpg").apply { parentFile?.mkdirs(); createNewFile() }
            try {
                val uri=androidx.core.content.FileProvider.getUriForFile(applicationContext,"$packageName.files",file)
                fotoCameraPeca=file; draft.put("cameraPeca",file.absolutePath); rascunhoPecaPrefs().edit().putString("json",draft.toString()).apply()
                startActivityForResult(Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE).apply {
                    putExtra(android.provider.MediaStore.EXTRA_OUTPUT,uri); addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    clipData=ClipData.newRawUri("Foto de peça",uri)
                },8802)
            } catch(_:Exception) { file.delete(); Toast.makeText(this,"Câmera indisponível. Você pode escolher uma foto do celular.",Toast.LENGTH_LONG).show() }
        })
        body.addView(botao("Ver / remover foto do rascunho") {
            val photos=draft.optJSONArray("fotos") ?: JSONArray()
            if(photos.length()==0) { Toast.makeText(this,"Nenhuma foto anexada.",Toast.LENGTH_SHORT).show(); return@botao }
            AlertDialog.Builder(this).setTitle("Fotos anexadas").setItems((0 until photos.length()).map { "Foto ${it+1}" }.toTypedArray()) { _,index->
                AlertDialog.Builder(this).setTitle("Foto ${index+1}").setPositiveButton("Ver") { _,_->mostrarFotoPeca(File(photos.optString(index))) }
                    .setNeutralButton("Remover do pedido") { _,_->draft.put("fotos",JSONArray().apply { for(i in 0 until photos.length()) if(i!=index) put(photos.get(i)) }); capture(); atualizarFotosPeca?.invoke() }.setNegativeButton("Voltar",null).show()
            }.show()
        })
        val dialog=AlertDialog.Builder(this).setTitle(if(draft.optBoolean("_novo",true)) "Cadastrar peça a pedir" else "Editar peça")
            .setView(ScrollView(this).apply { addView(body) }).setNegativeButton("Cancelar") { _,_->rascunhoPecaPrefs().edit().clear().apply(); pecaRascunho=null; atualizarFotosPeca=null }
            .setPositiveButton("Salvar",null).create()
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            try {
                capture()
                val clean=JSONObject(draft.toString()).apply { remove("_novo"); remove("cameraPeca") }
                val item=PartsRecords.build(clean,code.text.toString(),description.text.toString(),quantity.text.toString(),reference.text.toString(),draft.optString("situacaoPeca","a_pedir"),teamAccess!!.uid,teamAccess!!.profile["nome"]?.toString().orEmpty(),dataHoje())
                val parts=dadosPecas()
                if(!draft.optBoolean("_novo",true)&&!podeEditarPeca(item)) return@setOnClickListener
                if(!colocarRegistro(parts,if(draft.optBoolean("_novo",true)) -1 else 0,item)) return@setOnClickListener
                guardarPecas(parts); rascunhoPecaPrefs().edit().clear().apply(); pecaRascunho=null; atualizarFotosPeca=null
                dialog.dismiss(); pecas()
            } catch(error:IllegalArgumentException) { Toast.makeText(this,error.message,Toast.LENGTH_LONG).show() }
        } }; dialog.show()
    }
    private fun fotosPeca(item:JSONObject):List<File> {
        val all=item.optJSONArray("fotos") ?: JSONArray()
        return (0 until all.length()).map { File(all.optString(it)) }.filter { it.isFile&&it.length()>0&&it.canonicalPath.startsWith(filesDir.canonicalPath+File.separator) }
    }
    private fun mostrarFotoPeca(file:File) {
        val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true }; BitmapFactory.decodeFile(file.absolutePath,bounds)
        var sample=1; while(maxOf(bounds.outWidth,bounds.outHeight)/sample>1600) sample*=2
        val bitmap=BitmapFactory.decodeFile(file.absolutePath,BitmapFactory.Options().apply { inSampleSize=sample }) ?: return
        AlertDialog.Builder(this).setView(ImageView(this).apply { setImageBitmap(bitmap); adjustViewBounds=true }).setPositiveButton("Fechar",null).show()
    }
    private fun verFotosPeca(item:JSONObject) {
        val photos=fotosPeca(item)
        if(photos.isEmpty()) { Toast.makeText(this,"Fotos ficam no celular de quem anexou. Peça o compartilhamento ao autor.",Toast.LENGTH_LONG).show(); return }
        AlertDialog.Builder(this).setTitle("Fotos da peça").setItems(photos.indices.map { "Foto ${it+1}" }.toTypedArray()) { _,index->mostrarFotoPeca(photos[index]) }.show()
    }
    private fun compartilharPeca(item:JSONObject) {
        compartilharRelatorio(relatorioGrupo("Pedido de peça","pendencias",listOf(item)))
    }
    private fun resultadoFotoPeca(code:Int,result:Int,data:Intent?) {
        if(teamStore==null||teamAccess==null) return
        val draft=pecaRascunho ?: lerRascunhoPeca() ?: return
        val photos=draft.optJSONArray("fotos") ?: JSONArray()
        if(result==RESULT_OK) try {
            if(code==8802) {
                val file=fotoCameraPeca ?: draft.optString("cameraPeca").takeIf { it.isNotBlank() }?.let(::File)
                if(file!=null&&file.isFile&&file.length()>0&&(0 until photos.length()).none { photos.optString(it)==file.absolutePath }) photos.put(file.absolutePath)
            } else {
                val uris=mutableListOf<Uri>()
                data?.clipData?.let { clip->for(i in 0 until clip.itemCount) uris.add(clip.getItemAt(i).uri) } ?: data?.data?.let { uris.add(it) }
                for(uri in uris.take((6-photos.length()).coerceAtLeast(0))) {
                    val extension=contentResolver.getType(uri)?.let { android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
                        ?.takeIf { it in setOf("jpg","jpeg","png","webp","gif","heic","heif") } ?: "jpg"
                    val file=File(filesDir,"pecas/${java.util.UUID.randomUUID()}.$extension").apply { parentFile?.mkdirs() }
                    try {
                        contentResolver.openInputStream(uri)?.use { input->file.outputStream().use { output->
                            val buffer=ByteArray(8192); var total=0
                            while(true) { val count=input.read(buffer); if(count<0) break; total+=count; require(total<=20*1024*1024) { "A foto excede 20 MB." }; output.write(buffer,0,count) }
                        } }
                        if(file.isFile&&file.length()>0) photos.put(file.absolutePath)
                    } catch(error:Exception) { file.delete(); throw error }
                }
            }
        } catch(error:Exception) { Toast.makeText(this,error.message ?: "Não foi possível anexar a foto.",Toast.LENGTH_LONG).show() }
        draft.put("fotos",photos); draft.remove("cameraPeca"); pecaRascunho=draft
        rascunhoPecaPrefs().edit().putString("json",draft.toString()).apply(); atualizarFotosPeca?.invoke()
    }

    private fun chuvas() {
        val store = teamStore ?: return
        val coluna = pagina("Registro de chuva")
        themeScreen={chuvas()}
        refreshList = { chuvas() }
        coluna.addView(texto(TeamPolicy.label(store.team), 17f, palette.accent).apply {
            setPadding(0, dp(16), 0, dp(8))
        })
        val leituras = RainRecords.readings(dados(RainRecords.GROUP))
        val hoje = leituras.firstOrNull { it.optString("dataCadastro") == dataHoje() }
        coluna.addView(cartao("HOJE • ${dataHoje()}", hoje?.let(RainRecords::label) ?: "Chuva ainda não registrada") {
            formularioChuva(dataHoje())
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        coluna.addView(botao("+ Registrar chuva por data") {
            val hojeData = java.util.Calendar.getInstance()
            DatePickerDialog(this, { _, ano, mes, dia ->
                formularioChuva(String.format(Locale.ROOT, "%02d/%02d/%04d", dia, mes + 1, ano))
            }, hojeData.get(java.util.Calendar.YEAR), hojeData.get(java.util.Calendar.MONTH),
                hojeData.get(java.util.Calendar.DAY_OF_MONTH)).apply {
                datePicker.maxDate = System.currentTimeMillis()
            }.show()
        })
        coluna.addView(texto("Um acumulado por dia e turno. Informe 0 quando não chover. Ao corrigir, substitua pelo acumulado completo do turno.",
            14f, palette.muted).apply { setPadding(0, dp(8), 0, dp(16)) })
        coluna.addView(texto("HISTÓRICO DO TURNO", 17f, palette.accent))
        if (leituras.isEmpty()) coluna.addView(texto("Nenhum registro de chuva nesta equipe.", 15f))
        for (leitura in leituras) {
            coluna.addView(cartao("${leitura.optString("dataCadastro")} • ${RainRecords.label(leitura)}",
                leitura.optString("observacaoChuva").ifBlank { "Sem observação" } +
                    "\nRegistrado por: " + leitura.optString("registradoPorNome", "Usuário")) {
                comRegistroAtual(RainRecords.GROUP, leitura.optString("id")) { index ->
                    dados(RainRecords.GROUP).optJSONObject(index)?.let { registro -> detalhesChuva(registro) }
                }
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        }
    }

    private fun podeEditarChuva(registro: JSONObject) =
        (teamAccess?.manages == true || registro.optString("registradoPorUid") == teamAccess?.uid) &&
            teamSync?.canEdit(RainRecords.GROUP, registro.optString("id")) != false

    private fun detalhesChuva(registro: JSONObject) {
        val dialogo = AlertDialog.Builder(this).setTitle("Chuva • ${registro.optString("dataCadastro")}")
            .setMessage("${TeamPolicy.label(teamStore!!.team)}\nAcumulado do turno: ${RainRecords.label(registro)}\n" +
                "Registrado por: ${registro.optString("registradoPorNome")}\n\n" +
                registro.optString("observacaoChuva").ifBlank { "Sem observação" })
            .setNegativeButton("Fechar", null)
        val editavel = podeEditarChuva(registro)
        if (editavel) dialogo.setPositiveButton("Corrigir registro", null)
        if(podeGerenciar()) dialogo.setNeutralButton("Excluir") { _,_->comRegistroAtual(RainRecords.GROUP,registro.optString("id")) {index->remover(RainRecords.GROUP,index) {chuvas()}}}
        val exibido = dialogo.show()
        exibido.setOnDismissListener { teamSync?.available(hasWindowFocus() && mapa == null) }
        if (editavel) exibido.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            exibido.setOnDismissListener(null)
            exibido.dismiss()
            formularioChuva(registro.optString("dataCadastro"))
        }
    }

    private fun formularioChuva(data: String) {
        val store = teamStore ?: return
        val access = teamAccess ?: return
        val dia = RainRecords.date(data) ?: return
        pausarSincronizacao()
        val all=todosRegistros(RainRecords.GROUP)
        val deleted=(0 until all.length()).mapNotNull {all.optJSONObject(it)}.firstOrNull {it.optString("id")==RainRecords.id(dia)&&RecordArchive.archived(it)}
        if(deleted!=null) {
            if(podeGerenciar()) detalhesExcluido(RainRecords.GROUP,deleted.optString("id"))
            else Toast.makeText(this,"Esse registro de chuva foi excluído. Peça ao supervisor para restaurar.",Toast.LENGTH_LONG).show()
            return
        }
        val atual = RainRecords.readings(dados(RainRecords.GROUP)).firstOrNull { it.optString("id") == RainRecords.id(dia) }
        if (atual != null && !podeEditarChuva(atual)) {
            detalhesChuva(atual)
            return
        }
        val corpo = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(8), dp(22), dp(12))
        }
        corpo.addView(texto("${TeamPolicy.label(store.team)}\nData: $data", 16f, palette.accent))
        corpo.addView(texto("Chuva acumulada do turno (mm)", 16f).apply { setPadding(0, dp(18), 0, dp(4)) })
        val volume = campo("Ex.: 12,5 ou 0", atual?.optString("chuvaMm")?.replace('.', ',') ?: "").apply {
            setTextColor(palette.text);setHintTextColor(palette.muted)
            backgroundTintList=null
            setSingleLine(true)
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            filters = arrayOf(android.text.InputFilter.LengthFilter(7))
        }
        corpo.addView(volume)
        corpo.addView(texto("Observação (opcional)", 16f).apply { setPadding(0, dp(14), 0, dp(4)) })
        val observacao = campo("Ex.: chuva durante a madrugada", atual?.optString("observacaoChuva") ?: "").apply {
            setTextColor(palette.text);setHintTextColor(palette.muted)
            backgroundTintList=null
            filters = arrayOf(android.text.InputFilter.LengthFilter(1000))
        }
        corpo.addView(observacao)
        corpo.addView(texto("Registre o total deste turno. Não some novamente o que já foi informado.", 14f,
            palette.muted).apply { setPadding(0, dp(14), 0, 0) })
        val dialogo = AlertDialog.Builder(this)
            .setCustomTitle(texto(if (atual == null) "Registrar chuva" else "Corrigir chuva", 22f).apply {
                setPadding(dp(22), dp(22), dp(22), dp(12)); setTypeface(null, Typeface.BOLD)
            })
            .setView(ScrollView(this).apply { addView(corpo) })
            .setNegativeButton("Cancelar", null).setPositiveButton("Salvar", null).create()
        dialogo.setOnShowListener {
            dialogo.window?.setBackgroundDrawable(GradientDrawable().apply {
                setColor(palette.surface); cornerRadius = dp(22).toFloat()
            })
            dialogo.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(palette.muted)
            dialogo.getButton(AlertDialog.BUTTON_POSITIVE).apply {
                setTextColor(palette.accent)
                setOnClickListener {
                    if (RainRecords.amount(volume.text.toString()) == null) {
                        volume.error = "Informe de 0 a 9999,99 mm, com até duas casas decimais."
                        volume.requestFocus()
                        return@setOnClickListener
                    }
                    val novo = RainRecords.create(data, volume.text.toString(), observacao.text.toString(), store.team,
                        atual?.optString("registradoPorUid") ?: access.uid,
                        atual?.optString("registradoPorNome") ?: access.profile["nome"]?.toString().orEmpty())
                    val registro = atual?.let { JSONObject(it.toString()) } ?: JSONObject()
                    novo.keys().forEach { key -> registro.put(key, novo.get(key)) }
                    val registros = dados(RainRecords.GROUP)
                    if (!colocarRegistro(registros, if (atual == null) -1 else 0, registro)) return@setOnClickListener
                    guardar(RainRecords.GROUP, registros)
                    dialogo.dismiss()
                    chuvas()
                    Toast.makeText(this@MainActivity, "Chuva salva. O envio à equipe ocorrerá quando houver conexão.", Toast.LENGTH_LONG).show()
                }
            }
        }
        dialogo.setOnDismissListener { teamSync?.available(hasWindowFocus() && mapa == null) }
        dialogo.show()
    }

    private fun ocorrenciasCampo(concluidas: Boolean = false) {
        val coluna = pagina(if (concluidas) "Histórico de ocorrências" else "Ocorrências de campo", parent = { if (concluidas) ocorrenciasCampo() else inicio() })
        themeScreen={ocorrenciasCampo(concluidas)}
        refreshList = { ocorrenciasCampo(concluidas) }
        if (!concluidas) coluna.addView(botao("+ Nova ocorrência") {editarOcorrencia(-1)})
        val arr = dados("ocorrencias_campo")
        var quantidade = 0
        for (i in arr.length() - 1 downTo 0) {
            val obj = arr.optJSONObject(i) ?: continue
            if (RainRecords.isRain(obj)) continue
            if (obj.optBoolean("concluida", false) != concluidas) continue
            quantidade++
            val idx = i
            coluna.addView(cartao(obj.optString("tipo", "Ocorrência"),
                obj.optString("localizacao") + " • " + obj.optString("descricao")) { comRegistroAtual("ocorrencias_campo", obj.optString("id")) { opcoesOcorrencia(it) } },
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        }
        if (quantidade == 0) coluna.addView(texto("Nenhum registro nesta lista.", 14f))
        coluna.addView(botao(if (concluidas) "Voltar às abertas" else "✓ Histórico de concluídas") {
            ocorrenciasCampo(!concluidas)
        })
    }

    private fun editarOcorrencia(idx: Int) {
        pausarSincronizacao()
        val atual = if (idx >= 0) dados("ocorrencias_campo").optJSONObject(idx) else null
        if (!exigirEdicao("ocorrencias_campo", atual)) return
        val corpo = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(8), dp(18), dp(8)) }
        val baseTipos=arrayOf("Buraco","Erosão","Problema em curva","Obstáculo","Outro")
        val previousType=atual?.optString("tipo").orEmpty()
        val tipos=if(previousType.isNotBlank()&&previousType !in baseTipos) baseTipos+previousType else baseTipos
        val tipo = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, tipos)
            setSelection(tipos.indexOf(atual?.optString("tipo")).coerceAtLeast(0))
        }
        corpo.addView(texto("Tipo de ocorrência", 15f))
        corpo.addView(tipo)
        val localizacao = campo("Ex.: Estrada 5, carreador 2, talhão 10, curva 3", atual?.optString("localizacao") ?: "")
        val descricao = campo("Descreva o problema encontrado", atual?.optString("descricao") ?: "")
        corpo.addView(texto("Localização (digitação livre)", 15f))
        corpo.addView(localizacao)
        corpo.addView(texto("Descrição", 15f))
        corpo.addView(descricao)
        val gps = atual?.optString("gps") ?: ""
        val coordenadas = campo("Coordenadas GPS (opcional)", gps)
        corpo.addView(coordenadas)
        corpo.addView(botao("📍 Usar minha localização GPS") {
            val pos = local
            if (pos != null) coordenadas.setText("${pos.latitude}, ${pos.longitude}")
            else Toast.makeText(this, "GPS ainda não disponível", Toast.LENGTH_SHORT).show()
        })
        fotosFormulario = mutableListOf<String>().apply {
            atual?.optJSONArray("fotos")?.let { fotos -> for (i in 0 until fotos.length()) add(fotos.optString(i)) }
        }
        val resumo = texto("Fotos anexadas: ${fotosFormulario.size}", 14f)
        corpo.addView(resumo)
        atualizarFotosFormulario = { resumo.text = "Fotos anexadas: ${fotosFormulario.size}" }
        corpo.addView(botao("📷 Tirar foto") {
            startActivityForResult(Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE), 308)
        })
        corpo.addView(botao("🖼 Escolher fotos da galeria") {
            startActivityForResult(Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "image/*"; putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            }, 307)
        })
        corpo.addView(botao("Remover foto selecionada") {
            if (fotosFormulario.isNotEmpty()) AlertDialog.Builder(this).setTitle("Remover foto")
                .setItems(fotosFormulario.mapIndexed { i, _ -> "Foto ${i + 1}" }.toTypedArray()) { _, pos ->
                    fotosFormulario.removeAt(pos); atualizarFotosFormulario?.invoke()
                }.show()
        })
        AlertDialog.Builder(this).setTitle(if (idx < 0) "Nova ocorrência de campo" else "Editar ocorrência")
            .setView(ScrollView(this).apply { addView(corpo) })
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Salvar") { _, _ ->
                val arr = dados("ocorrencias_campo")
                val obj = atual ?: JSONObject().apply { put("id", System.currentTimeMillis().toString()) }
                obj.put("tipo", tipos[tipo.selectedItemPosition])
                obj.put("localizacao", localizacao.text.toString())
                obj.put("descricao", descricao.text.toString())
                obj.put("gps", coordenadas.text.toString())
                obj.put("fotos", JSONArray().apply { fotosFormulario.filter { File(it).isFile }.forEach { put(it) } })
                if (obj.optString("dataCadastro").isBlank()) obj.put("dataCadastro", dataHoje())
                if (!colocarRegistro(arr, idx, obj)) return@setPositiveButton
                guardar("ocorrencias_campo", arr)
                atualizarFotosFormulario = null
                ocorrenciasCampo()
                AlertDialog.Builder(this).setTitle("Ocorrência salva")
                    .setMessage("Deseja compartilhar agora?")
                    .setNegativeButton("Agora não", null)
                    .setPositiveButton("Compartilhar") { _, _ -> compartilharOcorrencia(obj) }.show()
            }.show()
    }

    private fun opcoesOcorrencia(idx: Int) {
        pausarSincronizacao()
        val obj = dados("ocorrencias_campo").optJSONObject(idx) ?: return
        val concluida = obj.optBoolean("concluida", false)
        if (teamSync?.canEdit("ocorrencias_campo", obj.optString("id")) == false) {
            AlertDialog.Builder(this).setTitle(obj.optString("tipo"))
                .setItems(arrayOf("Compartilhar", "Ver fotos")) { _, option ->
                    if (option == 0) compartilharOcorrencia(obj) else mostrarFotos(obj)
                }.show()
            return
        }
        val actions=listOf("Visualizar / compartilhar", "Editar", "Adicionar foto", "Ver fotos",
            if (concluida) "↩ Reabrir ocorrência" else "✓ Concluir ocorrência", "Excluir")
            .mapIndexed {index,label->label to index}.filter {it.second!=5||podeGerenciar()}
        AlertDialog.Builder(this).setTitle(obj.optString("tipo"))
            .setItems(actions.map {it.first}.toTypedArray()) { _, selected ->
                val opcao=actions[selected].second
                when (opcao) {
                    0 -> compartilharOcorrencia(obj)
                    1 -> editarOcorrencia(idx)
                    2 -> {
                        fotoPendente = obj.optString("id")
                        startActivityForResult(Intent(Intent.ACTION_GET_CONTENT).apply { type = "image/*" }, 309)
                    }
                    3 -> {
                        val fotos = fotosPendencia(obj)
                        if (fotos.isEmpty()) Toast.makeText(this, "Sem fotos", Toast.LENGTH_SHORT).show()
                        else AlertDialog.Builder(this).setTitle("Fotos")
                            .setItems(fotos.mapIndexed { i, _ -> "Foto ${i + 1}" }.toTypedArray()) { _, pos ->
                                AlertDialog.Builder(this).setView(ImageView(this).apply {
                                    setImageBitmap(BitmapFactory.decodeFile(fotos[pos].absolutePath))
                                    adjustViewBounds = true
                                }).setPositiveButton("Fechar", null).show()
                            }.show()
                    }
                    4 -> AlertDialog.Builder(this)
                        .setTitle(if (concluida) "Reabrir ocorrência?" else "Concluir ocorrência?")
                        .setMessage("O registro e suas fotos continuarão salvos no histórico.")
                        .setNegativeButton("Cancelar", null)
                        .setPositiveButton("Confirmar") { _, _ ->
                            val arr = dados("ocorrencias_campo")
                            val item = (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }.firstOrNull { it.optString("id") == obj.optString("id") } ?: return@setPositiveButton
                            item.put("concluida", !concluida)
                            if (concluida) item.remove("dataConclusao") else item.put("dataConclusao",
                                java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR")).format(java.util.Date()))
                            guardar("ocorrencias_campo", arr)
                            ocorrenciasCampo(concluida)
                        }.show()
                    5 -> remover("ocorrencias_campo", idx) { ocorrenciasCampo(concluida) }
                }
            }.show()
    }

    private fun textoOcorrencia(obj: JSONObject): String = buildString {
        append("OCORRÊNCIA DE CAMPO - COLHEITA GPS\n")
        append("Data: ").append(dataRegistro(obj)).append("\n")
        append("Tipo: ").append(obj.optString("tipo")).append("\n")
        append("Localização: ").append(obj.optString("localizacao")).append("\n")
        append("Descrição: ").append(obj.optString("descricao")).append("\n")
        if (obj.optString("gps").isNotBlank()) append("GPS: ").append(obj.optString("gps")).append("\n")
        append("Situação: ").append(if (obj.optBoolean("concluida", false)) "Concluída" else "Pendente")
        if (obj.optBoolean("concluida", false)) append("\nConcluída em: ").append(obj.optString("dataConclusao"))
    }

    private fun compartilharOcorrencia(obj: JSONObject) {
        compartilharRelatorio(relatorioGrupo("Ocorrência de campo","ocorrencias_campo",listOf(obj)))
    }

    private fun editarPendencia(idx:Int,equipamentoInicial:String="") {
        val key="pendencias"
        pausarSincronizacao()
        val atual=if(idx>=0) dados(key).optJSONObject(idx) else null
        if(!exigirEdicao(key,atual)) return
        val corpo=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;setPadding(dp(18),dp(8),dp(18),dp(8))}
        val equipamento=campo("Equipamento",atual?.optString("equipamento")?:equipamentoInicial)
        if(equipamentoInicial.isNotBlank()||atual!=null) equipamento.isEnabled=false
        if(equipamentoInicial.isBlank()&&atual==null) corpo.addView(botao("Escolher equipamento salvo") {
            val equipment=MaintenanceRecords.summaries(maintenanceCatalog())
            if(equipment.isEmpty()) Toast.makeText(this,"Nenhum equipamento salvo. Informe o número no campo.",Toast.LENGTH_LONG).show()
            else AlertDialog.Builder(this).setTitle("Equipamentos da equipe").setItems(equipment.map {it.number}.toTypedArray()) {_,position->equipamento.setText(equipment[position].number)}.show()
        })
        val descricao=campo("Defeito e localização",atual?.optString("descricao")?:"")
        val osPendencia=campo("O.S. da pendência (ex.: 12345)",atual?.optString("osPendencia")?:"")
        corpo.addView(equipamento);corpo.addView(osPendencia);corpo.addView(descricao)
        val urgency=CheckBox(this).apply {text="Marcar como urgente";isChecked=atual?.optBoolean("urgente")==true;minHeight=dp(48);setTextColor(palette.text)}
        corpo.addView(urgency)
        fotosFormulario=mutableListOf<String>().apply {
            atual?.optJSONArray("fotos")?.let {arr->for(i in 0 until arr.length()) add(arr.optString(i))}
            if(isEmpty()) atual?.optString("foto")?.takeIf {it.isNotBlank()}?.let {add(it)}
        }
        val resumo=texto("Fotos anexadas: ${fotosFormulario.size}",14f);corpo.addView(resumo)
        atualizarFotosFormulario={resumo.text="Fotos anexadas: ${fotosFormulario.size}"}
        corpo.addView(botao("📷 Tirar foto") {startActivityForResult(Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE),308)})
        corpo.addView(botao("🖼 Adicionar fotos da galeria") {
            startActivityForResult(Intent(Intent.ACTION_GET_CONTENT).apply {type="image/*";putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true)},307)
        })
        corpo.addView(botao("Remover foto selecionada") {
            if(fotosFormulario.isNotEmpty()) AlertDialog.Builder(this).setTitle("Remover foto")
                .setItems(fotosFormulario.mapIndexed {i,_->"Foto ${i+1}"}.toTypedArray()) {_,pos->fotosFormulario.removeAt(pos);atualizarFotosFormulario?.invoke()}.show()
        })
        AlertDialog.Builder(this).setTitle(if(idx<0) "Novo registro" else "Editar registro")
            .setView(corpo).setNegativeButton("Cancelar",null).setPositiveButton("Salvar") {_,_->
                val number=MaintenanceRecords.number(equipamento.text.toString())
                if(number.isBlank()||equipamento.text.length>80||descricao.text.isBlank()) {
                    Toast.makeText(this,"Informe o equipamento (até 80 caracteres) e descreva a pendência.",Toast.LENGTH_LONG).show();return@setPositiveButton
                }
                if(idx<0) {
                    val catalog=maintenanceCatalog()
                    if((0 until catalog.length()).mapNotNull {catalog.optJSONObject(it)}.any {
                        MaintenanceRecords.isEquipment(it)&&MaintenanceRecords.number(it.optString("equipamento"))==number&&MaintenanceRecords.isArchived(it)
                    }) {
                        Toast.makeText(this,"Este equipamento foi excluído e está no histórico. Peça ao supervisor para restaurar o equipamento para novos registros.",Toast.LENGTH_LONG).show();return@setPositiveButton
                    }
                }
                val arr=dados(key)
                val obj=atual?:JSONObject().put("id",System.currentTimeMillis().toString())
                obj.put("equipamento",number).put("descricao",descricao.text.toString()).put("osPendencia",osPendencia.text.toString()).put("urgente",urgency.isChecked)
                val photos=JSONArray();fotosFormulario.filter {File(it).exists()}.forEach {photos.put(it)}
                obj.put("fotos",photos).put("foto",if(photos.length()>0) photos.optString(0) else "")
                if(obj.optString("dataCadastro").isBlank()) obj.put("dataCadastro",dataHoje())
                if(!colocarRegistro(arr,idx,obj)) return@setPositiveButton
                guardar(key,arr);atualizarFotosFormulario=null
                pendenciasEquipamento(number)
                AlertDialog.Builder(this).setTitle("Pendência salva").setMessage("Deseja compartilhar esta pendência agora?")
                    .setNegativeButton("Agora não",null).setPositiveButton("Compartilhar") {_,_->compartilharPendenciaMenu(obj)}.show()
            }.show()
    }
    private var diaCaminhoes=""
    private var corteCaminhoes:java.time.LocalTime?=null
    private var relogioCaminhoes:java.time.Clock=java.time.Clock.systemDefaultZone()
    private var periodoCaminhoesMostrado=""
    private val atualizarTurnoCaminhoes=object:Runnable {
        override fun run() {
            if(telaAtual!="caminhoes"||diaCaminhoes.isNotBlank()||!acessoConfirmado||isFinishing||isDestroyed) return
            val now=java.time.LocalDateTime.now(relogioCaminhoes)
            if(TruckShift.at(now).key!=periodoCaminhoesMostrado) {corteCaminhoes=null;caminhoes()}
            else agendarViradaCaminhoes(now)
        }
    }
    private fun agendarViradaCaminhoes(now:java.time.LocalDateTime) {
        syncUiHandler.removeCallbacks(atualizarTurnoCaminhoes)
        val delay=java.time.Duration.between(now,TruckShift.at(now).end).toMillis().coerceIn(1L,60_000L)
        syncUiHandler.postDelayed(atualizarTurnoCaminhoes,delay)
    }
    private fun abrirCaminhoes() {diaCaminhoes="";corteCaminhoes=null;caminhoes()}
    private fun escolherDiaCaminhao(initial:java.time.LocalDate,selected:(java.time.LocalDate)->Unit) {
        DatePickerDialog(this,{_,year,month,day->selected(java.time.LocalDate.of(year,month+1,day))},initial.year,initial.monthValue-1,initial.dayOfMonth).show()
    }
    private fun historicoCaminhoes() {
        val now=java.time.LocalDateTime.now(relogioCaminhoes)
        AlertDialog.Builder(this).setTitle("Histórico de caminhões")
            .setItems(arrayOf("Escolher data de início do turno","Todos os registros")) {_,option->
                if(option==1) {diaCaminhoes="todos";corteCaminhoes=null;caminhoes()}
                else escolherDiaCaminhao(RainRecords.date(diaCaminhoes)?:TruckShift.at(now).date) {date->diaCaminhoes=TruckRecords.label(date);corteCaminhoes=null;caminhoes()}
            }.setNegativeButton("Cancelar",null).show()
    }
    private fun caminhoes() {
        if(!exigirGestor()) return
        val column=pagina("Controle de caminhões")
        themeScreen={caminhoes()}
        telaAtual="caminhoes";refreshList={caminhoes()}
        val now=java.time.LocalDateTime.now(relogioCaminhoes)
        val shift=teamStore!!.team.substringAfterLast('_')
        val live=diaCaminhoes.isBlank()
        if(live&&periodoCaminhoesMostrado!=TruckShift.at(now).key) corteCaminhoes=null
        val selectedDate=RainRecords.date(diaCaminhoes)
        val interval=if(live) TruckShift.active(shift,now) else selectedDate?.let {TruckShift.period(shift,it)}
        val cutoff=interval?.let {period->
            corteCaminhoes?.let {hour->TruckShift.cutoff(period,hour)}?:if(live) now else period.end
        }
        val period=if(diaCaminhoes=="todos") "Histórico • todos os registros" else interval?.let {
            (if(live) "Turno atual\n" else "Histórico\n")+it.label()+cutoff?.let {end->"\nAté ${end.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))}"}.orEmpty()
        }?:"Turno $shift • ${TruckShift.hours(shift)}\nFora do horário deste turno • lista ativa vazia"
        column.addView(texto(TeamPolicy.label(teamStore!!.team),17f,palette.accent))
        column.addView(texto(period,16f))
        val filters=LinearLayout(this).apply {orientation=LinearLayout.HORIZONTAL}
        filters.addView(botao("Turno atual") {abrirCaminhoes()},LinearLayout.LayoutParams(0,-2,1f))
        filters.addView(botao("Histórico") {historicoCaminhoes()},LinearLayout.LayoutParams(0,-2,1f))
        column.addView(filters)
        if(interval!=null) column.addView(botao("Até que horas: ${corteCaminhoes?.toString()?:if(live) "agora" else "fim do turno"}") {
            val initial=corteCaminhoes?:if(live) now.toLocalTime() else interval.end.toLocalTime()
            TimePickerDialog(this,{_,hour,minute->
                val value=java.time.LocalTime.of(hour,minute)
                try {TruckShift.cutoff(interval,value);corteCaminhoes=value;caminhoes()}
                catch(error:IllegalArgumentException) {noticeCaminhao(error.message?:"Confira o horário.")}
            },initial.hour,initial.minute,true).show()
        })
        if(corteCaminhoes!=null) column.addView(botao("Remover horário de corte") {corteCaminhoes=null;caminhoes()})
        column.addView(botao("+ Registrar entrada") {editarCaminhao(-1)})
        val original=dados("caminhoes")
        val snapshot=if(diaCaminhoes=="todos") TruckShift.Snapshot(TruckRecords.rows(original,null),TruckRecords.summary(original,null))
            else TruckShift.snapshot(original,interval,cutoff)
        val rows=snapshot.rows;val summary=snapshot.summary
        column.addView(texto("Entradas: ${summary.entries} • Saídas: ${summary.exits}\nAguardando saída neste período: ${summary.pending}",16f))
        column.addView(texto("A lista zera na troca de turno. Os registros anteriores ficam no Histórico.",14f))
        for(record in rows) column.addView(cartao("Caminhão ${record.optString("numero")} • Turno ${record.optString("turno")}",TruckRecords.details(record)) {
            AlertDialog.Builder(this).setTitle("Caminhão ${record.optString("numero")}")
                .setItems(arrayOf("Registrar saída / editar entrada","Excluir e manter no histórico")) {_,option->
                    comRegistroAtual("caminhoes",record.optString("id")) {index->if(option==0) editarCaminhao(index) else remover("caminhoes",index) {caminhoes()}}
                }.show()
        },LinearLayout.LayoutParams(-1,-2).apply {topMargin=dp(10)})
        if(rows.isEmpty()) column.addView(texto("Nenhum caminhão neste período.",15f))
        val report=FieldReports.trucks(TeamPolicy.label(teamStore!!.team),period,rows,summary)
        relatorioAtual={report}
        column.addView(botao("Compartilhar caminhões") {
            compartilharRelatorio(report)
        })
        periodoCaminhoesMostrado=TruckShift.at(now).key
        if(live) agendarViradaCaminhoes(now)
    }
    private fun editarCaminhao(idx:Int) {
        if(!exigirGestor()) return
        pausarSincronizacao()
        val current=if(idx>=0) dados("caminhoes").optJSONObject(idx) else null
        if(!exigirEdicao("caminhoes",current)) return
        val selectedStart=RainRecords.date(diaCaminhoes)
        val shift=teamStore!!.team.substringAfterLast('_')
        var manualDate=false
        var entryDay=current?.let(TruckRecords::entryDay)?:selectedStart?:java.time.LocalDate.now(relogioCaminhoes)
        var exitDay=current?.let {if(it.optString("saida").isNotBlank()) TruckRecords.exitDay(it) else null}
        val ui=AccessUi(this,attach=false)
        val body=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;setPadding(dp(18),dp(8),dp(18),dp(12))}
        ui.add(body,ui.text(TeamPolicy.label(teamStore!!.team),15f,ui.green,true),0)
        val number=ui.field(body,"Número do caminhão").apply {setText(current?.optString("numero").orEmpty())}
        val entryButton=ui.button(body,"Data da entrada: ${TruckRecords.label(entryDay)}",true) {}
        entryButton.setOnClickListener {escolherDiaCaminhao(entryDay) {day->manualDate=true;entryDay=day;entryButton.text="Data da entrada: ${TruckRecords.label(day)}"}}
        val arrival=ui.field(body,"Entrada (HH:mm)").apply {setText(current?.optString("chegada").orEmpty());inputType=android.text.InputType.TYPE_CLASS_DATETIME or android.text.InputType.TYPE_DATETIME_VARIATION_TIME}
        ui.add(body,ui.text("Turno $shift: ${TruckShift.hours(shift)}. Informe a data real da entrada.",13f),5)
        arrival.addTextChangedListener(object:android.text.TextWatcher {
            override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int) {}
            override fun afterTextChanged(s:android.text.Editable?) {}
            override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int) {
                if(current==null&&selectedStart!=null&&!manualDate) TruckRecords.time(s.toString())?.let {hour->
                    entryDay=TruckShift.entryDate(selectedStart,shift,hour);entryButton.text="Data da entrada: ${TruckRecords.label(entryDay)}"
                }
            }
        })
        val departure=ui.field(body,"Saída (HH:mm), opcional").apply {setText(current?.optString("saida").orEmpty());inputType=android.text.InputType.TYPE_CLASS_DATETIME or android.text.InputType.TYPE_DATETIME_VARIATION_TIME}
        val exitButton=ui.button(body,"Data da saída: ${exitDay?.let(TruckRecords::label)?:"automática"}",true) {}
        exitButton.setOnClickListener {escolherDiaCaminhao(exitDay?:entryDay) {day->exitDay=day;exitButton.text="Data da saída: ${TruckRecords.label(day)}"}}
        ui.add(body,ui.text("Sem data de saída escolhida, um horário menor que a entrada será registrado no dia seguinte. Deixe a saída vazia enquanto o caminhão estiver na frente.",13f),8)
        ui.button(body,"Preencher entrada agora",true) {
            manualDate=true;entryDay=java.time.LocalDate.now(relogioCaminhoes);entryButton.text="Data da entrada: ${TruckRecords.label(entryDay)}"
            arrival.setText(java.time.LocalTime.now(relogioCaminhoes).format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")))
        }
        ui.button(body,"Preencher saída agora",true) {
            exitDay=java.time.LocalDate.now(relogioCaminhoes);exitButton.text="Data da saída: ${TruckRecords.label(exitDay!!)}"
            departure.setText(java.time.LocalTime.now(relogioCaminhoes).format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")))
        }
        ui.button(body,"🎤 Ditar informações",true) {ouvir {speech->
            Regex("(?:caminhão|caminhao)\\s*(?:número\\s*)?([0-9]+)",RegexOption.IGNORE_CASE).find(speech)?.groupValues?.get(1)?.let {number.setText(it)}
            val time=Regex("\\b([01]?[0-9]|2[0-3])[:h]([0-5][0-9])\\b").find(speech)?.let {it.groupValues[1].padStart(2,'0')+":"+it.groupValues[2]}
                ?:Regex("\\b([01]?[0-9]|2[0-3])\\s*horas?\\b",RegexOption.IGNORE_CASE).find(speech)?.let {it.groupValues[1].padStart(2,'0')+":00"}
            if(time!=null) if(Regex("sa[ií]da|saiu|partiu",RegexOption.IGNORE_CASE).containsMatchIn(speech)) departure.setText(time) else arrival.setText(time)
            Toast.makeText(this,"Confira as datas e os horários antes de salvar.",Toast.LENGTH_LONG).show()
        }}
        val dialog=AlertDialog.Builder(this).setTitle(if(current==null) "Registrar entrada de caminhão" else "Entrada e saída do caminhão")
            .setView(ScrollView(this).apply {addView(body)}).setNegativeButton("Cancelar",null).setPositiveButton("Salvar",null).create()
        dialog.setOnShowListener {dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if(!exigirGestor()) return@setOnClickListener
            val record=try {TruckRecords.build(current,number.text.toString(),arrival.text.toString(),departure.text.toString(),entryDay,exitDay,teamStore!!.team)}
                catch(error:IllegalArgumentException) {noticeCaminhao(error.message?:"Confira os dados do caminhão.");return@setOnClickListener}
            if(current==null||entryDay!=TruckRecords.entryDay(current)||record.optString("chegada")!=current.optString("chegada")) {
                try {TruckShift.requireEntry(shift,TruckShift.entry(record)!!)}
                catch(error:IllegalArgumentException) {arrival.error=error.message;return@setOnClickListener}
            }
            val all=dados("caminhoes")
            if(!colocarRegistro(all,if(current==null) -1 else 0,record)) return@setOnClickListener
            guardar("caminhoes",all);dialog.dismiss();caminhoes()
        }}
        dialog.setOnDismissListener {teamSync?.available(hasWindowFocus()&&mapa==null)}
        dialog.show()
    }
    private fun noticeCaminhao(message:String) {Toast.makeText(this,message,Toast.LENGTH_LONG).show()}

    private fun biblioteca() {
        themeScreen={biblioteca()}
        refreshList = { biblioteca() }
        telaAtual = "biblioteca"
        returnToParent = { inicio() }
        mapa?.salvar()
        mapa = null
        chaveAtual = ""
        status = null
        teamSync?.available(hasWindowFocus())

        val base = tela()
        val coluna = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val cab = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(escuro)
            setPadding(dp(20), dp(20), dp(20), dp(18))
        }
        cab.addView(texto("Mapas",23f,Color.WHITE))
        cab.addView(botao("←") { inicio() })
        cab.addView(
            texto("Seus mapas neste aparelho. Importe o mapa PDF pelo botão + ou encontre o arquivo no Drive abaixo.",14f,0xFFD1E8D3.toInt())
        )
        coluna.addView(cab)

        val lista = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL;tag="resultadosMapas"
            setPadding(dp(15), dp(12), dp(15), dp(100))
        }

        val favorites=teamStore?.let {store->teamAccess?.uid?.let {uid->MapFavorites(applicationContext,store.team,uid)}}
        fun renderMaps() {
        lista.removeAllViews()
        val stars=favorites?.ids().orEmpty()
        val todos=arquivos().filter {QuickFind.matches(buscaMapas,nome(it.nameWithoutExtension))}
            .sortedWith(compareBy<File> {it.nameWithoutExtension !in stars}.thenBy(String.CASE_INSENSITIVE_ORDER) {nome(it.nameWithoutExtension)})
        if (todos.isEmpty()) {
            lista.addView(
                texto(if(buscaMapas.isBlank()) "Toque em + para importar seu mapa PDF." else "Nenhum mapa encontrado. Altere ou apague a busca.", 16f).apply {
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
                    setColor(palette.surface)
                    cornerRadius = dp(14).toFloat()
                }
                elevation = dp(2).toFloat()
            }

            item.addView(
                texto(if(id in stars) "★" else "☆",26f,if(id in stars) palette.favorite else palette.muted).apply {
                    gravity=Gravity.CENTER;isFocusable=true
                    contentDescription=if(id in stars) "Remover ${nome(id)} dos favoritos" else "Marcar ${nome(id)} como favorito"
                    setOnClickListener {
                        try {favorites?.toggle(id);renderMaps()} catch(_:Exception) {Toast.makeText(this@MainActivity,"Não foi possível salvar o favorito. Tente novamente.",Toast.LENGTH_LONG).show()}
                    }
                },
                LinearLayout.LayoutParams(dp(48), dp(58))
            )

            val info = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
            }
            info.addView(texto(nome(id), 16f).apply { maxLines = 2 })
            info.addView(texto("Toque para abrir, compartilhar ou excluir", 12f, palette.muted))

            item.addView(info, LinearLayout.LayoutParams(0, -2, 1f))

            item.addView(
                texto("Opções", 12f).apply {
                    gravity = Gravity.CENTER
                    setOnClickListener { opcoesMapa(id) }
                },
                LinearLayout.LayoutParams(dp(64), dp(54))
            )

            item.setOnClickListener { opcoesMapa(id) }
            item.setOnLongClickListener { opcoesMapa(id); true }
            lista.addView(
                item,
                LinearLayout.LayoutParams(-1, -2).apply {
                    bottomMargin = dp(11)
                }
            )
        }

        }
        cab.addView(campoBusca("Buscar mapa ou lote",buscaMapas) {buscaMapas=it;renderMaps()},LinearLayout.LayoutParams(-1,-2).apply {topMargin=dp(12)})
        refreshList={renderMaps()};renderMaps()

        val scroll = ScrollView(this).apply {
            addView(lista)
        }
        coluna.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val driveFooter = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            // Reserve the right side for the existing floating + button.
            setPadding(dp(15), dp(6), dp(92), dp(18))
            addView(texto("Não tem o PDF neste celular?", 13f, palette.muted))
            addView(botao("Buscar mapas no Google Drive") {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://drive.google.com/drive/folders/1goZtocxdT1tgmjcNzpy3CoQHkeN4WLX4"))
                        .addCategory(Intent.CATEGORY_BROWSABLE))
                } catch (_: android.content.ActivityNotFoundException) {
                    Toast.makeText(this@MainActivity, "Não foi possível abrir o link. Confira se há um navegador instalado.", Toast.LENGTH_LONG).show()
                }
            }.apply {
                textSize = 14f
                minimumHeight = dp(54)
                contentDescription = "Abrir a pasta pública de mapas no Google Drive"
            }, LinearLayout.LayoutParams(-1, -2))
        }
        coluna.addView(driveFooter, LinearLayout.LayoutParams(-1, -2))
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
        pausarSincronizacao()
        if (!prefs().contains(id)) { biblioteca(); return }
        AlertDialog.Builder(this)
            .setTitle(nome(id))
            .setItems(
                arrayOf("Abrir", "Renomear", "Excluir", "Compartilhar imagem do mapa")
            ) { _, i ->
                when (i) {
                    0 -> abrir(id)
                    1 -> renomear(id)
                    2 -> excluir(id)
                    3 -> compartilharImagemMapa(id)
                }
            }.show()
    }

    private fun compartilharImagemMapa(id:String) {
        val store=teamStore?:return;val uid=teamAccess?.uid?:return
        if(!acessoConfirmado||!prefs().contains(id)) return
        if(mapa!=null&&chaveAtual==id&&mapa!!.pinturaEmAndamento()) {
            Toast.makeText(this,"Conclua ou cancele a pintura em edição antes de compartilhar.",Toast.LENGTH_LONG).show()
            return
        }
        if(mapa!=null&&chaveAtual==id&&!mapa!!.salvar()) {
            Toast.makeText(this,"Não foi possível preparar o mapa pintado. Suas pinturas continuam salvas. Tente novamente.",Toast.LENGTH_LONG).show()
            return
        }
        val original=File(pasta(),"$id.pdf")
        val name=nome(id)
        val painted=File(filesDir,"mapa_relatorio_$id.png").takeIf {it.isFile}
        if(painted==null&&!original.isFile) return
        val report=FieldReport(name,TeamPolicy.label(store.team),sections=emptyList())
        FieldReportSharing.prepareImages(this,report,
            {acessoConfirmado&&teamStore?.team==store.team&&teamAccess?.uid==uid&&prefs().contains(id)}) {
            val images=mutableListOf<File>();val folder=File(store.cache,"relatorios/${java.util.UUID.randomUUID()}").apply {mkdirs()}
            if(painted!=null) {
                val copy=File(folder,"Mapa-pintado.png");painted.copyTo(copy);images.add(copy)
            } else ParcelFileDescriptor.open(original,ParcelFileDescriptor.MODE_READ_ONLY).use {descriptor->PdfRenderer(descriptor).use {renderer->
                for(index in 0 until renderer.pageCount) renderer.openPage(index).use {page->
                    val scale=minOf(1600f/page.width,2200f/page.height)
                    val bitmap=Bitmap.createBitmap(maxOf(1,(page.width*scale).toInt()),maxOf(1,(page.height*scale).toInt()),Bitmap.Config.ARGB_8888)
                    try {
                        bitmap.eraseColor(Color.WHITE);page.render(bitmap,null,null,PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                        val file=File(folder,"Mapa-pagina-${index+1}.png");file.outputStream().use {check(bitmap.compress(Bitmap.CompressFormat.PNG,100,it))};images.add(file)
                    } finally {bitmap.recycle()}
                }
            }}
            images
        }
    }
    private fun renomear(id: String) {
        pausarSincronizacao()
        if (teamAccess?.manages == true && teamSync?.canEditMap(id) == false) { teamSync?.available(hasWindowFocus() && mapa == null); Toast.makeText(this, "Somente o responsável pelo mapa ou o supervisor pode renomeá-lo.", Toast.LENGTH_LONG).show(); return }
        val entrada = EditText(this).apply {AppTheme.field(this)

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
        pausarSincronizacao()
        if (teamAccess?.manages == true && teamSync?.canEditMap(id) == false) { teamSync?.available(hasWindowFocus() && mapa == null); Toast.makeText(this, "Somente o responsável pelo mapa ou o supervisor pode excluí-lo.", Toast.LENGTH_LONG).show(); return }
        AlertDialog.Builder(this)
            .setTitle("Excluir mapa?")
            .setMessage(
                "Isso também apaga as pinturas e os números de O.S. deste mapa."
            )
            .setPositiveButton("Excluir") { _, _ ->
                // The PDF may also be used by another local profile. Remove only this profile/catalog reference.
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

    // Talhões da O.S. são texto livre, seguindo o mesmo padrão do Estoque.
    // Preserva exatamente o que foi digitado, sem expandir ou reformatar intervalos.
    private fun validarTalhoes(entrada: String): String? = entrada.takeIf { it.isNotBlank() }

    private fun ordens(id: String) {
        pausarSincronizacao()
        if (!exigirGestor()) return
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
                listOf("Adicionar número","O.S. encerradas / histórico")
            ).toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("O.S. • ${nome(id)}")
            .setItems(itens) { _, pos ->
                if(pos==dados.size+1) {historicoOrdensEncerradas(id);return@setItems}
                val novo = pos == dados.size

                val campo = EditText(this).apply {AppTheme.field(this)

                    inputType = android.text.InputType.TYPE_CLASS_TEXT
                    setSingleLine(true)
                    if (!novo) setText(dados[pos])
                    hint = "Número da O.S."
                }

                val corpoOs = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(18), dp(8), dp(18), dp(8))
                }
                val talhoesOs = EditText(this).apply {AppTheme.field(this)

                    hint = "Talhões (ex.: 1 ao 10, 1 a 10, 1,2,3,4,5)"
                    setSingleLine(false)
                    minLines = 2
                    inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                    setText(if (novo) "" else prefTalhoes.getString(id + "_" + dados[pos], "") ?: "")
                }
                corpoOs.addView(campo)
                corpoOs.addView(talhoesOs)
                val closeButton=if(!novo) botao("Encerrar O.S.") {}.also {corpoOs.addView(it)} else null
                val dialogo = AlertDialog.Builder(this)
                    .setTitle(if (novo) "Nova O.S." else "Editar O.S.")
                    .setView(corpoOs)
                    .setPositiveButton("Salvar") { _, _ ->
                        val numero = campo.text.toString().trim()
                        val selecao = validarTalhoes(talhoesOs.text.toString())
                        if (numero.isNotEmpty() && selecao != null) {
                            if(numero in dados&&(novo||numero!=dados[pos])) {
                                Toast.makeText(this,"Essa O.S. já existe. Edite o cadastro existente para preservar os talhões.",Toast.LENGTH_LONG).show();ordens(id);return@setPositiveButton
                            }
                            if (!novo) prefTalhoes.edit().remove(id + "_" + dados[pos]).apply()
                            prefTalhoes.edit().putString(id + "_" + numero, selecao).apply()
                            if (novo) {
                                dados.add(numero)
                            } else {
                                dados[pos] = numero
                            }
                            guardar(dados)
                            ordens(id)
                        } else Toast.makeText(this, "Informe o número da O.S. e os talhões", Toast.LENGTH_LONG).show()
                    }
                    .setNegativeButton("Cancelar") { _, _ ->
                        ordens(id)
                    }

                if (!novo) {
                    dialogo.setNeutralButton("Excluir") { _, _ ->
                        excluirOrdemServico(id,dados[pos])
                    }
                }
                val opened=dialogo.create()
                closeButton?.setOnClickListener {
                    if(campo.text.toString().trim()!=dados[pos]||talhoesOs.text.toString()!=prefTalhoes.getString(id+"_"+dados[pos],"").orEmpty()) {
                        campo.error="Salve as alterações antes de encerrar a O.S.";return@setOnClickListener
                    }
                    opened.dismiss();encerrarOrdemServico(id,dados[pos])
                }
                opened.show()
            }
            .setNeutralButton("Imagem") {_,_->compartilharRelatorio(FieldReport("Ordens de serviço",TeamPolicy.label(teamStore!!.team),nome(id),listOf(secoesOrdens(id))))}
            .setNegativeButton("Fechar", null)
            .show()
    }

    private fun encerrarOrdemServico(mapId:String,number:String) {
        if(!exigirGestor()) return
        pausarSincronizacao()
        AlertDialog.Builder(this).setTitle("Encerrar O.S. $number?")
            .setMessage("A O.S. sairá da lista em andamento. Número, mapa, talhões, data e responsável ficarão no histórico da equipe, com sincronização quando houver conexão.")
            .setNegativeButton("Cancelar",null).setPositiveButton("Encerrar e guardar") {_,_->
                if(!exigirGestor()) return@setPositiveButton
                try {
                    synchronized(teamStore!!.lock) {
                        val prefs=getSharedPreferences("ordens_servico",0)
                        val current=JSONArray(prefs.getString(mapId,"[]"))
                        if((0 until current.length()).none {current.optString(it)==number}) return@setPositiveButton
                        val plots=getSharedPreferences("talhoes_por_os",0)
                        val fields=plots.getString(mapId+"_"+number,"").orEmpty()
                        val history=todosRegistros(OrderRecords.GROUP)
                        // If removing the active list failed earlier, reuse the preserved snapshot.
                        val existing=(0 until history.length()).mapNotNull {history.optJSONObject(it)}.any {
                            OrderRecords.closed(it)&&it.optString("mapaId")==mapId&&it.optString("numeroOs")==number&&it.optString("talhoes")==fields
                        }
                        if(!existing) history.put(OrderRecords.close(mapId,nome(mapId),number,fields,teamAccess!!.uid,
                            DashboardUi.name(teamAccess,sessionName),archiveDate(),System.currentTimeMillis()))
                        check(getSharedPreferences("registros_v3",0).edit().putString(OrderRecords.GROUP,history.toString()).commit()) {"Não foi possível guardar o histórico. A O.S. foi mantida."}
                        val remaining=JSONArray();for(i in 0 until current.length()) if(current.optString(i)!=number) remaining.put(current.optString(i))
                        check(prefs.edit().putString(mapId,remaining.toString()).commit()) {"O histórico foi salvo. Tente encerrar novamente para atualizar a lista ativa."}
                        plots.edit().remove(mapId+"_"+number).commit()
                    }
                    historicoOrdensEncerradas(mapId);teamSync?.retry()
                    Toast.makeText(this,"O.S. encerrada e preservada no histórico. Confira a sincronização da equipe.",Toast.LENGTH_LONG).show()
                } catch(error:Exception) {Toast.makeText(this,error.message?:"Não foi possível encerrar a O.S.",Toast.LENGTH_LONG).show()}
            }.show()
    }
    private fun historicoOrdensEncerradas(mapId:String?=null) {
        if(!exigirGestor()) return
        val column=pagina("O.S. encerradas",parent={if(mapId==null) paginaOrdens() else ordens(mapId)})
        themeScreen={historicoOrdensEncerradas(mapId)}
        refreshList={historicoOrdensEncerradas(mapId)}
        val records=FieldReports.rows(dados(OrderRecords.GROUP)).filter {OrderRecords.historical(it)&&(mapId==null||it.optString("mapaId")==mapId)}.sortedByDescending {it.optLong("encerradaTimestamp")}
        relatorioAtual={relatorioGrupo("Histórico de O.S. encerradas",OrderRecords.GROUP,records)}
        column.addView(texto(TeamPolicy.label(teamStore!!.team),16f,palette.accent))
        for(item in records) column.addView(cartao("O.S. ${item.optString("numeroOs")} • "+if(OrderRecords.closed(item)) "Encerrada" else "Reaberta",
            "${item.optString("nomeMapa")}\nTalhões: ${item.optString("talhoes")}\nEncerrada em: ${item.optString("encerradaEm")}\nPor: ${item.optString("encerradaPorNome")}") {
                val actions=mutableListOf<Pair<String,()->Unit>>()
                actions.add("Ver informações" to {AlertDialog.Builder(this).setTitle("O.S. ${item.optString("numeroOs")}")
                    .setMessage(RecordArchive.description(item)+"\nMapa: ${item.optString("nomeMapa")}").setPositiveButton("Fechar",null).show()})
                actions.add("Compartilhar imagem" to {compartilharRelatorio(relatorioGrupo("O.S. ${item.optString("numeroOs")}",OrderRecords.GROUP,listOf(item)))})
                if(OrderRecords.closed(item)) actions.add("Reabrir O.S." to {restaurarOrdemServico(item.optString("id"))})
                AlertDialog.Builder(this).setTitle("O.S. ${item.optString("numeroOs")}").setItems(actions.map {it.first}.toTypedArray()) {_,i->actions[i].second()}.show()
            },LinearLayout.LayoutParams(-1,-2).apply {topMargin=dp(10)})
        if(records.isEmpty()) column.addView(texto("Nenhuma O.S. encerrada nesta equipe.",15f))
        column.addView(botao("Compartilhar histórico por imagem") {relatorioAtual?.invoke()?.let(::compartilharRelatorio)})
    }

    private fun excluirOrdemServico(mapId:String,number:String) {
        if(!exigirGestor()) return
        AlertDialog.Builder(this).setTitle("Excluir O.S. $number?").setMessage("A ordem e seus talhões sairão da lista ativa e serão preservados em Registros excluídos.")
            .setNegativeButton("Cancelar",null).setPositiveButton("Excluir e manter no histórico") { _,_->
                if(!exigirGestor()) return@setPositiveButton
                synchronized(teamStore!!.lock) {
                    val prefs=getSharedPreferences("ordens_servico",0)
                    val current=try {JSONArray(prefs.getString(mapId,"[]"))} catch(_:Exception) {JSONArray()}
                    if((0 until current.length()).none {current.optString(it)==number}) return@setPositiveButton
                    val fields=getSharedPreferences("talhoes_por_os",0)
                    val snapshot=arquivarManutencao(JSONObject().put("id",java.util.UUID.randomUUID().toString())
                        .put("categoriaRegistro","ordem_servico").put("mapaId",mapId).put("nomeMapa",nome(mapId))
                        .put("numeroOs",number).put("talhoes",fields.getString(mapId+"_"+number,"").orEmpty()))
                    val history=todosRegistros("historico_os").put(snapshot)
                    if(!getSharedPreferences("registros_v3",0).edit().putString("historico_os",history.toString()).commit()) {
                        Toast.makeText(this,"Não foi possível preservar o histórico. A O.S. não foi removida.",Toast.LENGTH_LONG).show()
                        return@setPositiveButton
                    }
                    val remaining=JSONArray();for(i in 0 until current.length()) if(current.optString(i)!=number) remaining.put(current.optString(i))
                    prefs.edit().putString(mapId,remaining.toString()).commit()
                    fields.edit().remove(mapId+"_"+number).commit()
                }
                ordens(mapId)
            }.show()
    }
    private fun restaurarOrdemServico(id:String) {
        if(!exigirGestor()) return
        val all=todosRegistros("historico_os")
        val snapshot=(0 until all.length()).mapNotNull {all.optJSONObject(it)}.firstOrNull {it.optString("id")==id&&(RecordArchive.archived(it)||OrderRecords.closed(it))}?:return
        val reopening=OrderRecords.closed(snapshot)
        val body=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;setPadding(dp(18),dp(8),dp(18),dp(12))}
        val number=campo("Número da O.S.",snapshot.optString("numeroOs"));body.addView(number)
        val fields=campo("Talhões",snapshot.optString("talhoes"));body.addView(fields)
        val dialog=AlertDialog.Builder(this).setTitle(if(reopening) "Reabrir ordem de serviço" else "Restaurar ordem de serviço").setMessage("${snapshot.optString("nomeMapa")}\nSe esse número já existir com outros talhões, escolha um número diferente para preservar os dois registros.")
            .setView(body).setNegativeButton("Cancelar",null).setPositiveButton(if(reopening) "Reabrir" else "Restaurar",null).create()
        dialog.setOnShowListener {dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if(!exigirGestor()) return@setOnClickListener
            val value=number.text.toString().trim();if(value.isBlank()||fields.text.isBlank()) {number.error="Informe a O.S. e os talhões";return@setOnClickListener}
            synchronized(teamStore!!.lock) {
                val history=todosRegistros("historico_os")
                val index=(0 until history.length()).firstOrNull {history.optJSONObject(it)?.optString("id")==id&&(RecordArchive.archived(history.getJSONObject(it))||OrderRecords.closed(history.getJSONObject(it)))}?:return@setOnClickListener
                val item=history.getJSONObject(index);val mapId=item.optString("mapaId")
                val prefs=getSharedPreferences("ordens_servico",0);val areas=getSharedPreferences("talhoes_por_os",0)
                val current=try {JSONArray(prefs.getString(mapId,"[]"))} catch(_:Exception) {JSONArray()}
                val exists=(0 until current.length()).any {current.optString(it)==value}
                if(exists&&areas.getString(mapId+"_"+value,"")!=fields.text.toString()) {number.error="Essa O.S. já existe. Use outro número para preservar os dados atuais.";return@setOnClickListener}
                if(!exists) current.put(value)
                areas.edit().putString(mapId+"_"+value,fields.text.toString()).commit();prefs.edit().putString(mapId,current.toString()).commit()
                history.put(index,if(reopening) OrderRecords.reopen(item,value,fields.text.toString(),teamAccess!!.uid,DashboardUi.name(teamAccess,sessionName),archiveDate())
                    else RecordArchive.restore(item,teamAccess!!.uid,teamAccess!!.profile["nome"]?.toString().orEmpty(),archiveDate()).put("restauradaComo",value))
                guardar("historico_os",history)
            }
            dialog.dismiss();if(reopening) historicoOrdensEncerradas() else registrosExcluidos()
        } }
        dialog.show()
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
        if(requestCode==8801||requestCode==8802) { resultadoFotoPeca(requestCode,resultCode,data); return }
        tratarResultadoVozEFoto(requestCode, resultCode, data)
        if (requestCode == 310) {
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
        telaAtual = "mapa"
        returnToParent = { biblioteca() }
        pausarSincronizacao()
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

        cab.addView(texto("0°",15f,Color.WHITE).apply {
            gravity=Gravity.CENTER;contentDescription="Repor orientação original do mapa";setOnClickListener {visual.reporOrientacao()}
        },LinearLayout.LayoutParams(dp(48),dp(52)))
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
            "Medir distância", "Pincel", "Borracha", "GPS", "Salvar mapa pintado"
        ) else arrayOf(
            "Pintar talhão", "Medir área", "Medir distância", "Pincel",
            "Borracha", "Desfazer", "Excluir última pintura",
            "GPS", "Salvar mapa pintado", "Voltar aos mapas"
        )
        val navigation=arrayOf("Compartilhar mapa pintado","Repor orientação original (0°)","Como girar o mapa")
        AlertDialog.Builder(this).setTitle("Ferramentas")
            .setItems(opcoes+navigation) { _, i ->
                if(i>=opcoes.size) {
                    when(i-opcoes.size) {
                        0->compartilharImagemMapa(chaveAtual)
                        1->v.reporOrientacao()
                        2->AlertDialog.Builder(this).setTitle("Girar, aproximar e mover").setMessage("Coloque dois dedos no mapa e gire livremente. Afaste ou aproxime os dedos para mudar o zoom. Arraste com dois dedos para mover. O botão 0° repõe a orientação original do PDF; o GPS, as pinturas e as medidas acompanham o mapa.").setPositiveButton("Entendi",null).show()
                    }
                    return@setItems
                }
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
                    9 -> iniciarGps()
                    10 -> { v.salvar(); salvarImagemMapa() }
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
                    7 -> iniciarGps()
                    8 -> { v.salvar(); salvarImagemMapa() }
                    9 -> biblioteca()
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

    private fun sairDaConta() {
        profileWatch?.remove(); profileWatch = null
        mapa?.salvar()
        teamSync?.stop()
        teamSync = null
        acessoConfirmado = false
        podeAprovarUsuarios = false
        papelAtual = ""
        AccessRepository.logout(this)
    }

    private fun voltarTela() {
        returnToParent?.let { it(); return }
        when {
            telaAtual == "inicio" -> moveTaskToBack(true)
            chaveAtual.isNotEmpty() -> biblioteca()
            else -> inicio()
        }
    }
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { voltarTela() }

    override fun onPause() {
        super.onPause()
        syncUiHandler.removeCallbacks(atualizarTurnoCaminhoes)
        mapa?.salvar()
    }

    override fun onDestroy() {
        profileWatch?.remove()
        teamSync?.stop()
        syncUiHandler.removeCallbacksAndMessages(null)
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

    private val viewport=MapViewport()
    private val gestos=MapGestureController(viewport,20f*resources.displayMetrics.density)
    private val escala get()=viewport.scale
    private var anteriorX=0f
    private var anteriorY=0f
    private var inicioPincel:PointF?=null

    private var gpsLat: Double? = null
    private var gpsLon: Double? = null

    private val pontos = mutableListOf<PointF>()
    private val areas = mutableListOf<MutableList<PointF>>()
    private val coresAreas = mutableListOf<Int>()
    private val linhas = mutableListOf<MutableList<PointF>>()
    private val coresLinhas = mutableListOf<Int>()

    private var linhaAtual: MutableList<PointF>? = null

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
                viewport.fit(b.width,b.height,width,height)
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
            pontos.add(viewport.toMap(width/2f,height/2f))
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

        viewport.centerOn(p.x,p.y,width/2f,height/2f)
        invalidate()
    }

    fun reporOrientacao() {viewport.resetRotation(width/2f,height/2f);invalidate()}

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
        viewport.apply(c)

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
                val centro = viewport.toMap(width/2f,height/2f)
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

        if(e.pointerCount>1||gestos.active) {
            // Finish an existing stroke, but never paint/erase just because the first finger landed.
            if(linhaAtual!=null) salvar()
            linhaAtual=null;inicioPincel=null;verticeSelecionado=-1;arrastou=true
        }
        if(gestos.onTouch(e)) {invalidate();return true}
        val point=viewport.toMap(e.x,e.y)
        val x=point.x;val y=point.y

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
                    inicioPincel=PointF(x,y)
                }


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
                        viewport.pan(e.x-anteriorX,e.y-anteriorY)
                    }
                    1 -> if(arrastou) {
                        if(linhaAtual==null) {linhaAtual=mutableListOf(inicioPincel?:PointF(x,y));linhas.add(linhaAtual!!);coresLinhas.add(corAtual())}
                        linhaAtual?.add(PointF(x,y))
                    }
                    2 -> if(arrastou) apagar(x,y)
                }
                anteriorX = e.x
                anteriorY = e.y
            }

            MotionEvent.ACTION_CANCEL -> {verticeSelecionado=-1;inicioPincel=null;linhaAtual=null}
            MotionEvent.ACTION_UP -> {
                if (modo == 0 && !arrastou && hypot(e.x - inicioToqueX, e.y - inicioToqueY) < 14f * resources.displayMetrics.density) {
                    abrirAreaPintada(x, y)
                }
                if(modo==1&&linhaAtual==null) inicioPincel?.let {first->
                    if(hypot(first.x-x,first.y-y)>0.5f) {linhas.add(mutableListOf(first,PointF(x,y)));coresLinhas.add(corAtual())}
                }
                verticeSelecionado=-1;inicioPincel=null;linhaAtual=null
                if(modo==2&&!arrastou) apagar(x,y)
                if(modo==1||modo==2) salvar()
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

    private fun atualizarResumoMapa():Boolean {
        if (id.isEmpty()) return false
        val pref = context.getSharedPreferences("controle_colheita", 0).edit()
        val ha = hectaresPintados()
        if (ha != null) pref.putFloat("ultima_area_pintada_ha", ha.toFloat())
        else pref.remove("ultima_area_pintada_ha")
        pref.putString("ultimo_mapa", id).apply()
        val b = imagem ?: return false
        var saida:Bitmap?=null
        val destination=File(context.filesDir,"mapa_relatorio_$id.png")
        val draft=File(context.filesDir,"mapa_relatorio_$id.tmp")
        return try {
            val escalaImagem = minOf(1f, 2400f / maxOf(b.width, b.height))
            val w = maxOf(1, (b.width * escalaImagem).toInt())
            val h = maxOf(1, (b.height * escalaImagem).toInt())
            saida = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(saida)
            c.scale(w.toFloat() / b.width, h.toFloat() / b.height)
            c.drawBitmap(b, 0f, 0f, null)
            for (i in areas.indices) {
                c.drawPath(caminho(areas[i], true), Paint(3).apply { color = coresAreas.getOrElse(i) { 0x66FACC15 }; style = Paint.Style.FILL })
            }
            for (i in linhas.indices) {
                c.drawPath(caminho(linhas[i], false), Paint(3).apply { color = coresLinhas.getOrElse(i) { 0x66FACC15 }; style = Paint.Style.STROKE; strokeWidth = 17f; strokeCap = Paint.Cap.ROUND })
            }
            draft.outputStream().use {check(saida.compress(Bitmap.CompressFormat.PNG,100,it))}
            check(draft.renameTo(destination))
            true
        } catch (_: OutOfMemoryError) {false}
        catch (_: Exception) {false}
        finally {saida?.recycle();draft.delete()}
    }

    fun pinturaEmAndamento():Boolean = areaEmEdicao>=0||(modo==7&&pontos.isNotEmpty())

    fun salvar():Boolean {
        if (id.isEmpty()) return false

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
        val exported=atualizarResumoMapa()
        aoAtualizarArea?.invoke()
        return exported
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
