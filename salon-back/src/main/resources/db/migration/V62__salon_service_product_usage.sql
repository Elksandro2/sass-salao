-- Receita de um serviço: quanto de cada produto ele consome por execução. Alimenta o custo
-- estimado do serviço nos relatórios financeiros.

CREATE TABLE tb_salon_service_product_usage (
    id BIGSERIAL PRIMARY KEY,
    salon_service_id BIGINT NOT NULL REFERENCES tb_salon_service(id) ON DELETE CASCADE,
    product_id BIGINT NOT NULL REFERENCES tb_product(id),
    quantity_used DECIMAL(10, 2) NOT NULL
);
