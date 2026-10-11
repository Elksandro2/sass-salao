-- Venda avulsa de produto editável (como um agendamento).
--
-- Até aqui a venda só guardava o total e uma descrição montada ("2x Shampoo, 1x Óleo"), então
-- não dava pra editar os itens depois. Agora cada item fica registrado. Vendas antigas
-- continuam sem itens e podem ser editadas pelo total.
CREATE TABLE tb_cashflow_item (
    id BIGSERIAL PRIMARY KEY,
    cashflow_id BIGINT NOT NULL REFERENCES tb_cashflow(id) ON DELETE CASCADE,
    product_id BIGINT REFERENCES tb_product(id) ON DELETE SET NULL,
    product_name VARCHAR(200) NOT NULL,
    quantity INTEGER NOT NULL,
    unit_price NUMERIC(10, 2) NOT NULL,
    CONSTRAINT ck_cashflow_item_quantity CHECK (quantity > 0),
    CONSTRAINT ck_cashflow_item_price CHECK (unit_price >= 0)
);
CREATE INDEX idx_cashflow_item_cashflow ON tb_cashflow_item (cashflow_id);

-- % de comissão congelado na venda (editar a venda depois recalcula com o mesmo %), e se a
-- comissão foi digitada à mão (aí a edição não recalcula por cima).
ALTER TABLE tb_cashflow
    ADD COLUMN commission_percent NUMERIC(5, 2),
    ADD COLUMN commission_manual BOOLEAN NOT NULL DEFAULT FALSE;

-- Permissões pra gerente (ADMIN já tem acesso total): ver uma venda e editar a venda.
INSERT INTO tb_permission (name, endpoint, http_method, classe)
SELECT 'Ver Lançamento Fluxo Caixa', '/v1/cashflow/*', 'GET', 'Fluxo de Caixa'
WHERE NOT EXISTS (SELECT 1 FROM tb_permission WHERE endpoint = '/v1/cashflow/*' AND http_method = 'GET');

INSERT INTO tb_permission (name, endpoint, http_method, classe)
SELECT 'Editar Venda Avulsa', '/v1/cashflow/*/sale', 'PUT', 'Fluxo de Caixa'
WHERE NOT EXISTS (SELECT 1 FROM tb_permission WHERE endpoint = '/v1/cashflow/*/sale' AND http_method = 'PUT');

INSERT INTO tb_role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM tb_role r, tb_permission p
WHERE r.name = 'GERENTE_DE_ATENDIMENTO'
  AND ((p.endpoint = '/v1/cashflow/*' AND p.http_method = 'GET')
    OR (p.endpoint = '/v1/cashflow/*/sale' AND p.http_method = 'PUT'))
  AND NOT EXISTS (
      SELECT 1 FROM tb_role_permissions rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
