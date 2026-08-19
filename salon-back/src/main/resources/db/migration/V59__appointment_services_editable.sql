-- A cliente às vezes decide fazer mais alguma coisa durante o atendimento — permite editar os
-- serviços de um agendamento já existente (mesmo padrão de produtos/despesas da V56).
INSERT INTO tb_permission (name, endpoint, http_method, classe)
SELECT 'Editar Serviços do Agendamento', '/v1/appointments/*/services', 'PATCH', 'Agendamento'
WHERE NOT EXISTS (
    SELECT 1 FROM tb_permission WHERE endpoint = '/v1/appointments/*/services' AND http_method = 'PATCH'
);

INSERT INTO tb_role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM tb_role r, tb_permission p
WHERE r.name IN ('GERENTE_DE_ATENDIMENTO', 'FUNCIONARIA')
  AND p.endpoint = '/v1/appointments/*/services'
  AND p.http_method = 'PATCH'
  AND NOT EXISTS (
      SELECT 1 FROM tb_role_permissions rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
