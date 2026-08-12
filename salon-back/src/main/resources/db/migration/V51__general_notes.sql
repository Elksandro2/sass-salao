-- V51__general_notes.sql
--
-- Anotações gerais do salão (aba própria) — texto livre não vinculado a cliente/agendamento.
-- Distinta de tb_appointment.internal_notes (observação de UM atendimento específico).
CREATE TABLE tb_general_note (
    id BIGSERIAL PRIMARY KEY,
    content TEXT NOT NULL,
    author_id BIGINT NOT NULL REFERENCES tb_user(id),
    done BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ
);

CREATE INDEX idx_general_note_done_created ON tb_general_note (done, created_at DESC);

INSERT INTO tb_permission (name, endpoint, http_method, classe) VALUES
    ('Listar Anotações Gerais', '/v1/general-notes', 'GET', 'Anotação Geral'),
    ('Criar Anotação Geral', '/v1/general-notes', 'POST', 'Anotação Geral'),
    ('Editar Anotação Geral', '/v1/general-notes/*', 'PATCH', 'Anotação Geral'),
    ('Concluir Anotação Geral', '/v1/general-notes/*/done', 'PATCH', 'Anotação Geral'),
    ('Apagar Anotação Geral', '/v1/general-notes/*', 'DELETE', 'Anotação Geral');

-- ADMIN já tem bypass total (VerifyUserPermissions), só GERENTE precisa de linha explícita.
INSERT INTO tb_role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM tb_role r, tb_permission p
WHERE r.name = 'GERENTE_DE_ATENDIMENTO'
  AND p.classe = 'Anotação Geral';
