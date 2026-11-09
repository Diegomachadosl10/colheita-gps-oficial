# DM AGRO — Colheita GPS

Projeto Android. Correções de acesso na ramificação **dm-agro-login**, sem substituir a `main`.

## Baixar ou gerar o APK pelo celular

1. Abra [Actions → Gerar APK Colheita GPS](https://github.com/Diegomachadosl10/colheita-gps-oficial/actions/workflows/android.yml) no navegador do Android e entre no GitHub.
2. Para gerar outra versão, toque em **Run workflow**, escolha **dm-agro-login** e confirme **Run workflow**. Se o botão não aparecer, ative **Site para computador** no menu do navegador. Cada envio a essa ramificação também inicia a compilação.
3. Abra a execução mais recente dessa ramificação e espere o resultado verde. Em **Artifacts**, baixe **DM-AGRO-APK**.
4. Extraia o ZIP pelo aplicativo Arquivos e toque em **app-debug.apk**. Se necessário, permita a instalação por esse aplicativo.

O workflow executa `gradle testDebugUnitTest assembleDebug --no-daemon`, com Java 17, Gradle 8.9 e Android SDK. Os resultados dos testes ficam em **DM-AGRO-Testes**.

**Preservação de dados:** não limpe os dados e não desinstale o aplicativo para atualizar: mapas e registros de campo ficam armazenados localmente. O workflow passa a guardar a chave de assinatura de teste em cache para reutilizá-la nos próximos builds da mesma ramificação. Os APKs anteriores usavam chaves temporárias; se o Android recusar a atualização por diferença de assinatura, preserve o aplicativo instalado e exporte a cópia de segurança pelo administrador antes de planejar uma reinstalação. Não é possível recuperar a chave privada de um APK antigo. A assinatura de produção deve usar um keystore permanente antes de distribuição definitiva.

## Como funciona o acesso

- Cadastro e entrada usam a mesma matrícula, com zeros à esquerda preservados. Operadores e supervisores **não precisam de e-mail pessoal**.
- Os campos de senha do login, cadastro e confirmação possuem um botão de olho para mostrar ou ocultar o texto. A senha começa oculta; alternar a visualização preserva o texto digitado e a posição do cursor.
- O Firebase Authentication usa internamente `matricula.<matricula>@acesso.dm-agro.app`; esse endereço é um identificador técnico e não uma caixa de e-mail. O administrador mantém o acesso por seu e-mail já cadastrado.
- A solicitação fica em `usuarios/<UID do Firebase Authentication>`, com `status: pendente`, `papel: operador`, `perfil: operador` e `solicitacaoPerfil: operador` ou `supervisor`. Pedir o perfil de supervisor não concede permissão.
- Login e aplicativo usam a mesma validação: `status: aprovado` e papel conhecido. `papel` é a fonte principal; `perfil` atende contas antigas apenas quando `papel` está ausente ou vazio. Espaços, maiúsculas e acentos são normalizados. Papéis desconhecidos não são liberados.
- A aprovação é consultada no **servidor**, sem liberar acesso a partir de uma cópia antiga do cache. A tela de espera acompanha alterações quando conectada; também permite verificar manualmente.
- O painel e as alterações de usuários exigem `papel: administrador` e `status: aprovado`. Supervisores mantêm as funções de gestão de campo existentes, mas não recebem acesso às aprovações. Nenhuma tela cria administradores, altera a conta atual ou modifica contas com papel de administrador.
- A transação de aprovação relê as permissões de quem aprova e o cadastro de destino, e atualiza juntos `papel`, `perfil` e `status`.
- O botão verde **Sair da conta**, visível no topo da tela principal para todos os perfis, encerra a sessão e abre um formulário de login vazio. A opção do menu e a confirmação de saída pela tecla Voltar executam a mesma ação. A pilha de atividades é limpa; abrir novamente o aplicativo exige matrícula (ou e-mail do administrador) e senha, sem apagar registros locais.
- Se a conta Authentication for criada, mas a gravação da solicitação falhar, a mesma matrícula e senha permitem entrar e concluir a solicitação. Uma transação cria somente documentos ausentes, sem sobrescrever aprovações existentes.

## Teste em dois celulares

1. No segundo aparelho, solicite acesso como operador, sem e-mail pessoal. Anote matrícula e senha. Deve aparecer **Aguardando aprovação**.
2. No primeiro, entre como o administrador existente e aprove a solicitação após conferir a identidade.
3. Com o segundo aparelho conectado, confirme que a aprovação abre o aplicativo; se necessário, toque em **Verificar novamente**.
4. Saia pelo menu e entre outra vez com a mesma matrícula e senha. Não é necessário limpar os dados.
5. Teste senha incorreta, falta de conexão, bloqueio e **Voltar ao login**. A tela de login deve permanecer disponível sem ciclo.
6. Repita com um supervisor. Antes da aprovação ele deve aguardar; depois, deve acessar a gestão de campo sem ver **APROVAR USUÁRIOS**.
7. No administrador/supervisor, confira mapas georreferenciados, GPS, talhões, O.S., estoque, pendências, fotos, ocorrências, relatórios e compartilhamento. No operador, confira as funcionalidades já permitidas pelo perfil. As implementações de campo e suas restrições anteriores foram preservadas.

## Conta de teste 80911 e limites da verificação

Não houve acesso aos usuários, senhas ou regras do Firebase em produção. O fato de o Firestore mostrar `aprovado` não comprova que exista uma credencial válida no Authentication: são serviços distintos. A aprovação não cria nem redefine senhas.

Para uma conta já existente, o administrador pode comparar, no console Firebase, o usuário Authentication `matricula.80911@acesso.dm-agro.app` com o documento `usuarios/<mesmo UID>`. Se a credencial não existir, usar outro endereço legado ou tiver senha diferente, o APK sozinho não consegue reparar esses dados. Cadastros antigos criados com e-mail pessoal continuam podendo entrar com o endereço original; não há busca pública de usuários por matrícula. Uma migração desses endereços deve ocorrer em ambiente administrativo confiável, preservando o UID. Não foram inventados domínios alternativos nem criadas contas duplicadas.

A recuperação automática de senha de matrícula, sem e-mail, exige um serviço administrativo confiável com Firebase Admin SDK e autenticação do administrador principal. Não se deve colocar credenciais de serviço no APK nem enviar recuperação ao endereço interno. Este patch informa o caminho de suporte e preserva a recuperação por e-mail do administrador.

## Regras do Firebase

**Nenhuma regra foi alterada ou publicada.** O repositório não contém as regras atualmente implantadas, e esta sessão não possui credenciais administrativas do Firebase.

As restrições no aplicativo não substituem a autorização do servidor. Para garantir que um supervisor não aprove usuários por outro cliente, as regras implantadas precisam impedir que ele altere `status`, `papel`, `perfil` ou outros campos de autorização; a checagem de administrador deve usar a identidade confiável do administrador principal (UID fixo ou uma atribuição equivalente protegida), nunca um campo que o próprio usuário possa editar. O cadastro próprio deve permitir apenas uma solicitação pendente com papel inicial de operador, e a leitura própria precisa funcionar antes da aprovação. Não publique regras genéricas que possam expor mapas ou registros de campo.

Se as regras atuais ainda autorizarem supervisores a aprovar operadores, será necessária uma alteração pontual no servidor para remover essa autorização, preservando o restante das regras. Essa necessidade não pode ser verificada nem corrigida substituindo regras desconhecidas. O patch do aplicativo já remove essa possibilidade das telas e revalida o administrador em cada gravação.
