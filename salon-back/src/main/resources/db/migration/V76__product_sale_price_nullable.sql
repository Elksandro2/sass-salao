-- Produto "de uso interno" (usedInServiceRecipe, sem availableForSale) não tem preço de venda —
-- só o custeio (cost_price). O preço passa a ser obrigatório só via validação de aplicação
-- (ProductService), condicionada a availableForSale, e não mais via NOT NULL de banco.
ALTER TABLE tb_product ALTER COLUMN price DROP NOT NULL;
