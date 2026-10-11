-- Comissão de produto "0%" congelada num atendimento/venda passa a valer como "não
-- configurada" (NULL): esses registros usam o % atual do salão. Antes, um atendimento ou venda
-- feito enquanto a comissão estava zerada nunca passava a contar, mesmo depois de configurar o %.
UPDATE tb_appointment
SET snapshot_product_commission_percent = NULL
WHERE snapshot_product_commission_percent = 0;

UPDATE tb_cashflow
SET commission_percent = NULL
WHERE commission_percent = 0;

-- Comissão gravada como zero por falta de % na época (não digitada à mão) = comissão vazia.
UPDATE tb_cashflow
SET commission_amount = NULL
WHERE commission_manual = FALSE
  AND commission_amount = 0
  AND commission_percent IS NULL;

-- Vendas avulsas com vendedora, comissão não digitada à mão e ainda sem %: grava o % atual e o
-- valor da comissão (o sistema já calcularia assim; gravar deixa a tela do caixa coerente).
UPDATE tb_cashflow c
SET commission_percent = s.pct,
    commission_amount = ROUND(c.amount * s.pct / 100, 2)
FROM (SELECT product_commission_percent AS pct FROM tb_salon_business_settings ORDER BY id LIMIT 1) s
WHERE c.employee_id IS NOT NULL
  AND c.appointment_id IS NULL
  AND c.commission_manual = FALSE
  AND c.commission_percent IS NULL
  AND c.commission_amount IS NULL
  AND s.pct IS NOT NULL
  AND s.pct > 0;
