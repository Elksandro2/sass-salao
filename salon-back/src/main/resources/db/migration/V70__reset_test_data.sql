-- Limpa os dados de teste acumulados em produção antes do uso real começar, sem tocar em
-- configuração/seed do sistema (roles, permissões, feature flags, perfil do salão, config de
-- IA, tokens MCP) nem nas contas ADMIN/SYSADMIN — só dado de negócio (agendamento, cliente,
-- funcionária, produto, serviço, financeiro, etc.) volta pro estado de "banco recém-migrado".
--
-- Ordem: primeiro esvazia tudo que referencia tb_user (senão a exclusão de usuário abaixo
-- falha por violação de FK), depois remove só os usuários CLIENTE/FUNCIONARIA/GERENTE —
-- ADMIN e SYSADMIN sobrevivem, senão ninguém consegue logar depois desta migration.

TRUNCATE TABLE
    tb_appointment_expense_item,
    tb_appointment_product_item,
    tb_appointment_service_item,
    tb_appointment,
    tb_cashflow,
    tb_client_anamnesis,
    tb_email_outbox,
    tb_employee_mp_account,
    tb_employee,
    tb_fixed_expense,
    tb_general_note,
    tb_password_reset_token,
    tb_product,
    tb_push_subscription,
    tb_salon_service_product_usage,
    tb_salon_service,
    tb_staff_profile,
    tb_audit_log,
    tb_ai_call_log,
    tb_ai_recommendation
RESTART IDENTITY CASCADE;

DELETE FROM tb_user
WHERE role_id IN (
    SELECT id FROM tb_role WHERE name IN ('CLIENTE', 'FUNCIONARIA', 'GERENTE_DE_ATENDIMENTO')
);
