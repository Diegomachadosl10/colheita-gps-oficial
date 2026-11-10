# DM AGRO 1.2.2 — exclusão pelo toque, com histórico preservado

Ramificação de trabalho: `dm-agro-login`. Android `versionCode 8`, `versionName 1.2.2`.

## Comportamento combinado

- **Mapas:** tocar no cartão abre o menu Abrir, O.S., Renomear, Excluir e Compartilhar. A exclusão pede confirmação. Operadores continuam podendo excluir seus mapas pessoais; o catálogo de outro perfil no aparelho é preservado.
- **Pendências:** tocar no registro abre as ações. **Excluir pendência** aparece apenas para supervisor da equipe e administrador principal. Após confirmar, o registro sai da lista ativa e permanece no **Histórico de concluídas e excluídas**, com descrição, autoria da exclusão, data e fotos locais. Uma pendência excluída fica disponível para consulta e compartilhamento, sem ser confundida com uma manutenção concluída.
- **Equipamentos:** tocar no cartão abre opções. O gestor pode escolher **Excluir equipamento e suas pendências**. A confirmação informa o equipamento e a quantidade de pendências. O cadastro, as pendências abertas e as concluídas são preservados no histórico. Outros equipamentos continuam ativos. Consulte **Equipamentos excluídos** na tela de Pendências.
- Supervisores atuam somente na própria frente e turno; somente o UID principal pode acompanhar outras equipes. Operadores continuam cadastrando, editando os próprios registros e concluindo manutenções conforme as permissões existentes, mas não excluem nem alteram registros já excluídos.
- O histórico permanece nas cópias de segurança. Fotos e PDFs seguem o compartilhamento manual do plano gratuito; não existe envio automático desses arquivos ao Firebase Storage.
- A fila de sincronização trata também registros criados e removidos antes de uma conexão, sem reenviar inadvertidamente uma criação já cancelada. Alterações concorrentes continuam preservando cópias de conflito.

Não há desinstalação, limpeza de dados, mudança de plano ou migração destrutiva. Mapas, GPS, chuva por turno, talhões, O.S., estoque, ocorrências, relatórios, compartilhamento, login e saída permanecem no aplicativo.

## Por que publicar regras Firestore nesta versão

As regras anteriores permitiam ao operador marcar seu próprio documento como `excluido`. Esconder o botão no aplicativo não bloquearia uma versão antiga ou uma chamada direta ao servidor. Por isso o arquivo `firebase/firestore.rules` recebeu somente estas condições adicionais no grupo `pendencias`, que também contém o catálogo de equipamentos:

1. Criar um documento já marcado como excluído exige gestor da equipe.
2. Um operador só atualiza registros ativos mantendo-os ativos. Excluir, reativar ou alterar um registro excluído exige gestor.

Os documentos continuam guardados com conteúdo e a marca `excluido`; a exclusão física permanece proibida para todos os clientes. O aplicativo 1.2.2 apresenta esses documentos no histórico. Nenhuma regra foi publicada automaticamente. Não há novo índice, coleção ou campo obrigatório no Firestore. Regras de usuários, mapas, chuva e demais ferramentas não foram ampliadas. O plano permanece **Spark gratuito**.

## Passos pelo celular

1. No Firebase do projeto `dm-agro-c0e04`, abra **Firestore Database → Regras**. Guarde uma cópia do texto atual. Substitua pelo conteúdo completo de **REGRAS-FIRESTORE-1.2.2.txt** e toque em **Publicar**. Não altere Storage nem faturamento.
2. Extraia **PUBLICAR-DM-AGRO-1.2.2.zip** em uma pasta nova. Ele contém cinco arquivos de publicação, sem chave de assinatura privada.
3. Abra https://github.com/Diegomachadosl10/colheita-gps-oficial/releases/new . Crie a tag **v1.2.2**, selecione a ramificação **dm-agro-login** e use o título **DM AGRO 1.2.2**.
4. Na caixa **Attach binaries by dropping them here or selecting them**, abaixo da descrição, anexe os cinco arquivos extraídos: APK, `dm-agro-update.json`, `SHA256.txt`, código ZIP e Git bundle. Mantenha os nomes dos arquivos. Não use o botão Add files da descrição para anexar o APK.
5. Aguarde os uploads e publique como versão estável e mais recente (**Latest**), sem **Pre-release**.
6. No DM AGRO instalado, toque em **Verificar atualização → Baixar → Instalar** e confirme no Android. Não desinstale nem limpe dados. Atualize os dois celulares para que ambos exibam o novo histórico.

A conexão desta sessão tem apresentado HTTP 403 ao tentar gravar no repositório. Os arquivos de código e bundle anexados preservam os commits locais; os ZIPs automáticos **Source code** do GitHub não os substituem enquanto o envio dos commits continuar bloqueado.

## Teste no aplicativo

1. Como operador, toque num mapa pessoal, escolha Excluir e cancele: o mapa deve continuar. Confirme depois em um mapa de teste e verifique que ele sai do seu catálogo.
2. Ainda como operador, toque numa pendência e num equipamento: não deve haver opção de excluir.
3. Como supervisor da mesma equipe, exclua uma pendência de teste. O equipamento deve continuar ativo e a pendência deve aparecer no histórico como **Excluída**, com as fotos existentes nesse aparelho.
4. Em um equipamento com duas pendências de teste, escolha excluir o equipamento e as pendências. Cancele primeiro e confira que nada mudou. Repita e confirme: ele deve sair da lista ativa e aparecer em **Equipamentos excluídos**, com os registros preservados.
5. Confira o resultado no segundo celular da mesma equipe. Outro turno/frente permanece isolado. Fotos só estarão presentes nos aparelhos onde foram guardadas ou recebidas manualmente.
6. Faça uma exclusão sem internet e reconecte. Aguarde a confirmação de sincronização antes de comparar os celulares. Se houver alteração simultânea, consulte **Sincronização**: a cópia conflitante permanece preservada, sem substituir silenciosamente a versão compartilhada.
7. Abra chuva, um mapa anterior e uma pendência de outro equipamento para conferir a preservação das funções existentes.

## Validação

Compilação `testDebugUnitTest assembleDebug` concluída. **42 testes Android**, **15 testes das regras nos emuladores demo-dm-agro** e **3 testes Python** aprovados (60 no total). Incluem navegação, histórico, confirmação/cancelamento, exclusão restrita ao gestor, isolamento entre equipes, preservação de fotos locais e backup. A publicação de regras e a instalação real nos celulares são etapas do proprietário; não são tratadas como se já tivessem ocorrido.
