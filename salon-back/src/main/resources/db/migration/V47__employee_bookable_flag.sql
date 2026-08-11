-- Separa "recebe remuneração" de "pode ser escalado num agendamento". Até aqui, todo registro
-- em tb_employee era automaticamente elegível pra atender clientes (ver findAllActiveForBooking),
-- o que impedia gerentes de terem salário registrado sem aparecer como opção de atendimento.
-- Default true preserva o comportamento atual para todo Employee já existente (todos são
-- FUNCIONARIA, que continuam agendáveis).
ALTER TABLE tb_employee ADD COLUMN bookable BOOLEAN NOT NULL DEFAULT TRUE;
