package com.colheitagps.app

import com.google.firebase.firestore.FirebaseFirestoreException
import java.net.*
import java.util.concurrent.TimeoutException
import javax.net.ssl.SSLException

internal class AccessFailure(val code:String):Exception(code)
internal object AccessFailures {
    fun code(error:Throwable):String {
        var current:Throwable?=error
        repeat(8) {
            when(val e=current) {
                is AccessFailure->return e.code
                is FirebaseFirestoreException->return e.code.name
                is UnknownHostException->return "DNS"
                is SSLException->return "TLS"
                is SocketTimeoutException,is TimeoutException->return "TIMEOUT"
            }
            current=current?.cause
        }
        return "CONNECTION_FAILED"
    }
    fun description(error:Throwable):String {
        val code=code(error)
        val message=when(code) {
            "PERMISSION_DENIED"->"O Firestore recusou a consulta ao seu cadastro. É necessário conferir as regras publicadas ou a configuração de acesso."
            "SUPERVISOR_ENROLLMENT"->"O servidor ainda não liberou este cadastro de supervisor. Peça ao administrador para conferir a publicação das regras da versão 1.8.0."
            "PROFILE_INVALID"->"O cadastro ainda não está aprovado ou a equipe e o perfil precisam ser conferidos pelo administrador."
            "UNAUTHENTICATED","SESSION_CHANGED"->"Sua sessão precisa ser conferida novamente. Saia da conta e entre com seus dados no aplicativo."
            "DNS"->"O aplicativo não conseguiu localizar o endereço do Firestore nesta rede."
            "TLS"->"Não foi possível estabelecer uma conexão segura com o Firestore nesta rede."
            "TIMEOUT"->"O Firestore não respondeu dentro do tempo de verificação nesta rede."
            "INVALID_RESPONSE"->"A rede não retornou a resposta esperada do Firestore."
            else->"Não foi possível consultar o Firestore nesta rede."
        }
        return "$message\nCódigo: $code"
    }
}
