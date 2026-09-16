-- Corrige o efeito colateral do bug em cancel()/delete() (já corrigido no código): agendamento
-- que passou por DONE (faturou no Caixa) e depois teve o status corrigido e foi cancelado ficava
-- com o lançamento financeiro órfão, inflando o faturamento dos relatórios com receita que nunca
-- foi de fato recebida. Só remove lançamento de agendamento CANCELLED cujo pagamento nunca
-- chegou a ser PAID de verdade — dinheiro genuinamente recebido (PAID) nunca é tocado.
DELETE FROM tb_cashflow
USING tb_appointment
WHERE tb_cashflow.appointment_id = tb_appointment.id
  AND tb_appointment.status = 'CANCELLED'
  AND tb_appointment.payment_status IS DISTINCT FROM 'PAID';
