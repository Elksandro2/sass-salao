-- V53__mercadopago_split_permissions.sql
-- Permissões da Fase B (conexão OAuth da funcionária com a própria conta Mercado Pago).
-- O callback (/v1/employees/mercadopago/callback) não entra aqui: é público de propósito
-- (ver SecurityConfig) — quem autentica aquela chamada é o "state" de uso único, não o RBAC.

INSERT INTO tb_permission (name, endpoint, http_method, classe) VALUES
    ('Gerar Link de Conexão Mercado Pago', '/v1/employees/*/mercadopago/connect', 'GET', 'Funcionária'),
    ('Ver Status Conexão Mercado Pago', '/v1/employees/*/mercadopago/status', 'GET', 'Funcionária'),
    ('Desconectar Mercado Pago', '/v1/employees/*/mercadopago', 'DELETE', 'Funcionária');

INSERT INTO tb_role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM tb_role r, tb_permission p
WHERE r.name = 'GERENTE_DE_ATENDIMENTO'
  AND p.endpoint IN (
      '/v1/employees/*/mercadopago/connect',
      '/v1/employees/*/mercadopago/status',
      '/v1/employees/*/mercadopago'
  )
  AND p.classe = 'Funcionária';
