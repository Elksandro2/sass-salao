/**
 * appointmentRules.ts
 *
 * Utilitário de regras de negócio para agendamentos no frontend.
 * Centraliza toda a lógica de visibilidade e permissão de ações,
 * espelhando as Guard Clauses do AppointmentService.java no backend.
 *
 * IMPORTANTE: Este arquivo NÃO substitui a segurança do backend.
 * Serve apenas para melhorar a UX, ocultando ou desabilitando
 * ações que resultariam em erro de negócio.
 */

/** Subconjunto de AppointmentResponse com os campos necessários para as regras */
export interface AppointmentForRules {
  status: string;
  paymentStatus?: string | null;
  pixQrCode?: string | null;
}

// ────────────────────────────────────────────
// Status Terminais
// ────────────────────────────────────────────

/**
 * Retorna `true` se o status do agendamento é terminal (CANCELLED ou DONE).
 * Status terminais não permitem mais alterações de status de agendamento.
 */
export function isTerminalStatus(status: string): boolean {
  return status === 'CANCELLED' || status === 'DONE';
}

/**
 * Retorna `true` se o pagamento está em estado terminal (PAID ou CANCELLED).
 * Status de pagamento terminais não permitem mais alterações de pagamento.
 */
export function isTerminalPaymentStatus(paymentStatus: string | null | undefined): boolean {
  return paymentStatus === 'PAID' || paymentStatus === 'CANCELLED';
}

// ────────────────────────────────────────────
// Regras de Ação
// ────────────────────────────────────────────

/**
 * Retorna `true` se o agendamento pode ser cancelado.
 *
 * Espelha as guard clauses do `cancel()` no AppointmentService.java:
 * - Bloqueado se status = CANCELLED (já cancelado, estado terminal)
 * - Bloqueado se status = DONE (serviço concluído)
 * - Bloqueado se status = DECLINED (solicitação recusada)
 * - Bloqueado se paymentStatus = PAID (exige estorno antes de cancelar)
 */
export function canCancel(apt: AppointmentForRules): boolean {
  if (apt.status === 'CANCELLED') return false;
  if (apt.status === 'DONE') return false;
  if (apt.status === 'DECLINED') return false;
  if (apt.paymentStatus === 'PAID') return false;
  return true;
}

/**
 * Retorna o motivo pelo qual o cancelamento está bloqueado, ou `null` se pode cancelar.
 * Usado para tooltips e mensagens de feedback visual.
 */
export function getCancelBlockReason(apt: AppointmentForRules): string | null {
  if (apt.status === 'CANCELLED') return 'Este agendamento já foi cancelado.';
  if (apt.status === 'DONE') return 'Agendamentos concluídos não podem ser cancelados.';
  if (apt.status === 'DECLINED') return 'Esta solicitação já foi recusada pelo salão.';
  if (apt.paymentStatus === 'PAID') return 'Agendamentos pagos não podem ser cancelados sem estorno prévio.';
  return null;
}

/**
 * Retorna `true` se um agendamento cancelado pode ser reaberto ("descancelar").
 * Espelha a guard clause do `reopen()` no AppointmentService.java: só um agendamento
 * CANCELLED pode ser reaberto.
 */
export function canReopen(apt: AppointmentForRules): boolean {
  return apt.status === 'CANCELLED';
}

/**
 * Retorna `true` se o status do agendamento pode ser alterado pelo admin.
 *
 * Espelha as guard clauses do `updateStatus()` no AppointmentService.java:
 * - Bloqueado se status = CANCELLED (estado terminal)
 *
 * Nota: a transição para DONE é permitida mesmo com pagamento PAID (regra desacoplada).
 * A restrição de pagamento PAID para outras transições é tratada nas opções do select.
 */
export function canChangeStatus(apt: AppointmentForRules): boolean {
  if (apt.status === 'CANCELLED') return false;
  return true;
}

/**
 * Retorna o motivo pelo qual a alteração de status está bloqueada, ou `null` se pode alterar.
 */
export function getStatusChangeBlockReason(apt: AppointmentForRules): string | null {
  if (apt.status === 'CANCELLED') return 'Agendamentos cancelados não podem ter seu status alterado.';
  return null;
}

/**
 * Retorna as opções válidas de status para o select do admin,
 * com base no status e pagamento atuais.
 *
 * Regras:
 * - DONE sempre é permitido (mesmo com pagamento PAID — regra desacoplada)
 * - Outras transições são bloqueadas se pagamento = PAID ou CANCELLED
 */
export function getValidStatusOptions(
  apt: AppointmentForRules
): Array<{ value: string; label: string }> {
  const isPaymentTerminal = isTerminalPaymentStatus(apt.paymentStatus);

  const allOptions: Array<{ value: string; label: string }> = [
    { value: 'PENDING', label: 'Pendente' },
    { value: 'CONFIRMED', label: 'Confirmado' },
    { value: 'DONE', label: 'Concluído' },
  ];

  if (isPaymentTerminal) {
    // Quando o pagamento está em estado terminal, só DONE é permitido
    // (as demais transições seriam rejeitadas pelo backend)
    return allOptions.filter((o) => o.value === apt.status || o.value === 'DONE');
  }

  if (apt.status === 'CONFIRMED') {
    return allOptions.filter((o) => o.value !== 'PENDING');
  }

  return allOptions;
}

/**
 * Retorna `true` se o status de pagamento pode ser alterado pelo admin.
 *
 * Espelha as guard clauses do `updatePaymentStatus()` no AppointmentService.java:
 * - Bloqueado se status = CANCELLED
 * - Bloqueado se paymentStatus = PAID (irreversível pela UI)
 * - Bloqueado se paymentStatus = CANCELLED
 */
export function canChangePaymentStatus(apt: AppointmentForRules): boolean {
  if (apt.status === 'CANCELLED') return false;
  if (isTerminalPaymentStatus(apt.paymentStatus)) return false;
  return true;
}

/**
 * Retorna o motivo pelo qual a alteração de status de pagamento está bloqueada, ou `null`.
 */
export function getPaymentStatusChangeBlockReason(apt: AppointmentForRules): string | null {
  if (apt.status === 'CANCELLED') return 'Agendamentos cancelados não permitem alterações de pagamento.';
  if (apt.paymentStatus === 'PAID') return 'O pagamento já foi confirmado e não pode ser revertido pela interface.';
  if (apt.paymentStatus === 'CANCELLED') return 'O status de pagamento já foi cancelado.';
  return null;
}

/**
 * Retorna `true` se é possível gerar ou visualizar o PIX para este agendamento.
 *
 * Espelha as guard clauses do `generatePixPayment()` no AppointmentService.java:
 * - Bloqueado se status = CANCELLED
 * - Bloqueado se paymentStatus = PAID (já pago)
 */
export function canGeneratePix(apt: AppointmentForRules): boolean {
  if (apt.status === 'CANCELLED') return false;
  if (apt.paymentStatus === 'PAID') return false;
  return true;
}
