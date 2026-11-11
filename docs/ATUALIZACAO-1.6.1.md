# DM AGRO 1.6.1 — Caminhões por período de turno

## O que mudou

Foram removidos o cartão, os botões e a tela específica de aviso de equipamento encalhado, incluindo os atalhos no início, nas ocorrências, no menu geral e no mapa. Os avisos que já existiam continuam nas ocorrências comuns, com seu tipo, equipamento, fotos e coordenadas preservados. A rotação livre do mapa da versão 1.6.0 continua disponível.

O controle de caminhões abre em Turno atual e renova a lista ativa conforme o horário local do celular:

| Turno | Início | Fim |
| --- | --- | --- |
| A | 07:00 | 15:20 |
| B | 15:20 | 23:20 |
| C | 23:20 | 07:00 do dia seguinte |

O horário de fim pertence ao turno que começa naquele instante. O turno C é um único período atravessando a meia-noite: uma entrada às 00:30 do dia 12 pertence ao C iniciado às 23:20 do dia 11. O turno C não zera às 00:00.

Ao terminar o período, a lista ativa fica vazia. Caminhões sem saída de turnos anteriores não reaparecem como pendentes no período novo. Os registros permanecem no histórico, com os mesmos identificadores, informações e fotos locais. O reset é uma mudança da lista exibida; não há exclusão automática dos dados nem escrita destrutiva no Firebase.

A virada é verificada com a tela aberta e quando o aplicativo volta ao primeiro plano. O horário de corte da lista ativa também é limpo quando muda o período. Abrir Caminhões pelo início ou menu sempre retorna ao turno atual; voltar da edição de um registro do histórico mantém o filtro de consulta.

As permissões de frente e turno continuam as mesmas. Um supervisor acompanha a equipe do seu cadastro; fora do horário dela, a lista ativa fica vazia e o histórico continua acessível. O aplicativo não muda a equipe do usuário pelo relógio. O administrador pode usar Trocar equipe para acompanhar outro turno.

## Histórico e compartilhamento

Na tela Caminhões, use Histórico → Escolher data de início do turno para consultar um período anterior. Para o C da noite do dia 11 e madrugada do dia 12, selecione o dia 11. Histórico → Todos os registros permite consultar também cadastros antigos sem data/horário válido ou criados fora dos horários agora definidos.

O PDF, a imagem e o resumo em texto usam os registros e totais do período exibido. Até que horas continua disponível: no C, um corte de 01:00 corresponde à madrugada do dia seguinte ao início do turno. A consulta do turno completo mostra a situação até o encerramento; saídas posteriores podem ser conferidas em Todos os registros, que mantém os horários reais. O filtro não altera o horário de saída salvo.

Novas entradas e mudanças de data/horário de entrada precisam corresponder ao turno da equipe aberta. Ao digitar uma entrada histórica do C depois da meia-noite, a data real é ajustada para o dia seguinte à data de início escolhida, exceto se a pessoa já tiver escolhido a data manualmente. Cadastros antigos fora do horário continuam no histórico e podem receber correção de saída sem exigir mudança da entrada antiga.

## Instalar e testar pelo celular

Instale DM-AGRO-1.6.1.apk sobre a versão atual, sem desinstalar nem limpar dados. A versão é código 18, com a mesma assinatura dos APKs anteriores e Android mínimo 8.0/API 26.

1. Confira que o botão de encalhado saiu do início, das ocorrências e do menu do mapa. Se havia um aviso antigo, ele deve continuar nas ocorrências e permitir a edição normal autorizada.
2. Abra Caminhões e confira o período e a equipe no cabeçalho. Registre um caminhão com data e horário correspondentes ao seu turno.
3. Após a virada do turno, confira que a lista ativa e os totais zeraram. Abra Histórico → Todos os registros e confira que o caminhão anterior continua salvo, inclusive se ainda estava sem saída.
4. No turno C, confira que uma entrada antes da meia-noite e outra depois da meia-noite aparecem no mesmo período. O reset ocorre às 07:00.
5. Use Histórico → Escolher data de início do turno. Selecione a data inicial de um C anterior e confira os horários da madrugada do dia seguinte.
6. Teste Até que horas e compartilhe PDF ou imagem. Compare os totais com a tela. Confira também a rotação do mapa com dois dedos e o botão 0°.

Compilação testDebugUnitTest + assembleDebug aprovada: 115 testes Android, incluindo as três viradas com a tela aberta, continuidade do C à meia-noite, separação de caminhões antigos sem saída, histórico e corte após a meia-noite, edição de registro antigo sem perda de dados e regressão da rotação do mapa. O APK não foi instalado em aparelho físico nesta sessão; a conferência final permanece nos celulares.

## Disponibilizar a atualização pelo aplicativo

A conexão GitHub pode continuar recusando gravação com HTTP 403. Se isso acontecer, o APK e o código ficam prontos nesta entrega, mas a publicação não ocorre automaticamente. As alterações ficam na ramificação local dm-agro-login, no ZIP de código e no bundle; a main é preservada.

1. Baixe PUBLICAR-DM-AGRO-1.6.1.zip e extraia em uma pasta nova.
2. Abra https://github.com/Diegomachadosl10/colheita-gps-oficial/releases/new . Crie a tag v1.6.1, destino dm-agro-login e título DM AGRO 1.6.1.
3. Em Attach binaries… abaixo da descrição, anexe os cinco arquivos extraídos: DM-AGRO-1.6.1.apk, dm-agro-update.json, SHA256.txt, DM-AGRO-1.6.1-codigo.zip e DM-AGRO-1.6.1-dm-agro-login.bundle. Não use Add files dentro da descrição.
4. Publique como versão estável mais recente (Latest), sem Pre-release. Os aplicativos poderão encontrá-la ao abrir ou em Verificar atualização.

Os mesmos cinco arquivos também estão separados em reenvio-1.6.1 caso o ZIP não abra no celular. Publicar os anexos não atualiza o código da ramificação remota; o Source code automático do GitHub pode representar o commit remoto antigo. O ZIP de código e o bundle contêm esta versão.

Para gerar pelo Actions quando a ramificação remota contiver as alterações e a assinatura permanente estiver configurada: Actions → Gerar APK Colheita GPS → Run workflow → dm-agro-login. O APK desta entrega já está compilado e assinado.

Não há alteração nas regras do Firebase, no Worker Cloudflare, nas permissões de usuários ou no plano gratuito para esta correção.
