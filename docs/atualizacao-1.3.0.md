# DM AGRO 1.3.0 — painel verde, previsão e notificações

Android `versionCode 9`, pacote `com.colheitagps.app`, ramificação `dm-agro-login`. Atualizar sobre a versão instalada, com a mesma assinatura, sem desinstalar ou limpar dados.

## Painel escolhido

Tema verde escuro, marca DM AGRO, ícones vetoriais, cartões em duas colunas e faixa compacta de frente/turno. Fonte muito ampliada ou tela estreita usa uma coluna para manter legibilidade. A saudação usa o nome do cadastro, o nome do Authentication ou o rótulo de administrador. O principal pode definir seu próprio nome.

Mapas, Pendências, Equipamentos, Chuva por turno e Ocorrências continuam disponíveis ao operador. Supervisores e principal mantêm O.S., estoque, relatórios, caminhões, calendário e aprovações conforme suas permissões. Atualização, saída da conta, backup e sincronização ficam no menu ⋮. Voltar não encerra a sessão. O principal mantém a troca de equipe; supervisores não mudam sua frente/turno.

## Corrigir equipamento

Toque no equipamento → **Corrigir número do equipamento** (gestores). Pendências abertas e históricas conservam IDs, fotos e informações, e passam para o número correto. O número anterior fica registrado. Uma correção não mistura equipamentos que já possuem registros. Exclusão de equipamentos e pendências continua restrita a gestores, com confirmação e preservação do histórico da versão 1.2.2.

## Previsão gratuita no aplicativo

Fonte: **MET Norway / MET Weather API**, licença **CC BY 4.0** (https://api.met.no/doc/License). A API permite uso comercial sem chave paga, com identificação do aplicativo e atribuição. Foi verificada uma resposta real para a região de Santo Inácio, sem usar credenciais.

O usuário escolhe o município e estado de referência, sem alterar sua equipe. A lista de municípios é local, licenciada em MIT por Kelvin S. do Prado (https://github.com/kelvins/municipios-brasileiros); o aviso de licença acompanha os assets. Não há API paga de geocodificação nem coleta de GPS para essa consulta.

Temperatura, vento, umidade e condições são **previsões**, sem serem apresentadas como medições do campo. A tela informa a atualização do modelo, calcula faixas a partir dos horários disponíveis, identifica dados guardados e sinaliza desatualização/falha de conexão. As respostas são guardadas conforme o prazo da fonte e por no mínimo 15 minutos para evitar consultas desnecessárias.

## Notificações e contas — exige configuração única

O APK contém o cliente FCM, o recebimento em segundo plano e as telas administrativas. **Os avisos e a gestão do Authentication dependem de publicar e configurar o Worker do proprietário. O APK sozinho não fornece um servidor ou credencial administrativa.** Ver `backend/README.md` para implantação pelo celular.

Cada supervisor recebe novos registros e solicitações da própria frente/turno. Os avisos do administrador são limitados à frente escolhida, inicialmente **Santo Inácio**, em seus turnos. Seu acesso administrativo às demais frentes não muda. Mapas pessoais não geram notificações à equipe. O Android precisa autorizar as notificações, e o celular precisa de internet para receber; envio imediato não é uma garantia de entrega em um aparelho sem conexão, forçado a parar ou restringido pelo sistema.

O principal pode **Desativar acesso**, mantendo a matrícula reservada; **Corrigir matrícula**, atualizando Authentication e Firestore sem perder senha e UID; ou **Excluir definitivamente e liberar matrícula**, removendo credencial e cadastro com confirmação da senha de administrador. Registros operacionais continuam preservados. Cadastros anteriormente excluídos aparecem em uma lista própria para corrigir ou finalizar a exclusão. Supervisores não recebem essas ações administrativas.

## Regras a publicar e motivo

O arquivo completo é `firebase/firestore.rules`. As regras da versão 1.2.2 para exclusão e histórico continuam incluídas. As adições desta versão são:

- O UID principal pode criar/ajustar somente seu campo `nome`, sem alterar permissões da própria conta.
- Usuários autenticados leem somente o endereço público em `configuracao/servico`; somente o principal pode gravar um endereço HTTPS `workers.dev`, sem campo de segredo.
- Tokens de aparelhos, eventos, auditorias e operações de conta não são acessíveis diretamente pelo SDK do cliente. O servidor autentica e valida as operações com sua conta de serviço.
- Um marcador privado por UID em `contasEncerradas` impede que um token antigo de uma conta apagada recrie seu cadastro. A matrícula continua disponível para um novo UID, com nova solicitação e aprovação.

Nenhuma regra é publicada automaticamente. Não ativar Storage, Firebase Functions, faturamento ou plano pago. Manter **Spark e Workers Free**, respeitando as cotas gratuitas. As chaves privadas ficam exclusivamente no segredo do Worker e não acompanham o APK, o código público ou o pacote de publicação.

## Publicar atualização pelo celular

1. Configurar Worker e publicar as regras completas desta versão.
2. Extrair `PUBLICAR-DM-AGRO-1.3.0.zip` numa pasta nova.
3. GitHub → Releases → New release: tag **v1.3.0**, ramificação **dm-agro-login**, título **DM AGRO 1.3.0**; estável e mais recente (**Latest**).
4. Na caixa **Attach binaries…**, abaixo da descrição, anexar os cinco arquivos extraídos: APK, manifesto `dm-agro-update.json`, checksum, código ZIP e bundle. Manter os nomes e aguardar o término dos uploads.
5. No DM AGRO instalado: **Verificar atualização → Baixar → Instalar**. O botão fica no menu ⋮ e no login. Atualizar os dois celulares.

A conexão GitHub desta sessão vinha recusando gravação com HTTP 403. Os anexos `codigo.zip` e `.bundle` preservam o código atual enquanto os commits locais não puderem ser enviados ao repositório. Não presumir que os Source code automáticos ou o workflow remoto contenham estes commits.

## Teste nos celulares

Conferir nome, visual, Voltar, mapas, pendências e ferramentas anteriores. Como gestor, corrigir um equipamento fictício e verificar fotos e histórico. Escolher Santo Inácio/PR na previsão e conferir fonte e atualização. Configurar serviço e ativar as notificações no destinatário. Pelo outro celular, adicionar uma pendência na mesma equipe e conferir a notificação com o destinatário em segundo plano. Testar outra frente e verificar que ela não avisa o administrador inscrito em Santo Inácio. Em usuários fictícios, testar correção de matrícula, login pela matrícula nova, exclusão definitiva e novo cadastro da matrícula liberada.

Validação local: testes Android e renderização nativa, regras nos emuladores `demo-dm-agro`, testes do Worker com Google/Firestore/FCM simulados e testes do preparo de atualização. A publicação real do Worker, a configuração de seu segredo e a entrega FCM nos aparelhos dependem das etapas do proprietário; não são apresentadas como testes já realizados.

Resultado: compilação concluída, **51 testes Android**, **17 testes Firebase emulados**, **12 testes do Worker** e **3 testes Python** aprovados (**83 no total**). A imagem `PAINEL-1.3.0.png` foi renderizada pelos componentes Android em teste nativo; não é uma captura de um celular físico.
