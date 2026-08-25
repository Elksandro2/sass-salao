-- Liga/desliga tudo que depende da conta Mercado Pago do salão (PIX de agendamento, conexão de
-- split das funcionárias) sem afetar o gerador de QR Code PIX pra pagar equipe — esse não usa a
-- API do Mercado Pago, monta o código localmente a partir da chave PIX cadastrada de cada uma.
-- Nasce desligada: a Cristiane ainda não tem a conta MP configurada.
INSERT INTO tb_feature_flag (name, enabled, description) VALUES
    ('ENABLE_MERCADO_PAGO', FALSE, 'Habilita pagamento via PIX (Mercado Pago) em agendamentos e a conexão de split das funcionárias');
