-- A validação (backend CashFlowRequest e frontend cashflow.schema.ts) sempre permitiu até 500
-- caracteres na descrição, mas a coluna no banco ficou em VARCHAR(255) — qualquer descrição
-- entre 256 e 500 caracteres passava pela validação e quebrava só na hora de salvar, com um
-- erro genérico de "operação no banco de dados" sem explicação nenhuma pra quem usa o sistema.
-- Alinha a coluna com o limite que já era o pretendido em todo o resto do sistema.
ALTER TABLE tb_cashflow ALTER COLUMN description TYPE VARCHAR(500);
