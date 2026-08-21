-- Reforma do modelo de comissão: sai o escopo Global (não dava pra atribuir com exatidão a
-- um atendimento/serviço específico) e a comissão vira propriedade de CADA SERVIÇO (não mais
-- um % fixo por funcionária) — qualquer funcionária Comissionada/Fixo+Comissionada que realizar
-- o serviço recebe o % configurado nele. Comissão de produto vira uma única % do salão inteiro,
-- vale pra qualquer funcionária que vender qualquer produto (inclusive Salário Fixo, como
-- incentivo de venda).

ALTER TABLE tb_salon_service ADD COLUMN commission_percent NUMERIC(5, 2);

CREATE TABLE tb_salon_business_settings (
    id BIGSERIAL PRIMARY KEY,
    product_commission_percent NUMERIC(5, 2),
    updated_at TIMESTAMP
);
INSERT INTO tb_salon_business_settings (product_commission_percent, updated_at) VALUES (NULL, NOW());

ALTER TABLE tb_employee DROP COLUMN commission_scope;
ALTER TABLE tb_employee DROP COLUMN commission_value;
ALTER TABLE tb_employee DROP COLUMN product_commission_value;
