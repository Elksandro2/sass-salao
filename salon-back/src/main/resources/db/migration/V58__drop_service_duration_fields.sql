-- O salão passou a agendar só por dia ("ordem de chegada"), sem grade de horário — os campos
-- de duração do serviço (tempo estimado ao cliente, minutos pra encaixe na agenda) nunca são
-- usados de verdade e a checagem de conflito de horário baseada neles foi removida do backend.

ALTER TABLE tb_salon_service
    DROP COLUMN duration_min,
    DROP COLUMN duration_estimate;

ALTER TABLE tb_appointment_service_item
    DROP COLUMN custom_duration_min;
