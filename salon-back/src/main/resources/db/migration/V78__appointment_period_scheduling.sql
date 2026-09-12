-- "Manhã ou tarde" no lugar de hora exata: a equipe passa a marcar o dia + um bloco (manhã/tarde)
-- em vez de um horário cravado; o cliente ganha a mesma escolha como preferência (não vinculante)
-- pro dia que ele já indicava. Agendamento já existente continua com scheduled_at (hora exata) —
-- essas colunas novas só valem a partir de agora, ninguém precisa de backfill.
ALTER TABLE tb_appointment
    ADD COLUMN scheduled_date DATE,
    ADD COLUMN scheduled_period VARCHAR(10),
    ADD COLUMN preferred_period VARCHAR(10);
