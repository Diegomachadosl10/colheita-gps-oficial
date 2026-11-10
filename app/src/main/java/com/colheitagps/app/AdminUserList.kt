package com.colheitagps.app

import java.text.Normalizer
import java.util.Locale

internal data class AdminUser(val uid:String,val data:Map<String,Any>)
internal object AdminUserList {
    const val PAGE_SIZE=10
    private fun normalized(value:String)=Normalizer.normalize(value,Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"),"").lowercase(Locale.ROOT).trim()
    fun filter(users:List<AdminUser>,search:String,status:String,front:String):List<AdminUser> {
        val terms=normalized(search).split(Regex("\\s+")).filter { it.isNotEmpty() }
        return users.filter { user ->
            val d=user.data
            (status.isBlank()||AccessPolicy.normalized(d["status"])==status) &&
                (front.isBlank()||d["frenteId"]==front) &&
                terms.all { term -> normalized(listOf(d["nome"],d["matricula"],TeamPolicy.team(d)?.let(TeamPolicy::label),TeamPolicy.requested(d)).joinToString(" ")).contains(term) }
        }.sortedWith(compareBy({if(AccessPolicy.normalized(it.data["status"])=="pendente") 0 else 1},{normalized(it.data["nome"]?.toString().orEmpty())},{it.uid}))
    }
    fun page(users:List<AdminUser>,index:Int)=users.drop(index.coerceIn(0,lastPage(users))*PAGE_SIZE).take(PAGE_SIZE)
    fun lastPage(users:List<AdminUser>)=((users.size-1).coerceAtLeast(0))/PAGE_SIZE
}
