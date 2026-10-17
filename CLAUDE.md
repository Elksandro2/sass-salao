# CLAUDE.md

Instruções carregadas em toda sessão de IA neste repositório. Detalhes e justificativas ficam em `.aidev/` e `docs/`; aqui só o que o agente precisa lembrar sempre.

## Projeto

Sistema de gestão de salão (Cristiane): `salon-back` (Spring Boot, JPA, Flyway, PostgreSQL) e `salon-front` (React + TypeScript + Vite). Mapa de pacotes e telas em `PROJECT_MAP.md`; arquitetura em `docs/ARCHITECTURE.md`; testes em `docs/TESTING.md`.

Meta permanente: **o sistema mais correto e robusto possível, para gerar valor à dona do salão**. Na dúvida entre rápido e correto, escolha correto e diga o custo.

## Comunicação

- Responda em **português do Brasil**. Commits, PRs, issues e textos de interface também.
- Seja econômico em tokens: não rode a suíte inteira sem necessidade, rode só o teste da classe/arquivo afetado. Rode tudo quando o usuário pedir ou antes de integrar em `main` se algo transversal mudou.

## Regras duras

### 1. Dados de produção
- **Nunca altere ou faça backfill de dados de agendamento existentes por migration** (`scheduledAt` antigos ficam como estão). Correção pontual de produção é feita à mão (psql guiado), não em migration.
- Migration já aplicada em produção **nunca se edita**: crie a próxima (`V<n+1>__descricao.sql`). Confira o último número com `ls salon-back/src/main/resources/db/migration | sort -V | tail -3` (após `git pull`, outra pessoa pode ter criado).
- Migration destrutiva ou que mexe em dados existentes: SQL idempotente, e explique ao usuário o que ela faz em produção antes de integrar.

### 2. Permissões (a causa nº 1 de 403 em produção)
- Autorização vem do banco: `tb_permission` + `tb_role_permissions`. ADMIN/SYSADMIN passam por bypass, então **testar só como admin não revela permissão faltando**.
- Endpoint novo ou com caminho/método alterado = migration que cadastra a permissão e concede aos cargos certos (GERENTE etc.). Use a skill `nova-permissao`.
- Itens de menu/botões no front usam `PermissionGate`/`usePermission` com a mesma string `METHOD:/v1/...`.

### 3. Bug de produção: teste primeiro
Reproduza com um teste que falha, corrija, veja passar (skill `bug-primeiro-o-teste`). Sem exceção, exceto quando o usuário mandar explicitamente não testar; nesse caso diga que ficou sem teste.

### 4. Exclusão
Três jeitos, escolha por entidade: **desativar** (`active=false`, reversível, some das listagens), **status** (`CANCELLED`, máquina de estados do agendamento) e **exclusão definitiva** (só com confirmação na UI e *guarda de uso* no back: produto vendido/usado em receita, serviço realizado em agendamento, agendamento com lançamento financeiro). Não crie um quarto jeito.

### 5. Dinheiro e datas
- Todo campo monetário no front usa `CurrencyInput` (dígitos viram centavos, emite `"12.34"`). Não use `<input type=number>` para valor em R$.
- Valores financeiros usam o padrão **snapshot/congelamento** (preço no momento da venda/conclusão); mudar o catálogo não reescreve histórico.
- Agendamento: data efetiva = `scheduledAt` > `scheduledDate` > `preferredDate` > `createdAt`. Filtro "para a data" olha a data agendada, nunca a de criação.
- Cancelar agendamento cancela pagamento e remove o lançamento de caixa vinculado.

### 6. Reuso
Antes de criar componente/helper, consulte `.aidev/COMPONENTS.md`. Generalize o existente em vez de duplicar; catalogue o que for realmente novo e reutilizável no mesmo commit.

### 7. Dependências
Padrão é **não adicionar**. Se for preciso: versão estável mais recente conferida online, sem CVE alto, licença compatível (agente `dependency-evaluator`).

### 8. Segurança
Nunca logar token/senha. Fluxos de login não revelam se o e-mail existe. Nada de segredo no repositório (`.env` fora do git; `admin_credentials.txt` é local). Detalhes em `docs/SECURITY.md`.

### 9. Estilo de código
Funções pequenas e explícitas, nomes consistentes e pesquisáveis, comentário explica o *porquê*. Segue o estilo do arquivo vizinho. Detalhes em `.aidev/CODE_STYLE.md`.

## Fluxo de git

- Trabalho em `dev`; integração em `main` por fast-forward após push. O usuário confirma o CI sozinho: **não faça polling de CI**.
- Mensagens no padrão Conventional Commits em português: `feat(folha): ...`, `fix(agenda): ...`.
- Só faça `push`/merge em `main` quando o usuário pedir. Nunca `--force` em `main`.
- Antes de integrar: `git pull`, backend compila (`./mvnw -q -DskipTests compile` em `salon-back`), front tipa (`npx tsc -b` em `salon-front`) e os testes dos arquivos tocados passam.

## Sessões e continuidade

- **No início**: leia os 2–3 arquivos mais recentes de `.aidev/devlog/` para saber onde parou.
- **Ao encerrar** uma sessão que mexeu em código: escreva/atualize o devlog do dia (`.aidev/devlog/AAAA-MM-DD-<dev>.md`). Um hook (`Stop`) cobra isso uma vez por dia se houver commit sem devlog.
- Decisão de arquitetura que afeta o sistema todo vira ADR em `.aidev/adr/`.

## Índice

- `.aidev/PROCESS.md`: ciclo de trabalho, release, checklist pré-integração.
- `.aidev/COMPONENTS.md`: catálogo de componentes e helpers reutilizáveis.
- `.aidev/GLOSSARY.md`: vocabulário do negócio do salão.
- `.aidev/CODE_STYLE.md`, `.aidev/QUALITY.md`: estilo e portões de qualidade.
- `.aidev/adr/`, `.aidev/devlog/`, `.aidev/memory-templates/`.
- Agentes (`.claude/agents/`): `schema-reviewer`, `test-quality-reviewer`, `component-cataloger`, `dependency-evaluator`, `security-reviewer`.
- Skills (`.claude/skills/`): `nova-permissao`, `nova-migration`, `bug-primeiro-o-teste`.
