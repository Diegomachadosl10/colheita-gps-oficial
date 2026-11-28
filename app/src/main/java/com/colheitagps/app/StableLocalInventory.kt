package com.colheitagps.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.Collections

/** Read-only preflight for the current stable workspace, not the pre-team legacy importer.
 * No caller is wired into startup until server cutover and migration are homologated. */
internal class StableLocalInventory(context: Context) {
    private val app=context.applicationContext
    data class Attachment(val relativePath:String,val size:Long,val sha256:String)
    data class Snapshot(val uid:String,val team:String,val preferences:Map<String,Map<String,Any>>,
                        val synchronization:Map<String,Any>,val attachments:List<Attachment>,
                        val missing:Set<String>,val issues:Set<String>,val fingerprint:String) {
        val locallyConsistent get()=missing.isEmpty()&&issues.isEmpty()
        val attachmentBytes get()=attachments.sumOf {it.size}
    }
    private fun sha(bytes:ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {"%02x".format(it)}
    private fun immutable(values:Map<String,*>):Map<String,Any> = Collections.unmodifiableMap(values.entries.associate { (key,value)->
        require(value is String||value is Boolean||value is Int||value is Long||value is Float||value is Set<*>) {"Unsupported preference value"}
        if(value is Float) require(value.isFinite())
        if(value is Set<*>) require(value.all {it is String})
        key to (if(value is Set<*>) Collections.unmodifiableSet(value.toSet()) else value as Any)
    })
    fun capture(access:TeamAccess,team:String):Snapshot {
        require(TeamPolicy.allowed(access.uid,access.profile)&&access.mayUse(team)) {"Equipe não autorizada"}
        val uid=access.uid;val manages=access.manages
        require(uid.isNotBlank()&&uid.length<=128&&!uid.contains('/')&&!uid.contains('\\'))
        val originalRoot=File(app.filesDir,"equipes/$team").absoluteFile
        val root=File(app.filesDir.canonicalFile,"equipes/$team")
        require(root.canonicalPath==root.absolutePath) {"Source workspace contains a symlink"}
        val privateOwner=sha(uid.toByteArray())
        val preferences=linkedMapOf<String,Map<String,Any>>()
        val issues=sortedSetOf<String>();val missing=sortedSetOf<String>()
        val paths=sortedSetOf<String>();val photographs=sortedSetOf<String>()
        fun pref(name:String)=immutable(app.getSharedPreferences(name,Context.MODE_PRIVATE).all)
        fun safe(relative:String):File {
            require(relative.isNotBlank()&&!relative.startsWith('/')&&!relative.contains('\\')&&relative.split('/').none {it.isBlank()||it==".."||it=="."})
            val file=File(root,relative)
            require(file.canonicalPath==file.absolutePath&&file.canonicalPath.startsWith(root.path+File.separator)) {"Symlink or path outside source workspace"}
            return file
        }
        fun photo(path:String) {
            if(path.isBlank()) return
            try {
                val relative=if(path.startsWith("@arquivo/")) path.removePrefix("@arquivo/") else {
                    val source=File(path)
                    require(source.isAbsolute)
                    when {
                        source.path.startsWith(originalRoot.path+File.separator) -> source.relativeTo(originalRoot).invariantSeparatorsPath
                        source.path.startsWith(root.path+File.separator) -> source.relativeTo(root).invariantSeparatorsPath
                        else -> error("Attachment is outside source workspace")
                    }
                }
                require(File(relative).extension.lowercase() in setOf("jpg","jpeg","png","webp"))
                safe(relative);paths.add(relative);photographs.add(relative)
            } catch(_:Exception) {issues.add("ATTACHMENT_PATH_INVALID")}
        }
        fun media(value:Any?) {
            when(value) {
                is JSONObject -> value.keys().asSequence().forEach {key->
                    if(key in TeamContent.mediaFields||key=="cameraPeca") {
                        val field=value.opt(key)
                        when(field) {
                            is String -> photo(field)
                            is JSONArray -> (0 until field.length()).forEach {i->
                                if(field.opt(i) is String) photo(field.getString(i)) else issues.add("ATTACHMENT_VALUE_INVALID")
                            }
                            null,JSONObject.NULL -> Unit
                            else -> issues.add("ATTACHMENT_VALUE_INVALID")
                        }
                    } else media(value.opt(key))
                }
                is JSONArray -> (0 until value.length()).forEach {media(value.opt(it))}
            }
        }
        fun inspectJson(raw:String) {
            try {media(if(raw.trimStart().startsWith('[')) JSONArray(raw) else JSONObject(raw))}
            catch(_:Exception) {issues.add("OPERATIONAL_JSON_INVALID")}
        }
        val records=pref("equipe_${team}_registros_v3")
        if(manages&&records.keys.any {it !in TeamPolicy.recordGroups}) issues.add("UNKNOWN_RECORD_GROUP")
        preferences["registros_v3"]=immutable(records.filterKeys {it in TeamPolicy.recordGroups&&(manages||it in TeamPolicy.operatorGroups)})
        preferences.getValue("registros_v3").values.forEach {raw->
            if(raw !is String) issues.add("OPERATIONAL_VALUE_INVALID") else try {
                val rows=JSONArray(raw);val ids=mutableSetOf<String>()
                for(i in 0 until rows.length()) {
                    val row=rows.getJSONObject(i);val id=row.optString("id")
                    if(id.isBlank()||!ids.add(id)) issues.add("RECORD_ID_INVALID_OR_DUPLICATE")
                }
                media(rows)
            } catch(_:Exception) {issues.add("OPERATIONAL_JSON_INVALID")}
        }
        if(manages) {
            for(name in TeamPolicy.preferences.filter {it!="registros_v3"&&it!="mapas"&&it!="pinturas"}.sorted()) {
                preferences[name]=pref("equipe_${team}_$name")
                if(name=="controle_colheita") for(key in listOf("progresso","movimentos"))
                    preferences.getValue(name)[key]?.let {if(it is String) inspectJson(it) else issues.add("OPERATIONAL_VALUE_INVALID")}
            }
        }
        // Personal indexes are tied to this UID even on a phone previously used by another person.
        // Managers' shared indexes and this person's private indexes remain separate.
        val indexes=mutableListOf("privado/mapas" to "mapa_privado_${team}_${privateOwner}_mapas",
            "privado/pinturas" to "mapa_privado_${team}_${privateOwner}_pinturas")
        if(manages) indexes.addAll(listOf("mapas" to "equipe_${team}_mapas","pinturas" to "equipe_${team}_pinturas"))
        for((logical,source) in indexes) {
            preferences[logical]=pref(source)
            if(logical.endsWith("mapas")) for((id,name) in preferences.getValue(logical)) if(name is String) {
                try {require(id.isNotBlank()&&!id.contains('/')&&!id.contains('\\')&&id !in setOf(".",".."));val relative="mapas/$id.pdf";safe(relative);paths.add(relative)}
                catch(_:Exception) {issues.add("MAP_ID_INVALID")}
            }
        }
        // A cached mapa_relatorio_ID.png has no owner stamp. Do not infer ownership
        // from its filename; private painted previews must be regenerated from their paint index.
        val draft=pref("rascunhos_campo_${team}_$uid")
        preferences["rascunhos"]=immutable(draft.filterKeys {manages||it.startsWith("pendencia_")||it.startsWith("ocorrencia_")})
        preferences.getValue("rascunhos").values.forEach {if(it is String) inspectJson(it) else issues.add("DRAFT_VALUE_INVALID")}
        preferences["rascunho_peca"]=pref("rascunho_peca_${team}_$uid")
        preferences.getValue("rascunho_peca").values.forEach {if(it is String) inspectJson(it) else issues.add("DRAFT_VALUE_INVALID")}
        preferences["inicio"]=pref("inicio_${team}_$uid")
        val sync=linkedMapOf<String,Any>()
        for((key,value) in pref("sincronizacao_${uid}_$team")) {
            if(key.startsWith("n_")) continue // Notices need a separate server/device cutover, never blind replay.
            if(key.startsWith("b_")||key.startsWith("q_")||key.startsWith("c_")) {
                if(value !is String) {issues.add("SYNC_VALUE_INVALID");continue}
                try {
                    val wire=JSONObject(value)
                    val allowed=manages||(wire.optString("tipo")=="item"&&wire.optString("grupo") in TeamPolicy.operatorGroups)
                    if(allowed) {
                        sync[key]=value
                        // Local pending/conflicting records may contain attachments absent from current lists.
                        if(wire.optString("tipo")=="item") inspectJson(wire.getString("conteudo"))
                    }
                } catch(_:Exception) {issues.add("SYNC_JSON_INVALID")}
            } else if(key.startsWith("e_")) sync[key]=value
            // File transfer fingerprints belong to the old service. Media remains local on Spark.
        }
        // Sidecars preserve the original photograph as well as its marked version.
        val photosToCheck=java.util.ArrayDeque(photographs)
        val checkedPhotos=mutableSetOf<String>();val provenance=mutableMapOf<String,String>()
        while(photosToCheck.isNotEmpty()) {
            val relative=photosToCheck.removeFirst()
            if(!checkedPhotos.add(relative)) continue
            try {
                val image=safe(relative);val sidecar=File(image.path+".markup.json")
                if(sidecar.exists()) {
                    val sideRelative=relative+".markup.json";safe(sideRelative)
                    require(sidecar.length()<=262144)
                    paths.add(sideRelative)
                    val original=PhotoMarkup.open(root,image).original
                    require(original.extension.lowercase() in setOf("jpg","jpeg","png","webp"))
                    val source=original.relativeTo(root).invariantSeparatorsPath;safe(source);paths.add(source)
                    provenance[relative]=source;photosToCheck.add(source)
                }
            } catch(_:Exception) {issues.add("PHOTO_ORIGINAL_INVALID")}
        }
        for(start in provenance.keys) {
            var current=start;val chain=mutableSetOf<String>()
            while(provenance.containsKey(current)) {
                if(!chain.add(current)) {issues.add("PHOTO_ORIGINAL_CYCLE");break}
                current=provenance.getValue(current)
            }
        }
        val files=paths.mapNotNull {relative->
            try {
                val file=safe(relative)
                if(!file.isFile) {missing.add(relative);null} else {
                    val before=file.length() to file.lastModified()
                    val digest=MessageDigest.getInstance("SHA-256")
                    file.inputStream().use {input->val buffer=ByteArray(65536);while(true) {val count=input.read(buffer);if(count<0) break;digest.update(buffer,0,count)}}
                    require(before==(file.length() to file.lastModified())) {"Source changed during capture"}
                    Attachment(relative,file.length(),digest.digest().joinToString("") {"%02x".format(it)})
                }
            } catch(_:Exception) {issues.add("ATTACHMENT_UNSTABLE_OR_UNREADABLE");null}
        }
        val digest=MessageDigest.getInstance("SHA-256")
        fun feed(text:String) {val bytes=text.toByteArray();digest.update(ByteBuffer.allocate(4).putInt(bytes.size).array());digest.update(bytes)}
        fun values(name:String,data:Map<String,Any>) {
            feed(name);feed(data.size.toString())
            data.toSortedMap().forEach {(key,value)->
                feed(key)
                when(value) {
                    is Set<*> -> {feed("set");feed(value.size.toString());value.map {it as String}.sorted().forEach(::feed)}
                    else -> {feed(value.javaClass.name);feed(value.toString())}
                }
            }
        }
        feed("stable-inventory-v1");feed(uid);feed(team);feed(access.role)
        feed(preferences.size.toString());preferences.toSortedMap().forEach {(name,data)->values(name,data)};values("sync",sync)
        feed(files.size.toString())
        files.forEach {feed(it.relativePath);feed(it.size.toString());feed(it.sha256)}
        feed(missing.size.toString());missing.forEach(::feed);feed(issues.size.toString());issues.forEach(::feed)
        return Snapshot(uid,team,Collections.unmodifiableMap(preferences),immutable(sync),Collections.unmodifiableList(files),
            Collections.unmodifiableSet(missing),Collections.unmodifiableSet(issues),digest.digest().joinToString("") {"%02x".format(it)})
    }
}
