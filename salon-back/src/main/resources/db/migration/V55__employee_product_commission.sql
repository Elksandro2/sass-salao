-- Comissão única (%) sobre produtos vendidos, independente da comissão de serviços.
ALTER TABLE tb_employee ADD COLUMN product_commission_value DECIMAL(10, 2);
