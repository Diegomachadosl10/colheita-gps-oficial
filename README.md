# DM AGRO — Colheita GPS

Android, ramificação de trabalho **dm-agro-login**. A `main` permanece intacta.

## Versão 1.1.0 — equipes por frente e turno

- O UID principal possui acesso geral e aprova supervisores.
- Supervisores usam as ferramentas dentro de sua equipe e aprovam somente operadores/tratoristas da mesma frente e turno.
- Registros, mapas, pinturas, O.S., talhões, estoque, relatórios, caminhões, pendências e ocorrências são armazenados por equipe. Registros e anexos usam Firestore e Storage, com cache e fila local.
- As revisões detectam alterações simultâneas; cópias conflitantes são preservadas. O painel **Sincronização** informa o estado do envio.
- Dados antigos não são apagados nem atribuídos automaticamente a uma equipe. A importação de dados sem equipe é exclusiva do principal.
- Matrícula e senha não exigem e-mail pessoal. Os endereços técnicos continuam `matricula.<matricula>@acesso.dm-agro.app`.
- O olho nos campos permite mostrar/ocultar senha. **Voltar** navega sem encerrar a sessão; somente **Sair da conta** encerra a sessão.

**É necessário configurar o Firebase antes de usar esta versão.** Consulte [o guia para fazer pelo celular](firebase/CONFIGURAR-PELO-CELULAR.md). As regras novas ainda não foram publicadas automaticamente; nenhuma credencial administrativa do Firebase foi fornecida à sessão.

## Gerar APK pelo GitHub Actions

O workflow `.github/workflows/android.yml` testa as regras em emuladores isolados e depois executa os testes Android e `assembleDebug` com Java 17/Gradle 8.9. Abra Actions → **Gerar APK Colheita GPS** → **Run workflow** → escolha **dm-agro-login**. Espere o resultado verde e baixe **DM-AGRO-APK** em **Artifacts**, extraia o ZIP e instale o APK.

A integração atual recusou os envios com HTTP 403. Enquanto esse acesso não for resolvido, o GitHub contém a versão anterior; os arquivos e APKs desta conversa são as entregas atualizadas. Não substitua a `main`.

Não desinstale nem limpe os dados para atualizar. Os APKs produzidos nesta sessão compartilham a mesma assinatura de teste. O cache do workflow tenta preservar a chave de teste entre execuções, mas uma distribuição definitiva exige um keystore permanente protegido.

## Testes locais

```sh
gradle testDebugUnitTest assembleDebug --no-daemon
npm ci --prefix tests/firebase
tests/firebase/node_modules/.bin/firebase emulators:exec --project demo-dm-agro --only firestore,storage 'cd tests/firebase && npm test'
```

Os emuladores usam exclusivamente o projeto fictício `demo-dm-agro`. Não há implantação Firebase no workflow. As regras antigas fornecidas pelo proprietário estão em `firebase/firestore.anterior.rules`; os arquivos propostos são `firebase/firestore.rules`, `firebase/storage.rules` e `firebase/firestore.indexes.json`.
