-- Despesas itemizadas por agendamento (ex.: material extra usado no atendimento).
-- value_type = FIXED (valor em R$) ou PERCENTAGE (% sobre serviços+produtos do agendamento).

CREATE TABLE tb_appointment_expense_item (
    id BIGSERIAL PRIMARY KEY,
    appointment_id BIGINT NOT NULL REFERENCES tb_appointment(id) ON DELETE CASCADE,
    description VARCHAR(200) NOT NULL,
    value_type VARCHAR(20) NOT NULL,
    value DECIMAL(10, 2) NOT NULL
);

-- Mesmo padrão de acesso de internal-notes (V50): ADMIN já tem bypass total; GERENTE age sobre
-- qualquer agendamento; FUNCIONARIA só nos que ela é a profissional (recorte no service,
-- AppointmentService.assertCanManage — este migration só abre a porta dos endpoints).
INSERT INTO tb_permission (name, endpoint, http_method, classe)
SELECT 'Editar Produtos do Agendamento', '/v1/appointments/*/products', 'PATCH', 'Agendamento'
WHERE NOT EXISTS (
    SELECT 1 FROM tb_permission WHERE endpoint = '/v1/appointments/*/products' AND http_method = 'PATCH'
);

INSERT INTO tb_permission (name, endpoint, http_method, classe)
SELECT 'Editar Despesas do Agendamento', '/v1/appointments/*/expenses', 'PATCH', 'Agendamento'
WHERE NOT EXISTS (
    SELECT 1 FROM tb_permission WHERE endpoint = '/v1/appointments/*/expenses' AND http_method = 'PATCH'
);

INSERT INTO tb_role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM tb_role r, tb_permission p
WHERE r.name IN ('GERENTE_DE_ATENDIMENTO', 'FUNCIONARIA')
  AND p.endpoint IN ('/v1/appointments/*/products', '/v1/appointments/*/expenses')
  AND p.http_method = 'PATCH'
  AND NOT EXISTS (
      SELECT 1 FROM tb_role_permissions rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
