-- Congela os valores financeiros de cada agendamento no momento em que os itens são
-- criados/editados, para que alterações posteriores no cadastro (preço do serviço/produto,
-- % de comissão de serviço, % de comissão de produto do salão, custo da receita) não mudem
-- o resultado de atendimentos já realizados.
--
-- Todas as colunas são anuláveis: linha sem snapshot cai no valor atual (comportamento antigo).

ALTER TABLE tb_appointment_service_item
    ADD COLUMN snapshot_price NUMERIC(10, 2),
    ADD COLUMN snapshot_commission_percent NUMERIC(5, 2),
    ADD COLUMN snapshot_recipe_cost NUMERIC(12, 2);

ALTER TABLE tb_appointment_product_item
    ADD COLUMN snapshot_unit_price NUMERIC(10, 2),
    ADD COLUMN snapshot_cost_price NUMERIC(10, 2);

ALTER TABLE tb_appointment
    ADD COLUMN snapshot_product_commission_percent NUMERIC(5, 2);

-- Backfill best-effort: congela os agendamentos já existentes a partir dos valores atuais.
-- O custo de receita (snapshot_recipe_cost) fica NULL nas linhas antigas — é derivado da
-- receita do serviço em runtime e continua caindo no cálculo ao vivo para esses casos.

UPDATE tb_appointment_service_item i
SET snapshot_price = COALESCE(i.custom_price, s.price),
    snapshot_commission_percent = s.commission_percent
FROM tb_salon_service s
WHERE i.salon_service_id = s.id;

UPDATE tb_appointment_product_item i
SET snapshot_unit_price = COALESCE(i.custom_price, p.price),
    snapshot_cost_price = p.cost_price
FROM tb_product p
WHERE i.product_id = p.id;

UPDATE tb_appointment
SET snapshot_product_commission_percent =
    (SELECT product_commission_percent FROM tb_salon_business_settings ORDER BY id LIMIT 1);
