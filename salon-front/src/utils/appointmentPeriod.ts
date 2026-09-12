export type AppointmentPeriod = 'MORNING' | 'AFTERNOON';

export const PERIOD_LABELS: Record<AppointmentPeriod, string> = {
  MORNING: 'Manhã',
  AFTERNOON: 'Tarde',
};

export function periodLabel(period: string | null | undefined): string | null {
  if (!period) return null;
  return PERIOD_LABELS[period as AppointmentPeriod] ?? period;
}
