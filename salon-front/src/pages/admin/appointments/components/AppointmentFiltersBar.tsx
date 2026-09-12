import type { EmployeeData } from '../../employees/services/employees';

const inputCls = 'input-premium';
const labelCls = 'label-premium';

export interface AppointmentFiltersState {
  status: string;
  employeeId: string;
  clientName: string;
  startDate: string;
  endDate: string;
}

export const emptyAppointmentFilters: AppointmentFiltersState = {
  status: '',
  employeeId: '',
  clientName: '',
  startDate: '',
  endDate: '',
};

const statusOptions = [
  { value: 'PENDING', label: 'Pendente' },
  { value: 'REQUESTED', label: 'Solicitado' },
  { value: 'CONFIRMED', label: 'Confirmado' },
  { value: 'DECLINED', label: 'Recusado' },
  { value: 'DONE', label: 'Concluído' },
  { value: 'CANCELLED', label: 'Cancelado' },
];

interface AppointmentFiltersBarProps {
  filters: AppointmentFiltersState;
  employees: EmployeeData[];
  onChange: (patch: Partial<AppointmentFiltersState>) => void;
  onClear: () => void;
}

export function countActiveFilters(filters: AppointmentFiltersState): number {
  return Object.values(filters).filter((v) => v !== '').length;
}

/** Data de hoje no fuso do navegador, formato "YYYY-MM-DD" — mesmo formato dos filtros de data. */
function todayIsoDate(): string {
  const now = new Date();
  const year = now.getFullYear();
  const month = String(now.getMonth() + 1).padStart(2, '0');
  const day = String(now.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

/** O filtro "Hoje" está ativo quando De/Até apontam exatamente para o dia de hoje. */
export function isTodayFilterActive(filters: AppointmentFiltersState): boolean {
  const today = todayIsoDate();
  return filters.startDate === today && filters.endDate === today;
}

export const AppointmentFiltersBar = ({ filters, employees, onChange, onClear }: AppointmentFiltersBarProps) => {
  const activeCount = countActiveFilters(filters);
  const todayActive = isTodayFilterActive(filters);

  const toggleTodayFilter = () => {
    if (todayActive) {
      onChange({ startDate: '', endDate: '' });
    } else {
      const today = todayIsoDate();
      onChange({ startDate: today, endDate: today });
    }
  };

  return (
    <div className="flex flex-wrap gap-4 items-end bg-white/80 backdrop-blur-md rounded-2xl border border-[#eae1e1]/80 p-5 shadow-sm">
      <div className="space-y-1 min-w-[160px] flex-1">
        <label className={labelCls}>Status</label>
        <select
          className={inputCls}
          value={filters.status}
          onChange={(e) => onChange({ status: e.target.value })}
        >
          <option value="">Todos</option>
          {statusOptions.map((s) => (
            <option key={s.value} value={s.value}>
              {s.label}
            </option>
          ))}
        </select>
      </div>

      <div className="space-y-1 min-w-[180px] flex-1">
        <label className={labelCls}>Profissional</label>
        <select
          className={inputCls}
          value={filters.employeeId}
          onChange={(e) => onChange({ employeeId: e.target.value })}
        >
          <option value="">Todas</option>
          {employees.map((e) => (
            <option key={e.id} value={e.id}>
              {e.name}
            </option>
          ))}
        </select>
      </div>

      <div className="space-y-1 min-w-[180px] flex-1">
        <label className={labelCls}>Cliente</label>
        <input
          type="text"
          placeholder="Buscar por nome..."
          className={inputCls}
          value={filters.clientName}
          onChange={(e) => onChange({ clientName: e.target.value })}
        />
      </div>

      <div className="space-y-1">
        <label className={labelCls}>De</label>
        <input
          type="date"
          className={inputCls}
          value={filters.startDate}
          max={filters.endDate || undefined}
          onChange={(e) => onChange({ startDate: e.target.value })}
        />
      </div>

      <div className="space-y-1">
        <label className={labelCls}>Até</label>
        <input
          type="date"
          className={inputCls}
          value={filters.endDate}
          min={filters.startDate || undefined}
          onChange={(e) => onChange({ endDate: e.target.value })}
        />
      </div>

      <button
        type="button"
        onClick={toggleTodayFilter}
        aria-pressed={todayActive}
        className={`px-5 py-2.5 border text-sm font-semibold rounded-xl transition-all duration-200 cursor-pointer ${
          todayActive
            ? 'bg-[#be8a83] border-[#be8a83] text-white'
            : 'border-[#eae1e1] text-[#3b3036] hover:text-[#be8a83] hover:border-[#be8a83] bg-white'
        }`}
      >
        Agendamentos de Hoje
      </button>

      <button
        onClick={onClear}
        className="px-5 py-2.5 border border-[#eae1e1] text-sm font-semibold text-[#3b3036] hover:text-[#be8a83] hover:border-[#be8a83] bg-white rounded-xl transition-all duration-200 cursor-pointer flex items-center gap-2"
      >
        Limpar Filtros
        {activeCount > 0 && (
          <span className="inline-flex items-center justify-center w-5 h-5 text-[11px] font-bold rounded-full bg-[#be8a83] text-white">
            {activeCount}
          </span>
        )}
      </button>
    </div>
  );
};
