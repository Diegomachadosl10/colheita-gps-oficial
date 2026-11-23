# DM AGRO 2.2.4 — mensagem das pendências no WhatsApp

O envio por mensagem de texto agora usa negrito compatível com WhatsApp no título, na equipe, nos equipamentos, na numeração e nos rótulos dos campos. A descrição aparece com inicial maiúscula, sem alterar o registro salvo. O resumo informa quantas pendências e equipamentos estão selecionados e destaca a quantidade de urgentes quando houver.

A prioridade urgente recebe uma indicação própria no item. A ordem dos equipamentos e a prioridade dentro de cada equipamento continuam usando a classificação existente. O.S., data do registro, peças e marcação para parada de chuva permanecem na mensagem quando disponíveis. Os códigos e seus zeros iniciais são preservados.

Listas compostas somente de pendências abertas indicam esse estado no título, sem repetir Situação: Em aberto em cada item. Listas que incluem histórico mantêm a situação individual, incluindo conclusão e exclusão e suas datas. A data de envio é identificada como tal; não é apresentada como confirmação de sincronização ou de atualização do registro.

O formato vale para mensagem com ou sem fotos. As fotos continuam opcionais e os itens sem foto continuam presentes no texto. O envio por imagem mantém os cartões existentes, com descrição e fotos na mesma página. Não foram adicionados botões, permissões, serviços, PDF ou comandos de voz.

A alteração é de apresentação: não modifica registros, anexos, permissões ou sincronização. A prévia e o botão Enviar continuam sendo usados antes do seletor de aplicativos. O formato em negrito é interpretado pelo WhatsApp; outros destinatários podem exibir os asteriscos literalmente.

Instale sobre a versão atual, sem desinstalar nem limpar dados.

Validação: 38 testes relevantes aprovados nas APIs 28 e 35, incluindo o modelo enviado pelo usuário (com identificadores fictícios nos testes públicos), listas com vários equipamentos, histórico misto, códigos com zeros iniciais, dados privados omitidos, texto com/sem fotos, envio por imagem e a permanência da remoção da voz.

Os 2 casos finais com identificadores fictícios também foram conferidos antes da publicação. Os 7 testes dos scripts de publicação passaram.
