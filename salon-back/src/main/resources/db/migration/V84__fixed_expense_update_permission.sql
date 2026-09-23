-- Editar um gasto fixo já lançado (antes só dava pra criar ou apagar). Mesmo padrão de acesso
-- dos outros endpoints de Gasto Fixo (V60): ADMIN já tem bypass total; GERENTE_DE_ATENDIMENTO
-- precisa do vínculo explícito.
INSERT INTO tb_permission (name, endpoint, http_method, classe)
SELECT 'Editar Gasto Fixo', '/v1/fixed-expenses/*', 'PUT', 'Gasto Fixo'
WHERE NOT EXISTS (
    SELECT 1 FROM tb_permission WHERE endpoint = '/v1/fixed-expenses/*' AND http_method = 'PUT'
);

INSERT INTO tb_role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM tb_role r, tb_permission p
WHERE r.name = 'GERENTE_DE_ATENDIMENTO'
  AND p.endpoint = '/v1/fixed-expenses/*'
  AND p.http_method = 'PUT'
  AND NOT EXISTS (
      SELECT 1 FROM tb_role_permissions rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
