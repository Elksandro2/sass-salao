-- V52__employee_mercadopago_account.sql
--
-- Guarda a conexão OAuth de cada funcionária com a própria conta Mercado Pago, usada pro
-- split de pagamento (parte do serviço/produto cai direto na conta dela, sem passar pela
-- conta do salão). access_token/refresh_token são cifrados (EncryptedStringConverter, mesmo
-- padrão de CPF/PIX) porque dão controle sobre a conta MP dela caso vazem.
--
-- mp_user_id NÃO é sensível (é só o identificador público da conta dela no Mercado Pago,
-- necessário pra criar o pagamento em nome dela) — fica em texto claro de propósito, permite
-- índice/busca.
CREATE TABLE tb_employee_mp_account (
    id BIGSERIAL PRIMARY KEY,
    employee_id BIGINT NOT NULL UNIQUE REFERENCES tb_employee(id),
    mp_user_id VARCHAR(50) NOT NULL,
    access_token_encrypted TEXT NOT NULL,
    refresh_token_encrypted TEXT NOT NULL,
    public_key VARCHAR(255),
    token_expires_at TIMESTAMPTZ,
    connected_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ
);

CREATE INDEX idx_employee_mp_account_mp_user_id ON tb_employee_mp_account (mp_user_id);
