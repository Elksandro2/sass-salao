-- Antes do preço de venda ficar opcional (V76), ele era obrigatório mesmo pra produto que nunca
-- foi vendido — só usado na receita de serviço. Quem cadastrou preencheu 0 só pra passar da
-- validação. Esses produtos são "de uso", não "de venda": tiram o preço fictício e marcam como
-- não disponível para venda, pra sair da lista de Produtos (Venda) e aparecer só em Produtos (Uso).
UPDATE tb_product
SET price = NULL,
    available_for_sale = false,
    used_in_service_recipe = true
WHERE price = 0
  AND available_for_sale = true;
