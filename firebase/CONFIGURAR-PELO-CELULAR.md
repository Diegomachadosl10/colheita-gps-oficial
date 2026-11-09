# Ativar equipes no Firebase — DM AGRO 1.1.0

O código foi preparado para o projeto **dm-agro-c0e04**. O administrador principal é exclusivamente o UID **Z8zjZP3iWjRSD6imwiDO4VLboOY2**, informado pelo proprietário nesta conversa. Esse UID é um identificador público de autorização, não uma senha nem uma chave de serviço.

**As regras deste pacote são uma proposta testada localmente. Ainda não foram publicadas no projeto real.** Não instale o APK 1.1.0 para uso diário antes de concluir esta configuração. A versão 1.0.3 continua disponível na conversa.

## Por que as regras precisam mudar

As regras enviadas pelo proprietário conferiam somente a frente ao permitir aprovações por supervisores. Também permitiam que qualquer supervisor lesse as pendências da coleção antiga de todas as equipes. Isso não atende à separação confirmada: **frente + turno**.

As novas regras:

- Reservam o acesso geral ao UID principal, mesmo se outra conta tiver um campo `papel: administrador`.
- Permitem que supervisores aprovados aprovem/bloqueiem apenas operadores e tratoristas da própria frente e turno. Somente o principal aprova supervisores ou ajusta equipes.
- Mantêm o cadastro próprio como solicitação pendente. O cadastro não pode autoaprovar nem alterar a própria equipe após criação.
- Isolam registros em `equipes/<frente>_<turno>/registros/<id>`, conferindo equipe, autoria e revisão em cada gravação. Alterações antigas não sobrescrevem versões novas.
- Isolam fotos/PDFs em `equipes/<equipe>/arquivos/<autor>/<hash>` no Storage, sem criar links públicos de download.
- Preservam a coleção antiga `pendencias`, com leitura do próprio autor e do principal. Como os registros antigos não têm equipe confiável, o acesso amplo dos supervisores foi removido; os dados não são apagados.
- Não apagam usuários nem registros existentes. Exclusões da nova coleção são marcas sincronizadas, e os objetos de anexos são preservados no Storage.

## 1. Firestore: publicar as regras

1. No Chrome, abra https://console.firebase.google.com/project/dm-agro-c0e04/firestore e entre em **Regras**.
2. Guarde uma cópia das regras atuais. A versão fornecida na conversa também está preservada em `firestore.anterior.rules` neste pacote.
3. Copie **todo** o conteúdo de `firestore.rules` deste pacote e substitua o texto do editor de regras.
4. Toque em **Publicar** e confirme que o console não aponta erro.

O aplicativo consulta `configuracao/equipes` apenas para verificar que as regras novas estão ativas. **Não é necessário criar esse documento**. O aplicativo não pode gravá-lo.

## 2. Firestore: criar o índice de aprovações

Em **Índices → Composto → Adicionar índice**, crie:

| Campo | Ordem |
|---|---|
| frenteId | Crescente |
| turno | Crescente |
| papel | Crescente |
| solicitacaoPerfil | Crescente |

Coleção: **usuarios**. Escopo da consulta: **Coleção**. Aguarde o estado **Ativado/Pronto**. O mesmo índice está descrito em `firestore.indexes.json`. Se o console informar que o índice já existe, mantenha o existente.

## 3. Storage: permitir fotos e mapas por equipe

1. Abra **Storage** no mesmo projeto: https://console.firebase.google.com/project/dm-agro-c0e04/storage .
2. O bucket esperado pelo aplicativo é **dm-agro-c0e04.firebasestorage.app**. Se aparecer **Começar**, o Storage ainda precisa ser ativado. Se o console pedir mudança de plano, avise antes de confirmar, para avaliarmos esse requisito de cobrança.
3. Na aba **Regras**, guarde o texto atual e compartilhe-o se houver regras de outro uso que precisem ser preservadas. Este projeto Android não usava o SDK Storage antes desta integração.
4. Para este aplicativo, o conteúdo proposto está em `storage.rules`. As permissões são somente para os caminhos novos de equipes; os demais caminhos ficam fechados.
5. Ao publicar regras que consultam o Firestore, o console poderá pedir a ativação da integração entre Storage e Firestore. Essa integração é necessária para conferir a equipe do usuário.

Os arquivos têm limite de **menos de 100 MB** por anexo. Registros muito grandes e anexos acima desse limite permanecem locais e aparecem como pendência na sincronização.

## 4. Instalar e testar em dois celulares

Atualize sobre o APK anterior, sem desinstalar nem limpar os dados. A assinatura foi mantida.

1. Entre como principal, escolha uma equipe e aprove um supervisor da frente e turno correspondentes.
2. No supervisor, confirme que só aparecem solicitações de operadores/tratoristas daquela equipe; pedidos de supervisor e de outros turnos devem ficar indisponíveis.
3. No operador aprovado, crie uma pendência e uma ocorrência com foto. Com internet, confira os registros no supervisor e no administrador na mesma equipe. Os anexos podem levar mais tempo que o texto; acompanhe **Sincronização**.
4. Teste um supervisor da mesma frente com outro turno. Ele não deve acessar os registros anteriores.
5. Com o aplicativo já aberto, desligue a internet, crie um registro, religue a internet e aguarde o envio. A fila é preservada no aparelho. A abertura de uma nova sessão exige conexão para validar o acesso.
6. Teste alterações simultâneas: conflitos devem preservar uma cópia local, em vez de sobrescrever outro aparelho. A opção **Sincronização** mostra as pendências e permite recarregar a versão compartilhada. As cópias conflitantes ficam na pasta `conflitos` da equipe, incluída no backup.
7. Verifique mapas, pinturas, GPS, talhões, O.S., estoque, relatórios, caminhões, compartilhamento e backup dentro da equipe selecionada.
8. Teste **Voltar**: navega sem encerrar a sessão; na tela inicial, recolhe o aplicativo. Somente **Sair da conta** encerra a sessão e pede as credenciais novamente.

## Dados antigos do aparelho

Os dados das versões anteriores continuam preservados no local original. Eles não identificam equipe. Só o principal vê **Dados antigos deste aparelho** e pode copiar os dados para a equipe escolhida após conferir a origem. Não importe um conjunto que misture várias equipes. A importação não apaga os originais nem substitui IDs existentes; registros novos da equipe são mantidos. Supervisores não podem executar essa atribuição.

A troca de equipe de um usuário não move registros antigos para a nova equipe. Os registros continuam vinculados à frente e ao turno onde foram criados.

## Verificações realizadas e limites

Os testes de regras são executados com dados fictícios no projeto local `demo-dm-agro`; não usam nem modificam usuários ou registros reais. Os testes Android verificam políticas, isolamento local, referências de fotos e migração preservando originais. A compilação gera o APK de teste; o teste final com o projeto real depende da publicação das regras, do índice, do Storage e dos dois celulares.

A credencial antiga do João (80911) continua sendo um caso separado de Firebase Authentication: a aprovação no Firestore não cria nem redefine uma senha.
