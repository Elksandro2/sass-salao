# Processo de trabalho

Como funciona o ciclo "a IA implementa, o dev guia" neste projeto.

## Ciclo de uma mudança

1. **Entender**: ler o pedido; se o domínio for novo, `GLOSSARY.md`; ler os 2–3 devlogs mais recentes.
2. **Explorar**: `PROJECT_MAP.md`, depois Grep/Read. Para impacto amplo, o grafo (`graphify`, opcional, ver abaixo).
3. **Reuso**: consultar `COMPONENTS.md`.
4. **Propor** (curto) em mudança não trivial ou ambígua; só perguntar o que o código não responde.
5. **Implementar**: bug ⇒ teste primeiro; endpoint novo ⇒ permissão; schema ⇒ migration nova.
6. **Verificar**: compilar back, tipar front, rodar só os testes tocados.
7. **Commitar em `dev`** (Conventional Commits, pt-BR). Corpo explica o porquê.
8. **Integrar** quando o usuário pedir: push `dev`, fast-forward `main`, push `main`. O usuário acompanha o CI.
9. **Devlog** do dia se a sessão mexeu em código.

## Checklist pré-integração

- [ ] `git pull` feito (outras pessoas mudam o repositório; migrations podem colidir de número).
- [ ] Back compila; front passa em `npx tsc -b`.
- [ ] Testes das classes/arquivos tocados passam (sem rodar suíte inteira por padrão).
- [ ] Endpoint novo ⇒ migration de permissão (cargos conferidos).
- [ ] Migration nova ⇒ número livre, idempotente, não toca dados de agendamento existentes.
- [ ] Campos de dinheiro usam `CurrencyInput`.
- [ ] Componente/helper novo reutilizável ⇒ `COMPONENTS.md` atualizado.
- [ ] Termo de domínio novo ⇒ `GLOSSARY.md`.
- [ ] Decisão estrutural ⇒ ADR.
- [ ] Sem segredo, sem `console.log`/`System.out` esquecidos.

## Bug de produção

1. Ler o log real (`docker logs` do backend), não só o que a UI mostrou.
2. Teste que falha. 3. Correção. 4. Teste passa. 5. Dado de produção inconsistente: oferecer correção segura (migration idempotente ou psql guiado, com query de conferência).

## Release

Sem CHANGELOG formal hoje. Cada commit `feat`/`fix` descreve a mudança para a dona do salão no corpo quando relevante. Se virar SaaS com mais clientes, adotar `CHANGELOG.md` (tipos: adicionado, alterado, corrigido, removido, segurança) e tags semânticas.

## Decisões e memória

- **ADR** (`.aidev/adr/`): decisão versionada e revisável (ex.: multi-tenant, política de exclusão).
- **Devlog** (`.aidev/devlog/`): estado e intenção do dia, handoff entre sessões/pessoas.
- **Memória do Claude** (`memory-templates/`): correções de comportamento do agente e contexto vivo. Não substitui ADR nem documentação.

## Regra de ouro: documentação vira código quando possível

Regra que depende de a IA "lembrar" é frágil. Quando aparecer 3 vezes em feedback, vira hook, script, teste ou agente (como o hook de devlog e o `schema-reviewer`).

## Grafo de código (opcional)

O [graphify](https://github.com/safishamsi/graphify) monta um grafo do código por AST (sem LLM) e responde "quem chama X / o que depende de Y" mais rápido que grep. Uso nos outros projetos: `uv tool install --with tree-sitter-sql graphifyy`, depois `graphify .` e `graphify query "..."`. `graphify-out/` fica no `.gitignore`. Adotar se o código crescer a ponto de a busca textual não bastar.
