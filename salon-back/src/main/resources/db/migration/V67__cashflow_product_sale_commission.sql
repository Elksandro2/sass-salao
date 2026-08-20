ALTER TABLE tb_cashflow ADD COLUMN employee_id BIGINT REFERENCES tb_employee(id);
ALTER TABLE tb_cashflow ADD COLUMN commission_amount NUMERIC(10, 2);
