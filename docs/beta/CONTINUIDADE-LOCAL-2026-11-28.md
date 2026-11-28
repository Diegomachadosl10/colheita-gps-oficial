# Continuidade local e baselines de sincronização

Desenvolvimento na branch `dm-agro-professional-beta`, sem copiar dados reais, alterar regras/Worker ou oferecer outro APK. A versão estável disponível continua sendo a 2.2.8/código 40. O candidato Beta 2.3.0/código 41 permanece separado.

## Inventário de origem, somente leitura

`StableLocalInventory` prepara a conferência do espaço local da versão estável atual (`equipes/<equipe>` e preferências sem prefixo Beta). Não é o importador anterior à separação por equipes. Ainda não está ligado à inicialização ou a uma tela do aplicativo.

O inventário mantém as strings originais dos registros, IDs, histórico, pinturas, referências, rascunhos e filas/baselines/conflitos do UID informado. Não modifica nem apaga preferências ou arquivos. Gestores incluem os grupos da equipe; operadores incluem apenas os grupos autorizados. Índices de mapas pessoais permanecem separados por UID, inclusive em celulares compartilhados. Prévia pintada sem marca de proprietário não é atribuída automaticamente ao usuário pelo nome do arquivo.

Fotografias referenciadas, seus arquivos de marcação e a cadeia de originais são identificados, assim como PDFs registrados nos índices autorizados. Arquivos recebem tamanho e SHA-256. Caminhos fora da equipe, travessia e links simbólicos são recusados; referências ausentes, IDs repetidos, JSON inválido e ciclos de origem impedem o resultado de ser considerado localmente consistente. O fingerprint também identifica equipe/UID/papel e inclui contagens e campos com comprimento explícito.

Credenciais, tokens de avisos, perfis de outras pessoas e preferências gerais de autenticação não entram. Filas de notificações não são reproduzidas: exigem corte próprio para evitar avisos antigos duplicados. Os envelopes locais são preservados, mas a validade de autoria, revisão e relação com o servidor ainda deve ser verificada antes de aplicar uma migração.

**Limites:** isso não gera backup restaurável, não copia o destino, não reescreve caminhos e não aplica migração. As preferências e os arquivos não formam uma transação única: a futura cópia deve impedir escrita concorrente e recapturar/conferir o fingerprint, além de conferir o plano do servidor e o corte dos clientes antigos. Não usar `locallyConsistent` como autorização de migração.

## Primeira comparação com o servidor

Antes, uma conta sem baseline local podia enfileirar todos os registros existentes como criações antes de receber o primeiro snapshot. Se esses registros já estivessem no servidor, a revisão zero provocaria conflitos artificiais. Isso é relevante tanto à troca de conta no celular quanto à futura continuidade de dados.

`SyncBaseline` compara os dois lados com as mesmas regras de mídia local e arquivamento. Ao receber um snapshot confirmado do servidor, a sincronização pode adotar o autor e a revisão reais de um registro **somente se** o conteúdo, identidade e estado forem iguais, não existir baseline nem conflito e não houver alteração pendente diferente. Uma criação de revisão zero idêntica, gerada pela varredura inicial, é redundante e pode sair da fila. Pendências baseadas em revisão conhecida, mudanças locais e conflitos continuam preservados. Essa adoção não regrava registros/fotos, não envia alteração e não cria notificação.

Cada documento recebido é validado separadamente: equipe/frente/turno, ID derivado de tipo/grupo/chave, revisão inteira positiva, autor/editor, formato tipado e coerência do estado. Um documento inválido fica fora da aplicação local e aparece no diagnóstico existente; não bloqueia a leitura dos demais nem é apagado no servidor. A mesma validação ocorre antes de usar a cópia compartilhada para resolver um conflito e antes de atualizar um documento existente.

Isso reforça o cliente; não substitui as regras do Firebase nem homologa o servidor real. A conexão administrativa com Firebase continua pendente. Não houve implantação ou migração.

## Validação

A suíte local executou **375 testes Android**, com zero falhas, erros ou casos ignorados. Os 28 casos novos (14 cenários nas APIs 28 e 35) cobrem inventário sem escrita, mídia pendente, originais encadeados, mapas pessoais, caminhos/ciclos inválidos, mudança de conteúdo, estados de arquivamento/conclusão, ordem de pontos, autoria/revisão e documentos de outra equipe ou identidade forjada. São dados sintéticos, sem conta real criada nem conexão com o Firebase de produção.

Também passaram `assembleDebug`, `assembleDebugAndroidTest` e `lintDebug`: zero erros de lint e 128 avisos. Isso não representa distribuição de APK.

No [Actions 46141372481](https://github.com/Diegomachadosl10/colheita-gps-oficial/actions/runs/46141372481), sobre `c122c6c79de9439a2f03b2214efaaaa38f6fb05f`, passaram 69 testes de regras em emuladores, 18 testes do Worker estável, 21 do Worker Beta, 14 Python e os três testes nativos Android API 35. Estes validam PDF/compartilhamento, autorização offline no Keystore e GPS com redes desligadas; não medem a recepção física de satélites no campo.

Os quatro jobs ficaram aprovados. A primeira tentativa Android parou **antes dos testes**, no download de Guava pelo Maven Central, com HTTP 429. Somente esse job foi repetido, com o mesmo código; a repetição aprovou compilação, testes e lint. Não houve troca de dependência, supressão de teste ou publicação para contornar a falha de rede.
