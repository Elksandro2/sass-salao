-- Produtos vendidos dentro de um agendamento (mesmo padrão de tb_appointment_service_item).

CREATE TABLE tb_appointment_product_item (
    id BIGSERIAL PRIMARY KEY,
    appointment_id BIGINT NOT NULL REFERENCES tb_appointment(id) ON DELETE CASCADE,
    product_id BIGINT NOT NULL REFERENCES tb_product(id),
    quantity INTEGER NOT NULL DEFAULT 1,
    custom_price DECIMAL(10, 2)
);
