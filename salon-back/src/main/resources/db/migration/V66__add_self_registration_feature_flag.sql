-- Seed ENABLE_SELF_REGISTRATION feature flag
INSERT INTO tb_feature_flag (name, enabled, description) VALUES
    ('ENABLE_SELF_REGISTRATION', FALSE, 'Ativa ou desativa o auto-cadastro de clientes na tela de login');
