# DM AGRO — correção de GPS offline

- Esta branch `fix/gps-offline` parte da produção estável 2.2.7 (`e07aee3`). Corrigir o GPS usado no campo sem trazer o namespace, cadastro ou migração da Professional Beta para a produção.
- O relato de 28/11/2026 foi confirmado por Diego: com o mapa já aberto, a bolinha para/some e fica buscando quando cai a internet. O foco é a localização no mapa aberto, não mudar a autenticação ou regras Firebase.
- Preservar dados, mapas, pinturas, aplicação `com.colheitagps.app` e certificado fixado em `scripts/android-signing.sha256`. Não exigir desinstalação, limpar dados ou trocar chave.
- Correção candidata 2.2.8, código 40. O 39 estava reservado para a Beta ainda não distribuída; a futura Beta precisa usar código maior que 40 se esta correção for publicada.
- Testar GPS por satélite, leitura inicial de precisão reduzida identificada como aproximada, perda/retorno, pausas, provedores, saltos e remoção de assinaturas. No Android nativo, usar somente posições fictícias com Wi-Fi/dados desligados; não autenticar usuários reais nem gerar registros no Firebase.
- Só disponibilizar um APK depois de verificar compilação, testes, assinatura e atualização compatível. A autorização anterior de Diego para disponibilizar a atualização no aplicativo permanece registrada na branch Professional; uma correção completa da versão estável deve preservar o projeto Beta separado e não ser apresentada como conclusão da Professional.
- Não ativar custos, serviços de localização pagos, escuta por voz ou localização em segundo plano. Não exportar chave privada nem anexar APK assinado pela chave descartável do runner.
