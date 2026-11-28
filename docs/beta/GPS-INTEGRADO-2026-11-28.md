# GPS offline — correção estável e integração no Beta

Em 28/11, Diego confirmou a perda/parada do marcador com o mapa já aberto quando cai a internet. Foi preparada uma correção independente da versão estável, mantendo a Professional separada e sem aguardar mudanças de Firebase ou migração de dados.

**A versão disponível aos usuários é a [2.2.8, código 40](https://github.com/Diegomachadosl10/colheita-gps-oficial/releases/tag/v2.2.8).** [Correções, testes e publicação verificada](https://github.com/Diegomachadosl10/colheita-gps-oficial/blob/dm-agro-login/docs/GPS-OFFLINE-2.2.8.md). O manifesto do aplicativo e o APK público foram baixados sem autenticação e conferidos por tamanho, hash e assinatura. Mapas, pinturas, regras, serviço e dados dos usuários não foram migrados.

O rastreador usa somente GNSS, mantém uma solicitação contínua, identifica precisão reduzida em âmbar e posição antiga em cinza, tolera retomada com leituras espaçadas e remove o transporte AndroidX corretamente ao pausar. O código do rastreador e o teste nativo sem redes são idênticos na estável e nesta integração Beta.

O Beta incorporou essas correções em `8a25cd638a3e08db3d19ec97766e54bed8f0534d` e passou para **2.3.0/código 41**, reservado para uma futura atualização superior à estável 40. **Não houve distribuição de APK Beta.** Identidade, namespace, permissões e bloqueio de serviços Beta não homologados continuam preservados.

[Actions 46115678501](https://github.com/Diegomachadosl10/colheita-gps-oficial/actions/runs/46115678501): os quatro jobs de integração passaram — Android/lint, regras nos emuladores, serviços/publicador e Android nativo. O último executou três testes, incluindo o de GPS com Wi-Fi/dados desligados e ausência de rede ativa, com posições fictícias. A suíte Python local passou com 14 testes. O publicador também incorpora o pacote mínimo APK/manifesto/SHA256, sem acrescentar arquivos de código/histórico implicitamente.

A integração preserva a correção para futuras versões; não conclui a Professional. Continuam pendentes os acessos administrativos seguros, homologação do servidor, continuidade de credenciais, migração local/outbox/corte e demais ferramentas do plano. Testes com GNSS sintético não medem a recepção física no campo.
