---
name: nova-migration
description: Cria uma migration Flyway segura para o banco do salão (schema ou dados). Use ao adicionar/alterar coluna, tabela, índice, constraint ou ao corrigir dados de produção por SQL versionado.
---

# Nova migration

1. `git pull` e descubra o próximo número: `ls salon-back/src/main/resources/db/migration | sort -V | tail -3`. Arquivo: `V<n>__descricao_curta.sql`.
2. **Nunca** edite migration já aplicada. **Nunca** altere/backfill dados de agendamento existentes.
3. Coluna NOT NULL em tabela com dados: padrão em 3 passos (adicionar nula, preencher, impor NOT NULL), ou `DEFAULT` constante.
4. Idempotência quando possível: `IF NOT EXISTS`, `WHERE NOT EXISTS`.
5. FK sempre com índice; ação `ON DELETE` explícita; timestamps `TIMESTAMPTZ` quando o resto da tabela usar.
6. Valor longo: lembre do limite `varchar(255)` (já causou erro em produção); use `TEXT` ou limite no service.
7. Atualize a entidade JPA e DTOs no mesmo commit; confira `ddl-auto` não mascara diferença (schema é do Flyway).
8. Rode o agente `schema-reviewer` e um teste de integração que suba o contexto (Testcontainers aplica todas as migrations).
9. Se mexe em dados de produção, explique ao usuário o efeito esperado e como conferir depois (query de verificação).
