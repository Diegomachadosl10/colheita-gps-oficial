# DM AGRO — Colheita GPS

Android, ramificação de trabalho **dm-agro-login**. A `main` permanece intacta.

## Versão 1.2.0

Veja [as melhorias, testes e publicação de atualizações](ATUALIZACOES-1.2.0.md). O download dentro do aplicativo depende de publicar os releases no GitHub com a assinatura permanente.

## Equipes por frente e turno

- O UID principal possui acesso geral e aprova supervisores.
- Supervisores usam as ferramentas dentro de sua equipe e aprovam somente operadores/tratoristas da mesma frente e turno.
- Registros de manutenção, estoque, relatórios e caminhões são armazenados por equipe. Os mapas novos dos operadores e tratoristas e suas pinturas ficam privados no aparelho. Equipamentos permanecem cadastrados mesmo sem pendências abertas. Registros de texto usam Firestore com cache e fila local. **Plano Spark gratuito: fotos e PDFs permanecem locais e são compartilhados manualmente**, sem ativar Storage nem faturamento.
- As revisões detectam alterações simultâneas; cópias conflitantes são preservadas. O painel **Sincronização** informa o estado do envio.
- Dados antigos não são apagados nem atribuídos automaticamente a uma equipe. A importação de dados sem equipe é exclusiva do principal.
- Matrícula e senha não exigem e-mail pessoal. Os endereços técnicos continuam `matricula.<matricula>@acesso.dm-agro.app`.
- O olho nos campos permite mostrar/ocultar senha. **Voltar** navega sem encerrar a sessão; somente **Sair da conta** encerra a sessão.

**O plano deve permanecer Spark, sem upgrade. É necessário publicar as regras Firestore e criar o índice antes de usar esta versão.** Consulte [o guia para fazer pelo celular](firebase/CONFIGURAR-PELO-CELULAR.md). As regras novas ainda não foram publicadas automaticamente; nenhuma credencial administrativa do Firebase foi fornecida à sessão.

## Gerar APK pelo GitHub Actions

O workflow `.github/workflows/android.yml` testa as regras em emuladores isolados e depois executa os testes Android e `assembleDebug` com Java 17/Gradle 8.9. Abra Actions → **Gerar APK Colheita GPS** → **Run workflow** → escolha **dm-agro-login**. Espere o resultado verde e baixe **DM-AGRO-APK** em **Artifacts**, extraia o ZIP e instale o APK.

A integração atual recusou os envios com HTTP 403. Enquanto esse acesso não for resolvido, o GitHub contém a versão anterior; os arquivos e APKs desta conversa são as entregas atualizadas. Não substitua a `main`.

Não desinstale nem limpe os dados para atualizar. Os APKs produzidos nesta sessão compartilham a mesma assinatura de teste. O workflow agora exige a chave permanente em segredo protegido e verifica o certificado antes de publicar, para não depender de um cache que pode expirar.

## Testes locais

```sh
gradle testDebugUnitTest assembleDebug --no-daemon
npm ci --prefix tests/firebase
tests/firebase/node_modules/.bin/firebase emulators:exec --project demo-dm-agro --only firestore,storage 'cd tests/firebase && npm test'
```

Os emuladores usam exclusivamente o projeto fictício `demo-dm-agro`. Não há implantação Firebase no workflow. A publicação GitHub só acontece em execução manual, na ramificação dm-agro-login, com a opção publicar_atualizacao marcada. As regras antigas fornecidas pelo proprietário estão em `firebase/firestore.anterior.rules`; os arquivos para publicar no modo gratuito são `firebase/firestore.rules` e `firebase/firestore.indexes.json`. `firebase/storage.rules` é apenas preparação isolada e não deve ser publicado nem ativado neste plano.
