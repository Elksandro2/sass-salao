-- Backfill do custo de receita congelado dos agendamentos que ficaram sem snapshot na V72.
-- Best-effort: usa a receita atual do serviço (mesma fórmula do getEstimatedCost em Java —
-- custo por unidade = cost_price / capacity, arredondado a 4 casas, × quantidade consumida).
-- Serviço sem receita com custo conhecido => 0 (congela "sem custo").

UPDATE tb_appointment_service_item i
SET snapshot_recipe_cost = COALESCE((
    SELECT SUM(ROUND(p.cost_price / p.capacity, 4) * u.quantity_used)
    FROM tb_salon_service_product_usage u
    JOIN tb_product p ON p.id = u.product_id
    WHERE u.salon_service_id = i.salon_service_id
      AND p.cost_price IS NOT NULL
      AND p.capacity IS NOT NULL
      AND p.capacity > 0
), 0)
WHERE i.snapshot_recipe_cost IS NULL;
