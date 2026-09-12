-- A receita de um serviço podia até então só consumir produto na unidade cadastrada nele
-- (ex.: produto embalado em Litro obrigava a receita a ser lançada em Litro). Agora a receita
-- tem sua própria unidade, independente — pode lançar "30 ml" de um produto cuja embalagem
-- está cadastrada em Litro.
--
-- Backfill: congela cada linha existente na unidade do produto vigente hoje, exatamente o
-- comportamento que ela já tinha (o cálculo de custo continuaria igual mesmo sem backfill —
-- cai no fallback pra unidade do produto — mas gravar explicitamente evita que uma futura
-- reedição do produto reinterprete silenciosamente uma receita antiga).
ALTER TABLE tb_salon_service_product_usage ADD COLUMN unit VARCHAR(10);

UPDATE tb_salon_service_product_usage u
SET unit = p.unit
FROM tb_product p
WHERE u.product_id = p.id AND u.unit IS NULL;
