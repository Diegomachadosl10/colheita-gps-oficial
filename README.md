# DM AGRO — Colheita GPS

Android, ramificação de trabalho **dm-agro-login**. A `main` permanece intacta.

## Versão 2.0.0

Veja [as dez melhorias da rotina no campo](docs/EVOLUCAO-2.0.0.md). Mantido [o envio de pendências por mensagem, imagem e de todas as abertas](docs/PENDENCIAS-WHATSAPP-1.9.2.md). Mantida [a organização das telas e o menu simplificado](docs/VISUAL-1.9.1.md). Mantidos [busca, favoritos, urgência e temas claro/noturno](docs/MELHORIAS-1.9.0.md). A [simplificação da versão 1.8.3](docs/SIMPLIFICACAO-1.8.3.md) foi preservada. O [envio de estoque por mensagem e fotos](docs/ESTOQUE-WHATSAPP-1.8.2.md) continua disponível. A [correção do fechamento ao compartilhar](docs/CORRECAO-COMPARTILHAMENTO-1.8.1.md) foi preservada. A configuração dos supervisores permanece descrita na [versão 1.8.0](docs/ATUALIZACAO-1.8.0.md). O download dentro do aplicativo depende de publicar os releases no GitHub com a assinatura permanente. Histórico: [versão 1.2.0](ATUALIZACOES-1.2.0.md).

## Equipes por frente e turno

- O UID principal possui acesso geral e gerencia todos os usuários. Supervisores entram ao concluir o cadastro, sem aprovação do principal; operadores e tratoristas continuam pendentes até a aprovação de um gestor da equipe.
- Supervisores usam as ferramentas dentro de sua equipe e aprovam somente operadores/tratoristas da mesma frente e turno.
- Registros de manutenção, estoque e caminhões são armazenados por equipe. Os mapas novos dos operadores e tratoristas e suas pinturas ficam privados no aparelho. Equipamentos permanecem cadastrados mesmo sem pendências abertas. Registros de texto usam Firestore com cache e fila local. **Plano Spark gratuito: anexos permanecem locais; o estoque é enviado por mensagem com fotos/mapas anexados, e os relatórios das demais ferramentas usam imagens**, sem ativar Storage nem faturamento.
- As revisões detectam alterações simultâneas; cópias conflitantes são preservadas. O painel **Sincronização** informa o estado do envio.
- Dados antigos não são apagados nem atribuídos automaticamente a uma equipe. A importação de dados sem equipe é exclusiva do principal.
- Matrícula e senha não exigem e-mail pessoal. Os endereços técnicos continuam `matricula.<matricula>@acesso.dm-agro.app`.
- O olho nos campos permite mostrar/ocultar senha. **Voltar** navega sem encerrar a sessão; somente **Sair da conta** encerra a sessão.

**O plano deve permanecer Spark, sem upgrade. É necessário publicar as regras Firestore e criar o índice antes de usar esta versão.** Consulte [o guia para fazer pelo celular](firebase/CONFIGURAR-PELO-CELULAR.md). As regras novas ainda não foram publicadas automaticamente; nenhuma credencial administrativa do Firebase foi fornecida à sessão.

## Gerar APK pelo GitHub Actions

O workflow `.github/workflows/android.yml` testa as regras em emuladores isolados e depois executa os testes Android e `assembleDebug` com Java 17/Gradle 8.9. Abra Actions → **Gerar APK Colheita GPS** → **Run workflow** → escolha **dm-agro-login**. Espere o resultado verde e baixe **DM-AGRO-APK** em **Artifacts**, extraia o ZIP e instale o APK.

O usuário autorizou explicitamente o envio da versão 2.0.0 para Diegomachadosl10/colheita-gps-oficial, somente na branch dm-agro-login, preservando main. Após essa autorização, tanto o Git quanto a integração GitHub recusaram a escrita com HTTP 403 (Resource not accessible by integration). Enquanto o acesso de escrita não estiver resolvido, o GitHub contém a versão anterior; os arquivos e APKs desta conversa são as entregas atualizadas. Não substitua a `main`.

Não desinstale nem limpe os dados para atualizar. Os APKs produzidos nesta sessão compartilham a mesma assinatura de teste. O workflow agora exige a chave permanente em segredo protegido e verifica o certificado antes de publicar, para não depender de um cache que pode expirar.

## Testes locais

```sh
gradle testDebugUnitTest assembleDebug --no-daemon
npm ci --prefix tests/firebase
tests/firebase/node_modules/.bin/firebase emulators:exec --project demo-dm-agro --only firestore,storage 'cd tests/firebase && npm test'
```

Os emuladores usam exclusivamente o projeto fictício `demo-dm-agro`. Não há implantação Firebase no workflow. A publicação GitHub só acontece em execução manual, na ramificação dm-agro-login, com a opção publicar_atualizacao marcada. As regras antigas fornecidas pelo proprietário estão em `firebase/firestore.anterior.rules`; os arquivos para publicar no modo gratuito são `firebase/firestore.rules` e `firebase/firestore.indexes.json`. `firebase/storage.rules` é apenas preparação isolada e não deve ser publicado nem ativado neste plano.
