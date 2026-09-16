-- Exclusão definitiva de agendamento (issue: "criei/cliente criou errado, quero apagar de vez",
-- diferente de cancelar). ADMIN já tem acesso irrestrito via VerifyUserPermissions (bypassa a
-- tabela de permissões pra esse cargo) — só falta o vínculo explícito pra GERENTE_DE_ATENDIMENTO.
-- FUNCIONARIA fica de fora de propósito: exclusão é ação de gestão, cancelar/reabrir já cobre
-- o dia a dia dela.
INSERT INTO tb_permission (name, endpoint, http_method, classe)
SELECT 'Excluir Agendamento', '/v1/appointments/*', 'DELETE', 'Agendamento'
WHERE NOT EXISTS (
    SELECT 1 FROM tb_permission WHERE endpoint = '/v1/appointments/*' AND http_method = 'DELETE'
);

INSERT INTO tb_role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM tb_role r, tb_permission p
WHERE r.name = 'GERENTE_DE_ATENDIMENTO'
  AND p.endpoint = '/v1/appointments/*'
  AND p.http_method = 'DELETE'
  AND NOT EXISTS (
      SELECT 1 FROM tb_role_permissions rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
