-- Gestão de folha de pagamento das colaboradoras (tela "Folha de Pagamento").
--
-- 1) Dias trabalhados de diarista, um registro por dia. Substitui a contagem automática +
--    ajuste por período (tb_diarista_worked_days_override, V73), que não dizia QUAIS dias foram
--    pagos nem permitia uma diária com valor diferente. A tabela antiga fica no banco só como
--    histórico — não é mais lida pelo sistema.
CREATE TABLE tb_employee_work_day (
    id BIGSERIAL PRIMARY KEY,
    employee_id BIGINT NOT NULL REFERENCES tb_employee(id) ON DELETE CASCADE,
    work_date DATE NOT NULL,
    amount NUMERIC(10, 2) NOT NULL,
    justification VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_employee_work_day UNIQUE (employee_id, work_date),
    CONSTRAINT ck_employee_work_day_amount CHECK (amount >= 0)
);

-- 2) Lançamentos avulsos na folha: acréscimos (bonificação, 13º, férias, hora extra...) e
--    abatimentos (vale/adiantamento, desconto). O valor é sempre positivo — o tipo diz o sinal.
CREATE TABLE tb_payroll_adjustment (
    id BIGSERIAL PRIMARY KEY,
    employee_id BIGINT NOT NULL REFERENCES tb_employee(id) ON DELETE CASCADE,
    adjustment_date DATE NOT NULL,
    type VARCHAR(30) NOT NULL,
    amount NUMERIC(10, 2) NOT NULL,
    description VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_payroll_adjustment_amount CHECK (amount > 0)
);
CREATE INDEX idx_payroll_adjustment_employee_date ON tb_payroll_adjustment (employee_id, adjustment_date);

-- 3) Registro de "a colaboradora já foi paga por este período". calculated_amount congela o
--    valor que o sistema calculou no momento do registro — se algo do período for editado
--    depois (ex.: um agendamento corrigido), a tela compara e avisa a diferença.
CREATE TABLE tb_payroll_payment (
    id BIGSERIAL PRIMARY KEY,
    employee_id BIGINT NOT NULL REFERENCES tb_employee(id) ON DELETE CASCADE,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    calculated_amount NUMERIC(12, 2) NOT NULL,
    amount_paid NUMERIC(12, 2) NOT NULL,
    paid_at DATE NOT NULL,
    payment_method VARCHAR(20),
    notes VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_payroll_payment_period CHECK (period_start <= period_end),
    CONSTRAINT ck_payroll_payment_amount CHECK (amount_paid >= 0)
);
CREATE INDEX idx_payroll_payment_employee_period ON tb_payroll_payment (employee_id, period_start, period_end);

-- 4) Valores manuais do "lucro deste atendimento": quando preenchidos, substituem o cálculo
--    automático só deste agendamento. As comissões manuais também valem para a folha da
--    profissional (é o que ela de fato recebe por aquele atendimento).
ALTER TABLE tb_appointment
    ADD COLUMN override_recipe_cost NUMERIC(10, 2),
    ADD COLUMN override_products_cost NUMERIC(10, 2),
    ADD COLUMN override_service_commission NUMERIC(10, 2),
    ADD COLUMN override_product_commission NUMERIC(10, 2),
    ADD COLUMN profit_override_note VARCHAR(500);
