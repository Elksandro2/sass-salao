-- 1) A permissão de PATCH /v1/appointments/*/cancel (V8) só foi vinculada à CLIENTE.
-- Gerente e funcionária cancelam pelo backend (cancel() aceita qualquer "isStaff"), mas o botão
-- "Cancelar" no admin fica escondido pro PermissionGate delas por falta desse vínculo — mesmo
-- buraco que confirm/decline/status/produtos/despesas já tiveram corrigido (V24/V44/V50/V56/V59).
INSERT INTO tb_role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM tb_role r, tb_permission p
WHERE r.name IN ('GERENTE_DE_ATENDIMENTO', 'FUNCIONARIA')
  AND p.endpoint = '/v1/appointments/*/cancel'
  AND p.http_method = 'PATCH'
  AND NOT EXISTS (
      SELECT 1 FROM tb_role_permissions rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

-- 2) "Descancelar" (PATCH /v1/appointments/*/reopen) — desfaz um cancelamento por engano.
INSERT INTO tb_permission (name, endpoint, http_method, classe)
SELECT 'Reabrir Agendamento', '/v1/appointments/*/reopen', 'PATCH', 'Agendamento'
WHERE NOT EXISTS (
    SELECT 1 FROM tb_permission WHERE endpoint = '/v1/appointments/*/reopen' AND http_method = 'PATCH'
);

INSERT INTO tb_role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM tb_role r, tb_permission p
WHERE r.name IN ('GERENTE_DE_ATENDIMENTO', 'FUNCIONARIA')
  AND p.endpoint = '/v1/appointments/*/reopen'
  AND p.http_method = 'PATCH'
  AND NOT EXISTS (
      SELECT 1 FROM tb_role_permissions rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
