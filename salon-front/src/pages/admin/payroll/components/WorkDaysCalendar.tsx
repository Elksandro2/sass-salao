import { useMemo, useState } from 'react';
import { ChevronLeft, ChevronRight } from 'lucide-react';
import type { WorkDay } from '../services/payroll';
import { formatBRL, monthLabel, parseIsoDate, toIsoDate } from '../payrollUtils';

const WEEKDAYS = ['Dom', 'Seg', 'Ter', 'Qua', 'Qui', 'Sex', 'Sáb'];

interface WorkDaysCalendarProps {
  periodStart: string;
  periodEnd: string;
  workDays: WorkDay[];
  /** Dias em que ela teve atendimento concluído — ajudam a lembrar quais dias ela veio. */
  appointmentDates: string[];
  defaultRate: number | null;
  busyDate: string | null;
  /** Clique num dia ainda não marcado. */
  onMark: (date: string) => void;
  /** Clique num dia marcado (editar valor/justificativa ou desmarcar). */
  onOpenDay: (day: WorkDay) => void;
}

/**
 * Calendário de diárias: cada dia marcado é uma diária paga. Substitui o antigo "número de dias"
 * solto — agora dá pra ver exatamente quais dias entram na folha e, quando o valor do dia foi
 * diferente do padrão, ele aparece destacado.
 */
export const WorkDaysCalendar = ({
  periodStart,
  periodEnd,
  workDays,
  appointmentDates,
  defaultRate,
  busyDate,
  onMark,
  onOpenDay,
}: WorkDaysCalendarProps) => {
  const months = useMemo(() => {
    const list: { year: number; monthIndex: number }[] = [];
    const start = parseIsoDate(periodStart);
    const end = parseIsoDate(periodEnd);
    let y = start.getFullYear();
    let m = start.getMonth();
    while (y < end.getFullYear() || (y === end.getFullYear() && m <= end.getMonth())) {
      list.push({ year: y, monthIndex: m });
      m += 1;
      if (m > 11) {
        m = 0;
        y += 1;
      }
    }
    return list;
  }, [periodStart, periodEnd]);

  const [monthPos, setMonthPos] = useState(0);
  const safePos = Math.min(monthPos, months.length - 1);
  const { year, monthIndex } = months[safePos] ?? months[0];

  const byDate = useMemo(() => new Map(workDays.map((d) => [d.date, d])), [workDays]);
  const withAppointment = useMemo(() => new Set(appointmentDates), [appointmentDates]);

  const firstWeekday = new Date(year, monthIndex, 1).getDay();
  const daysInMonth = new Date(year, monthIndex + 1, 0).getDate();
  const cells: (string | null)[] = [
    ...Array.from({ length: firstWeekday }, () => null),
    ...Array.from({ length: daysInMonth }, (_, i) => toIsoDate(new Date(year, monthIndex, i + 1))),
  ];

  return (
    <div className="rounded-xl border border-[#eae1e1] bg-white p-4">
      <div className="flex items-center justify-between mb-3">
        <button
          type="button"
          aria-label="Mês anterior"
          disabled={safePos === 0}
          onClick={() => setMonthPos(safePos - 1)}
          className="p-1.5 rounded-lg text-[#7a7074] hover:bg-[#fdf6f5] disabled:opacity-30 disabled:cursor-not-allowed cursor-pointer"
        >
          <ChevronLeft size={16} />
        </button>
        <span className="text-sm font-semibold text-[#3b3036]">{monthLabel(year, monthIndex)}</span>
        <button
          type="button"
          aria-label="Próximo mês"
          disabled={safePos >= months.length - 1}
          onClick={() => setMonthPos(safePos + 1)}
          className="p-1.5 rounded-lg text-[#7a7074] hover:bg-[#fdf6f5] disabled:opacity-30 disabled:cursor-not-allowed cursor-pointer"
        >
          <ChevronRight size={16} />
        </button>
      </div>

      <div className="grid grid-cols-7 gap-1 text-center">
        {WEEKDAYS.map((w) => (
          <span key={w} className="text-[10px] font-semibold uppercase text-[#7a7074] pb-1">
            {w}
          </span>
        ))}
        {cells.map((date, i) => {
          if (!date) return <span key={`empty-${i}`} />;
          const inPeriod = date >= periodStart && date <= periodEnd;
          const day = byDate.get(date);
          const hasAppointment = withAppointment.has(date);
          const custom = day && defaultRate != null && day.amount !== defaultRate;
          const busy = busyDate === date;
          const dayNumber = Number(date.slice(8, 10));

          let cls = 'bg-white text-[#3b3036] border-[#eae1e1] hover:border-[#be8a83]';
          if (day) cls = 'bg-[#be8a83] text-white border-[#be8a83] hover:bg-[#a6726b]';
          if (day && custom) cls = 'bg-amber-500 text-white border-amber-500 hover:bg-amber-600';
          if (!inPeriod) cls = 'bg-transparent text-[#7a7074]/40 border-transparent cursor-default';

          const title = day
            ? `${formatBRL(day.amount)}${day.justification ? ` — ${day.justification}` : ''}`
            : hasAppointment
              ? 'Teve atendimento neste dia — clique pra marcar a diária'
              : 'Clique pra marcar como dia trabalhado';

          return (
            <button
              key={date}
              type="button"
              disabled={!inPeriod || busy}
              title={inPeriod ? title : undefined}
              onClick={() => (day ? onOpenDay(day) : onMark(date))}
              className={`relative h-11 rounded-lg border text-xs font-semibold flex flex-col items-center justify-center transition-all cursor-pointer disabled:cursor-not-allowed ${cls} ${busy ? 'opacity-50' : ''}`}
            >
              <span>{dayNumber}</span>
              {day && custom && (
                <span className="text-[9px] font-medium leading-none">
                  {Math.round(day.amount)}
                </span>
              )}
              {hasAppointment && inPeriod && (
                <span
                  className={`absolute bottom-1 right-1 h-1.5 w-1.5 rounded-full ${day ? 'bg-white' : 'bg-[#be8a83]'}`}
                />
              )}
            </button>
          );
        })}
      </div>

      <div className="flex flex-wrap gap-x-4 gap-y-1 mt-3 text-[11px] text-[#7a7074]">
        <span className="flex items-center gap-1.5">
          <span className="h-3 w-3 rounded bg-[#be8a83]" /> diária padrão
        </span>
        <span className="flex items-center gap-1.5">
          <span className="h-3 w-3 rounded bg-amber-500" /> valor diferente
        </span>
        <span className="flex items-center gap-1.5">
          <span className="h-1.5 w-1.5 rounded-full bg-[#be8a83]" /> teve atendimento
        </span>
      </div>
    </div>
  );
};
