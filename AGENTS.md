# DM AGRO Professional — Beta

- Desenvolvimento nesta fase: branch `dm-agro-professional-beta`, baseada na versão estável 2.2.7. `dm-agro-login` é a linha de produção; `main` contém uma base antiga. Não trocar silenciosamente a base por `main`.
- O pedido atual exige apresentar a auditoria e o plano técnico antes de mudanças estruturais. Consulte `docs/beta/AUDITORIA-2026-11-26.md` e `docs/beta/PLANO-TECNICO.md` e respeite a etapa autorizada pelo usuário.
- Não publicar releases, mudar o manifesto estável de atualização, implantar regras/Worker ou migrar dados de produção sem autorização para essa ação. Criar uma branch Beta não autoriza publicar para todos.
- Preservar `applicationId` `com.colheitagps.app`, a assinatura identificada em `scripts/android-signing.sha256`, os IDs dos registros, mapas, pinturas, históricos e compatibilidade dos backups. Atualizar sem desinstalar nem limpar os dados.
- A chave privada nunca pertence ao Git, aos logs, aos relatórios, ao chat nem aos artefatos públicos. Não gerar uma chave substituta para contornar a falta do segredo de assinatura no GitHub.
- O workflow `beta-audit.yml` valida sem acessar segredos e sem distribuir APK. O APK intermediário de um runner não deve ser oferecido como atualização compatível.
- Firebase Spark e Cloudflare Workers Free. Não ativar faturamento, Storage pago, anúncios, compras ou APIs pagas. Emuladores e testes usam exclusivamente `demo-dm-agro`, usuários e registros fictícios.
- Não confundir regras testadas no repositório com regras implantadas. Não afirmar que multiempresa, IA, cobrança, publicação Play ou conformidade LGPD estão concluídos quando forem apenas planos.
- Preservar as funções atuais, inclusive chuva, peças, previsão, versículo, passagem de turno e preventiva. PDF, relatórios de turno e voz foram retirados da produção por pedidos anteriores; o novo pedido prevê reintrodução planejada no Beta, sem restaurar cegamente código antigo.
- Validar com testes Android, lint, testes do Worker, do publicador e regras em emuladores antes de distribuir mudanças. Manter commits pequenos e documentação compatível com o que foi efetivamente testado.
