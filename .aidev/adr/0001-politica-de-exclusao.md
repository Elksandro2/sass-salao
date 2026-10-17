# ADR 0001 — Política de exclusão: desativar, status e exclusão definitiva

- **Status:** accepted
- **Date:** 2026-10-16
- **Deciders:** José Elksandro

## Context

Cadastros (produto, serviço) e agendamentos precisam sair de cena por motivos diferentes: descontinuado (reversível), cancelado (parte do ciclo de vida) ou lançado por engano (apagar de verdade). Excluir de verdade algo já usado quebra histórico financeiro.

## Decision

Três mecanismos, escolhidos por entidade:
1. **Desativar** (`active=false`): cadastros reversíveis; somem das listagens, preservam histórico.
2. **Status** (`CANCELLED`): agendamentos; cancelar também cancela o pagamento e remove o lançamento de caixa vinculado.
3. **Exclusão definitiva**: sempre com confirmação na UI e **guarda de uso** no backend (produto vendido ou em receita; serviço realizado; agendamento com lançamento financeiro). Violação vira `BusinessException` com mensagem clara.

Não criar um quarto mecanismo.

## Consequences

- Positivo: histórico financeiro íntegro; erro de digitação pode ser apagado.
- Negativo: guardas precisam ser mantidas quando uma entidade ganha novo vínculo.
- Neutro: pagamento "cancelado" nunca é escolhido manualmente.

## References

- `ProductService.permanentlyDelete`, `SalonServiceManager.permanentlyDelete`, `AppointmentService.delete/cancel`
