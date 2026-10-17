# Glossário do negócio

| Termo | Significado no sistema |
|---|---|
| **Salão / Cristiane** | Cliente atual (dona do salão), único tenant hoje. |
| **Agendamento** (`Appointment`) | Atendimento marcado. Status: PENDING, REQUESTED, CONFIRMED, DECLINED, DONE, CANCELLED. |
| **Período** (`AppointmentPeriod`) | MORNING/AFTERNOON: agendamento por bloco, sem hora exata. |
| **Data efetiva** | `scheduledAt` > `scheduledDate` > `preferredDate` > `createdAt`. Filtros "para a data" usam isto. |
| **Status de pagamento** (`PaymentStatus`) | PENDING, PAID, FAILED, CANCELLED (CANCELLED só pelo cancelamento do agendamento, nunca manual). |
| **Fluxo de caixa** (`CashFlow`) | Entradas e saídas livres + lançamento automático de agendamento concluído. |
| **Gasto fixo** | Despesa recorrente (aluguel, luz) em tela própria; entra no lucro líquido separado do fluxo de caixa. |
| **Produto de venda** | Vendido ao cliente (tem preço de venda). |
| **Produto de uso** | Consumido em receita de serviço (sem preço de venda; pode ser os dois). |
| **Receita (de serviço)** | Lista de produtos de uso, quantidade e unidade consumidos por serviço. |
| **Snapshot / congelamento** | Valores guardados no momento da venda/conclusão; mudar o catálogo não altera o histórico. |
| **Comissão** | Percentual do profissional sobre serviço e/ou produto. |
| **Remuneração** (`RemunerationType`) | FIXO (salário mensal), DIARISTA, DIÁRIA+COMISSÃO, COMISSIONADO. |
| **Folha / Pagamento** | Cálculo do que se paga a cada profissional no período; "Marcar como pago" registra o pagamento realizado. |
| **Desativar** | `active=false`: reversível, some das listagens. |
| **Exclusão definitiva** | Remoção real, com confirmação e guarda de uso. |
| **Cargo / Permissão** | Role e par método+rota em `tb_permission`. ADMIN/SYSADMIN têm bypass. |
