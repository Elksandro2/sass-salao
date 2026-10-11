import { useCallback, useEffect, useState } from 'react';
import { createPortal } from 'react-dom';
import { AlertTriangle, Pencil, Trash2, X } from 'lucide-react';
import { PAYMENT_METHODS, payrollApi, type PayrollPayment } from '../services/payroll';
import { employeesApi, type EmployeeData } from '../../employees/services/employees';
import {
  currentMonthRange,
  formatBRL,
  formatShortDate,
  describePayment,
  paymentSpan,
  salaryMonthLabel,
} from '../payrollUtils';
import { PaymentModal, type PaymentFormValues } from './PayrollModals';
import { PayrollEmployeeDetail } from './PayrollEmployeeDetail';
import { useAlert } from '../../../../hooks/useAlert';
import { getApiErrorMessage } from '../../../../utils/apiError';

const inputCls = 'input-premium';
const labelCls = 'label-premium';

/**
 * Pagamentos da folha já registrados — como o Fluxo de Caixa, mas só da folha: filtra por
 * período coberto e colaboradora, edita (datas, forma, observação) e exclui. Clicar numa linha
 * abre a folha completa daquele período, onde dá pra corrigir atendimentos, vendas, diárias e
 * lançamentos.
 */
export const PaymentsList = () => {
  const initial = currentMonthRange();
  const [from, setFrom] = useState(initial.from);
  const [to, setTo] = useState(initial.to);
  const [employeeId, setEmployeeId] = useState('');
  const [employees, setEmployees] = useState<EmployeeData[]>([]);
  const [payments, setPayments] = useState<PayrollPayment[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [editTarget, setEditTarget] = useState<PayrollPayment | null>(null);
  const [detailTarget, setDetailTarget] = useState<PayrollPayment | null>(null);
  const { error: showError, confirm } = useAlert();

  useEffect(() => {
    employeesApi
      .findAll({}, 0, 1000)
      .then((page) => setEmployees(page.content))
      .catch(() => setEmployees([]));
  }, []);

  const load = useCallback(async () => {
    if (from && to && from > to) return;
    setIsLoading(true);
    try {
      setPayments(
        await payrollApi.listPayments({
          from: from || undefined,
          to: to || undefined,
          employeeId: employeeId ? Number(employeeId) : undefined,
        })
      );
    } catch (err) {
      await showError(getApiErrorMessage(err, 'Erro ao carregar os pagamentos'));
    } finally {
      setIsLoading(false);
    }
  }, [from, to, employeeId, showError]);

  useEffect(() => {
    load();
  }, [load]);

  const saveEdit = async (values: PaymentFormValues) => {
    if (!editTarget) return;
    setIsSaving(true);
    try {
      await payrollApi.updatePayment(editTarget.id, {
        employeeId: editTarget.employeeId,
        periodStart: values.periodStart,
        periodEnd: values.periodEnd,
        salaryMonths: values.salaryMonths,
        paidAt: values.paidAt,
        paymentMethod: values.paymentMethod || null,
        notes: values.notes.trim() || null,
      });
      setEditTarget(null);
      await load();
    } catch (err) {
      await showError(getApiErrorMessage(err, 'Erro ao salvar o pagamento'));
    } finally {
      setIsSaving(false);
    }
  };

  const remove = (p: PayrollPayment) =>
    confirm(
      `Excluir o pagamento de ${p.employeeName} (${describePayment(p)})? Isso volta a ficar em aberto.`,
      async () => {
        try {
          await payrollApi.deletePayment(p.id);
          await load();
        } catch (err) {
          await showError(getApiErrorMessage(err, 'Erro ao excluir o pagamento'));
        }
      },
      { isDangerous: true, confirmText: 'Excluir' }
    );

  const total = payments.reduce((sum, p) => sum + p.amountPaid, 0);

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap gap-4 items-end bg-white rounded-2xl border border-[#eae1e1]/80 p-5 shadow-sm">
        <div className="space-y-1">
          <label htmlFor="payments-from" className={labelCls}>
            Período coberto — de
          </label>
          <input
            id="payments-from"
            type="date"
            value={from}
            max={to || undefined}
            onChange={(e) => setFrom(e.target.value)}
            className={inputCls}
          />
        </div>
        <div className="space-y-1">
          <label htmlFor="payments-to" className={labelCls}>
            até
          </label>
          <input
            id="payments-to"
            type="date"
            value={to}
            min={from || undefined}
            onChange={(e) => setTo(e.target.value)}
            className={inputCls}
          />
        </div>
        <div className="space-y-1">
          <label htmlFor="payments-employee" className={labelCls}>
            Colaboradora
          </label>
          <select
            id="payments-employee"
            value={employeeId}
            onChange={(e) => setEmployeeId(e.target.value)}
            className={inputCls}
          >
            <option value="">Todas</option>
            {employees.map((e) => (
              <option key={e.id} value={e.id}>
                {e.name}
              </option>
            ))}
          </select>
        </div>
        <button
          type="button"
          onClick={() => {
            setFrom('');
            setTo('');
          }}
          className="px-4 py-2.5 border border-[#eae1e1] text-sm font-semibold text-[#3b3036] hover:border-[#be8a83] rounded-xl cursor-pointer"
        >
          Todos os períodos
        </button>
        <p className="ml-auto text-sm text-[#7a7074]">
          Total: <span className="font-bold text-[#3b3036]">{formatBRL(total)}</span>
        </p>
      </div>

      <div className="bg-white rounded-2xl border border-[#eae1e1]/80 shadow-sm overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-left border-collapse min-w-[760px]">
            <thead>
              <tr className="bg-[#fcf9f9]/50 border-b border-[#eae1e1]">
                {['Colaboradora', 'Período coberto', 'Valor', 'Pago em', ''].map((h, i) => (
                  <th
                    key={i}
                    className={`px-5 py-4 text-xs font-semibold text-[#3b3036] font-heading uppercase tracking-wider ${h === 'Valor' ? 'text-right' : ''}`}
                  >
                    {h}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y divide-[#eae1e1]/40">
              {isLoading ? (
                <tr>
                  <td colSpan={5} className="px-6 py-10 text-center text-sm text-[#7a7074]">
                    Carregando...
                  </td>
                </tr>
              ) : payments.length === 0 ? (
                <tr>
                  <td colSpan={5} className="px-6 py-10 text-center text-sm text-[#7a7074]">
                    Nenhum pagamento registrado nesse filtro.
                  </td>
                </tr>
              ) : (
                payments.map((p) => (
                  <tr
                    key={p.id}
                    onClick={() => setDetailTarget(p)}
                    className="cursor-pointer hover:bg-[#fdf6f5] transition-colors"
                    title="Abrir a folha deste período"
                  >
                    <td className="px-5 py-4 text-sm font-semibold text-[#3b3036]">
                      {p.employeeName}
                    </td>
                    <td className="px-5 py-4 text-sm text-[#3b3036] whitespace-nowrap">
                      {p.periodStart && p.periodEnd ? (
                        `${formatShortDate(p.periodStart)} a ${formatShortDate(p.periodEnd)}`
                      ) : (
                        <span className="text-[#7a7074]">só salário</span>
                      )}
                      {p.salaryMonths.map((s) => (
                        <span key={s.month} className="block text-[11px] text-[#7a7074]">
                          Salário de {salaryMonthLabel(s.month)} · {formatBRL(s.amount)}
                        </span>
                      ))}
                      {p.notes && (
                        <span className="block text-[11px] text-[#7a7074]">{p.notes}</span>
                      )}
                    </td>
                    <td className="px-5 py-4 text-sm text-right whitespace-nowrap">
                      <span className="font-bold text-[#be8a83]">{formatBRL(p.amountPaid)}</span>
                      {p.changedSincePayment && p.recalculatedAmount != null && (
                        <span className="flex items-center justify-end gap-1 text-[11px] font-semibold text-amber-700">
                          <AlertTriangle size={11} /> mudou depois: hoje{' '}
                          {formatBRL(p.recalculatedAmount)}
                        </span>
                      )}
                    </td>
                    <td className="px-5 py-4 text-sm text-[#7a7074] whitespace-nowrap">
                      {formatShortDate(p.paidAt)}
                      {p.paymentMethod &&
                        ` · ${PAYMENT_METHODS[p.paymentMethod] ?? p.paymentMethod}`}
                    </td>
                    <td
                      className="px-5 py-4 text-right whitespace-nowrap"
                      onClick={(e) => e.stopPropagation()}
                    >
                      <button
                        type="button"
                        onClick={() => setEditTarget(p)}
                        title="Editar pagamento"
                        aria-label="Editar pagamento"
                        className="p-2 text-indigo-600 hover:bg-indigo-50 border border-[#eae1e1] hover:border-indigo-200 rounded-xl transition-all cursor-pointer mr-2"
                      >
                        <Pencil size={15} />
                      </button>
                      <button
                        type="button"
                        onClick={() => remove(p)}
                        title="Excluir pagamento"
                        aria-label="Excluir pagamento"
                        className="p-2 text-rose-600 hover:bg-rose-50 border border-[#eae1e1] hover:border-rose-200 rounded-xl transition-all cursor-pointer"
                      >
                        <Trash2 size={15} />
                      </button>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>

      {editTarget && (
        <PaymentModal
          employeeId={editTarget.employeeId}
          employeeName={editTarget.employeeName}
          payment={editTarget}
          isSaving={isSaving}
          onClose={() => setEditTarget(null)}
          onSubmit={saveEdit}
        />
      )}

      {detailTarget &&
        createPortal(
          <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-[#261f23]/40 backdrop-blur-md">
            <div className="bg-white rounded-2xl shadow-xl w-full max-w-5xl border border-[#eae1e1]/85 overflow-hidden animate-scale-up max-h-[90vh] flex flex-col">
              <div className="flex items-start justify-between px-6 py-4 border-b border-[#eae1e1] bg-[#fcf9f9]/50 shrink-0">
                <div>
                  <h3 className="font-heading text-lg font-bold text-[#3b3036]">
                    {detailTarget.employeeName}
                  </h3>
                  <p className="text-xs text-[#7a7074] mt-0.5">
                    Folha de {formatShortDate(paymentSpan(detailTarget).from)} a{' '}
                    {formatShortDate(paymentSpan(detailTarget).to)}
                  </p>
                </div>
                <button
                  type="button"
                  onClick={() => {
                    setDetailTarget(null);
                    load();
                  }}
                  aria-label="Fechar"
                  className="p-1.5 text-gray-400 hover:text-gray-600 hover:bg-gray-100 rounded-lg transition-all cursor-pointer"
                >
                  <X size={20} />
                </button>
              </div>
              <div className="overflow-y-auto flex-1 min-h-0">
                <PayrollEmployeeDetail
                  employeeId={detailTarget.employeeId}
                  from={paymentSpan(detailTarget).from}
                  to={paymentSpan(detailTarget).to}
                  onChanged={load}
                />
              </div>
            </div>
          </div>,
          document.body
        )}
    </div>
  );
};
