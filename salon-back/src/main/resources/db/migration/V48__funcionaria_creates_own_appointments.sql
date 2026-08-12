-- V48__funcionaria_creates_own_appointments.sql
--
-- FUNCIONARIA passa a poder criar agendamentos (cliente chega no salão, ela cadastra na hora),
-- reaproveitando a permissão POST /v1/appointments que já existe (criada na V8 para CLIENTE).
--
-- O recorte de segurança — ela só pode se atribuir como profissional, nunca uma colega — é feito
-- em AppointmentService.create(), não aqui: o modelo de permissões deste projeto trabalha em
-- cima de endpoint + método HTTP, não de instância (mesmo padrão documentado na V44).

INSERT INTO tb_role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM tb_role r, tb_permission p
WHERE r.name = 'FUNCIONARIA'
  AND p.endpoint = '/v1/appointments'
  AND p.http_method = 'POST'
  AND NOT EXISTS (
      SELECT 1 FROM tb_role_permissions rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
