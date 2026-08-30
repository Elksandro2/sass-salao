-- Dias trabalhados por diarista num período.
--
-- O padrão é automático: o relatório conta os dias distintos em que a diarista foi a
-- profissional de um atendimento concluído. Esta tabela guarda apenas os AJUSTES manuais que a
-- administração fizer para um período específico (ex.: veio trabalhar um dia sem cliente, ou
-- faltou). Sem linha aqui => vale o número automático.

CREATE TABLE tb_diarista_worked_days_override (
    id BIGSERIAL PRIMARY KEY,
    employee_id BIGINT NOT NULL REFERENCES tb_employee(id) ON DELETE CASCADE,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    days_worked INTEGER NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_diarista_override UNIQUE (employee_id, period_start, period_end),
    CONSTRAINT ck_diarista_days_nonneg CHECK (days_worked >= 0)
);
