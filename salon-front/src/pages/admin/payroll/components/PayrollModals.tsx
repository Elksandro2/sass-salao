import { useEffect, useState, type ReactNode } from 'react';
import { createPortal } from 'react-dom';
import { X } from 'lucide-react';
import { CurrencyInput } from '../../../../components/CurrencyInput';
import {
  ADJUSTMENT_HINTS,
  ADJUSTMENT_LABELS,
  PAYMENT_METHODS,
  payrollApi,
  type PayrollAdjustment,
  type PayrollAdjustmentType,
  type PayrollPayment,
  type WorkDay,
} from '../services/payroll';
import { formatBRL, formatShortDate, todayIso } from '../payrollUtils';

const inputCls = 'input-premium';
const labelCls = 'label-premium';

interface ShellProps {
  title: string;
  subtitle?: string;
  onClose: () => void;
  children: ReactNode;
  footer: ReactNode;
}

// Renderizado direto no <body>: a página da folha tem animação de entrada (transform), e um
// ancestral com transform faz o position:fixed se prender à página em vez da tela — o modal
// aparecia fora do centro, exigindo scroll.
const ModalShell = ({ title, subtitle, onClose, children, footer }: ShellProps) =>
  createPortal(
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-[#261f23]/40 backdrop-blur-md">
      <div className="bg-white rounded-2xl shadow-xl w-full max-w-md border border-[#eae1e1]/85 overflow-hidden animate-scale-up max-h-[85vh] flex flex-col">
        <div className="flex items-start justify-between px-6 py-4 border-b border-[#eae1e1] bg-[#fcf9f9]/50 shrink-0">
          <div>
            <h3 className="font-heading text-lg font-bold text-[#3b3036]">{title}</h3>
            {subtitle && <p className="text-xs text-[#7a7074] mt-0.5">{subtitle}</p>}
          </div>
          <button
            type="button"
            onClick={onClose}
            aria-label="Fechar"
            className="p-1.5 text-gray-400 hover:text-gray-600 hover:bg-gray-100 rounded-lg transition-all cursor-pointer"
          >
            <X size={20} />
          </button>
        </div>
        <div className="p-6 space-y-4 overflow-y-auto flex-1 min-h-0">{children}</div>
        <div className="flex justify-end gap-2 px-6 py-4 border-t border-[#eae1e1] bg-[#fcf9f9]/50 shrink-0">
          {footer}
        </div>
      </div>
    </div>,
    document.body
  );

const CancelButton = ({ onClick }: { onClick: () => void }) => (
  <button
    type="button"
    onClick={onClick}
    className="px-5 py-2.5 border border-[#eae1e1] font-semibold text-sm text-[#3b3036] hover:bg-white hover:border-[#be8a83]/50 rounded-xl transition-all cursor-pointer"
  >
    Cancelar
  </button>
);

// ─── Pagamento ──────────────────────────────────────────────────────────────

export interface PaymentFormValues {
  periodStart: string;
  periodEnd: string;
  paidAt: string;
  paymentMethod: string;
  notes: string;
}

interface PaymentModalProps {
  employeeId: number;
  employeeName: string;
  /** Pagamento existente (edição) ou null (novo). */
  payment: PayrollPayment | null;
  defaultPeriodStart: string;
  defaultPeriodEnd: string;
  isSaving: boolean;
  onClose: () => void;
  onSubmit: (values: PaymentFormValues) => void;
}

/**
 * Marcar período como pago / editar pagamento. O valor não é digitado: é sempre o que o sistema
 * calcula pro período escolhido (prévia ao vivo). Pagar diferente se faz com um lançamento
 * (bônus/desconto) antes. As datas são livres — a API recusa dia que já está em outro pagamento.
 */
export const PaymentModal = ({
  employeeId,
  employeeName,
  payment,
  defaultPeriodStart,
  defaultPeriodEnd,
  isSaving,
  onClose,
  onSubmit,
}: PaymentModalProps) => {
  const [values, setValues] = useState<PaymentFormValues>({
    periodStart: payment?.periodStart ?? defaultPeriodStart,
    periodEnd: payment?.periodEnd ?? defaultPeriodEnd,
    paidAt: payment?.paidAt ?? todayIso(),
    paymentMethod: payment?.paymentMethod ?? 'PIX',
    notes: payment?.notes ?? '',
  });
  const [preview, setPreview] = useState<{
    amount: number | null;
    conflict: string | null;
    loading: boolean;
  }>({
    amount: null,
    conflict: null,
    loading: true,
  });
  const set = (patch: Partial<PaymentFormValues>) => setValues((prev) => ({ ...prev, ...patch }));

  const invalidOrder =
    !!values.periodStart && !!values.periodEnd && values.periodStart > values.periodEnd;

  useEffect(() => {
    if (!values.periodStart || !values.periodEnd || values.periodStart > values.periodEnd) {
      setPreview({ amount: null, conflict: null, loading: false });
      return;
    }
    let cancelled = false;
    setPreview((prev) => ({ ...prev, loading: true }));
    const timer = setTimeout(() => {
      payrollApi
        .previewPayment(employeeId, values.periodStart, values.periodEnd, payment?.id)
        .then(
          (p) =>
            !cancelled &&
            setPreview({ amount: p.calculatedAmount, conflict: p.conflict, loading: false })
        )
        .catch(() => !cancelled && setPreview({ amount: null, conflict: null, loading: false }));
    }, 300);
    return () => {
      cancelled = true;
      clearTimeout(timer);
    };
  }, [employeeId, values.periodStart, values.periodEnd, payment?.id]);

  const blocked = invalidOrder || !!preview.conflict || preview.loading || !values.paidAt;

  return (
    <ModalShell
      title={payment ? 'Editar pagamento' : 'Marcar período como pago'}
      subtitle={employeeName}
      onClose={onClose}
      footer={
        <>
          <CancelButton onClick={onClose} />
          <button
            type="button"
            disabled={isSaving || blocked}
            onClick={() => onSubmit(values)}
            className="btn-premium text-sm px-5 py-2.5 disabled:opacity-50"
          >
            {isSaving ? 'Salvando...' : payment ? 'Salvar' : 'Registrar pagamento'}
          </button>
        </>
      }
    >
      <div className="grid grid-cols-2 gap-3">
        <div>
          <label htmlFor="pay-start" className={labelCls}>
            De
          </label>
          <input
            id="pay-start"
            type="date"
            className={inputCls}
            value={values.periodStart}
            onChange={(e) => set({ periodStart: e.target.value })}
          />
        </div>
        <div>
          <label htmlFor="pay-end" className={labelCls}>
            Até
          </label>
          <input
            id="pay-end"
            type="date"
            className={inputCls}
            value={values.periodEnd}
            onChange={(e) => set({ periodEnd: e.target.value })}
          />
        </div>
      </div>
      {invalidOrder && <p className="text-xs text-rose-600">O "De" precisa ser antes do "Até".</p>}
      {preview.conflict && (
        <p className="text-xs text-rose-600 rounded-lg bg-rose-50 border border-rose-200 px-3 py-2">
          {preview.conflict}
        </p>
      )}

      <div className="rounded-xl border border-[#eae1e1] bg-[#fcf9f9]/50 px-4 py-3">
        <p className="text-[11px] font-semibold uppercase tracking-wider text-[#7a7074]">
          Valor do período
        </p>
        <p className="text-2xl font-extrabold text-[#3b3036]" data-testid="payment-preview-amount">
          {preview.loading ? '…' : preview.amount != null ? formatBRL(preview.amount) : '—'}
        </p>
        <p className="text-[11px] text-[#7a7074] mt-1">
          Calculado pelo sistema (salário, diárias, comissões e lançamentos do período). Pra pagar
          diferente, lance um bônus ou desconto antes de registrar.
        </p>
        {payment &&
          payment.amountPaid !== preview.amount &&
          preview.amount != null &&
          !preview.loading && (
            <p className="text-[11px] font-semibold text-amber-700 mt-1">
              Registrado hoje: {formatBRL(payment.amountPaid)} — ao salvar passa a ser{' '}
              {formatBRL(preview.amount)}.
            </p>
          )}
      </div>

      <div className="grid grid-cols-2 gap-3">
        <div>
          <label htmlFor="pay-date" className={labelCls}>
            Pago em
          </label>
          <input
            id="pay-date"
            type="date"
            className={inputCls}
            value={values.paidAt}
            onChange={(e) => set({ paidAt: e.target.value })}
          />
        </div>
        <div>
          <label htmlFor="pay-method" className={labelCls}>
            Forma
          </label>
          <select
            id="pay-method"
            className={inputCls}
            value={values.paymentMethod}
            onChange={(e) => set({ paymentMethod: e.target.value })}
          >
            {Object.entries(PAYMENT_METHODS).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </select>
        </div>
      </div>
      <div>
        <label htmlFor="pay-notes" className={labelCls}>
          Observação (opcional)
        </label>
        <textarea
          id="pay-notes"
          rows={2}
          maxLength={1000}
          className={`${inputCls} resize-none`}
          value={values.notes}
          onChange={(e) => set({ notes: e.target.value })}
          placeholder="Ex.: pago em duas vezes"
        />
      </div>
      {values.periodStart && values.periodEnd && !invalidOrder && (
        <p className="text-[11px] text-[#7a7074]">
          Cobre {formatShortDate(values.periodStart)} a {formatShortDate(values.periodEnd)}. Não
          gera lançamento no Fluxo de Caixa — a folha já sai do lucro no relatório.
        </p>
      )}
    </ModalShell>
  );
};

// ─── Acréscimo / abatimento ─────────────────────────────────────────────────

export interface AdjustmentFormValues {
  date: string;
  type: PayrollAdjustmentType;
  amount: string;
  description: string;
}

interface AdjustmentModalProps {
  employeeName: string;
  adjustment: PayrollAdjustment | null;
  defaultDate: string;
  isSaving: boolean;
  onClose: () => void;
  onSubmit: (values: AdjustmentFormValues) => void;
}

export const AdjustmentModal = ({
  employeeName,
  adjustment,
  defaultDate,
  isSaving,
  onClose,
  onSubmit,
}: AdjustmentModalProps) => {
  const [values, setValues] = useState<AdjustmentFormValues>({
    date: adjustment?.date ?? defaultDate,
    type: adjustment?.type ?? 'BONIFICACAO',
    amount: adjustment ? adjustment.amount.toFixed(2) : '',
    description: adjustment?.description ?? '',
  });
  const set = (patch: Partial<AdjustmentFormValues>) =>
    setValues((prev) => ({ ...prev, ...patch }));

  return (
    <ModalShell
      title={adjustment ? 'Editar lançamento' : 'Novo lançamento na folha'}
      subtitle={employeeName}
      onClose={onClose}
      footer={
        <>
          <CancelButton onClick={onClose} />
          <button
            type="button"
            disabled={isSaving}
            onClick={() => onSubmit(values)}
            className="btn-premium text-sm px-5 py-2.5 disabled:opacity-50"
          >
            {isSaving ? 'Salvando...' : 'Salvar'}
          </button>
        </>
      }
    >
      <div>
        <label htmlFor="adj-type" className={labelCls}>
          Tipo
        </label>
        <select
          id="adj-type"
          className={inputCls}
          value={values.type}
          onChange={(e) => set({ type: e.target.value as PayrollAdjustmentType })}
        >
          <optgroup label="Soma no pagamento">
            {(
              ['BONIFICACAO', 'DECIMO_TERCEIRO', 'FERIAS', 'HORA_EXTRA', 'OUTRO_ACRESCIMO'] as const
            ).map((t) => (
              <option key={t} value={t}>
                {ADJUSTMENT_LABELS[t]}
              </option>
            ))}
          </optgroup>
          <optgroup label="Abate do pagamento">
            <option value="DESCONTO">{ADJUSTMENT_LABELS.DESCONTO}</option>
            {/* Vale saiu das opções; só aparece pra editar um lançamento antigo de vale. */}
            {adjustment?.type === 'VALE' && <option value="VALE">{ADJUSTMENT_LABELS.VALE}</option>}
          </optgroup>
        </select>
        <p className="text-xs text-[#7a7074] mt-1">{ADJUSTMENT_HINTS[values.type]}</p>
      </div>
      <div className="grid grid-cols-2 gap-3">
        <div>
          <label htmlFor="adj-amount" className={labelCls}>
            Valor
          </label>
          <CurrencyInput
            id="adj-amount"
            className={inputCls}
            value={values.amount}
            onValueChange={(v) => set({ amount: v })}
          />
        </div>
        <div>
          <label htmlFor="adj-date" className={labelCls}>
            Data de referência
          </label>
          <input
            id="adj-date"
            type="date"
            className={inputCls}
            value={values.date}
            onChange={(e) => set({ date: e.target.value })}
          />
        </div>
      </div>
      <p className="text-xs text-[#7a7074] -mt-2">
        A data define em qual período da folha o lançamento entra.
      </p>
      <div>
        <label htmlFor="adj-desc" className={labelCls}>
          Descrição (opcional)
        </label>
        <input
          id="adj-desc"
          type="text"
          maxLength={500}
          className={inputCls}
          value={values.description}
          onChange={(e) => set({ description: e.target.value })}
          placeholder="Ex.: meta de vendas batida em outubro"
        />
      </div>
    </ModalShell>
  );
};

// ─── Dia trabalhado (diarista) ──────────────────────────────────────────────

export interface WorkDayFormValues {
  amount: string;
  justification: string;
}

interface WorkDayModalProps {
  employeeName: string;
  date: string;
  /** Dia já marcado (edição) ou null (marcar com valor personalizado). */
  workDay: WorkDay | null;
  defaultRate: number | null;
  isSaving: boolean;
  onClose: () => void;
  onSubmit: (values: WorkDayFormValues) => void;
  onRemove?: () => void;
}

export const WorkDayModal = ({
  employeeName,
  date,
  workDay,
  defaultRate,
  isSaving,
  onClose,
  onSubmit,
  onRemove,
}: WorkDayModalProps) => {
  const [values, setValues] = useState<WorkDayFormValues>({
    amount: (workDay?.amount ?? defaultRate ?? 0).toFixed(2),
    justification: workDay?.justification ?? '',
  });
  useEffect(() => {
    setValues({
      amount: (workDay?.amount ?? defaultRate ?? 0).toFixed(2),
      justification: workDay?.justification ?? '',
    });
  }, [workDay, defaultRate, date]);

  return (
    <ModalShell
      title={`Diária de ${formatShortDate(date)}`}
      subtitle={employeeName}
      onClose={onClose}
      footer={
        <>
          {workDay && onRemove && (
            <button
              type="button"
              disabled={isSaving}
              onClick={onRemove}
              className="mr-auto px-4 py-2.5 text-sm font-semibold text-rose-600 hover:bg-rose-50 rounded-xl transition-all cursor-pointer disabled:opacity-50"
            >
              Desmarcar dia
            </button>
          )}
          <CancelButton onClick={onClose} />
          <button
            type="button"
            disabled={isSaving}
            onClick={() => onSubmit(values)}
            className="btn-premium text-sm px-5 py-2.5 disabled:opacity-50"
          >
            {isSaving ? 'Salvando...' : 'Salvar'}
          </button>
        </>
      }
    >
      <div>
        <label htmlFor="wd-amount" className={labelCls}>
          Valor desta diária
        </label>
        <CurrencyInput
          id="wd-amount"
          className={inputCls}
          value={values.amount}
          onValueChange={(v) => setValues((prev) => ({ ...prev, amount: v }))}
        />
        <p className="text-xs text-[#7a7074] mt-1">
          Diária padrão cadastrada:{' '}
          {defaultRate != null ? formatBRL(defaultRate) : 'não cadastrada'}
        </p>
      </div>
      <div>
        <label htmlFor="wd-reason" className={labelCls}>
          Justificativa (opcional)
        </label>
        <textarea
          id="wd-reason"
          rows={2}
          maxLength={500}
          className={`${inputCls} resize-none`}
          value={values.justification}
          onChange={(e) => setValues((prev) => ({ ...prev, justification: e.target.value }))}
          placeholder="Ex.: meio período, feriado, ficou até mais tarde..."
        />
      </div>
    </ModalShell>
  );
};
