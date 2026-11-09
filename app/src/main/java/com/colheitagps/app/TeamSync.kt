package com.colheitagps.app

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.StorageMetadata
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Durable per-record queue. Writes compare revisions; conflicts preserve the local copy. */
internal class TeamSync(context: Context, private val access: TeamAccess, val store: TeamStore,
    private val changed: () -> Unit, private val status: (String) -> Unit) {
    private val local = context.applicationContext.getSharedPreferences("sincronizacao_${access.uid}_${store.team}", 0)
    private val db = FirebaseFirestore.getInstance()
    private val records = db.collection("equipes").document(store.team).collection("registros")
    private val worker = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private var subscription: ListenerRegistration? = null
    @Volatile private var stopped = false
    @Volatile private var available = false
    private var ready = false
    private var applying = false
    private val remote = linkedMapOf<String, JSONObject>()
    private val preferences = TeamPolicy.preferences.associateWith { store.prefs(it) }
    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> if (!applying && !stopped) execute { scan(); flush() } }
    private val poll = object : Runnable {
        override fun run() { if (!stopped) { execute { scan(); flush(); scanFiles() }; handler.postDelayed(this, 12000) } }
    }
    private fun execute(action: () -> Unit) {
        if (!stopped) try { worker.execute {
            if (active()) try { action() } catch (e: Exception) { announce("Envio pendente. Confira a conexão e toque em Sincronização.") }
        } } catch (_: java.util.concurrent.RejectedExecutionException) { /* Session already closed. */ }
    }
    private fun active() = !stopped && FirebaseAuth.getInstance().currentUser?.uid == access.uid
    private fun announce(message: String) { handler.post { if (active()) status(message) } }
    private fun notifyChanges() { handler.post { if (active()) changed() } }
    private fun hash(value: ByteArray) = MessageDigest.getInstance("SHA-256").digest(value).joinToString("") { "%02x".format(it) }
    private fun hash(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
    private fun id(kind: String, group: String, key: String) = hash("$kind|$group|$key".toByteArray())
    private fun base(id: String) = local.getString("b_$id", null)?.let(::JSONObject)
    private fun permitted(group: String) = access.manages || group in TeamPolicy.operatorGroups
    private fun editable(d: JSONObject) = access.manages || d.optString("autorUid") == access.uid || d.optString("grupo") == "pinturas" ||
        (d.optString("grupo") == "arquivos" && d.optString("chave").matches(Regex("mapa_relatorio_[0-9a-f]{64}[.]png")))
    private fun proposed(kind: String, group: String, key: String, content: String, deleted: Boolean = false): JSONObject = JSONObject()
        .put("tipo", kind).put("grupo", group).put("chave", key).put("conteudo", content).put("excluido", deleted)
    fun start() {
        preferences.values.forEach { it.registerOnSharedPreferenceChangeListener(prefListener) }
        val query = if (access.manages) records else records.whereIn("grupo", TeamPolicy.operatorGroups.toList())
        subscription = query.addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
            if (!active()) return@addSnapshotListener
            if (error != null) { announce("Sincronização indisponível. Confira a conexão ou avise o administrador."); return@addSnapshotListener }
            if (snapshot == null || snapshot.metadata.isFromCache || snapshot.metadata.hasPendingWrites()) return@addSnapshotListener
            execute {
                scan()
                for (d in snapshot.documents) remote[d.id] = JSONObject(d.data.orEmpty())
                ready = true
                applyRemote(); flush()
            }
        }
        handler.post(poll)
    }
    fun available(value: Boolean) { available = value; if (value) execute { scan(); applyRemote(); flush() } }
    fun retry() { execute { scan(); applyRemote(); flush(); scanFiles() } }
    fun stop() {
        stopped = true; subscription?.remove(); handler.removeCallbacks(poll)
        preferences.values.forEach { it.unregisterOnSharedPreferenceChangeListener(prefListener) }
        worker.shutdown()
    }
    private fun queue(key: String, value: JSONObject) {
        if (local.contains("c_$key")) return
        val old = base(key)
        if (old != null && !editable(old)) {
            announce("Somente o autor ou o supervisor pode alterar esse registro.")
            local.edit().remove("b_$key").commit(); remote[key] = old
            return
        }
        if (value.optString("conteudo").toByteArray().size >= 740000) {
            local.edit().putString("e_item_$key", "Registro maior que o limite de envio").commit()
            announce("Registro muito grande. Divida as informações antes de enviar."); return
        }
        local.edit().remove("e_item_$key").commit()
        val pending = value.put("base", value.optLong("__syncBase", old?.optLong("versao") ?: 0L))
            .put("autorUid", old?.optString("autorUid") ?: access.uid)
        local.edit().putString("q_$key", pending.toString()).commit()
    }
    private fun scan() {
        if (applying || !active()) return
        val now = linkedMapOf<String, JSONObject>()
        for ((name, prefs) in preferences) for ((key, raw) in prefs.all) {
            if (raw !is String) continue
            if (name == "registros_v3" || (name == "controle_colheita" && key == "movimentos")) {
                val group = if (name == "registros_v3") key else "movimentos"
                if (group !in TeamPolicy.recordGroups || !permitted(group)) continue
                val arr = try { JSONArray(raw) } catch (_: Exception) { continue }
                var assigned = false
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    if (obj.optString("id").isBlank()) { obj.put("id", UUID.randomUUID().toString()); assigned = true }
                    val recordKey = obj.getString("id")
                    val wire = proposed("item", group, recordKey, store.encode(obj.toString()))
                    if (obj.has("__syncBase")) wire.put("__syncBase", obj.getLong("__syncBase"))
                    now[id("item", group, recordKey)] = wire
                }
                if (assigned) { applying = true; prefs.edit().putString(key, arr.toString()).commit(); applying = false }
            } else if (name in setOf("mapas", "pinturas", "ordens_servico", "talhoes_por_os") && permitted(name)) {
                now[id("preferencia", name, key)] = proposed("preferencia", name, key, store.encode(raw))
            }
        }
        for ((key, value) in now) {
            val old = base(key)
            if (old == null || old.optBoolean("excluido") || store.encode(store.decode(old.optString("conteudo"))) != value.getString("conteudo")) queue(key, value)
        }
        for (key in local.all.keys.filter { it.startsWith("e_item_") && it.removePrefix("e_item_") !in now })
            local.edit().remove(key).commit()
        for ((prefKey, value) in local.all) {
            if (!prefKey.startsWith("b_") || value !is String) continue
            val key = prefKey.removePrefix("b_"); val old = JSONObject(value)
            if (old.optString("tipo") != "arquivo" && !old.optBoolean("excluido") && key !in now && permitted(old.optString("grupo")))
                queue(key, proposed(old.getString("tipo"), old.getString("grupo"), old.getString("chave"), old.getString("conteudo"), true))
        }
    }
    private fun flush() {
        if (!ready || !active()) return
        for ((prefKey, raw) in local.all) {
            if (!prefKey.startsWith("q_") || raw !is String || !active()) continue
            val key = prefKey.removePrefix("q_"); val change = JSONObject(raw)
            if (local.contains("c_$key")) continue
            try {
                val written = Tasks.await(db.runTransaction { tx ->
                    check(active())
                    val doc = tx.get(records.document(key))
                    val revision = (doc.get("versao") as? Number)?.toLong() ?: 0L
                    check(revision == change.getLong("base")) { "CONFLITO_DE_VERSAO" }
                    val data = mutableMapOf<String, Any>(
                        "equipeId" to store.team, "frenteId" to store.team.substringBeforeLast('_'), "turno" to store.team.substringAfterLast('_'),
                        "tipo" to change.getString("tipo"), "grupo" to change.getString("grupo"), "chave" to change.getString("chave"),
                        "conteudo" to change.getString("conteudo"), "excluido" to change.getBoolean("excluido"),
                        "autorUid" to change.getString("autorUid"), "editadoPor" to access.uid,
                        "versao" to revision + 1, "atualizadoEm" to FieldValue.serverTimestamp()
                    )
                    tx.set(records.document(key), data)
                    data.remove("atualizadoEm")
                    JSONObject(data)
                }, 25, TimeUnit.SECONDS)
                synchronized(store.lock) { local.edit().putString("b_$key", written.toString()).remove(prefKey).commit() }
                remote.remove(key)
            } catch (e: Exception) {
                var cause: Throwable? = e
                while (cause != null && cause.message?.contains("CONFLITO_DE_VERSAO") != true) cause = cause.cause
                if (cause != null) {
                    val copy = File(store.files, "conflitos/${System.currentTimeMillis()}_$key.json").apply { parentFile?.mkdirs() }
                    copy.writeText(change.toString(2))
                    local.edit().putString("c_$key", change.toString()).remove(prefKey).commit()
                    announce("Há alterações simultâneas. Sua cópia foi preservada em Sincronização.")
                } else { announce("Envio pendente. Confira a conexão ou avise o administrador."); break }
            }
        }
        applyRemote()
        val pending = local.all.keys.count { it.startsWith("q_") }
        val conflicts = local.all.keys.count { it.startsWith("c_") }
        val errors = local.all.keys.count { it.startsWith("e_") }
        if (errors > 0) announce("$errors registros ou anexos permanecem somente neste aparelho. Toque em Sincronização.")
        else if (conflicts == 0) announce(if (pending == 0) "Registros sincronizados" else "$pending alterações aguardando envio")
    }
    private fun applyRemote() {
        if (!available || !active()) return
        var changed = false
        for ((key, data) in remote.toMap()) {
            if (local.contains("q_$key") || local.contains("c_$key")) continue
            val old = base(key)
            if (old != null && old.optLong("versao") >= data.optLong("versao")) { remote.remove(key); continue }
            if (!permitted(data.optString("grupo"))) continue
            applying = true
            try {
                if (!available) break
                if (data.getString("tipo") == "arquivo") {
                    download(data)
                    local.edit().putString("b_$key", data.toString()).commit()
                    remote.remove(key); changed = true
                    continue
                }
                synchronized(store.lock) {
                if (!available) return@synchronized
                when (data.getString("tipo")) {
                    "item" -> {
                        val group = data.getString("grupo")
                        val prefs = preferences.getValue(if (group == "movimentos") "controle_colheita" else "registros_v3")
                        val prefKey = if (group == "movimentos") "movimentos" else group
                        val current = JSONArray(prefs.getString(prefKey, "[]")); val result = JSONArray(); var found = false
                        for (i in 0 until current.length()) {
                            val obj = current.optJSONObject(i) ?: continue
                            if (obj.optString("id") == data.getString("chave")) {
                                found = true
                                if (!data.optBoolean("excluido")) result.put(JSONObject(store.decode(data.getString("conteudo"))))
                            } else result.put(obj)
                        }
                        if (!found && !data.optBoolean("excluido")) result.put(JSONObject(store.decode(data.getString("conteudo"))))
                        prefs.edit().putString(prefKey, result.toString()).commit()
                    }
                    "preferencia" -> {
                        val edit = preferences.getValue(data.getString("grupo")).edit()
                        if (data.optBoolean("excluido")) edit.remove(data.getString("chave"))
                        else edit.putString(data.getString("chave"), store.decode(data.getString("conteudo")))
                        edit.commit()
                    }
                }
                local.edit().putString("b_$key", data.toString()).commit()
                remote.remove(key); changed = true
                }
            } catch (_: Exception) { announce("Alguns anexos aguardam conexão. Toque em Sincronização para tentar novamente.") }
            finally { applying = false }
        }
        if (changed) notifyChanges()
    }
    private fun download(data: JSONObject) {
        if (data.optBoolean("excluido")) {
            val path = data.getString("chave")
            val file = store.file(path)
            if (file.isFile && local.getString("f_$path", null)?.let { it != hash(file) } == true) {
                val copy = File(store.files, "conflitos/${System.currentTimeMillis()}_${file.name}")
                copy.parentFile?.mkdirs(); file.copyTo(copy)
                announce("O anexo foi removido da equipe, mas sua alteração local foi preservada.")
            }
            file.delete()
            local.edit().remove("f_$path").remove("m_$path").commit()
            return
        }
        val content = JSONObject(data.getString("conteudo"))
        val path = data.getString("chave")
        val file = store.file(path)
        val digest = content.getString("hash")
        val storagePath = content.getString("storage")
        require(storagePath.startsWith("equipes/${store.team}/arquivos/") && !storagePath.contains(".."))
        if (file.isFile && hash(file) == digest) { local.edit().putString("f_$path", digest).putString("m_$path", "${file.lastModified()}:${file.length()}").commit(); return }
        file.parentFile?.mkdirs()
        val temporary = File(store.cache, UUID.randomUUID().toString())
        try {
            Tasks.await(FirebaseStorage.getInstance().reference.child(storagePath).getFile(temporary), 35, TimeUnit.SECONDS)
            check(temporary.length() < 100L * 1024 * 1024 && hash(temporary) == digest)
            if (file.isFile && local.getString("f_$path", null)?.let { it != hash(file) } == true) {
                val copy = File(store.files, "conflitos/${System.currentTimeMillis()}_${file.name}")
                copy.parentFile?.mkdirs(); file.copyTo(copy)
                announce("Um anexo foi alterado em dois aparelhos. Sua cópia anterior foi preservada.")
            }
            temporary.copyTo(file, overwrite = true)
            local.edit().putString("f_$path", digest).putString("m_$path", "${file.lastModified()}:${file.length()}").commit()
        } finally { temporary.delete() }
    }
    private fun scanFiles() {
        if (!ready || !active()) return
        for (key in local.all.keys.filter { it.startsWith("e_arquivo_") })
            if (!store.file(key.removePrefix("e_arquivo_")).exists()) local.edit().remove(key).commit()
        for ((key, raw) in local.all) {
            if (!key.startsWith("b_") || raw !is String) continue
            val data = JSONObject(raw)
            if (data.optString("tipo") != "arquivo" || data.optBoolean("excluido")) continue
            val path = data.getString("chave")
            if (local.contains("f_$path") && !store.file(path).exists())
                queue(key.removePrefix("b_"), proposed("arquivo", "arquivos", path, data.getString("conteudo"), true))
        }
        flush()
        for (file in store.files.walkTopDown().onEnter { it.name != "conflitos" }.filter { it.isFile }) {
            if (!active()) return
            val ext = file.extension.lowercase()
            if (ext !in setOf("jpg", "jpeg", "png", "pdf")) continue
            val path = file.relativeTo(store.files).invariantSeparatorsPath
            if (file.length() >= 100L * 1024 * 1024) {
                local.edit().putString("e_arquivo_$path", "Arquivo com 100 MB ou mais").commit()
                announce("Um arquivo supera 100 MB e permanece salvo apenas neste aparelho."); continue
            }
            local.edit().remove("e_arquivo_$path").commit()
            val fingerprint = "${file.lastModified()}:${file.length()}"
            if (local.contains("f_$path") && local.getString("m_$path", null) == fingerprint) continue
            val digest = hash(file)
            if (local.getString("f_$path", null) == digest) { local.edit().putString("m_$path", fingerprint).commit(); continue }
            val contentType = when (ext) { "pdf" -> "application/pdf"; "png" -> "image/png"; else -> "image/jpeg" }
            val storagePath = "equipes/${store.team}/arquivos/${access.uid}/$digest"
            val ref = FirebaseStorage.getInstance().reference.child(storagePath)
            val snapshot = File(store.cache, UUID.randomUUID().toString())
            try {
                file.copyTo(snapshot)
                if (hash(snapshot) != digest) continue
                try { Tasks.await(ref.metadata, 10, TimeUnit.SECONDS) }
                catch (e: Exception) {
                    val storageError = e.cause as? StorageException ?: e as? StorageException
                    if (storageError?.errorCode != StorageException.ERROR_OBJECT_NOT_FOUND) throw e
                    Tasks.await(ref.putFile(Uri.fromFile(snapshot), StorageMetadata.Builder().setContentType(contentType).build()), 45, TimeUnit.SECONDS)
                }
                val content = JSONObject().put("hash", digest).put("storage", storagePath).toString()
                queue(id("arquivo", "arquivos", path), proposed("arquivo", "arquivos", path, content))
                flush()
                if (base(id("arquivo", "arquivos", path))?.optString("conteudo") == content) {
                    val edit = local.edit().putString("f_$path", digest)
                    if (fingerprint == "${file.lastModified()}:${file.length()}") edit.putString("m_$path", fingerprint)
                    edit.commit()
                }
            } catch (_: Exception) { announce("Fotos e mapas aguardam envio. Confira a conexão ou avise o administrador."); return }
            finally { snapshot.delete() }
        }
        if (local.all.keys.none { it.startsWith("q_") || it.startsWith("c_") || it.startsWith("e_") }) announce("Registros e anexos sincronizados")
    }
    fun baseRevision(group: String, key: String): Long = base(id("item", group, key))?.optLong("versao") ?: 0L
    fun canEdit(group: String, key: String): Boolean {
        if (access.manages) return true
        val record = base(id("item", group, key)) ?: return true
        return editable(record)
    }
    fun canEditMap(key: String): Boolean = base(id("preferencia", "mapas", key))?.let(::editable) ?: true
    fun diagnostics(): String = "${TeamPolicy.label(store.team)}\n" +
        "${local.all.keys.count { it.startsWith("q_") }} alterações aguardando envio\n" +
        "${local.all.keys.count { it.startsWith("c_") }} conflitos preservados\n" +
        "${local.all.keys.count { it.startsWith("e_") }} registros ou arquivos acima do limite de envio\n" +
        "Os anexos são enviados quando há conexão. Alterações simultâneas não sobrescrevem sua cópia local."
    fun useSharedCopies() { execute {
        for (key in local.all.keys.filter { it.startsWith("c_") }) {
            val id = key.removePrefix("c_")
            val doc = Tasks.await(records.document(id).get(Source.SERVER), 15, TimeUnit.SECONDS)
            local.edit().remove(key).remove("q_$id").remove("b_$id").commit()
            if (doc.exists()) remote[id] = JSONObject(doc.data.orEmpty())
        }
        applyRemote()
    } }
}
