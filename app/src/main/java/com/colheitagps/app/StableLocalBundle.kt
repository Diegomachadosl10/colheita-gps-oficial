package com.colheitagps.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.Collections
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/** Private recovery checkpoint. It never installs preferences, starts sync or activates the Beta. */
internal class StableLocalBundle(context:Context,
    private val capture:(TeamAccess,String)->StableLocalInventory.Snapshot=StableLocalInventory(context)::capture) {
    private val app=context.applicationContext
    data class Receipt(val archive:File,val sha256:String,val uid:String,val team:String,val role:String,val fingerprint:String)
    data class Restoration(val directory:File,val snapshot:StableLocalInventory.Snapshot)
    companion object {
        private const val MANIFEST="snapshot.json"
        private const val MAX_MANIFEST=32*1024*1024
        private const val MAX_BYTES=1024L*1024*1024
        private const val MAX_FILES=10000
        private val locks=java.util.concurrent.ConcurrentHashMap<String,Any>()
        private val digestPattern=Regex("[0-9a-f]{64}")
    }
    private fun lock(uid:String,team:String)=locks.computeIfAbsent("$uid/$team") {Any()}
    private fun digest()=MessageDigest.getInstance("SHA-256")
    private fun hex(bytes:ByteArray)=bytes.joinToString("") {"%02x".format(it)}
    private fun hash(file:File):String=file.inputStream().use {input->
        val d=digest();val buffer=ByteArray(65536)
        while(true) {val n=input.read(buffer);if(n<0) break;d.update(buffer,0,n)}
        hex(d.digest())
    }
    private fun authorized(access:TeamAccess,team:String) {
        require(TeamPolicy.allowed(access.uid,access.profile)&&access.mayUse(team)) {"Equipe não autorizada"}
        require(access.uid.isNotBlank()&&access.uid.length<=128&&!access.uid.contains('/')&&!access.uid.contains('\\'))
    }
    private fun relative(path:String) {
        require(path.isNotBlank()&&!path.startsWith('/')&&!path.contains('\\')&&path.split('/').none {it in setOf("",".","..")}) {"Caminho inválido"}
    }
    private fun checked(root:File,path:String):File {
        relative(path)
        return File(root,path).also {require(it.absolutePath==it.canonicalPath&&it.canonicalPath.startsWith(root.path+File.separator)) {"Caminho fora da pasta ou link simbólico"}}
    }
    private fun privatePermissions(file:File,directory:Boolean=false) {
        check(file.setReadable(false,false)&&file.setReadable(true,true)&&file.setWritable(false,false)&&file.setWritable(true,true)
            &&file.setExecutable(false,false)&&(!directory||file.setExecutable(true,true))) {"Não foi possível proteger a cópia privada"}
    }
    private fun folder(file:File):File {
        require(file.absolutePath==file.canonicalPath) {"Pasta contém link simbólico"}
        check((file.isDirectory||file.mkdirs())&&file.isDirectory)
        privatePermissions(file,true)
        return file
    }
    private fun bucket(uid:String,team:String):File {
        val root=folder(File(app.noBackupFilesDir.canonicalFile,"beta_transition"))
        val owner=hex(digest().digest(uid.toByteArray(Charsets.UTF_8)))
        return folder(checked(folder(checked(root,owner)),team))
    }
    private fun values(data:Map<String,Any>)=JSONObject().apply {
        for((key,value) in data.toSortedMap()) {
            val type=when(value) {is String->"string";is Boolean->"boolean";is Int->"int";is Long->"long";is Float->"float";is Set<*>->"set";else->error("Preferência incompatível")}
            val stored=when(value) {
                is Set<*> -> JSONArray(value.map {it as String}.sorted())
                is Float -> java.lang.Float.toHexString(value.also {require(it.isFinite())})
                else -> value
            }
            put(key,JSONObject().put("type",type).put("value",stored))
        }
    }
    private fun readValues(data:JSONObject):Map<String,Any> = Collections.unmodifiableMap(data.keys().asSequence().associateWith {key->
        val item=data.getJSONObject(key);val value=item.get("value")
        when(item.getString("type")) {
            "string" -> (value as? String)?:error("Texto inválido")
            "boolean" -> (value as? Boolean)?:error("Booleano inválido")
            "int" -> (value as? Int)?:error("Inteiro inválido")
            "long" -> {require(value is Int||value is Long);item.getLong("value")}
            "float" -> {require(value is String);java.lang.Float.valueOf(value).also {require(it.isFinite())}}
            "set" -> {
                require(value is JSONArray)
                val strings=(0 until value.length()).map {value.get(it) as? String?:error("Conjunto inválido")}
                require(strings.size==strings.toSet().size)
                Collections.unmodifiableSet(strings.toSet())
            }
            else -> error("Tipo de preferência incompatível")
        }
    })
    private fun manifest(snapshot:StableLocalInventory.Snapshot,role:String):ByteArray=JSONObject()
        .put("schema",1).put("uid",snapshot.uid).put("team",snapshot.team).put("role",role).put("fingerprint",snapshot.fingerprint)
        .put("preferences",JSONObject().apply {snapshot.preferences.toSortedMap().forEach {(name,data)->put(name,values(data))}})
        .put("synchronization",values(snapshot.synchronization))
        .put("attachments",JSONArray().apply {snapshot.attachments.forEach {put(JSONObject().put("path",it.relativePath).put("size",it.size).put("sha256",it.sha256))}})
        .toString().toByteArray(Charsets.UTF_8).also {require(it.size<=MAX_MANIFEST) {"Índice da cópia excede o limite"}}

    private fun readManifest(input:InputStream):JSONObject {
        val bytes=ByteArrayOutputStream();val buffer=ByteArray(65536)
        while(true) {val n=input.read(buffer);if(n<0) break;require(bytes.size().toLong()+n<=MAX_MANIFEST);bytes.write(buffer,0,n)}
        return JSONObject(bytes.toString(Charsets.UTF_8.name()))
    }
    private fun consume(input:InputStream,size:Long,output:OutputStream?=null):String {
        val d=digest();val buffer=ByteArray(65536);var count=0L
        while(true) {
            val n=input.read(buffer);if(n<0) break
            count+=n;require(count<=size) {"Arquivo maior que o inventário"}
            d.update(buffer,0,n);output?.write(buffer,0,n)
        }
        require(count==size) {"Arquivo incompleto"}
        return hex(d.digest())
    }
    private fun inspect(archive:File,access:TeamAccess,receipt:Receipt,target:File?=null):StableLocalInventory.Snapshot {
        require(archive.length()<=MAX_BYTES+MAX_MANIFEST+MAX_FILES*8192L+1024*1024) {"Cópia excede o limite"}
        ZipFile(archive).use {zip->
            val entries=zip.entries().asSequence().toList()
            require(entries.size in 1..MAX_FILES+1&&entries.none {it.isDirectory}) {"Índice ZIP inválido"}
            require(entries.map {it.name}.toSet().size==entries.size) {"Entradas ZIP repetidas"}
            val header=zip.getEntry(MANIFEST)?:error("Índice ausente")
            require(header.size in 0..MAX_MANIFEST.toLong())
            val json=zip.getInputStream(header).use(::readManifest)
            require(json.opt("schema")==1&&json.getString("uid")==access.uid&&json.getString("team")==receipt.team
                &&json.getString("role")==access.role&&json.getString("fingerprint")==receipt.fingerprint) {"Cópia de outro usuário, papel ou equipe"}
            val files=json.getJSONArray("attachments");require(files.length()<=MAX_FILES)
            val attachments=(0 until files.length()).map {i->val row=files.getJSONObject(i)
                val path=row.getString("path");relative(path)
                require(row.get("size") is Int||row.get("size") is Long)
                val size=row.getLong("size");val sha=row.getString("sha256")
                require(size in 0..MAX_BYTES&&sha.matches(digestPattern))
                StableLocalInventory.Attachment(path,size,sha)
            }
            require(attachments.map {it.relativePath}.toSet().size==attachments.size)
            require(attachments.sumOf {it.size}<=MAX_BYTES)
            require(entries.map {it.name}.toSet()==attachments.map {"files/${it.relativePath}"}.toSet()+MANIFEST) {"Conteúdo ZIP não pertence ao inventário"}
            val preferences=json.getJSONObject("preferences")
            val snapshot=StableLocalInventory.Snapshot(access.uid,receipt.team,
                Collections.unmodifiableMap(preferences.keys().asSequence().associateWith {readValues(preferences.getJSONObject(it))}),
                readValues(json.getJSONObject("synchronization")),Collections.unmodifiableList(attachments),emptySet(),emptySet(),receipt.fingerprint)
            for(file in attachments) {
                val entry=zip.getEntry("files/${file.relativePath}");require(entry.size==file.size)
                val destination=target?.let {checked(it,"files/${file.relativePath}")}
                if(destination!=null) {folder(destination.parentFile!!);check(destination.createNewFile());privatePermissions(destination)}
                val actual=zip.getInputStream(entry).use {input->
                    if(destination==null) consume(input,file.size) else FileOutputStream(destination).use {out->consume(input,file.size,out).also {out.fd.sync()}}
                }
                require(actual==file.sha256) {"Arquivo corrompido"}
            }
            if(target!=null) FileOutputStream(checked(target,MANIFEST)).use {out->out.write(json.toString().toByteArray(Charsets.UTF_8));out.fd.sync()}
                .also {privatePermissions(checked(target,MANIFEST))}
            return snapshot
        }
    }

    private fun readReceipt(access:TeamAccess,team:String,fingerprint:String):Receipt {
        require(fingerprint.matches(digestPattern))
        val root=bucket(access.uid,team)
        val file=checked(root,"$fingerprint.receipt.json")
        require(file.isFile&&file.length()<=4096) {"Comprovante da cópia ausente ou inválido"}
        val json=JSONObject(file.readText(Charsets.UTF_8))
        require(json.opt("schema")==1&&json.getString("uid")==access.uid&&json.getString("team")==team
            &&json.getString("role")==access.role&&json.getString("fingerprint")==fingerprint) {"Comprovante de outro usuário, papel ou equipe"}
        val sha=json.getString("sha256");require(sha.matches(digestPattern))
        return Receipt(checked(root,"$fingerprint.zip"),sha,access.uid,team,access.role,fingerprint)
    }
    private fun saveReceipt(receipt:Receipt,access:TeamAccess):Receipt {
        val root=receipt.archive.parentFile!!
        val file=checked(root,"${receipt.fingerprint}.receipt.json")
        if(file.exists()) {require(readReceipt(access,receipt.team,receipt.fingerprint)==receipt) {"Comprovante existente diverge da cópia"};return receipt}
        val temporary=File.createTempFile("receipt-",".partial",root)
        try {
            privatePermissions(temporary)
            val json=JSONObject().put("schema",1).put("uid",receipt.uid).put("team",receipt.team).put("role",receipt.role)
                .put("fingerprint",receipt.fingerprint).put("sha256",receipt.sha256)
            FileOutputStream(temporary).use {out->out.write(json.toString().toByteArray(Charsets.UTF_8));out.fd.sync()}
            check(!file.exists()&&temporary.renameTo(file))
            return receipt
        } finally {temporary.delete()}
    }
    /** Reopens a checkpoint after process restart, including when the original files are gone. */
    fun open(access:TeamAccess,team:String,fingerprint:String):Receipt = synchronized(lock(access.uid,team)) {
        authorized(access,team)
        val receipt=readReceipt(access,team,fingerprint)
        require(receipt.archive.isFile&&hash(receipt.archive)==receipt.sha256) {"Cópia ausente ou alterada"}
        inspect(receipt.archive,access,receipt)
        receipt
    }

    fun create(access:TeamAccess,team:String):Receipt = synchronized(lock(access.uid,team)) {
        authorized(access,team)
        val snapshot=capture(access,team)
        require(snapshot.uid==access.uid&&snapshot.team==team&&snapshot.locallyConsistent&&snapshot.fingerprint.matches(digestPattern)) {"Origem ainda precisa de revisão"}
        require(snapshot.attachments.size<=MAX_FILES&&snapshot.attachmentBytes<=MAX_BYTES)
        val bytes=manifest(snapshot,access.role)
        val root=bucket(access.uid,team);val archive=checked(root,"${snapshot.fingerprint}.zip")
        val receipt=Receipt(archive,"",access.uid,team,access.role,snapshot.fingerprint)
        if(archive.exists()) {
            require(archive.isFile&&inspect(archive,access,receipt)==snapshot) {"Cópia existente diverge da origem"}
            require(capture(access,team).fingerprint==snapshot.fingerprint) {"Origem mudou durante a conferência"}
            return@synchronized saveReceipt(receipt.copy(sha256=hash(archive)),access)
        }
        val temporary=File.createTempFile("checkpoint-",".partial",root)
        try {
            privatePermissions(temporary)
            FileOutputStream(temporary).use {out->
                ZipOutputStream(out).use {zip->
                    zip.setLevel(0)
                    zip.putNextEntry(ZipEntry(MANIFEST));zip.write(bytes);zip.closeEntry()
                    val sourceRoot=File(app.filesDir.canonicalFile,"equipes/$team")
                    require(sourceRoot.absolutePath==sourceRoot.canonicalPath)
                    for(file in snapshot.attachments) {
                        val source=checked(sourceRoot,file.relativePath);require(source.isFile)
                        zip.putNextEntry(ZipEntry("files/${file.relativePath}"))
                        val actual=source.inputStream().use {consume(it,file.size,zip)}
                        require(actual==file.sha256) {"Arquivo de origem mudou durante a cópia"}
                        zip.closeEntry()
                    }
                    zip.finish();zip.flush();out.fd.sync()
                }
            }
            require(inspect(temporary,access,receipt)==snapshot) {"Cópia não corresponde ao inventário"}
            require(capture(access,team).fingerprint==snapshot.fingerprint) {"Origem mudou durante a cópia"}
            val sha=hash(temporary)
            check(!archive.exists()&&temporary.renameTo(archive)) {"Não foi possível finalizar a cópia"}
            saveReceipt(receipt.copy(sha256=sha),access)
        } finally {temporary.delete()}
    }

    fun restoreQuarantine(receipt:Receipt,access:TeamAccess):Restoration = synchronized(lock(access.uid,receipt.team)) {
        authorized(access,receipt.team)
        require(receipt.uid==access.uid&&receipt.role==access.role&&receipt.fingerprint.matches(digestPattern)&&receipt.sha256.matches(digestPattern)) {"Cópia de outro usuário ou papel"}
        val root=bucket(access.uid,receipt.team)
        val archive=checked(root,"${receipt.fingerprint}.zip")
        require(receipt==readReceipt(access,receipt.team,receipt.fingerprint)
            &&receipt.archive.absolutePath==archive.path&&archive.isFile&&hash(archive)==receipt.sha256) {"Cópia ausente, alterada ou fora da pasta privada"}
        val restores=folder(checked(root,"restores"));val name=UUID.randomUUID().toString()
        val temporary=folder(checked(restores,"$name.partial"));val complete=checked(restores,name)
        try {
            val snapshot=inspect(archive,access,receipt,temporary)
            require(hash(archive)==receipt.sha256) {"Cópia mudou durante a restauração"}
            check(!complete.exists()&&temporary.renameTo(complete))
            Restoration(complete,snapshot)
        } finally {if(temporary.exists()) temporary.deleteRecursively()}
    }
}
