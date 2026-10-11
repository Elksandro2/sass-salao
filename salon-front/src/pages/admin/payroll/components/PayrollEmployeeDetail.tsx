import { useCallback, useEffect, useState, type ReactNode } from 'react';
import { AlertTriangle, CalendarCheck, Pencil, Plus, Trash2, Wallet } from 'lucide-react';
import { appointmentsApi, type AppointmentResponse } from '../../../appointments/services/appointments';
import { AppointmentDetailModal } from '../../appointments/components/AppointmentDetailModal';
import { cashFlowApi, type CashFlowRecord } from '../../cashflow/services/cashflow';
import { SaleEditModal } from '../../cashflow/components/SaleEditModal';
import {
  ADJUSTMENT_LABELS,
  PAYMENT_METHODS,
  payrollApi,
  type PayrollAdjustment,
  type PayrollEmployeeDetail as Detail,
  type PayrollPayment,
  type WorkDay,
} from '../services/payroll';
import { formatBRL, formatShortDate } from '../payrollUtils';
import { remunerationIsDaily } from '../../../../utils/remuneration';
import { useAlert } from '../../../../hooks/useAlert';
import { getApiErrorMessage } from '../../../../utils/apiError';
import {
  AdjustmentModal,
  PaymentModal,
  WorkDayModal,
  type AdjustmentFormValues,
  type PaymentFormValues,
  type WorkDayFormValues,
} from './PayrollModals';
import { WorkDaysCalendar } from './WorkDaysCalendar';

interface PayrollEmployeeDetailProps {
  employeeId: number;
  from: string;
  to: string;
  /** Algo mudou (diária, lançamento, pagamento) — o resumo da lista precisa recarregar. */
  onChanged: () => void;
  /** Pede pra abrir o modal de pagamento já aberto ao expandir (botão "Marcar como pago" da lista). */
  openPaymentOnLoad?: boolean;
  onPaymentModalHandled?: () => void;
}

const Section = ({ title, action, children }: { title: string; action?: ReactNode; children: ReactNode }) => (
  <section className="space-y-2">
    <div className="flex items-center justify-between gap-2">
      <h5 className="text-xs font-bold uppercase tracking-wider text-[#7a7074]">{title}</h5>
      {action}
    </div>
    {children}
  </section>
);

const SmallButton = ({ onClick, children, disabled }: { onClick: () => void; children: ReactNode; disabled?: boolean }) => (
  <button
    type="button"
    onClick={onClick}
    disabled={disabled}
    className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-semibold rounded-lg border border-[#be8a83]/40 text-[#be8a83] hover:bg-[#be8a83]/5 disabled:opacity-40 disabled:cursor-not-allowed cursor-pointer"
  >
    {children}
  </button>
);

const IconButton = ({ onClick, label, danger, children }: { onClick: () => void; label: string; danger?: boolean; children: ReactNode }) => (
  <button
    type="button"
    onClick={onClick}
    title={label}
    aria-label={label}
    className={`p-1.5 rounded-lg transition-all cursor-pointer ${danger ? 'text-gray-400 hover:text-rose-600 hover:bg-rose-50' : 'text-gray-400 hover:text-[#be8a83] hover:bg-[#fdf6f5]'}`}
  >
    {children}
  </button>
);

const Line = ({ label, value, negative, strong, hint }: { label: string; value: number; negative?: boolean; strong?: boolean; hint?: string }) => (
  <div className={`flex justify-between gap-4 text-sm ${strong ? 'font-bold pt-2 border-t border-[#eae1e1]' : ''}`}>
    <span className={strong ? 'text-[#3b3036]' : 'text-[#7a7074]'}>
      {label}
      {hint && <span className="block text-[11px] font-normal text-[#7a7074]/80">{hint}</span>}
    </span>
    <span className={negative ? 'text-rose-600' : strong ? 'text-[#be8a83]' : 'text-[#3b3036]'}>
      {negative ? '− ' : ''}
      {formatBRL(value)}
    </span>
  </div>
);

export const PayrollEmployeeDetail = ({
  employeeId,
  from,
  to,
  onChanged,
  openPaymentOnLoad,
  onPaymentModalHandled,
}: PayrollEmployeeDetailProps) => {
  const [detail, setDetail] = useState<Detail | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [busyDate, setBusyDate] = useState<string | null>(null);

  const [paymentModal, setPaymentModal] = useState<{ payment: PayrollPayment | null } | null>(null);
  const [adjustmentModal, setAdjustmentModal] = useState<{ adjustment: PayrollAdjustment | null } | null>(null);
  const [workDayModal, setWorkDayModal] = useState<{ date: string; workDay: WorkDay | null } | null>(null);
  // Mesmo modal de detalhes/edição da tela de Agendamentos. appointmentChanged: houve edição
  // enquanto estava aberto — ao fechar, a folha recarrega (comissão, diária, valores mudam).
  const [appointmentTarget, setAppointmentTarget] = useState<AppointmentResponse | null>(null);
  const [appointmentChanged, setAppointmentChanged] = useState(false);
  const [openingAppointmentId, setOpeningAppointmentId] = useState<number | null>(null);
  const [saleTarget, setSaleTarget] = useState<CashFlowRecord | null>(null);
  const [openingSaleId, setOpeningSaleId] = useState<number | null>(null);

  const { error: showError, confirm } = useAlert();

  const load = useCallback(async () => {
    setIsLoading(true);
    try {
      setDetail(await payrollApi.getEmployeeDetail(employeeId, from, to));
    } catch (err) {
      await showError(getApiErrorMessage(err, 'Erro ao carregar a folha da colaboradora'));
    } finally {
      setIsLoading(false);
    }
  }, [employeeId, from, to, showError]);

  useEffect(() => {
    load();
  }, [load]);

  useEffect(() => {
    if (openPaymentOnLoad && detail) {
      setPaymentModal({ payment: null });
      onPaymentModalHandled?.();
    }
  }, [openPaymentOnLoad, detail, onPaymentModalHandled]);

  const afterChange = async () => {
    await load();
    onChanged();
  };

  /** Roda uma ação de escrita com tratamento de erro padrão; devolve true se deu certo. */
  const run = async (action: () => Promise<unknown>, errorMsg: string) => {
    setIsSaving(true);
    try {
      await action();
      await afterChange();
      return true;
    } catch (err) {
      await showError(getApiErrorMessage(err, errorMsg));
      return false;
    } finally {
      setIsSaving(false);
    }
  };

  if (isLoading && !detail) {
    return <p className="px-6 py-6 text-sm text-[#7a7074]">Carregando folha...</p>;
  }
  if (!detail) return null;

  const { summary } = detail;
  const b = summary.breakdown;
  const name = summary.employeeName;
  const isDaily = remunerationIsDaily(summary.remunerationType);
  const defaultRate = summary.remunerationValue;
  const appointmentDates = detail.appointments.map((a) => a.date);

  // --- Diárias ---
  const markDay = async (date: string) => {
    if (defaultRate == null) {
      setWorkDayModal({ date, workDay: null });
      return;
    }
    setBusyDate(date);
    try {
      await payrollApi.createWorkDay({ employeeId, date });
      await afterChange();
    } catch (err) {
      await showError(getApiErrorMessage(err, 'Erro ao marcar o dia'));
    } finally {
      setBusyDate(null);
    }
  };

  const saveWorkDay = async (values: WorkDayFormValues) => {
    if (!workDayModal) return;
    const input = {
      employeeId,
      date: workDayModal.date,
      amount: values.amount ? Number(values.amount) : null,
      justification: values.justification.trim() || null,
    };
    const ok = await run(
      () => (workDayModal.workDay?.id != null
        ? payrollApi.updateWorkDay(workDayModal.workDay.id, input)
        : payrollApi.createWorkDay(input)),
      'Erro ao salvar a diária'
    );
    if (ok) setWorkDayModal(null);
  };

  const removeWorkDay = async () => {
    if (!workDayModal?.workDay) return;
    const { date } = workDayModal;
    const ok = await run(() => payrollApi.unmarkWorkDay(employeeId, date), 'Erro ao desmarcar o dia');
    if (ok) setWorkDayModal(null);
  };

  const markAppointmentDays = () =>
    run(
      () => payrollApi.createWorkDaysBulk(employeeId, detail.unmarkedAppointmentDates),
      'Erro ao marcar os dias com atendimento'
    );

  // --- Lançamentos ---
  const saveAdjustment = async (values: AdjustmentFormValues) => {
    if (!adjustmentModal) return;
    if (!values.amount || Number(values.amount) <= 0) {
      await showError('Informe um valor maior que zero.');
      return;
    }
    const input = {
      employeeId,
      date: values.date,
      type: values.type,
      amount: Number(values.amount),
      description: values.description.trim() || null,
    };
    const ok = await run(
      () => (adjustmentModal.adjustment
        ? payrollApi.updateAdjustment(adjustmentModal.adjustment.id, input)
        : payrollApi.createAdjustment(input)),
      'Erro ao salvar o lançamento'
    );
    if (ok) setAdjustmentModal(null);
  };

  const deleteAdjustment = (a: PayrollAdjustment) =>
    confirm(
      `Excluir o lançamento "${ADJUSTMENT_LABELS[a.type]}" de ${formatBRL(a.amount)}?`,
      async () => {
        await run(() => payrollApi.deleteAdjustment(a.id), 'Erro ao excluir o lançamento');
      },
      { isDangerous: true, confirmText: 'Excluir' }
    );

  // --- Pagamentos ---
  const savePayment = async (values: PaymentFormValues) => {
    if (!paymentModal) return;
    if (!values.periodStart || !values.periodEnd || !values.paidAt) {
      await showError('Preencha o período e a data do pagamento.');
      return;
    }
    const input = {
      employeeId,
      periodStart: values.periodStart,
      periodEnd: values.periodEnd,
      amountPaid: Number(values.amountPaid || 0),
      paidAt: values.paidAt,
      paymentMethod: values.paymentMethod || null,
      notes: values.notes.trim() || null,
    };
    const ok = await run(
      () => (paymentModal.payment
        ? payrollApi.updatePayment(paymentModal.payment.id, input)
        : payrollApi.createPayment(input)),
      'Erro ao registrar o pagamento'
    );
    if (ok) setPaymentModal(null);
  };

  const deletePayment = (p: PayrollPayment) =>
    confirm(
      `Excluir o pagamento de ${formatShortDate(p.periodStart)} a ${formatShortDate(p.periodEnd)}? O período volta a ficar em aberto.`,
      async () => {
        await run(() => payrollApi.deletePayment(p.id), 'Erro ao excluir o pagamento');
      },
      { isDangerous: true, confirmText: 'Excluir' }
    );

  const openAppointment = async (appointmentId: number) => {
    setOpeningAppointmentId(appointmentId);
    try {
      setAppointmentChanged(false);
      setAppointmentTarget(await appointmentsApi.findById(appointmentId));
    } catch (err) {
      await showError(getApiErrorMessage(err, 'Erro ao abrir o agendamento'));
    } finally {
      setOpeningAppointmentId(null);
    }
  };

  const closeAppointment = async () => {
    setAppointmentTarget(null);
    if (appointmentChanged) await afterChange();
  };

  const openSale = async (cashFlowId: number) => {
    setOpeningSaleId(cashFlowId);
    try {
      setSaleTarget(await cashFlowApi.findById(cashFlowId));
    } catch (err) {
      await showError(getApiErrorMessage(err, 'Erro ao abrir a venda'));
    } finally {
      setOpeningSaleId(null);
    }
  };

  const changedPayments = detail.payments.filter((p) => p.changedSincePayment);

  return (
    <div className="px-4 sm:px-6 py-5 bg-[#fcf9f9]/50 border-t border-[#eae1e1] space-y-6">
      {changedPayments.length > 0 && (
        <div className="flex gap-2 rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-xs text-amber-800">
          <AlertTriangle size={16} className="shrink-0" />
          <span>
            {changedPayments.length === 1 ? 'Um pagamento já registrado mudou' : `${changedPayments.length} pagamentos já registrados mudaram`}{' '}
            de valor depois de pago (algum atendimento, diária ou lançamento do período foi editado). Confira
            em "Pagamentos" — pode haver diferença a acertar.
          </span>
        </div>
      )}

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Composição */}
        <Section title="De onde vem o valor">
          <div className="rounded-xl border border-[#eae1e1] bg-white p-4 space-y-1.5">
            {b.fixedSalary > 0 && (
              <Line label="Salário fixo" value={b.fixedSalary} hint="Proporcional aos dias do período" />
            )}
            {(isDaily || b.workDaysCount > 0) && (
              <Line label={`Diárias (${b.workDaysCount} dia${b.workDaysCount === 1 ? '' : 's'})`} value={b.dailyTotal} />
            )}
            {b.serviceCommission > 0 && <Line label="Comissão sobre serviços" value={b.serviceCommission} />}
            {b.appointmentProductCommission > 0 && (
              <Line label="Comissão sobre produtos (em atendimentos)" value={b.appointmentProductCommission} />
            )}
            {b.saleProductCommission > 0 && (
              <Line label="Comissão sobre vendas avulsas" value={b.saleProductCommission} />
            )}
            {b.additions > 0 && <Line label="Bonificações e acréscimos" value={b.additions} />}
            {b.discounts > 0 && <Line label="Descontos" value={b.discounts} negative />}
            <Line label="Custo da folha no período" value={b.salonCost} strong />
            {b.advances > 0 && <Line label="Vales já entregues" value={b.advances} negative />}
            <Line label="Valor a pagar" value={b.netPayable} strong />
            {summary.paidInPeriod > 0 && (
              <>
                <Line label="Pagamentos registrados no período" value={summary.paidInPeriod} negative />
                <Line label="Falta pagar" value={summary.outstanding} strong />
              </>
            )}
          </div>
        </Section>

        {/* Diárias */}
        {isDaily ? (
          <Section
            title="Dias trabalhados"
            action={
              detail.unmarkedAppointmentDates.length > 0 && (
                <SmallButton onClick={markAppointmentDays} disabled={isSaving || defaultRate == null}>
                  <CalendarCheck size={14} />
                  Remarcar {detail.unmarkedAppointmentDates.length} dia(s) desmarcado(s)
                </SmallButton>
              )
            }
          >
            {defaultRate == null && (
              <p className="text-xs text-amber-700">
                Ela não tem diária padrão cadastrada (Equipe). Cada dia vai pedir o valor.
              </p>
            )}
            <WorkDaysCalendar
              periodStart={detail.periodStart}
              periodEnd={detail.periodEnd}
              workDays={detail.workDays}
              appointmentDates={appointmentDates}
              unmarkedDates={detail.unmarkedAppointmentDates}
              defaultRate={defaultRate}
              busyDate={busyDate}
              onMark={markDay}
              onOpenDay={(day) => setWorkDayModal({ date: day.date, workDay: day })}
            />
            <p className="text-[11px] text-[#7a7074]">
              Dia com atendimento já conta como diária sozinho. Clique num dia marcado pra mudar o valor
              (com justificativa) ou desmarcar; clique num dia vazio pra marcar um dia trabalhado sem
              atendimento.
            </p>
          </Section>
        ) : (
          <Section
            title="Lançamentos (bônus, 13º, vale...)"
            action={
              <SmallButton onClick={() => setAdjustmentModal({ adjustment: null })}>
                <Plus size={14} /> Adicionar
              </SmallButton>
            }
          >
            <AdjustmentList
              items={detail.adjustments}
              onEdit={(a) => setAdjustmentModal({ adjustment: a })}
              onDelete={deleteAdjustment}
            />
          </Section>
        )}
      </div>

      {isDaily && (
        <Section
          title="Lançamentos (bônus, 13º, vale...)"
          action={
            <SmallButton onClick={() => setAdjustmentModal({ adjustment: null })}>
              <Plus size={14} /> Adicionar
            </SmallButton>
          }
        >
          <AdjustmentList
            items={detail.adjustments}
            onEdit={(a) => setAdjustmentModal({ adjustment: a })}
            onDelete={deleteAdjustment}
          />
        </Section>
      )}

      {/* Atendimentos */}
      <Section title={`Atendimentos concluídos (${detail.appointments.length})`}>
        {detail.appointments.length === 0 ? (
          <p className="text-sm text-[#7a7074]">Nenhum atendimento concluído no período.</p>
        ) : (
          <div className="overflow-x-auto rounded-xl border border-[#eae1e1] bg-white">
            <table className="w-full text-left text-sm min-w-[720px]">
              <thead>
                <tr className="border-b border-[#eae1e1] text-[11px] uppercase tracking-wider text-[#7a7074]">
                  <th className="px-3 py-2 font-semibold">Data</th>
                  <th className="px-3 py-2 font-semibold">Cliente</th>
                  <th className="px-3 py-2 font-semibold">Serviços</th>
                  <th className="px-3 py-2 font-semibold text-right">Serviços</th>
                  <th className="px-3 py-2 font-semibold text-right">Produtos</th>
                  <th className="px-3 py-2 font-semibold text-right">Comissão</th>
                  <th className="px-3 py-2" />
                </tr>
              </thead>
              <tbody className="divide-y divide-[#eae1e1]/50">
                {detail.appointments.map((a) => (
                  <tr
                    key={a.appointmentId}
                    onClick={() => openAppointment(a.appointmentId)}
                    className="cursor-pointer hover:bg-[#fdf6f5] transition-colors"
                    title="Abrir e editar o agendamento"
                  >
                    <td className="px-3 py-2 text-[#7a7074] whitespace-nowrap">{formatShortDate(a.date)}</td>
                    <td className="px-3 py-2 text-[#3b3036]">{a.clientName ?? '—'}</td>
                    <td className="px-3 py-2 text-[#7a7074]">{a.serviceNames}</td>
                    <td className="px-3 py-2 text-right text-[#3b3036]">{formatBRL(a.servicesValue)}</td>
                    <td className="px-3 py-2 text-right text-[#3b3036]">{a.productsValue > 0 ? formatBRL(a.productsValue) : '—'}</td>
                    <td className="px-3 py-2 text-right font-semibold text-[#be8a83] whitespace-nowrap">
                      {formatBRL(a.serviceCommission + a.productCommission)}
                      {a.commissionManual && (
                        <span className="block text-[10px] font-normal text-amber-600">valor manual</span>
                      )}
                    </td>
                    <td className="px-3 py-2 text-right">
                      <span className="inline-flex items-center gap-1 text-xs font-semibold text-[#be8a83]">
                        <Pencil size={12} />
                        {openingAppointmentId === a.appointmentId ? 'Abrindo...' : 'Abrir'}
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Section>

      {/* Vendas avulsas */}
      {detail.productSales.length > 0 && (
        <Section title={`Vendas avulsas de produto (${detail.productSales.length})`}>
          <div className="overflow-x-auto rounded-xl border border-[#eae1e1] bg-white">
            <table className="w-full text-left text-sm min-w-[560px]">
              <thead>
                <tr className="border-b border-[#eae1e1] text-[11px] uppercase tracking-wider text-[#7a7074]">
                  <th className="px-3 py-2 font-semibold">Data</th>
                  <th className="px-3 py-2 font-semibold">Venda</th>
                  <th className="px-3 py-2 font-semibold text-right">Valor</th>
                  <th className="px-3 py-2 font-semibold text-right">Comissão</th>
                  <th className="px-3 py-2" />
                </tr>
              </thead>
              <tbody className="divide-y divide-[#eae1e1]/50">
                {detail.productSales.map((s) => (
                  <tr
                    key={s.cashFlowId}
                    onClick={() => openSale(s.cashFlowId)}
                    className="cursor-pointer hover:bg-[#fdf6f5] transition-colors"
                    title="Abrir e editar a venda"
                  >
                    <td className="px-3 py-2 text-[#7a7074] whitespace-nowrap">{formatShortDate(s.date)}</td>
                    <td className="px-3 py-2 text-[#3b3036]">{s.description}</td>
                    <td className="px-3 py-2 text-right text-[#3b3036]">{formatBRL(s.amount)}</td>
                    <td className="px-3 py-2 text-right font-semibold text-[#be8a83]">{formatBRL(s.commission)}</td>
                    <td className="px-3 py-2 text-right">
                      <span className="inline-flex items-center gap-1 text-xs font-semibold text-[#be8a83]">
                        <Pencil size={12} />
                        {openingSaleId === s.cashFlowId ? 'Abrindo...' : 'Abrir'}
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Section>
      )}

      {/* Pagamentos */}
      <Section
        title="Pagamentos registrados"
        action={
          <SmallButton onClick={() => setPaymentModal({ payment: null })}>
            <Wallet size={14} /> Marcar período como pago
          </SmallButton>
        }
      >
        {detail.payments.length === 0 ? (
          <p className="text-sm text-[#7a7074]">Nenhum pagamento registrado ainda.</p>
        ) : (
          <div className="overflow-x-auto rounded-xl border border-[#eae1e1] bg-white">
            <table className="w-full text-left text-sm min-w-[640px]">
              <thead>
                <tr className="border-b border-[#eae1e1] text-[11px] uppercase tracking-wider text-[#7a7074]">
                  <th className="px-3 py-2 font-semibold">Período</th>
                  <th className="px-3 py-2 font-semibold">Pago em</th>
                  <th className="px-3 py-2 font-semibold text-right">Valor pago</th>
                  <th className="px-3 py-2 font-semibold text-right">Calculado</th>
                  <th className="px-3 py-2" />
                </tr>
              </thead>
              <tbody className="divide-y divide-[#eae1e1]/50">
                {detail.payments.map((p) => (
                  <tr key={p.id} className={p.changedSincePayment ? 'bg-amber-50/60' : undefined}>
                    <td className="px-3 py-2 text-[#3b3036] whitespace-nowrap">
                      {formatShortDate(p.periodStart)} a {formatShortDate(p.periodEnd)}
                      {p.notes && <span className="block text-[11px] text-[#7a7074]">{p.notes}</span>}
                    </td>
                    <td className="px-3 py-2 text-[#7a7074] whitespace-nowrap">
                      {formatShortDate(p.paidAt)}
                      {p.paymentMethod && ` · ${PAYMENT_METHODS[p.paymentMethod] ?? p.paymentMethod}`}
                    </td>
                    <td className="px-3 py-2 text-right font-semibold text-[#3b3036]">{formatBRL(p.amountPaid)}</td>
                    <td className="px-3 py-2 text-right text-[#7a7074] whitespace-nowrap">
                      {formatBRL(p.calculatedAmount)}
                      {p.changedSincePayment && p.recalculatedAmount != null && (
                        <span className="block text-[11px] font-semibold text-amber-700">
                          hoje: {formatBRL(p.recalculatedAmount)}
                        </span>
                      )}
                    </td>
                    <td className="px-3 py-2 text-right whitespace-nowrap">
                      <IconButton label="Editar pagamento" onClick={() => setPaymentModal({ payment: p })}>
                        <Pencil size={14} />
                      </IconButton>
                      <IconButton label="Excluir pagamento" danger onClick={() => deletePayment(p)}>
                        <Trash2 size={14} />
                      </IconButton>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Section>

      <SaleEditModal
        sale={saleTarget}
        onClose={() => setSaleTarget(null)}
        onSaved={async () => {
          setSaleTarget(null);
          await afterChange();
        }}
      />

      <AppointmentDetailModal
        appointment={appointmentTarget}
        onClose={closeAppointment}
        onNotesSaved={(updated) => {
          setAppointmentTarget(updated);
          setAppointmentChanged(true);
        }}
      />

      {paymentModal && (
        <PaymentModal
          employeeName={name}
          payment={paymentModal.payment}
          defaultPeriodStart={from}
          defaultPeriodEnd={to}
          suggestedAmount={summary.outstanding > 0 ? summary.outstanding : b.netPayable}
          isSaving={isSaving}
          onClose={() => setPaymentModal(null)}
          onSubmit={savePayment}
        />
      )}
      {adjustmentModal && (
        <AdjustmentModal
          employeeName={name}
          adjustment={adjustmentModal.adjustment}
          defaultDate={to}
          isSaving={isSaving}
          onClose={() => setAdjustmentModal(null)}
          onSubmit={saveAdjustment}
        />
      )}
      {workDayModal && (
        <WorkDayModal
          employeeName={name}
          date={workDayModal.date}
          workDay={workDayModal.workDay}
          defaultRate={defaultRate}
          isSaving={isSaving}
          onClose={() => setWorkDayModal(null)}
          onSubmit={saveWorkDay}
          onRemove={removeWorkDay}
        />
      )}
    </div>
  );
};

const AdjustmentList = ({
  items,
  onEdit,
  onDelete,
}: {
  items: PayrollAdjustment[];
  onEdit: (a: PayrollAdjustment) => void;
  onDelete: (a: PayrollAdjustment) => void;
}) =>
  items.length === 0 ? (
    <p className="text-sm text-[#7a7074]">Nenhum lançamento no período.</p>
  ) : (
    <ul className="rounded-xl border border-[#eae1e1] bg-white divide-y divide-[#eae1e1]/50">
      {items.map((a) => (
        <li key={a.id} className="flex items-center gap-3 px-3 py-2 text-sm">
          <div className="flex-1 min-w-0">
            <p className="font-semibold text-[#3b3036]">{ADJUSTMENT_LABELS[a.type]}</p>
            <p className="text-[11px] text-[#7a7074] truncate">
              {formatShortDate(a.date)}
              {a.description ? ` · ${a.description}` : ''}
            </p>
          </div>
          <span className={`font-semibold whitespace-nowrap ${a.addition ? 'text-emerald-600' : 'text-rose-600'}`}>
            {a.addition ? '+ ' : '− '}
            {formatBRL(a.amount)}
          </span>
          <IconButton label="Editar lançamento" onClick={() => onEdit(a)}>
            <Pencil size={14} />
          </IconButton>
          <IconButton label="Excluir lançamento" danger onClick={() => onDelete(a)}>
            <Trash2 size={14} />
          </IconButton>
        </li>
      ))}
    </ul>
  );
