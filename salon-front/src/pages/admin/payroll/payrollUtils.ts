export const formatBRL = (value: number | null | undefined) =>
  new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(value ?? 0);

const pad = (n: number) => String(n).padStart(2, '0');

/** Date local -> "YYYY-MM-DD" (sem passar por UTC, que desloca o dia em fuso negativo). */
export const toIsoDate = (d: Date) =>
  `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;

/** "YYYY-MM-DD" -> Date local ao meio-dia (evita o "um dia a menos" de fuso). */
export const parseIsoDate = (value: string) => new Date(`${value}T12:00:00`);

export const todayIso = () => toIsoDate(new Date());

export const monthRange = (year: number, monthIndex: number) => ({
  from: toIsoDate(new Date(year, monthIndex, 1)),
  to: toIsoDate(new Date(year, monthIndex + 1, 0)),
});

export const currentMonthRange = () => {
  const now = new Date();
  return monthRange(now.getFullYear(), now.getMonth());
};

/** O período é exatamente um mês do calendário? Devolve {year, monthIndex} ou null. */
export const asWholeMonth = (from: string, to: string) => {
  const start = parseIsoDate(from);
  if (start.getDate() !== 1) return null;
  const range = monthRange(start.getFullYear(), start.getMonth());
  return range.to === to ? { year: start.getFullYear(), monthIndex: start.getMonth() } : null;
};

/**
 * Período sugerido no "Marcar como pago": do dia seguinte ao último período pago até o fim
 * daquele mês; sem nenhum pagamento, o mês inteiro de {@code reference} (início do filtro).
 */
export const suggestPaymentPeriod = (payments: { periodEnd: string }[], reference: string) => {
  const lastEnd = payments
    .map((p) => p.periodEnd)
    .sort()
    .at(-1);
  const start = lastEnd ? parseIsoDate(lastEnd) : parseIsoDate(reference);
  if (lastEnd) start.setDate(start.getDate() + 1);
  else start.setDate(1);
  return { from: toIsoDate(start), to: monthRange(start.getFullYear(), start.getMonth()).to };
};

export const formatShortDate = (value: string | null | undefined) =>
  value ? parseIsoDate(value).toLocaleDateString('pt-BR') : '—';

export const monthLabel = (year: number, monthIndex: number) => {
  const label = new Date(year, monthIndex, 1).toLocaleDateString('pt-BR', {
    month: 'long',
    year: 'numeric',
  });
  return label.charAt(0).toUpperCase() + label.slice(1);
};
