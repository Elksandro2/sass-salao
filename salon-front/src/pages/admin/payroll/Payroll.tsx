import { Fragment, useCallback, useEffect, useState } from 'react';
import { AlertTriangle, ChevronDown, ChevronLeft, ChevronRight, ChevronUp, Info, Wallet } from 'lucide-react';
import { payrollApi, type PayrollEmployeeSummary, type PayrollSummary, type PeriodStatus } from './services/payroll';
import { PayrollEmployeeDetail } from './components/PayrollEmployeeDetail';
import {
  asWholeMonth,
  currentMonthRange,
  formatBRL,
  formatShortDate,
  monthLabel,
  monthRange,
} from './payrollUtils';
import { remunerationLabel } from '../../../utils/remuneration';
import { useAlert } from '../../../hooks/useAlert';
import { getApiErrorMessage } from '../../../utils/apiError';

const inputCls = 'input-premium';
const labelCls = 'label-premium';

const STATUS_STYLE: Record<PeriodStatus, { label: string; cls: string }> = {
  PAGO: { label: 'Pago', cls: 'bg-emerald-50 text-emerald-700 border-emerald-200' },
  PARCIAL: { label: 'Parcial', cls: 'bg-amber-50 text-amber-700 border-amber-200' },
  EM_ABERTO: { label: 'Em aberto', cls: 'bg-rose-50 text-rose-700 border-rose-200' },
};

/** Resumo curto da composição: "Salário + comissão", "3 diárias + comissão"... */
const compositionLabel = (item: PayrollEmployeeSummary) => {
  const b = item.breakdown;
  const parts: string[] = [];
  if (b.fixedSalary > 0) parts.push('salário');
  if (b.workDaysCount > 0) parts.push(`${b.workDaysCount} diária${b.workDaysCount === 1 ? '' : 's'}`);
  if (b.totalCommission > 0) parts.push('comissões');
  if (b.additions > 0) parts.push('extras');
  if (b.discounts > 0 || b.advances > 0) parts.push('descontos/vales');
  return parts.length ? parts.join(' + ') : 'nada no período';
};

const StatCard = ({ label, value, sub, tone }: { label: string; value: string; sub?: string; tone: string }) => (
  <div className="bg-white rounded-2xl border border-[#eae1e1]/80 p-5 shadow-sm relative overflow-hidden">
    <div className={`absolute top-0 left-0 right-0 h-1 bg-gradient-to-r ${tone}`} />
    <p className="text-[10px] font-bold text-[#7a7074] uppercase tracking-wider mb-1">{label}</p>
    <p className="text-xl font-extrabold text-[#3b3036]">{value}</p>
    {sub && <p className="text-[11px] text-[#7a7074] mt-1">{sub}</p>}
  </div>
);

export const Payroll = () => {
  const initial = currentMonthRange();
  const [from, setFrom] = useState(initial.from);
  const [to, setTo] = useState(initial.to);
  const [summary, setSummary] = useState<PayrollSummary | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [expandedId, setExpandedId] = useState<number | null>(null);
  const [payNowId, setPayNowId] = useState<number | null>(null);
  const { error: showError } = useAlert();

  const load = useCallback(async () => {
    if (!from || !to || from > to) return;
    setIsLoading(true);
    try {
      setSummary(await payrollApi.getSummary(from, to));
    } catch (err) {
      await showError(getApiErrorMessage(err, 'Erro ao carregar a folha de pagamento'));
    } finally {
      setIsLoading(false);
    }
  }, [from, to, showError]);

  useEffect(() => {
    load();
  }, [load]);

  const month = asWholeMonth(from, to);
  const shiftMonth = (delta: number) => {
    const base = month ?? { year: Number(from.slice(0, 4)), monthIndex: Number(from.slice(5, 7)) - 1 };
    const range = monthRange(base.year, base.monthIndex + delta);
    setFrom(range.from);
    setTo(range.to);
  };

  const toggle = (employeeId: number) => setExpandedId((prev) => (prev === employeeId ? null : employeeId));
  const payNow = (employeeId: number) => {
    setExpandedId(employeeId);
    setPayNowId(employeeId);
  };
  const handlePayModalOpened = useCallback(() => setPayNowId(null), []);

  return (
    <div className="space-y-6 animate-fade-in-up">
      <div>
        <h2 className="font-heading text-2xl font-bold text-[#3b3036]">Folha de Pagamento</h2>
        <p className="text-xs text-[#7a7074] mt-1">
          Quanto cada colaboradora tem a receber, de onde vem cada valor e até quando ela já foi paga.
        </p>
      </div>

      {/* Período */}
      <div className="flex flex-wrap gap-4 items-end bg-white/80 backdrop-blur-md rounded-2xl border border-[#eae1e1]/80 p-5 shadow-sm">
        <div className="space-y-1">
          <span className={labelCls}>Mês</span>
          <div className="flex items-center gap-1">
            <button
              type="button"
              aria-label="Mês anterior"
              onClick={() => shiftMonth(-1)}
              className="p-2 rounded-xl border border-[#eae1e1] text-[#7a7074] hover:text-[#be8a83] hover:border-[#be8a83] cursor-pointer"
            >
              <ChevronLeft size={16} />
            </button>
            <span className="min-w-[150px] text-center text-sm font-semibold text-[#3b3036]">
              {month ? monthLabel(month.year, month.monthIndex) : 'Período personalizado'}
            </span>
            <button
              type="button"
              aria-label="Próximo mês"
              onClick={() => shiftMonth(1)}
              className="p-2 rounded-xl border border-[#eae1e1] text-[#7a7074] hover:text-[#be8a83] hover:border-[#be8a83] cursor-pointer"
            >
              <ChevronRight size={16} />
            </button>
          </div>
        </div>
        <div className="space-y-1">
          <label htmlFor="payroll-from" className={labelCls}>De</label>
          <input id="payroll-from" type="date" value={from} max={to} onChange={(e) => setFrom(e.target.value)} className={inputCls} />
        </div>
        <div className="space-y-1">
          <label htmlFor="payroll-to" className={labelCls}>Até</label>
          <input id="payroll-to" type="date" value={to} min={from} onChange={(e) => setTo(e.target.value)} className={inputCls} />
        </div>
        <p className="text-[11px] text-[#7a7074] max-w-xs">
          Use um período livre pra pagamento quinzenal ou semanal — o salário fixo é proporcional aos dias.
        </p>
      </div>

      {/* Indicadores */}
      {summary && (
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
          <StatCard
            label="A pagar no período"
            value={formatBRL(summary.totalNetPayable)}
            sub={`Custo total da folha: ${formatBRL(summary.totalSalonCost)}`}
            tone="from-indigo-400 to-indigo-500"
          />
          <StatCard label="Já pago (no período)" value={formatBRL(summary.totalPaidInPeriod)} tone="from-emerald-400 to-emerald-500" />
          <StatCard label="Falta pagar" value={formatBRL(summary.totalOutstanding)} tone="from-rose-400 to-rose-500" />
        </div>
      )}

      {/* Lista */}
      <div className="bg-white rounded-2xl border border-[#eae1e1]/80 shadow-sm overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-left border-collapse min-w-[860px]">
            <thead>
              <tr className="bg-[#fcf9f9]/50 border-b border-[#eae1e1]">
                {['Colaboradora', 'Regime', 'Composição', 'A pagar', 'Situação do período', 'Último pagamento', ''].map((h, i) => (
                  <th
                    key={i}
                    className={`px-5 py-4 text-xs font-semibold text-[#3b3036] font-heading uppercase tracking-wider ${h === 'A pagar' ? 'text-right' : ''}`}
                  >
                    {h}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y divide-[#eae1e1]/40">
              {isLoading && !summary ? (
                <tr>
                  <td colSpan={7} className="px-6 py-10 text-center text-sm text-[#7a7074]">Carregando...</td>
                </tr>
              ) : summary && summary.items.length > 0 ? (
                summary.items.map((item) => {
                  const expanded = expandedId === item.employeeId;
                  const status = STATUS_STYLE[item.periodStatus];
                  return (
                    <Fragment key={item.employeeId}>
                      <tr
                        className={`cursor-pointer transition-colors ${expanded ? 'bg-[#fdf6f5]' : 'hover:bg-[#fcf9f9]/20'}`}
                        onClick={() => toggle(item.employeeId)}
                      >
                        <td className="px-5 py-4 text-sm font-semibold text-[#3b3036]">
                          {item.employeeName}
                          {!item.active && (
                            <span className="ml-2 px-2 py-0.5 text-[10px] font-semibold rounded-full bg-gray-100 text-gray-500">inativa</span>
                          )}
                          <span className="block text-[11px] font-normal text-[#7a7074]">
                            {item.doneAppointmentsCount} atendimento{item.doneAppointmentsCount === 1 ? '' : 's'}
                          </span>
                        </td>
                        <td className="px-5 py-4 text-sm text-[#7a7074]">
                          {item.remunerationType ? remunerationLabel(item.remunerationType) : (
                            <span className="text-amber-600">sem remuneração cadastrada</span>
                          )}
                        </td>
                        <td className="px-5 py-4 text-sm text-[#7a7074]">{compositionLabel(item)}</td>
                        <td className="px-5 py-4 text-sm font-extrabold text-[#be8a83] text-right whitespace-nowrap">
                          {formatBRL(item.breakdown.netPayable)}
                          {item.outstanding > 0 && item.periodStatus !== 'EM_ABERTO' && (
                            <span className="block text-[11px] font-semibold text-rose-600">falta {formatBRL(item.outstanding)}</span>
                          )}
                        </td>
                        <td className="px-5 py-4 text-sm">
                          <span className={`px-2.5 py-1 text-xs font-semibold rounded-full border ${status.cls}`}>{status.label}</span>
                          {item.hasPaymentGaps && (
                            <span className="flex items-center gap-1 text-[11px] text-amber-700 mt-1">
                              <AlertTriangle size={11} /> tem período sem pagamento no meio
                            </span>
                          )}
                        </td>
                        <td className="px-5 py-4 text-sm whitespace-nowrap">
                          {item.lastPayment ? (
                            <>
                              <span className="font-semibold text-[#3b3036]">{formatShortDate(item.lastPayment.paidAt)}</span>
                              <span className="block text-[11px] text-[#7a7074]">
                                {formatBRL(item.lastPayment.amountPaid)} · ref. {formatShortDate(item.lastPayment.periodStart)} a{' '}
                                {formatShortDate(item.lastPayment.periodEnd)}
                              </span>
                            </>
                          ) : (
                            <span className="text-[11px] text-[#7a7074]">nenhum pagamento</span>
                          )}
                        </td>
                        <td className="px-5 py-4 text-right whitespace-nowrap" onClick={(e) => e.stopPropagation()}>
                          {item.periodStatus !== 'PAGO' && (
                            <button
                              type="button"
                              onClick={() => payNow(item.employeeId)}
                              className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-semibold rounded-lg bg-[#be8a83] text-white hover:bg-[#a6726b] cursor-pointer mr-2"
                            >
                              <Wallet size={13} /> Marcar como pago
                            </button>
                          )}
                          <button
                            type="button"
                            onClick={() => toggle(item.employeeId)}
                            aria-label={expanded ? 'Recolher' : 'Ver detalhes'}
                            className="p-1.5 rounded-lg text-[#7a7074] hover:bg-[#fdf6f5] cursor-pointer align-middle"
                          >
                            {expanded ? <ChevronUp size={16} /> : <ChevronDown size={16} />}
                          </button>
                        </td>
                      </tr>
                      {expanded && (
                        <tr>
                          <td colSpan={7} className="p-0">
                            <PayrollEmployeeDetail
                              employeeId={item.employeeId}
                              from={from}
                              to={to}
                              onChanged={load}
                              openPaymentOnLoad={payNowId === item.employeeId}
                              onPaymentModalHandled={handlePayModalOpened}
                            />
                          </td>
                        </tr>
                      )}
                    </Fragment>
                  );
                })
              ) : (
                <tr>
                  <td colSpan={7} className="px-6 py-10 text-center text-sm text-[#7a7074]">
                    Nenhuma colaboradora com remuneração no período.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </div>

      <div className="flex gap-2 rounded-xl border border-[#eae1e1] bg-white px-4 py-3 text-xs text-[#7a7074]">
        <Info size={15} className="shrink-0 text-[#be8a83]" />
        <span>
          A folha já é descontada do lucro no relatório financeiro. Por isso, <strong>não lance salário de
          colaboradora em Gastos Fixos nem como saída no Fluxo de Caixa</strong>: o mesmo dinheiro seria
          contado duas vezes.
        </span>
      </div>
    </div>
  );
};
