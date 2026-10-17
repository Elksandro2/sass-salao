---
name: nova-permissao
description: Cria a migration Flyway que cadastra a permissão de um endpoint novo ou alterado da API do salão e concede aos cargos certos. Use ao criar/mudar caminho ou método de endpoint, ou quando um cargo (ex.: GERENTE) recebe 403 num endpoint que deveria acessar.
---

# Nova permissão de endpoint

Autorização é dado: linhas em `tb_permission` (endpoint, método) ligadas a cargos em `tb_role_permissions`. ADMIN/SYSADMIN têm bypass, então **teste só com admin não revela permissão faltando**.

## Passos

1. Confirme caminho completo (prefixo `/v1`) e método no controller. Variável de caminho usa padrão Ant (`/v1/appointments/*`).
2. Veja como permissões parecidas foram cadastradas: `grep -rn "<trecho>" salon-back/src/main/resources/db/migration`. Copie o padrão e as colunas reais da tabela (confira o schema nas migrations V2/V8 antes de escrever).
3. Descubra os cargos. Se a tarefa não diz, pergunte antes.
4. Próximo número: `ls salon-back/src/main/resources/db/migration | sort -V | tail -3` (após `git pull`).
5. SQL **idempotente** (`WHERE NOT EXISTS`) para a permissão e para o vínculo com cada cargo.
6. Nunca edite migration já aplicada em produção.
7. No front, o item de menu/botão usa a mesma string `METHOD:/v1/...` em `PermissionGate`/`usePermission`.
8. Rode o teste do controller afetado (`./mvnw -q test -Dtest=<Controller>Test` em `salon-back`; sobe Postgres via Testcontainers e aplica as migrations).
9. Ao concluir, diga ao usuário quais cargos ganharam acesso.
