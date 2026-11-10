package com.colheitagps.app

import java.time.LocalDate

internal data class Verse(val text:String,val reference:String)
/** Local calendar dates keep the day's reading stable offline, without a paid service. */
internal object DailyVerse {
    private val verses=listOf(
        Verse("O Senhor é o meu pastor; nada me faltará.","Salmos 23:1"),
        Verse("Tudo posso naquele que me fortalece.","Filipenses 4:13"),
        Verse("Orai sem cessar.","1 Tessalonicenses 5:17"),
        Verse("Alegrai-vos sempre no Senhor; outra vez digo, alegrai-vos.","Filipenses 4:4"),
        Verse("O meu socorro vem do Senhor que fez o céu e a terra.","Salmos 121:2"),
        Verse("Entrega o teu caminho ao Senhor; confia nele, e ele tudo fará.","Salmos 37:5"),
        Verse("O Senhor te abençoe e te guarde.","Números 6:24"),
        Verse("Lâmpada para os meus pés é tua palavra, e luz para o meu caminho.","Salmos 119:105"),
        Verse("Confia no Senhor de todo o teu coração, e não te estribes no teu próprio entendimento.","Provérbios 3:5"),
        Verse("Este é o dia que fez o Senhor; regozijemo-nos, e alegremo-nos nele.","Salmos 118:24"),
        Verse("Deus é o nosso refúgio e fortaleza, socorro bem presente na angústia.","Salmos 46:1"),
        Verse("O Senhor é bom, uma fortaleza no dia da angústia, e conhece os que confiam nele.","Naum 1:7"),
        Verse("E tudo quanto fizerdes, fazei-o de todo o coração, como ao Senhor, e não aos homens.","Colossenses 3:23"),
        Verse("Todas as vossas coisas sejam feitas com caridade.","1 Coríntios 16:14"),
        Verse("Nós o amamos a ele porque ele nos amou primeiro.","1 João 4:19"),
        Verse("O amor não faz mal ao próximo. De sorte que o cumprimento da lei é o amor.","Romanos 13:10"),
        Verse("Bem-aventurados os pacificadores, porque eles serão chamados filhos de Deus.","Mateus 5:9"),
        Verse("Porque andamos por fé, e não por vista.","2 Coríntios 5:7"),
        Verse("Lançando sobre ele toda a vossa ansiedade, porque ele tem cuidado de vós.","1 Pedro 5:7"),
        Verse("Aquele que não ama não conhece a Deus; porque Deus é amor.","1 João 4:8"),
        Verse("O coração alegre serve de bom remédio, mas o espírito abatido virá a secar os ossos.","Provérbios 17:22"),
        Verse("A resposta branda desvia o furor, mas a palavra dura suscita a ira.","Provérbios 15:1"),
        Verse("Alegrai-vos na esperança, sede pacientes na tribulação, perseverai na oração.","Romanos 12:12"),
        Verse("Não te deixes vencer do mal, mas vence o mal com o bem.","Romanos 12:21"),
        Verse("Buscai ao Senhor enquanto se pode achar, invocai-o enquanto está perto.","Isaías 55:6"),
        Verse("Grandes coisas fez o Senhor por nós, pelas quais estamos alegres.","Salmos 126:3"),
        Verse("O Senhor é a minha força e o meu cântico; e se fez a minha salvação.","Salmos 118:14"),
        Verse("Deleita-te também no Senhor, e te concederá os desejos do teu coração.","Salmos 37:4"),
        Verse("Em todo o tempo ama o amigo; e na angústia nasce o irmão.","Provérbios 17:17"),
        Verse("Reconhece-o em todos os teus caminhos, e ele endireitará as tuas veredas.","Provérbios 3:6"),
        Verse("Tudo tem o seu tempo determinado, e há tempo para todo o propósito debaixo do céu.","Eclesiastes 3:1")
    )
    fun forDate(date:LocalDate=LocalDate.now()):Verse=verses[Math.floorMod(date.toEpochDay(),verses.size.toLong()).toInt()]
}
