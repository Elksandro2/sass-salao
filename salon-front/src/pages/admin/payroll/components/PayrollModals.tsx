import { useEffect, useState, type ReactNode } from 'react';
import { createPortal } from 'react-dom';
import { X } from 'lucide-react';
import { CurrencyInput } from '../../../../components/CurrencyInput';
import {
  ADJUSTMENT_HINTS,
  ADJUSTMENT_LABELS,
  PAYMENT_METHODS,
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
const ModalShell = ({ title, subtitle, onClose, children, footer }: ShellProps) => createPortal(
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
  amountPaid: string;
  paidAt: string;
  paymentMethod: string;
  notes: string;
}

interface PaymentModalProps {
  employeeName: string;
  /** Pagamento existente (edição) ou null (novo). */
  payment: PayrollPayment | null;
  defaultPeriodStart: string;
  defaultPeriodEnd: string;
  /** Valor sugerido pra um pagamento novo (o que falta pagar no período). */
  suggestedAmount: number;
  isSaving: boolean;
  onClose: () => void;
  onSubmit: (values: PaymentFormValues) => void;
}

export const PaymentModal = ({
  employeeName,
  payment,
  defaultPeriodStart,
  defaultPeriodEnd,
  suggestedAmount,
  isSaving,
  onClose,
  onSubmit,
}: PaymentModalProps) => {
  const [values, setValues] = useState<PaymentFormValues>({
    periodStart: payment?.periodStart ?? defaultPeriodStart,
    periodEnd: payment?.periodEnd ?? defaultPeriodEnd,
    amountPaid: (payment?.amountPaid ?? suggestedAmount).toFixed(2),
    paidAt: payment?.paidAt ?? todayIso(),
    paymentMethod: payment?.paymentMethod ?? 'PIX',
    notes: payment?.notes ?? '',
  });
  const set = (patch: Partial<PaymentFormValues>) => setValues((prev) => ({ ...prev, ...patch }));

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
            disabled={isSaving}
            onClick={() => onSubmit(values)}
            className="btn-premium text-sm px-5 py-2.5 disabled:opacity-50"
          >
            {isSaving ? 'Salvando...' : payment ? 'Salvar' : 'Registrar pagamento'}
          </button>
        </>
      }
    >
      <p className="text-xs text-[#7a7074]">
        Registra que ela já recebeu por este período. Não gera lançamento no Fluxo de Caixa — a folha
        já é descontada do lucro no relatório financeiro.
      </p>
      <div className="grid grid-cols-2 gap-3">
        <div>
          <label htmlFor="pay-start" className={labelCls}>De</label>
          <input
            id="pay-start"
            type="date"
            className={inputCls}
            value={values.periodStart}
            max={values.periodEnd || undefined}
            onChange={(e) => set({ periodStart: e.target.value })}
          />
        </div>
        <div>
          <label htmlFor="pay-end" className={labelCls}>Até</label>
          <input
            id="pay-end"
            type="date"
            className={inputCls}
            value={values.periodEnd}
            min={values.periodStart || undefined}
            onChange={(e) => set({ periodEnd: e.target.value })}
          />
        </div>
      </div>
      <div>
        <label htmlFor="pay-amount" className={labelCls}>Valor pago</label>
        <CurrencyInput
          id="pay-amount"
          className={inputCls}
          value={values.amountPaid}
          onValueChange={(v) => set({ amountPaid: v })}
        />
        {!payment && (
          <p className="text-xs text-[#7a7074] mt-1">
            Sugerido: {formatBRL(suggestedAmount)} (o que o sistema calcula pro período do filtro). Se
            mudar as datas, confira o valor.
          </p>
        )}
        {payment && (
          <p className="text-xs text-[#7a7074] mt-1">
            Calculado no registro: {formatBRL(payment.calculatedAmount)}. Salvar recalcula com os dados de hoje.
          </p>
        )}
      </div>
      <div className="grid grid-cols-2 gap-3">
        <div>
          <label htmlFor="pay-date" className={labelCls}>Pago em</label>
          <input
            id="pay-date"
            type="date"
            className={inputCls}
            value={values.paidAt}
            onChange={(e) => set({ paidAt: e.target.value })}
          />
        </div>
        <div>
          <label htmlFor="pay-method" className={labelCls}>Forma</label>
          <select
            id="pay-method"
            className={inputCls}
            value={values.paymentMethod}
            onChange={(e) => set({ paymentMethod: e.target.value })}
          >
            {Object.entries(PAYMENT_METHODS).map(([value, label]) => (
              <option key={value} value={value}>{label}</option>
            ))}
          </select>
        </div>
      </div>
      <div>
        <label htmlFor="pay-notes" className={labelCls}>Observação (opcional)</label>
        <textarea
          id="pay-notes"
          rows={2}
          maxLength={1000}
          className={`${inputCls} resize-none`}
          value={values.notes}
          onChange={(e) => set({ notes: e.target.value })}
          placeholder="Ex.: pago em duas vezes, combinado arredondar..."
        />
      </div>
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
  const set = (patch: Partial<AdjustmentFormValues>) => setValues((prev) => ({ ...prev, ...patch }));

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
        <label htmlFor="adj-type" className={labelCls}>Tipo</label>
        <select
          id="adj-type"
          className={inputCls}
          value={values.type}
          onChange={(e) => set({ type: e.target.value as PayrollAdjustmentType })}
        >
          <optgroup label="Soma no pagamento">
            {(['BONIFICACAO', 'DECIMO_TERCEIRO', 'FERIAS', 'HORA_EXTRA', 'OUTRO_ACRESCIMO'] as const).map((t) => (
              <option key={t} value={t}>{ADJUSTMENT_LABELS[t]}</option>
            ))}
          </optgroup>
          <optgroup label="Abate do pagamento">
            {(['VALE', 'DESCONTO'] as const).map((t) => (
              <option key={t} value={t}>{ADJUSTMENT_LABELS[t]}</option>
            ))}
          </optgroup>
        </select>
        <p className="text-xs text-[#7a7074] mt-1">{ADJUSTMENT_HINTS[values.type]}</p>
      </div>
      <div className="grid grid-cols-2 gap-3">
        <div>
          <label htmlFor="adj-amount" className={labelCls}>Valor</label>
          <CurrencyInput
            id="adj-amount"
            className={inputCls}
            value={values.amount}
            onValueChange={(v) => set({ amount: v })}
          />
        </div>
        <div>
          <label htmlFor="adj-date" className={labelCls}>Data de referência</label>
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
        <label htmlFor="adj-desc" className={labelCls}>Descrição (opcional)</label>
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

  const differs = defaultRate == null || Number(values.amount) !== defaultRate;

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
        <label htmlFor="wd-amount" className={labelCls}>Valor desta diária</label>
        <CurrencyInput
          id="wd-amount"
          className={inputCls}
          value={values.amount}
          onValueChange={(v) => setValues((prev) => ({ ...prev, amount: v }))}
        />
        <p className="text-xs text-[#7a7074] mt-1">
          Diária padrão cadastrada: {defaultRate != null ? formatBRL(defaultRate) : 'não cadastrada'}
        </p>
      </div>
      <div>
        <label htmlFor="wd-reason" className={labelCls}>
          Justificativa {differs ? '(obrigatória — valor diferente do padrão)' : '(opcional)'}
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
