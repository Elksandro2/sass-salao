-- Gastos fixos/operacionais do salão (aluguel, água, luz, etc.), lançados livremente pela
-- administração numa tela dedicada — base de cálculo pros relatórios de saúde financeira.

CREATE TABLE tb_fixed_expense (
    id BIGSERIAL PRIMARY KEY,
    description VARCHAR(200) NOT NULL,
    amount DECIMAL(10, 2) NOT NULL,
    date DATE NOT NULL,
    created_by_user_id BIGINT REFERENCES tb_user(id),
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

-- Dado financeiro: mesmo nível de acesso do Fluxo de Caixa (ADMIN + GERENTE_DE_ATENDIMENTO).
INSERT INTO tb_permission (name, endpoint, http_method, classe)
SELECT 'Listar Gastos Fixos', '/v1/fixed-expenses', 'GET', 'Gasto Fixo'
WHERE NOT EXISTS (
    SELECT 1 FROM tb_permission WHERE endpoint = '/v1/fixed-expenses' AND http_method = 'GET'
);

INSERT INTO tb_permission (name, endpoint, http_method, classe)
SELECT 'Criar Gasto Fixo', '/v1/fixed-expenses', 'POST', 'Gasto Fixo'
WHERE NOT EXISTS (
    SELECT 1 FROM tb_permission WHERE endpoint = '/v1/fixed-expenses' AND http_method = 'POST'
);

INSERT INTO tb_permission (name, endpoint, http_method, classe)
SELECT 'Excluir Gasto Fixo', '/v1/fixed-expenses/*', 'DELETE', 'Gasto Fixo'
WHERE NOT EXISTS (
    SELECT 1 FROM tb_permission WHERE endpoint = '/v1/fixed-expenses/*' AND http_method = 'DELETE'
);

INSERT INTO tb_role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM tb_role r, tb_permission p
WHERE r.name = 'GERENTE_DE_ATENDIMENTO'
  AND p.classe = 'Gasto Fixo'
  AND NOT EXISTS (
      SELECT 1 FROM tb_role_permissions rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
