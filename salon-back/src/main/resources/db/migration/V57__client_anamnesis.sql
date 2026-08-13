-- Ficha de anamnese do cliente (dado de saúde, sensível pela LGPD). Um registro por cliente,
-- upsert sempre. Campos de texto livre são cifrados na aplicação (ver EncryptedStringConverter);
-- aqui a coluna já nasce TEXT pra caber o ciphertext em base64.

CREATE TABLE tb_client_anamnesis (
    id BIGSERIAL PRIMARY KEY,
    client_id BIGINT NOT NULL UNIQUE REFERENCES tb_user(id) ON DELETE CASCADE,
    allergies_encrypted TEXT,
    health_conditions_encrypted TEXT,
    medications_encrypted TEXT,
    additional_notes_encrypted TEXT,
    skin_type VARCHAR(20),
    hair_type VARCHAR(20),
    consent_given_at TIMESTAMP NOT NULL,
    consent_given_by_user_id BIGINT NOT NULL REFERENCES tb_user(id),
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP,
    updated_by_user_id BIGINT REFERENCES tb_user(id)
);

-- Dado de saúde: acesso restrito a quem administra o salão. ADMIN já tem bypass total
-- (VerifyUserPermissions), GERENTE_DE_ATENDIMENTO recebe permissão explícita.
-- FUNCIONARIA NÃO recebe acesso — minimização de dados (LGPD, Art. 6º, III).
INSERT INTO tb_permission (name, endpoint, http_method, classe)
SELECT 'Ver Ficha de Anamnese', '/v1/clients/*/anamnesis', 'GET', 'Anamnese'
WHERE NOT EXISTS (
    SELECT 1 FROM tb_permission WHERE endpoint = '/v1/clients/*/anamnesis' AND http_method = 'GET'
);

INSERT INTO tb_permission (name, endpoint, http_method, classe)
SELECT 'Editar Ficha de Anamnese', '/v1/clients/*/anamnesis', 'PUT', 'Anamnese'
WHERE NOT EXISTS (
    SELECT 1 FROM tb_permission WHERE endpoint = '/v1/clients/*/anamnesis' AND http_method = 'PUT'
);

INSERT INTO tb_permission (name, endpoint, http_method, classe)
SELECT 'Excluir Ficha de Anamnese', '/v1/clients/*/anamnesis', 'DELETE', 'Anamnese'
WHERE NOT EXISTS (
    SELECT 1 FROM tb_permission WHERE endpoint = '/v1/clients/*/anamnesis' AND http_method = 'DELETE'
);

INSERT INTO tb_role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM tb_role r, tb_permission p
WHERE r.name = 'GERENTE_DE_ATENDIMENTO'
  AND p.classe = 'Anamnese'
  AND NOT EXISTS (
      SELECT 1 FROM tb_role_permissions rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
