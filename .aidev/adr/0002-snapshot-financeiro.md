# ADR 0002 — Valores financeiros congelados (snapshot)

- **Status:** accepted
- **Date:** 2026-10-16
- **Deciders:** José Elksandro

## Context

Preço de serviço/produto, comissão e custo de receita mudam com o tempo. Relatórios e folha de períodos passados não podem mudar quando o catálogo muda.

## Decision

No momento da venda/conclusão, o agendamento (e o item de caixa) guarda o valor aplicado (preço, preço customizado por item, comissão, custo). Relatórios leem o snapshot. Preço pode ser editado por item no momento do lançamento (ex.: produto de 100 vendido por 85).

## Consequences

- Positivo: histórico estável e auditável; permite negociar preço na venda.
- Negativo: campos duplicados e cuidado ao editar agendamento concluído (recalcular só o que é editado).

## References

- `CashFlowService` (`customPrice`), `AppointmentService`, relatórios em `reports`
