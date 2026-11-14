package com.colheitagps.app

/** Enrollment only; blocked/deleted accounts never gain access through this policy. */
internal object EnrollmentPolicy {
    fun initial(requested:String):Map<String,String> {
        require(requested in setOf("operador","tratorista","supervisor"))
        val role=if(requested=="supervisor") "supervisor" else "operador"
        return mapOf("papel" to role,"perfil" to role,"solicitacaoPerfil" to requested,
            "status" to if(requested=="supervisor") "aprovado" else "pendente")
    }
    val supervisorActivation=mapOf("papel" to "supervisor","perfil" to "supervisor","status" to "aprovado")
    fun pendingSupervisor(uid:String,email:String?,data:Map<String,Any>?):Boolean =
        !TeamPolicy.primary(uid) && data!=null && data["status"]=="pendente" &&
            data["solicitacaoPerfil"]=="supervisor" && data["papel"]=="operador" && data["perfil"]=="operador" &&
            TeamPolicy.team(data)!=null && (data["matricula"] as? String)?.let {
                it.matches(Regex("[0-9]{1,20}")) && email==AccessPolicy.internalEmail(it)
            }==true
}
