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

export const formatShortDate = (value: string | null | undefined) =>
  value ? parseIsoDate(value).toLocaleDateString('pt-BR') : '—';

export const monthLabel = (year: number, monthIndex: number) => {
  const label = new Date(year, monthIndex, 1).toLocaleDateString('pt-BR', {
    month: 'long',
    year: 'numeric',
  });
  return label.charAt(0).toUpperCase() + label.slice(1);
};

/** "2026-10-01" → "outubro/2026". */
export const salaryMonthLabel = (month: string) => {
  const d = parseIsoDate(month);
  return `${d.toLocaleDateString('pt-BR', { month: 'long' })}/${d.getFullYear()}`;
};

interface PaymentLike {
  periodStart: string | null;
  periodEnd: string | null;
  salaryMonths: { month: string }[];
}

/** Dias que o pagamento cobre; pagamento só de salário cobre os meses do salário. */
export const paymentSpan = (p: PaymentLike) => {
  if (p.periodStart && p.periodEnd) return { from: p.periodStart, to: p.periodEnd };
  const months = p.salaryMonths.map((s) => s.month).sort();
  const first = months[0] ?? todayIso();
  const last = parseIsoDate(months.at(-1) ?? first);
  return { from: first, to: monthRange(last.getFullYear(), last.getMonth()).to };
};

/** "01/10/2026 a 15/10/2026 · salário de outubro/2026" (ou só uma das partes). */
export const describePayment = (p: PaymentLike) => {
  const parts: string[] = [];
  if (p.periodStart && p.periodEnd) {
    parts.push(`${formatShortDate(p.periodStart)} a ${formatShortDate(p.periodEnd)}`);
  }
  if (p.salaryMonths.length > 0) {
    parts.push(`salário de ${p.salaryMonths.map((s) => salaryMonthLabel(s.month)).join(', ')}`);
  }
  return parts.join(' · ');
};
