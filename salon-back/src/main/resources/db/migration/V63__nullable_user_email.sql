-- Cliente cadastrado pelo salão agora pode ter só o nome — email é uma funcionalidade
-- desligada por feature flag nesta versão (EMAIL_NOTIFICATIONS), não removida do sistema.
-- Múltiplos NULLs continuam permitidos numa coluna UNIQUE no Postgres.
ALTER TABLE tb_user ALTER COLUMN email DROP NOT NULL;
