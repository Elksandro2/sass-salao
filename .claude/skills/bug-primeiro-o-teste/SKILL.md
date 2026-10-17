---
name: bug-primeiro-o-teste
description: Fluxo de correção de bug de produção do salão: reproduzir com teste que falha, corrigir, ver passar, e checar causas comuns (permissão, varchar, data efetiva, lançamento órfão). Use ao receber relato de erro de produção ou log de exceção.
---

# Bug: primeiro o teste

1. **Entenda a causa real nos logs**, não só a mensagem da UI (`docker logs` do backend nas últimas 24h). Já houve diagnóstico errado por olhar só a tela.
2. Checklist de suspeitos recorrentes:
   - 403: permissão faltando para o cargo (skill `nova-permissao`).
   - "Integridade no banco": `varchar(255)`, FK, unique, NOT NULL.
   - Filtro de data: usar data de criação em vez da data agendada (cadeia `scheduledAt > scheduledDate > preferredDate > createdAt`).
   - Lançamento de caixa órfão (agendamento cancelado/revertido com `CashFlow` vinculado).
   - Valor negativo/zero chegando ao service (deve virar `BusinessException` com mensagem clara, não 500).
3. Escreva o teste que reproduz (service com Mockito ou integração; no front, Vitest + RTL). **Confirme que falha pelo motivo certo.**
4. Corrija o mínimo necessário.
5. Confirme que o teste passa e que os testes vizinhos da classe continuam passando.
6. Se dados de produção ficaram inconsistentes, ofereça correção (migration só se for seguro e idempotente; senão psql guiado passo a passo).
7. Commit `fix(<área>): ...` descrevendo causa e correção.
