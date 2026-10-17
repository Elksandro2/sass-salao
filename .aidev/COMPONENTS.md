# Catálogo de componentes reutilizáveis

Consulte **antes** de criar algo novo. Se precisar de variação, generalize o existente. Ao criar algo genuinamente reutilizável, registre aqui no mesmo commit (caminho, propósito, quando usar / não usar).

## Frontend (`salon-front/src`)

### `components/CurrencyInput.tsx`
- **Propósito:** campo monetário R$ mascarado. Dígitos digitados viram centavos (`"1234"` → `R$ 12,34`) e emite string decimal com ponto (`"12.34"`).
- **Props:** `value`, `onValueChange`, `className`, `placeholder`, `disabled`, `id`, `name`, `onBlur`.
- **Usar:** qualquer valor em R$ (preço, custo, salário, despesa, valor pago). Com react-hook-form, via `Controller`.
- **Não usar:** quantidades, porcentagens, litros/ml.
- **Teste:** `fireEvent.change` recebe dígitos como centavos (`'200000'` = R$ 2.000,00).

### `components/SearchableSelect.tsx`
- **Propósito:** busca e seleção no mesmo campo (serviço, produto, cliente, profissional).
- **Usar:** listas longas onde digitar para filtrar é melhor que `<select>`.

### `components/modal/*` (`ModalForm`, `ConfirmDialog`)
- **Propósito:** formulários em modal e confirmação de ação destrutiva (desativar, excluir definitivamente, cancelar).
- **Regra:** toda exclusão/cancelamento pede confirmação com texto que explica o efeito.

### `components/table/Table.tsx`
- **Propósito:** tabela paginada padrão (`columns`, `data`, `keyExtractor`, `currentPage`, `totalPages`, `onPageChange`, `emptyMessage`).

### `components/permissions/*` e `hooks/usePermission`
- **Propósito:** `PermissionGate`/`usePermission('METHOD:/v1/...')` escondem ações sem permissão. É conforto de UI; a barreira real é o backend.

### `components/ProtectedRoute.tsx`, `components/CanI.tsx`
- **Propósito:** proteção de rota / verificação de capacidade.

### `hooks/useAlert`
- **Propósito:** `error/success/alert/confirm` padronizados. Não usar `alert()` nativo.

### `utils/apiError` (`getApiErrorMessage`)
- **Propósito:** extrai a mensagem amigável do erro da API (`BusinessException`).

### `utils/appointmentPeriod` (`PERIOD_LABELS`)
- **Propósito:** rótulos de Manhã/Tarde para agendamento por bloco.

### `utils/pagination` (`normalizePage`, `SpringPageResponse`)
- **Propósito:** padroniza resposta de página do Spring.

### `test/test-utils` (`customRender`)
- **Propósito:** render com providers para testes de tela.

## Backend (`salon-back`)

### `@Auditable`
- **Propósito:** registra auditoria de ações em service/controller.

### `BusinessException`
- **Propósito:** erro de regra de negócio com mensagem clara ao usuário (vira 4xx, não 500). Use para valores inválidos, guardas de exclusão, estados proibidos.

### `verifyUserPermissions.userOwnResourceOrHasPermission(...)`
- **Propósito:** base de `@PreAuthorize` (dono do recurso ou permissão por rota). ADMIN/SYSADMIN têm bypass.

### Spring Data `Specification` (filtros de listagem)
- **Propósito:** filtros compostos (data, status, profissional). Para agendamento, filtrar por data **agendada** (`scheduledAt`/`scheduledDate`/`preferredDate`), não de criação.

### `billAppointmentOnce` (CashFlow)
- **Propósito:** gera o lançamento de caixa do agendamento concluído exatamente uma vez. Cancelar/excluir agendamento remove o lançamento vinculado.

> Atualize este arquivo com o agente `component-cataloger` após features que criem helpers/componentes novos.
