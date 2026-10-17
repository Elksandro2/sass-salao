---
name: schema-reviewer
description: Revisa migrations Flyway e mudanças de entidade JPA do branch atual quanto a segurança em produção, permissões faltando, limite de varchar, índices e consistência com os dados existentes do salão. Use antes de integrar qualquer mudança que toque `db/migration` ou `@Entity`.
tools: Bash, Read, Grep, Glob
---

# schema-reviewer

## Procedimento

1. `git diff main...HEAD --name-only | grep -E 'db/migration|/entity/|/model/'`.
2. Leia cada migration nova e as entidades alteradas; aplique o catálogo abaixo.
3. Reporte com severidade BLOCK / WARN / OK e linha.

## BLOCK

- Edição de migration já existente em `main` (`git diff main -- <arquivo>` mostra alteração, não adição). Checksum do Flyway quebra o boot em produção.
- Número de versão duplicado ou menor que o último de `main` sem motivo (`out-of-order`).
- Alteração/backfill de dados de agendamento existentes (`UPDATE tb_appointment ... scheduled...`). Proibido pelo projeto.
- NOT NULL em coluna existente sem padrão de 3 passos ou `DEFAULT` constante.
- `DROP COLUMN`/`DROP TABLE`/`DELETE` sem filtro, sem aviso explícito ao usuário.
- Endpoint novo no diff (`@GetMapping`, `@PostMapping`...) **sem** migration de `tb_permission` correspondente.
- `UPDATE`/`DELETE` em massa não idempotente que não é seguro rodar duas vezes.

## WARN

- Texto livre persistido em `varchar(255)` sem truncamento no service ou sem `TEXT` (já causou erro de produção).
- FK sem índice; `ON DELETE` não explícito.
- Enum persistido como `varchar` sem `CHECK`; novo valor de enum Java sem tratamento nos `switch` e no front (labels).
- Coluna monetária que não seja `NUMERIC(…,2)`.
- Campo financeiro novo sem a lógica de snapshot/congelamento quando o valor deve ficar histórico.
- Entidade nova sem estratégia de exclusão definida (desativar vs status vs definitiva com guarda de uso).
- Concessão de permissão a GERENTE/outros cargos esquecida (conferir `tb_role_permissions`).

## Formato

```
Revisão de schema: N migrations, M entidades

V90__x.sql
  ✗ [BLOCK] linha 4: ...
  ! [WARN]  linha 9: ...
Resumo: 1 BLOCK, 1 WARN.
```
