-- Salário fixo pago por MÊS, não proporcional aos dias de cada pagamento.
--
-- Um pagamento da folha agora tem duas partes independentes:
--   * dias (period_start/period_end): comissões, diárias, bônus e descontos desses dias;
--   * salários: os meses cujo salário fixo foi quitado nesse pagamento (tabela abaixo).
-- Um pagamento pode ser só de salário (sem dias) — por isso o período passa a ser opcional.
ALTER TABLE tb_payroll_payment
    ALTER COLUMN period_start DROP NOT NULL,
    ALTER COLUMN period_end DROP NOT NULL;

ALTER TABLE tb_payroll_payment
    ADD CONSTRAINT ck_payroll_payment_period_pair
        CHECK ((period_start IS NULL) = (period_end IS NULL));

-- Salário de um mês só pode ser quitado uma vez por colaboradora (UNIQUE garante mesmo com
-- dois cliques ao mesmo tempo). salary_month = dia 1 do mês de referência.
CREATE TABLE tb_payroll_salary_payment (
    id BIGSERIAL PRIMARY KEY,
    payment_id BIGINT NOT NULL REFERENCES tb_payroll_payment(id) ON DELETE CASCADE,
    employee_id BIGINT NOT NULL REFERENCES tb_employee(id) ON DELETE CASCADE,
    salary_month DATE NOT NULL,
    amount NUMERIC(12, 2) NOT NULL,
    CONSTRAINT uq_payroll_salary_month UNIQUE (employee_id, salary_month),
    CONSTRAINT ck_payroll_salary_month_first_day CHECK (EXTRACT(DAY FROM salary_month) = 1),
    CONSTRAINT ck_payroll_salary_amount CHECK (amount >= 0)
);
CREATE INDEX idx_payroll_salary_payment_payment ON tb_payroll_salary_payment (payment_id);
