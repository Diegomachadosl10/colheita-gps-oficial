# DM AGRO 1.2.1 — registro de chuva por turno

Versão Android: `versionCode 7`, `versionName 1.2.1`. Ramificação: `dm-agro-login`.

## Funcionamento

- A tela inicial e o menu oferecem **Registro de chuva** para operadores, tratoristas, supervisores e administrador.
- A frente e o turno vêm da equipe aberta. Somente o administrador principal pode trocar a equipe acompanhada.
- **Registrar chuva por data** permite escolher o dia e informar o acumulado do turno em milímetros, com vírgula ou ponto e até duas casas decimais. Zero registra explicitamente um turno sem chuva; ausência de registro continua identificada separadamente.
- Existe um acumulado por data e equipe. Para uma leitura posterior, **Corrigir registro** substitui o acumulado completo, sem somar novamente o volume já informado. O autor e o supervisor da equipe podem corrigir; os demais operadores podem consultar.
- O histórico ordena pela data da medição e identifica o autor. O calendário do gestor também mostra a chuva na data correspondente.
- A gravação local funciona sem rede; a fila e o controle de versão existentes enviam o texto quando a conexão volta. Alterações simultâneas seguem a preservação de conflitos do sincronizador.

## Firebase e compatibilidade

Nenhuma regra, índice, configuração de cobrança ou serviço de produção foi alterado. Chuva é um subtipo de observação de campo (`grupo=ocorrencias_campo`, `categoriaRegistro=chuva`), portanto usa a coleção, as permissões por equipe, a autoria e a sincronização já publicadas. A nova tela filtra esse subtipo da lista comum de ocorrências, preservando ambos no armazenamento e na sincronização. Isso evita introduzir um grupo desconhecido pelo sincronizador das versões já instaladas.

Clientes 1.2.0 podem apresentar essas observações na lista comum de ocorrências, com título **Registro de chuva** e a medição em texto. Atualize os dois celulares para 1.2.1 antes do teste operacional da tela específica. Os mapas e as demais ferramentas continuam no mesmo armazenamento; a instalação deve ser feita por cima, sem desinstalar ou limpar dados.

O plano permanece **Spark gratuito**. Não ativar Storage. Chuva sincroniza somente texto; fotos e PDFs mantêm o compartilhamento manual existente.

## Verificação executada

- `testDebugUnitTest assembleDebug`: compilação concluída, 31 testes Android aprovados, incluindo entrada decimal e zero, correção sem duplicação, preservação de ocorrências/fotos e edição restrita a autor/gestor.
- Emuladores locais `demo-dm-agro`: 14 testes de regras aprovados, incluindo chuva criada pelo operador, correção pelo autor/supervisor e bloqueio de outras equipes, usando as regras existentes sem alterações.
- Preparo de atualização: 3 testes Python aprovados. A inspeção do APK e do certificado ocorre novamente ao gerar o manifesto.
- O download e a confirmação da instalação pelo Android real dependem da publicação de `v1.2.1` e do teste nos celulares. Essa etapa não foi simulada como se já tivesse ocorrido.

## Publicação e teste pelo Android

A publicação pública verificada no início deste trabalho foi `v1.2.0`, código 6. O APK 1.2.1 deve conservar o certificado de assinatura registrado em `scripts/android-signing.sha256`. O preparo dos arquivos verifica certificado, pacote, versão e checksum do APK real.

A conexão GitHub desta sessão recusou anteriormente a gravação com HTTP 403. O pacote de publicação é entregue para envio pelo proprietário pelo celular; nenhuma publicação automática é presumida.

1. Extraia `PUBLICAR-DM-AGRO-1.2.1.zip` em uma pasta nova no celular.
2. Abra https://github.com/Diegomachadosl10/colheita-gps-oficial/releases/new e crie a tag **v1.2.1**; selecione a ramificação **dm-agro-login**, título **DM AGRO 1.2.1** e publicação estável, marcada como mais recente (**Latest**), sem **Pre-release**.
3. Na caixa **Attach binaries by dropping them here or selecting them**, abaixo da descrição, envie os cinco arquivos extraídos, mantendo os nomes: `DM-AGRO-1.2.1.apk`, `dm-agro-update.json`, `SHA256.txt`, `DM-AGRO-1.2.1-codigo.zip` e `DM-AGRO-1.2.1-dm-agro-login.bundle`. Não use **Add files** da descrição para o APK. Aguarde todos os uploads e publique.
4. **Mantenha a versão 1.2.0 instalada para o teste.** No DM AGRO, toque em **Verificar atualização**, confira **1.2.1**, baixe e confirme a instalação pelo Android. Se solicitado, permita instalar aplicativos por esta fonte (**DM AGRO**). Não é necessário desinstalar, limpar dados ou digitar credenciais GitHub no aplicativo.
5. Abra novamente, confira que a sessão e os registros foram preservados e procure **Registro de chuva**. Atualize o segundo celular pelo mesmo botão.
6. Entre com o operador, selecione uma data e registre **12,5 mm**, com uma observação. Confira no supervisor da mesma frente e turno. Outra equipe não deve ter acesso a esse registro.
7. Corrija para **15 mm**. Deve permanecer um único registro desse dia e turno. Em outro dia, registre **0 mm** e confira a diferença entre zero e dia não informado.
8. Registre outra data sem internet, volte a conectar e confira o recebimento no supervisor. Confira também que as ocorrências comuns e os mapas anteriores continuam disponíveis.

Os arquivos `codigo.zip` e `.bundle` anexados contêm o código atual, inclusive os commits locais. Os arquivos **Source code** gerados automaticamente pelo GitHub dependem do commit remoto da tag e não substituem esses anexos enquanto os commits locais não forem enviados ao repositório.
