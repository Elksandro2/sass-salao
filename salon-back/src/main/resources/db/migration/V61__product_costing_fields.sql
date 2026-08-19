-- Custeio de produtos: marca, quanto o salão pagou, e capacidade/unidade da embalagem —
-- base pra calcular custo por uso (ver tb_salon_service_product_usage) e alimentar os
-- relatórios de lucro por serviço.
ALTER TABLE tb_product
    ADD COLUMN brand VARCHAR(100),
    ADD COLUMN cost_price DECIMAL(10, 2),
    ADD COLUMN capacity DECIMAL(10, 2),
    ADD COLUMN unit VARCHAR(10);
