# Etapa 1 — nossa equipe no Beta

Escopo autorizado em 26/11/2026: continuar o desenvolvimento somente para nossa equipe/empresa, deixando outras empresas para depois. Branch `dm-agro-professional-beta`, baseada na produção 2.2.7. Versão interna em desenvolvimento: **2.3.0, código 39**. Não é uma atualização publicada.

Este documento registra a primeira etapa. A continuação de estoque com texto livre/observações e PDF opcional está na [entrega 2](ENTREGA-2-ESTOQUE-PDF.md); as pendências abaixo refletem o momento desta primeira entrega.

## Implementado no código

- Identificação DM AGRO, Gestão Operacional Agrícola, Beta gratuita e autoria nas configurações. O início informa que os dados de teste estão separados. Não existe seletor de empresa nem cobrança.
- Perfis, configuração e registros do Beta usam `ambientes/beta`. Preferências operacionais, rascunhos, favoritos e arquivos de mapas/anexos ficam em espaços locais próprios. A base estável é preservada; a importação automática e o botão de importar dados antigos foram retirados desta etapa. Backups Beta são identificados e a restauração recusa backups estáveis antes de gravar.
- Firebase Authentication continua sendo o existente, com o mesmo UID e matrícula/e-mail técnico. Senhas continuam no Firebase Authentication; não são persistidas pelo aplicativo. Uma aprovação de produção não concede aprovação no Beta. Contas existentes podem entrar e completar o cadastro Beta sem recriar a credencial. Qualquer uso real dessa identidade compartilhada dependerá da homologação de entrada/cadastro, recuperação de senha e corte de filas antes da distribuição.
- Operador, tratorista, ajudante e supervisor solicitam acesso pendente. Somente Diego aprova supervisores. Supervisores aprovados gerenciam os turnos da própria frente e aprovam os demais perfis dessa frente; não aprovam outro supervisor nem alcançam outra frente.
- Operadores mantêm criação/edição dos próprios registros de pendências/ocorrências. Não concluem, reabrem, excluem ou restauram pendências e não cadastram equipamentos. Somente gestor autorizado conclui após confirmação; o registro recebe data e identificação de quem concluiu. Cancelar a confirmação mantém os dados.
- A sincronização preserva o formato local existente, mas transmite o conteúdo dos itens como campos Firestore tipados (`formato: 2`, `dados`). As regras conseguem verificar a conclusão no servidor; um JSON opaco ou payload duplo é recusado. Comparação canônica de conteúdo evita reenvio causado apenas pela ordem das propriedades ou por `1` versus `1.0`.
- Regras adicionais candidatas em `firebase/firestore.beta.rules`, acompanhadas de índices em `firebase/firestore.beta.indexes.json`. As regras estáveis, `firebase.json` e o Worker estável não foram alterados. Exclusão continua sendo arquivamento recuperável, sem remoção física do histórico.
- Chamadas do Beta ao serviço de notificações e à correção/exclusão global de credenciais ficam bloqueadas antes da rede. Não se reaproveita o Worker de produção, não se apaga token estável no logout e a tela informa que os avisos Beta ainda dependem de preparação. Isso é uma proteção temporária de desenvolvimento, **não uma entrega de notificações Beta**.

## Validação e limites

A validação local cobre compilação, testes Android/Robolectric, lint, regras em emuladores do projeto fictício `demo-dm-agro`, Worker e publicador. A suíte das regras estáveis também é executada com o arquivo composto Beta para demonstrar que ele mantém os comportamentos anteriores fora do novo namespace. Os números finais e a execução no Actions ficam registrados nas evidências desta etapa.

Código validado: `04451b8aa556edff85f9f81ba6d3d2864ee1e931`. [Execução no GitHub Actions](https://github.com/Diegomachadosl10/colheita-gps-oficial/actions/runs/45802356211) e [evidências verificáveis](evidencias-etapa-1.json).

| Verificação local | Resultado |
| --- | --- |
| Compilação Android | `assembleDebug` aprovado |
| Testes Android/Robolectric | 293 aprovados; zero falhas/erros/ignorados |
| Regras Firestore/Storage | 28 estáveis + 28 de compatibilidade + 10 Beta; todos aprovados |
| Worker estável | 18 aprovados |
| Publicador | 7 aprovados |
| Lint | Zero erros; 128 avisos já existentes na base auditada |
| Identidade do APK local | `com.colheitagps.app`, código 39, mesmo certificado SHA-256 fixado no repositório |

O APK local foi usado somente para conferir compilação/identidade e não foi disponibilizado como atualização. A assinatura do runner no Actions é descartável; o workflow guarda somente relatórios, sem APK nem chave. O segredo da chave permanente no fluxo de distribuição ainda precisa ser recuperado antes de liberar qualquer atualização.

Os testes verificam tentativas diretas de autoaprovação, conclusão/exclusão por operador, troca de autoria/revisão, acesso de outra frente, uso de perfis estáveis como autorização Beta, formato opaco e payload ambíguo. Verificam também arquivos/preferências estáveis preservados, URIs de compartilhamento legíveis e componentes existentes de mapas, GPS, estoque, manutenção, chuva, peças e caminhões.

Robolectric e emuladores não substituem homologação no Android físico. Não houve teste com a matrícula ou senha de colegas, uso de dados/mapas de terceiros, teste real de WhatsApp, instalação sobre o aplicativo de Diego nem mudança em Firebase/Cloudflare reais. A aprovação no teste de regras não significa que o servidor atual aceita o Beta: o namespace permanece negado até uma implantação autorizada. O namespace separa os registros da produção, mas não é um projeto Firebase independente; cotas, identidade e infraestrutura continuam compartilhadas.

## Pendências antes de disponibilizar o Beta

1. Preparar e revisar a configuração de servidor específica, confrontando a revisão real das regras com a base auditada. A implantação do arquivo composto só poderá ser feita após autorização; não substituir regras reais que tenham evoluído desde a auditoria. Manter os clientes estáveis funcionando.
2. Preparar serviço de notificações Beta, autorização e filas separadas, testes de entrega/repetição e teste no celular. Não habilitar a flag no Android enquanto o serviço estiver apontando para a produção. Preparar também o tratamento de trabalhos antigos do WorkManager antes de qualquer instalação real.
3. Homologar aprovação, cadastro com a identidade existente, revogação e uso offline após reinício. A política de autorização offline persistente e o catálogo editável de frentes/turnos ainda não foram implementados; os turnos A/B/C e as nove frentes atuais permanecem.
4. Continuar o plano: estoque com observações/texto livre, PDF como opção separada, relatórios de turno, indicadores com cálculos conferidos, revisão visual de todas as telas e feedback. Esses itens **ainda não estão concluídos nesta etapa**. As funções existentes continuam na base, sem reintroduzir o comando de voz contínuo que foi retirado.
5. Recuperar a assinatura no CI usando a chave permanente existente, criar canal Beta opt-in separado do `latest`, conferir atualização por cima/backup e testar no Android. O workflow desta branch publica somente relatórios de teste; nenhum APK.

Os colegas continuam na versão estável enquanto isso. Publicar atualização, implantar regras, importar registros reais ou gerar custos exige a autorização específica já definida por Diego. Cadastro de outras empresas, assinaturas e APIs pagas continuam fora desta fase.
