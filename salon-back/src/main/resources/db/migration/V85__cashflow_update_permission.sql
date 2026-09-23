-- 1) Editar um lançamento manual do fluxo de caixa (antes só dava pra criar ou apagar).
INSERT INTO tb_permission (name, endpoint, http_method, classe)
SELECT 'Editar Fluxo Caixa', '/v1/cashflow/*', 'PUT', 'Fluxo de Caixa'
WHERE NOT EXISTS (
    SELECT 1 FROM tb_permission WHERE endpoint = '/v1/cashflow/*' AND http_method = 'PUT'
);

INSERT INTO tb_role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM tb_role r, tb_permission p
WHERE r.name = 'GERENTE_DE_ATENDIMENTO'
  AND p.endpoint = '/v1/cashflow/*'
  AND p.http_method = 'PUT'
  AND NOT EXISTS (
      SELECT 1 FROM tb_role_permissions rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

-- 2) Corrige um buraco antigo (V8): "Deletar Fluxo Caixa" (/v1/cashflow/*, DELETE) foi cadastrada
-- mas nunca vinculada à GERENTE_DE_ATENDIMENTO — o bind da V8 só cobria '/v1/cashflow' (sem o
-- curinga), então o botão de excluir ficava escondido pra ela mesmo o backend permitindo. Mesmo
-- padrão de bug já corrigido pra outros endpoints (ver V75).
INSERT INTO tb_role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM tb_role r, tb_permission p
WHERE r.name = 'GERENTE_DE_ATENDIMENTO'
  AND p.endpoint = '/v1/cashflow/*'
  AND p.http_method = 'DELETE'
  AND NOT EXISTS (
      SELECT 1 FROM tb_role_permissions rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
