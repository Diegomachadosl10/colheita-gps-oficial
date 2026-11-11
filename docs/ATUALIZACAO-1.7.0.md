# DM AGRO 1.7.0 — O.S. encerradas e diagnóstico de acesso

## Encerrar O.S. e guardar no histórico

Supervisor e administrador podem abrir Ordens de serviço → escolher mapa → tocar na O.S. → Encerrar O.S. → Encerrar e guardar. Se houver alterações no número ou nos talhões ainda não salvas, a tela pede salvar antes de encerrar para não ignorar as mudanças.

A O.S. sai da lista em andamento e permanece no histórico com número, mapa, talhões, data e responsável. O registro é salvo primeiro no aparelho e sincronizado no grupo historico_os da equipe quando houver conexão. A confirmação local de encerramento não significa que o envio ao servidor já terminou; confira Sincronização. Não se apaga o mapa nem os dados de estoque e relatórios associados.

O botão O.S. encerradas / histórico está na página de Ordens de serviço e na lista de O.S. de cada mapa. Permite consultar, compartilhar PDF/imagem e reabrir. A reabertura mantém o histórico do encerramento e não sobrescreve uma O.S. existente com o mesmo número e outros talhões: é preciso escolher outro número. Operadores não recebem permissão para encerrar ou reabrir. Supervisores continuam limitados à equipe autorizada.

## Acesso que demora a responder

A investigação começou porque a conta entra pelos dados móveis, mas permanece em Entrando… pelo Wi-Fi doméstico. O Chrome consegue chegar ao domínio de autenticação e o Wi-Fi está permitido ao aplicativo. A causa específica ainda não foi reproduzida ou comprovada neste ambiente. Esta versão não deve ser apresentada como confirmação de que o acesso pelo Wi-Fi foi corrigido.

Foi corrigido o tratamento da espera na tela: após 25 segundos sem a tarefa de autenticação concluir, aparece Aguardando resposta do acesso, com explicação, Testar conexão e acesso às configurações de Wi-Fi. A solicitação original do SDK continua em andamento; ela não é cancelada ou considerada aprovada pelo limite de tempo. Não se iniciam solicitações de login simultâneas, inclusive após recriação da tela. A tecla Voltar permite sair para a tela inicial do celular enquanto aguarda, sem efetuar logout. A verificação de atualizações permanece acessível.

Quando o SDK devolve sucesso, o acesso ainda depende da mesma conta autenticada e da verificação de autorização no servidor. Quando devolve erro, o botão e os campos são liberados. Respostas antigas de outras telas não autorizam acesso ou substituem uma nova tentativa. Falhas desconhecidas do serviço não são mais classificadas automaticamente como senha incorreta. As leituras de autorização no Firestore agora têm limite de espera de 20 segundos e continuam falhando sem conceder acesso se não houver confirmação do servidor.

O teste de conexão consulta somente a configuração pública do projeto na rede selecionada pelo Android. Não envia matrícula, e-mail, senha, token de usuário ou dados da equipe. Não altera a rede usada pelo aplicativo. O resultado informa versão, API do Android, tipo de rede e se houve resposta HTTP, erro de DNS, TLS ou tempo esgotado. Resposta HTTP 200 nesse teste não comprova login, reCAPTCHA ou acesso aos outros serviços do Google. O botão Copiar resultado fornece texto para continuar a investigação sem enviar fotos contendo credenciais.

## Testar no celular

1. Instale DM-AGRO-1.7.0.apk sobre o aplicativo atual. Não desinstale e não limpe dados. A assinatura é a mesma das versões anteriores.
2. Ative o Wi-Fi e tente entrar digitando seus dados somente no aplicativo.
3. Se continuar esperando, aguarde 25 segundos e toque em Testar conexão. Pode ser necessário rolar a tela.
4. Se o aplicativo estiver com a sessão salva e mostrar Não foi possível verificar, o botão Testar conexão também aparece nessa tela. Aguarde o resultado e toque em Copiar resultado. Envie somente esse texto para continuarmos a investigação. Não envie a senha ou captura com a senha visível.
5. Se precisar usar o aplicativo enquanto o Wi-Fi é investigado, alterne para os dados móveis, que já funcionaram no teste anterior.
6. Para testar a O.S., use uma ordem fictícia como TESTE-OS com talhões de exemplo. Encerre, abra o histórico, confira os dados e reabra usando um número diferente se já existir outro cadastro. Confira a sincronização da equipe.

A rotação livre do mapa, o reset dos caminhões nos horários A 07:00–15:20, B 15:20–23:20 e C 23:20–07:00 do dia seguinte e a remoção do botão de encalhado continuam incluídos.

## Publicar a atualização pelo celular

A versão é 1.7.0, código 19, Android mínimo 8.0/API 26. O APK fornecido já está compilado e assinado. A conexão GitHub pode continuar recusando gravação com erro 403; nesse caso, a publicação não acontece automaticamente.

1. Baixe PUBLICAR-DM-AGRO-1.7.0.zip e extraia em uma pasta nova.
2. Abra https://github.com/Diegomachadosl10/colheita-gps-oficial/releases/new . Crie a tag v1.7.0, destino dm-agro-login e título DM AGRO 1.7.0.
3. Em Attach binaries… abaixo da descrição, anexe os cinco arquivos: DM-AGRO-1.7.0.apk, dm-agro-update.json, SHA256.txt, DM-AGRO-1.7.0-codigo.zip e DM-AGRO-1.7.0-dm-agro-login.bundle. Não use Add files dentro do campo de descrição.
4. Publique como versão estável mais recente (Latest), sem Pre-release. Depois, os aplicativos poderão encontrar a atualização ao abrir ou em Verificar atualização.

Os arquivos também estão separados em reenvio-1.7.0, caso o ZIP não abra no celular. Publicar os anexos não atualiza o código da ramificação remota; o Source code automático pode conter o commit remoto anterior. O ZIP de código e o bundle contêm esta implementação na ramificação dm-agro-login. A main permanece preservada.

Para compilar pelo Actions quando o código remoto estiver atualizado e a assinatura permanente estiver configurada: Actions → Gerar APK Colheita GPS → Run workflow → dm-agro-login.

## Verificação e configuração

Compilação testDebugUnitTest + assembleDebug aprovada, com 123 testes Android sem falhas. Incluem tarefa de login sem resposta, exibição da ajuda após 25 segundos, liberação após erro de rede, prevenção de logins simultâneos, classificação do diagnóstico sem reproduzir dados brutos, fechamento de O.S. offline sem perder informações, impedimento de encerrar com alterações não salvas, reabertura sem sobrescrever outra ordem e proteção dos gestores. Os testes Android simulam respostas do SDK; não fazem login na conta do usuário nem reproduzem o Wi-Fi doméstico.

Emuladores Firebase: 22 testes aprovados, incluindo gravação e leitura de O.S. encerradas e reabertura por gestores, com acesso negado às demais equipes e aos operadores. Não houve alteração nas regras publicadas ou no arquivo de regras desta versão; o grupo historico_os já era previsto nas regras de 1.5.0. Se aquelas regras ainda não foram publicadas, elas continuam necessárias para a sincronização desse grupo. Nenhuma alteração no Worker Cloudflare, assinatura, administrador principal ou plano gratuito foi feita. O APK não foi instalado em aparelho físico nesta sessão.
