-- Editar dados básicos de um agendamento já criado (profissional, dia/período) — mesmo padrão
-- de acesso de services/products/expenses (V56): ADMIN já tem bypass total; GERENTE age sobre
-- qualquer agendamento; FUNCIONARIA só nos que ela é a profissional atribuída (recorte no
-- service, AppointmentService.assertCanManage — esta migration só abre a porta do endpoint).
INSERT INTO tb_permission (name, endpoint, http_method, classe)
SELECT 'Editar Dados do Agendamento', '/v1/appointments/*/details', 'PATCH', 'Agendamento'
WHERE NOT EXISTS (
    SELECT 1 FROM tb_permission WHERE endpoint = '/v1/appointments/*/details' AND http_method = 'PATCH'
);

INSERT INTO tb_role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM tb_role r, tb_permission p
WHERE r.name IN ('GERENTE_DE_ATENDIMENTO', 'FUNCIONARIA')
  AND p.endpoint = '/v1/appointments/*/details'
  AND p.http_method = 'PATCH'
  AND NOT EXISTS (
      SELECT 1 FROM tb_role_permissions rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
