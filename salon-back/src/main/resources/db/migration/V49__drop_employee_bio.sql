-- V49__drop_employee_bio.sql
-- Campo "Biografia/Especialidade" removido a pedido da dona do salão — não era usado.
ALTER TABLE tb_employee DROP COLUMN bio;
