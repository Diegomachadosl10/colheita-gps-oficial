# DM AGRO 1.7.1 — Acesso por HTTPS e telas enxutas

## Acesso ao aplicativo

A leitura do cadastro e a verificação da configuração de equipes agora usam a API HTTPS do Firestore, com o ID token Firebase da própria conta autenticada. Substitui-se o transporte dessas duas leituras, mantendo o projeto, o UID, o conteúdo do cadastro, a interpretação das permissões e a consulta obrigatória ao servidor. Não é usada conta de serviço, chave de administrador, perfil em cache ou aprovação automática. As mesmas regras do Firestore avaliam cada consulta HTTPS. Somente o cadastro do usuário autenticado e o documento configuracao/equipes são consultados nesse fluxo.

As leituras têm prazo total de 20 segundos, incluindo resolução de DNS. O token é obtido pelo SDK da conta já autenticada; em caso de rejeição de autenticação, há uma única tentativa de renovação. Mudanças de conta e respostas atrasadas não autorizam uma sessão diferente. Conteúdo inesperado, outro documento, falha de rede ou permissão recusada não são interpretados como cadastro aprovado.

A mensagem diferencia PERMISSION_DENIED, TIMEOUT, DNS, TLS e sessão inválida. Uma recusa do servidor deixa de aparecer apenas como instrução genérica para conferir a internet. Os botões e a tela de teste de conexão da versão 1.7.0 foram removidos. Não é solicitado envio de senha nem de captura dos campos de login.

A mudança é voltada à etapa de verificação de acesso, onde a captura do usuário mostrava a falha. A autenticação de senha continua no Firebase Authentication. A sincronização operacional e os ouvintes das outras ferramentas continuam usando o SDK existente; o funcionamento completo na rede doméstica ainda precisa ser conferido no aparelho. Não se afirma que a causa específica do Wi-Fi foi reproduzida neste ambiente.

## Telas simplificadas

- Login e erros de autorização: removidos Testar conexão, Copiar resultado e o atalho específico de configurações de Wi-Fi. Permanecem as ações de entrada, recuperação, atualização e nova tentativa já necessárias ao acesso.
- Caminhões: um único botão Compartilhar caminhões reúne mensagem, PDF e imagem.
- Relatório de turno: um único Compartilhar relatório reúne mensagem, PDF, imagem e salvar PDF no celular.
- Envio de estoques: um único Compartilhar estoques reúne mensagem com fotos, PDF e imagem. As fotos continuam opcionais por lote.
- Início: removido o botão duplicado de Registros excluídos; a função permanece no menu ⋮. A restauração de equipamentos permanece também no histórico de equipamentos.
- Equipamento: removido o botão de voltar aos equipamentos que duplicava a navegação do cabeçalho.
- Biblioteca de mapas: removida a exportação da lista do catálogo do cabeçalho. O PDF e a imagem de cada mapa continuam disponíveis nas opções do próprio mapa. O atalho para a pasta pública do Drive é mantido.

Permanecem O.S. encerradas com histórico e reabertura, rotação livre do mapa, GPS, talhões, estoque, pendências, peças, chuva, ocorrências, relatórios e caminhões com os períodos A 07:00–15:20, B 15:20–23:20 e C 23:20–07:00 do dia seguinte. O botão de encalhado continua removido.

## Teste real autorizado no Firebase

Foi criada uma conta técnica temporária por matrícula interna, autenticada e excluída ao terminar. A consulta HTTPS ao próprio documento de cadastro retornou NOT_FOUND/404, e não PERMISSION_DENIED, porque nenhum perfil foi gravado. Não foi aprovada conta de teste, não foi escrito cadastro no Firestore e nenhuma conta existente foi alterada. O segredo gerado para esse teste não foi publicado ou incluído no código ou na entrega.

Esse teste comprova criação/autenticação no serviço e resposta da consulta autenticada a partir do ambiente de desenvolvimento. Não constitui teste completo do aplicativo com perfil aprovado, nem teste pela rede Wi-Fi do usuário.

## Instalar e conferir no celular

1. Instale DM-AGRO-1.7.1.apk sobre o aplicativo atual. Não desinstale e não limpe dados.
2. Mantenha o Wi-Fi ligado e desligue os dados móveis durante a tentativa, para assegurar que o teste usa o Wi-Fi.
3. Abra o aplicativo e toque em Verificar novamente se a tela anterior aparecer. Se estiver no login, digite seus dados apenas no aplicativo.
4. Confira se abre o painel/aplicativo. Se houver erro, informe somente o código e a mensagem apresentados, sem enviar senha.
5. Após entrar, confira também Sincronização no menu ⋮ e as funções de campo. A adaptação HTTPS desta versão cobre a verificação inicial; a sincronização operacional continua no SDK existente.
6. Confira o botão único de compartilhar nos caminhões, relatórios e envio de estoques. Na O.S., Encerrar O.S. mantém os dados em O.S. encerradas / histórico.

## Publicação da atualização

Versão 1.7.1, código 20, Android mínimo 8.0/API 26, mesma assinatura dos APKs anteriores. O APK está compilado. Se o envio ao GitHub continuar recusado com HTTP 403, a versão não é publicada automaticamente; o código fica na ramificação local dm-agro-login e nos anexos de código e bundle. A main é preservada.

Pelo celular: extrair PUBLICAR-DM-AGRO-1.7.1.zip; abrir https://github.com/Diegomachadosl10/colheita-gps-oficial/releases/new ; criar tag v1.7.1, destino dm-agro-login e título DM AGRO 1.7.1. Em Attach binaries… abaixo da descrição, anexar os cinco arquivos extraídos: DM-AGRO-1.7.1.apk, dm-agro-update.json, SHA256.txt, DM-AGRO-1.7.1-codigo.zip e DM-AGRO-1.7.1-dm-agro-login.bundle. Publicar como versão estável mais recente, sem Pre-release. Não usar Add files dentro da descrição.

Publicar os anexos não atualiza a ramificação remota. O Source code automático pode corresponder ao commit antigo; o ZIP de código e o bundle fornecidos contêm esta versão. Os mesmos arquivos estão separados em reenvio-1.7.1.

## Verificação

Compilação testDebugUnitTest + assembleDebug aprovada: 126 testes Android sem falhas. Incluem decodificação da resposta HTTPS, tratamento de permissão e autenticação recusadas, rejeição de HTML/documento diferente, bloqueio de perfis pendentes/bloqueados/excluídos, validação do UID principal, preservação de O.S., rotação do mapa e viradas de turno.

Emuladores Firebase: 23 testes aprovados. O teste adicional usa chamadas REST autenticadas no projeto de demonstração e confirma leitura própria permitida, leitura alheia e não autenticada negadas, UID principal preservado e perfil bloqueado retornando seu status sem aprovação. O teste usa tokens sem assinatura somente no emulador; o aplicativo real usa o ID token assinado do SDK Firebase e as regras do projeto real.

Não houve alteração em regras Firebase, serviço Cloudflare, plano, administrador principal ou dados operacionais. A instalação, o Wi-Fi doméstico e a sincronização entre aparelhos precisam da conferência final nos celulares.
