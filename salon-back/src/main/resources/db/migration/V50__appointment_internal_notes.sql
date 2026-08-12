-- V50__appointment_internal_notes.sql
--
-- Observação interna da equipe sobre um agendamento específico — distinta de client_notes
-- (o que o CLIENTE escreveu ao pedir o horário). Editável a qualquer momento pela equipe,
-- não só na criação, e fica visível depois no histórico do cliente.
ALTER TABLE tb_appointment ADD COLUMN internal_notes TEXT;

-- Mesmo padrão de acesso de confirm/decline/status (V24/V44): ADMIN já tem bypass total
-- (VerifyUserPermissions), não precisa de linha aqui. GERENTE age sobre qualquer agendamento;
-- FUNCIONARIA só nos que ela é a profissional (recorte feito no service,
-- AppointmentService.assertCanManage — este migration só abre a porta do endpoint).
INSERT INTO tb_permission (name, endpoint, http_method, classe)
SELECT 'Editar Observação Interna do Agendamento', '/v1/appointments/*/internal-notes', 'PATCH', 'Agendamento'
WHERE NOT EXISTS (
    SELECT 1 FROM tb_permission WHERE endpoint = '/v1/appointments/*/internal-notes' AND http_method = 'PATCH'
);

INSERT INTO tb_role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM tb_role r, tb_permission p
WHERE r.name IN ('GERENTE_DE_ATENDIMENTO', 'FUNCIONARIA')
  AND p.endpoint = '/v1/appointments/*/internal-notes'
  AND p.http_method = 'PATCH'
  AND NOT EXISTS (
      SELECT 1 FROM tb_role_permissions rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
