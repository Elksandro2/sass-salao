# Portões de qualidade

Estado atual e metas. O CI está em `.github/workflows/ci.yml` (ver `docs/CI_CD.md`); aqui só o que se acrescenta.

| Função | Ferramenta | Situação |
|---|---|---|
| Testes back | JUnit + Mockito + Testcontainers | existente |
| Testes front | Vitest + RTL | existente (cobertura em `cobertura/`) |
| Tipos front | `npx tsc -b` | existente |
| Segredos | **gitleaks** (pre-commit) | novo (`.pre-commit-config.yaml`) |
| Dependências back | OWASP dependency-check ou Trivy fs | sugerido |
| Dependências front | `npm audit --omit=dev` | sugerido |
| Complexidade | revisar hotspots (arquivos grandes + muito alterados) após feature grande | manual |
| Migrations | agente `schema-reviewer` | manual, antes de integrar |
| Testes bons | agente `test-quality-reviewer` | manual, antes de integrar |

## Hotspots

Após feature grande, cruzar tamanho × churn:

```bash
git log --since=3.months --name-only --pretty=format: | sort | uniq -c | sort -rn | head -15
```

Arquivo que é grande **e** muito alterado (ex.: `AppointmentService`, `Folha`, `AppointmentDetailModal`) entra na fila de refatoração antes da próxima feature nele.

## Cobertura

Não imponha número global agora; exija teste em: regra financeira (comissão, folha, caixa), permissões, guardas de exclusão e correção de bug.
