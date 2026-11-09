# DM AGRO 1.2.0 — melhorias e teste pelo celular

Plano **Spark gratuito mantido**. Não ativar Storage nem faturamento. Código na ramificação **dm-agro-login**, sem substituir a main.

## Mudanças no aplicativo

- A tela inicial mostra **Olá, nome do cadastro**. O painel inicial do administrador também mostra seu nome.
- **Voltar** em Equipamento 21569 retorna a Pendências. No histórico desse equipamento, retorna ao próprio equipamento. Relatórios de turno, calendário aberto pelo estoque e mapas também usam sua tela de origem. Voltar não encerra a sessão; **Sair da conta** continua sendo a saída explícita.
- Os equipamentos permanecem salvos por frente e turno, mesmo quando não há pendências abertas. **Cadastrar equipamento**, **Adicionar pendência**, **Histórico deste equipamento** e **Reabrir pendência** permitem reutilizar o cadastro.
- Cadastros anteriores de equipamentos são reconhecidos a partir de pendências abertas ou concluídas, sem apagar os registros. Catálogos usam a mesma autorização de manutenção; não exigem nova coleção nem novo índice.
- Mapas novos de operadores e tratoristas ficam no catálogo privado da pessoa, no aparelho, e suas pinturas não são enviadas automaticamente ao supervisor. Mapas anteriormente existentes são preservados. O compartilhamento explícito de PDF continua disponível.
- O cadastro pede frente, turno e apenas **Operador** ou **Supervisor**. O campo livre de função foi removido. Contas antigas de tratoristas continuam válidas com as mesmas restrições de operador.
- **Excluir usuário** aparece apenas para o UID principal. Retira a pessoa da lista ativa e encerra o acesso ao aplicativo, preservando pendências, fotos e autoria. **Não apaga a credencial no Firebase Authentication: a matrícula continua reservada.** Apagar definitivamente credenciais de outras pessoas exige acesso administrativo confiável ao Authentication e não é feito pelo APK.

## Instalação e teste

Atualize **os dois celulares de teste** antes de usar o catálogo persistente. Versões anteriores não conhecem esse tipo de cadastro. Instale sobre a versão anterior, sem desinstalar nem limpar dados; a assinatura foi mantida.

1. Entre com operador/supervisor e confira o nome no início.
2. Abra Pendências → equipamento 21569 → Voltar. Deve retornar a Pendências.
3. Conclua a última pendência do equipamento. O equipamento deve permanecer na lista, com zero abertas, permitindo outra pendência.
4. Abra o histórico desse equipamento e reabra uma pendência. Nome, descrição e fotos locais devem ser preservados.
5. Cadastre outro equipamento na mesma equipe e confirme sua aparição no supervisor. Outra frente/turno permanece isolada.
6. Importe um PDF como operador. O mapa não deve ser adicionado automaticamente ao catálogo do supervisor.
7. Faça um cadastro novo e confirme que não existe campo livre Função e que há somente os perfis Operador e Supervisor.

## Ativar a exclusão de acesso

É necessário um ajuste **somente nas regras Firestore**, sem mudar o plano:

1. No ramo de atualização permitido ao supervisor, acrescentar `resource.data.status != 'excluido'`. Isso impede reativação de uma pessoa excluída pelo administrador.
2. Permitir apenas ao principal consultar `configuracao/exclusaoUsuarios`. É uma verificação de que as regras novas estão ativas; **não é necessário criar esse documento**.

O arquivo completo proposto e testado é `firebase/firestore.rules`. Antes de publicar, guarde as regras que estão no console e depois substitua pelo arquivo completo. Nenhuma regra foi publicada automaticamente por esta sessão. O índice de usuários existente continua válido.

O botão Excluir só executa a ação quando essa verificação no servidor funciona. O próprio administrador e o UID principal não podem ser excluídos pelo aplicativo.

## Atualizações dentro do próprio aplicativo

O botão **Verificar atualização** aparece no início, no menu e no login. O aplicativo consulta um canal público de **GitHub Releases**, baixa o APK dentro do DM AGRO e confere pacote, versão, tamanho, checksum e assinatura. Somente uma versão mais nova com a mesma assinatura do aplicativo instalado pode abrir a instalação. O Android solicita a confirmação da pessoa e pode pedir uma vez a permissão para instalar pelo DM AGRO.

Não há cobrança Firebase nem alteração de regras para esse canal. Supervisores e operadores apenas consultam e instalam; a publicação depende do proprietário do repositório.

### Estado real da publicação

A conexão GitHub desta sessão anteriormente recusou escrita com HTTP 403. Até que o código e os arquivos de release sejam enviados, **não existe atualização pública nesse canal**. Nessa situação o aplicativo informa que ainda não há atualização publicada; ele não afirma estar na última versão sem ter consultado um manifesto válido.

O código e o workflow estão preparados para essa publicação, mas um botão sozinho não hospeda o APK. A primeira instalação da versão 1.2.0 ainda é feita pelo arquivo entregue na conversa.

### Assinatura permanente

As instalações desta conversa usam a mesma chave de assinatura. **Não gerar uma nova chave**: o Android recusaria a atualização sobre os dados existentes.

O workflow exige o segredo protegido **ANDROID_UPDATE_KEYSTORE_BASE64**, contendo a chave já usada, e confere o certificado público registrado em `scripts/android-signing.sha256`. Ele falha antes de publicar um APK com outra assinatura. A chave privada não entra no código, no APK distribuído nem no pacote público da atualização.

### Publicar uma melhoria futura

Depois de resolver o acesso de escrita ao GitHub e configurar o segredo:

1. Alterar o código em `dm-agro-login`, aumentar **versionCode** e **versionName** e enviar os commits.
2. Em Actions → Gerar APK Colheita GPS, escolher `dm-agro-login`, marcar **publicar_atualizacao** e informar as melhorias.
3. O workflow executa testes, gera o APK com a assinatura permanente e prepara os metadados pelo APK real.
4. Todos os arquivos são enviados a um release inicialmente privado como rascunho. Somente depois do upload completo ele é publicado.
5. Os usuários tocam em Verificar atualização → Baixar → Instalar e confirmam no Android.

O manifesto público fica em `https://github.com/Diegomachadosl10/colheita-gps-oficial/releases/latest/download/dm-agro-update.json`. O canal não usa senhas, tokens GitHub no APK, Storage ou funções pagas.
