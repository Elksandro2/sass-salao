/**
 * Tipos de remuneração — espelha o enum RemunerationType do backend.
 * Centraliza rótulos e as regras "precisa de valor" / "é diária" usadas em vários formulários.
 */
export type RemunerationType =
  | 'SALARIO_FIXO'
  | 'COMISSIONADO'
  | 'FIXO_E_COMISSIONADO'
  | 'DIARISTA'
  | 'DIARIA_E_COMISSIONADO';

/** Ordem de exibição nos <select>. */
export const REMUNERATION_TYPES: RemunerationType[] = [
  'SALARIO_FIXO',
  'COMISSIONADO',
  'FIXO_E_COMISSIONADO',
  'DIARISTA',
  'DIARIA_E_COMISSIONADO',
];

export const REMUNERATION_LABELS: Record<RemunerationType, string> = {
  SALARIO_FIXO: 'Salário fixo',
  COMISSIONADO: 'Comissionado',
  FIXO_E_COMISSIONADO: 'Salário fixo + comissionado',
  DIARISTA: 'Diarista',
  DIARIA_E_COMISSIONADO: 'Diarista + comissão',
};

export function remunerationLabel(type: string | null | undefined): string {
  if (!type) return 'Não definido';
  return REMUNERATION_LABELS[type as RemunerationType] ?? type;
}

/** Recebe por dia trabalhado — {@code remunerationValue} é o valor da diária. */
export function remunerationIsDaily(type: string | null | undefined): boolean {
  return type === 'DIARISTA' || type === 'DIARIA_E_COMISSIONADO';
}

/** Tem salário fixo mensal como base. */
export function remunerationHasFixedSalary(type: string | null | undefined): boolean {
  return type === 'SALARIO_FIXO' || type === 'FIXO_E_COMISSIONADO';
}

/** Recebe comissão de serviço (% de cada serviço realizado). */
export function remunerationPaysServiceCommission(type: string | null | undefined): boolean {
  return (
    type === 'COMISSIONADO' ||
    type === 'FIXO_E_COMISSIONADO' ||
    type === 'DIARIA_E_COMISSIONADO'
  );
}

/** Precisa de {@code remunerationValue} preenchido (salário base OU valor da diária). */
export function remunerationNeedsValue(type: string | null | undefined): boolean {
  return remunerationHasFixedSalary(type) || remunerationIsDaily(type);
}

/** Rótulo do campo de valor, conforme o tipo. */
export function remunerationValueFieldLabel(type: string | null | undefined): string {
  return remunerationIsDaily(type) ? 'Valor da diária (R$)' : 'Valor do salário fixo (R$)';
}
