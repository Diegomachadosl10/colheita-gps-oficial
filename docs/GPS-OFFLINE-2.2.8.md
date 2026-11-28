# DM AGRO 2.2.8 — GPS por satélite no mapa

Correção independente baseada na versão estável 2.2.7. A Professional Beta permanece separada: esta versão não troca o ambiente de dados, a autenticação, as regras Firebase, o serviço de avisos, mapas ou pinturas. Mantém `com.colheitagps.app` e a assinatura permanente. O código Android é 40; o 39 estava reservado para uma Beta não distribuída, que deverá usar código maior antes de qualquer distribuição futura.

## Relato e correções

Diego confirmou que, com o mapa aberto, a bolinha para/some e fica buscando quando a internet cai. A auditoria encontrou caminhos que podem produzir esse comportamento aparente: rejeição de GNSS acima de 50 m enquanto uma estimativa de rede menor que 20 m podia aparecer como posição atual; rejeição de leituras GNSS degradadas em relação à anterior; confirmação de retomada limitada a intervalos curtos. Não houve acesso ao receptor físico de Diego para afirmar uma causa única no seu aparelho.

- A posição do mapa passa a vir exclusivamente do provedor GPS por satélite do Android. Estimativas de Wi-Fi, telefonia ou do provedor combinado não iniciam nem deslocam a bolinha. O rastreador não consulta conectividade, Firebase ou um serviço de localização pago.
- Uma única solicitação contínua de GPS permanece ativa enquanto o mapa está em primeiro plano, inclusive com o aparelho parado. Não há cancelamento/recriação de consultas pontuais a cada mudança de rede ou a cada 15 segundos enquanto o receptor tenta adquirir satélites.
- A leitura atual de GPS com precisão reduzida, até 100 m, pode atualizar a posição sem ficar presa ao ponto antigo de melhor precisão. Acima de 25 m o marcador fica âmbar e o texto informa **GPS aproximado ±N m**. Não é apresentado como uma posição de boa precisão.
- Leituras não finitas, sem precisão, acima de 100 m ou antigas demais continuam recusadas. Coordenadas não são suavizadas, deslocadas nem encaixadas em um talhão. Saltos incompatíveis ainda precisam de confirmação; a confirmação tolera leituras espaçadas de até 15 segundos.
- Uma posição com mais de 8 segundos fica cinza, identificada como última posição. Aceitar uma amostra entregue com atraso de até 15 segundos não transforma uma posição antiga em atual. A primeira leitura válida e coerente retoma o acompanhamento.
- O texto acompanha os satélites usados/visíveis quando o Android disponibiliza essa informação. Mudar Wi-Fi/dados não reinicia a assinatura GPS; desligar/religar a localização do aparelho provoca a nova assinatura necessária.
- O teste também detectou uma solicitação remanescente na API 28 depois de parar o rastreador. O cancelamento agora usa `LocationManagerCompat.removeUpdates`, que remove o transporte compatível criado pelo AndroidX. Isso evita acumular solicitações ao pausar/retomar nas versões antigas do Android.
- Perder a permissão precisa deixa a posição anterior cinza. Não há pedido de localização em segundo plano.

## Teste nativo sem internet

`OfflineGpsNativeTest` desliga Wi-Fi e dados móveis no emulador Android API 35 e verifica que não existe rede ativa. Usa uma atividade visível, o `LocationManager` real e um provedor GPS de teste autorizado no emulador, com coordenadas fictícias. Confere a primeira leitura de 65 m identificada como aproximada, melhora para 6 m, movimento, desligar/religar o GPS e cancelamento ao sair. Nenhuma conta é criada/autenticada, e nenhum registro operacional é enviado ao Firebase.

Esse teste comprova a integração Android sem rede com posições sintéticas; não mede a antena, assistência GNSS ou condições de céu do celular usado no campo. A primeira posição real depende de o receptor conseguir uma leitura dos satélites. A correção é do GPS com o mapa aberto e salvo no aparelho; não muda o requisito atual de verificação de acesso ao entrar novamente na versão estável.

## Validação e distribuição

- [Actions 46114463001](https://github.com/Diegomachadosl10/colheita-gps-oficial/actions/runs/46114463001), fonte `dd6aadd40fdd944e67401f5d655df535bdf62c02`: três jobs aprovados. Executados 289 testes Android, 1 teste instrumentado com redes desligadas, 18 testes do Worker e 7 testes Python do publicador. A compilação local inclui o APK de aplicativo e o APK de teste; lint sem erros, com 123 avisos.
- O teste nativo terminou na API 35 em 28/11/2026 e cobriu ausência de rede ativa, GNSS fictício aproximado, melhora, movimento, retorno do provedor e cancelamento. Isso complementa os testes das APIs 28/35 que detectaram a assinatura remanescente no Android antigo.
- APK local verificado: 13.121.381 bytes, SHA-256 `bd461aeab7ddb5375eb301995e814dcb4d42217f670052d27f0bca7e6e9bd55a`. Certificado idêntico ao fixado em `scripts/android-signing.sha256`. Nenhuma chave privada foi anexada.
- A revisão automática bloqueou uma tentativa de anexar também ZIP de código e bundle do histórico. A alternativa preparada contém **exatamente APK, manifesto e SHA256**, suficientes para atualizar o aplicativo. Arquivos de código/histórico ficam fora da publicação desta correção.
- O publicador pré-assinado passou a aceitar o pacote mínimo de três arquivos, mantendo compatibilidade com o formato antigo completo. Dois testes verificam o pacote mínimo e a rejeição de combinações incompletas; a suíte Python agora tem 9 testes aprovados.

[Versão 2.2.8 publicada](https://github.com/Diegomachadosl10/colheita-gps-oficial/releases/tag/v2.2.8), código 40, em 28/11/2026. [Publicador 46115317909 aprovado](https://github.com/Diegomachadosl10/colheita-gps-oficial/actions/runs/46115317909), com os 9 testes e verificação do APK/assinatura antes de marcar a release como mais recente. O manifesto estável e o APK foram baixados novamente **sem autenticação**, conferidos por tamanho, SHA-256 e certificado. O aplicativo oferece esta versão em seu fluxo normal de atualização.

O pacote público contém somente `DM-AGRO-2.2.8.apk`, `dm-agro-update.json` e `SHA256.txt`. O manifesto tem SHA-256 `6ca3c48fe1b0a5bc2859a8398dc6ff1271b69031636873fc76e26fcf7c90db05`. Nenhum arquivo de código/histórico foi enviado na tentativa bloqueada; a alternativa mínima foi aprovada e publicada. O APK de teste com a chave descartável do runner não foi distribuído.
