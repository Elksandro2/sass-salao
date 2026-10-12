import { useEffect, useState, type ReactNode } from 'react';
import { createPortal } from 'react-dom';
import { X } from 'lucide-react';
import { CurrencyInput } from '../../../../components/CurrencyInput';
import {
  ADJUSTMENT_HINTS,
  ADJUSTMENT_LABELS,
  PAYMENT_METHODS,
  payrollApi,
  type CoverageRange,
  type SalaryMonthStatus,
  type PayrollAdjustment,
  type PayrollAdjustmentType,
  type PayrollPayment,
  type WorkDay,
} from '../services/payroll';
import { formatBRL, formatShortDate, salaryMonthLabel, todayIso } from '../payrollUtils';

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
  /** null nos dois = pagamento só de salário. */
  periodStart: string | null;
  periodEnd: string | null;
  /** Meses (dia 1, "AAAA-MM-01") cujo salário fixo entra no pagamento. */
  salaryMonths: string[];
  paidAt: string;
  paymentMethod: string;
  notes: string;
}

interface PaymentModalProps {
  employeeId: number;
  employeeName: string;
  /** Pagamento já registrado sendo editado. */
  payment: PayrollPayment;
  isSaving: boolean;
  onClose: () => void;
  onSubmit: (values: PaymentFormValues) => void;
}

/**
 * Editar pagamento já registrado (Pagamentos realizados). Datas dos dias são editáveis (a API
 * recusa dia já pago em outro pagamento); salários já incluídos podem ser tirados. O valor não
 * é digitado: é sempre o calculado (prévia ao vivo).
 */
export const PaymentModal = ({
  employeeId,
  employeeName,
  payment,
  isSaving,
  onClose,
  onSubmit,
}: PaymentModalProps) => {
  const hasDays = payment.periodStart != null;
  const [values, setValues] = useState<PaymentFormValues>({
    periodStart: payment.periodStart,
    periodEnd: payment.periodEnd,
    salaryMonths: payment.salaryMonths.map((s) => s.month),
    paidAt: payment.paidAt,
    paymentMethod: payment.paymentMethod ?? 'PIX',
    notes: payment.notes ?? '',
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
    hasDays && !!values.periodStart && !!values.periodEnd && values.periodStart > values.periodEnd;
  const missingDates = hasDays && (!values.periodStart || !values.periodEnd);
  const monthsKey = values.salaryMonths.join(',');

  useEffect(() => {
    if (invalidOrder || missingDates) {
      setPreview({ amount: null, conflict: null, loading: false });
      return;
    }
    let cancelled = false;
    setPreview((prev) => ({ ...prev, loading: true }));
    const timer = setTimeout(() => {
      payrollApi
        .previewPayment(employeeId, {
          periodStart: hasDays ? values.periodStart : null,
          periodEnd: hasDays ? values.periodEnd : null,
          salaryMonths: values.salaryMonths,
          ignorePaymentId: payment.id,
        })
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
  }, [employeeId, values.periodStart, values.periodEnd, monthsKey, payment.id]); // eslint-disable-line react-hooks/exhaustive-deps

  const blocked =
    invalidOrder ||
    missingDates ||
    !!preview.conflict ||
    preview.loading ||
    preview.amount == null ||
    !values.paidAt;

  const toggleMonth = (month: string) =>
    set({
      salaryMonths: values.salaryMonths.includes(month)
        ? values.salaryMonths.filter((m) => m !== month)
        : [...values.salaryMonths, month].sort(),
    });

  return (
    <ModalShell
      title="Editar pagamento"
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
            {isSaving ? 'Salvando...' : 'Salvar'}
          </button>
        </>
      }
    >
      {hasDays ? (
        <div className="grid grid-cols-2 gap-3">
          <div>
            <label htmlFor="pay-start" className={labelCls}>
              Dias — de
            </label>
            <input
              id="pay-start"
              type="date"
              className={inputCls}
              value={values.periodStart ?? ''}
              onChange={(e) => set({ periodStart: e.target.value })}
            />
          </div>
          <div>
            <label htmlFor="pay-end" className={labelCls}>
              até
            </label>
            <input
              id="pay-end"
              type="date"
              className={inputCls}
              value={values.periodEnd ?? ''}
              onChange={(e) => set({ periodEnd: e.target.value })}
            />
          </div>
        </div>
      ) : (
        <p className="text-xs text-[#7a7074]">
          Pagamento só de salário (sem dias de comissão/diária).
        </p>
      )}
      {invalidOrder && <p className="text-xs text-rose-600">O "de" precisa ser antes do "até".</p>}

      {payment.salaryMonths.length > 0 && (
        <div className="space-y-1.5">
          <span className={labelCls}>Salário fixo incluído</span>
          {payment.salaryMonths.map((s) => (
            <label
              key={s.month}
              className="flex items-center justify-between gap-2 rounded-lg border border-[#eae1e1] px-3 py-2 text-sm cursor-pointer"
            >
              <span className="flex items-center gap-2">
                <input
                  type="checkbox"
                  checked={values.salaryMonths.includes(s.month)}
                  onChange={() => toggleMonth(s.month)}
                />
                Salário de {salaryMonthLabel(s.month)}
              </span>
              <span className="text-xs text-[#7a7074]">{formatBRL(s.amount)}</span>
            </label>
          ))}
          <p className="text-[11px] text-[#7a7074]">
            Desmarcar tira o salário deste pagamento — ele volta a ficar pendente.
          </p>
        </div>
      )}

      {preview.conflict && (
        <p className="text-xs text-rose-600 rounded-lg bg-rose-50 border border-rose-200 px-3 py-2">
          {preview.conflict}
        </p>
      )}

      <div className="rounded-xl border border-[#eae1e1] bg-[#fcf9f9]/50 px-4 py-3">
        <p className="text-[11px] font-semibold uppercase tracking-wider text-[#7a7074]">Valor</p>
        <p className="text-2xl font-extrabold text-[#3b3036]" data-testid="payment-preview-amount">
          {preview.loading ? '…' : preview.amount != null ? formatBRL(preview.amount) : '—'}
        </p>
        <p className="text-[11px] text-[#7a7074] mt-1">
          Calculado pelo sistema. Pra pagar diferente, lance um bônus ou desconto antes.
        </p>
        {payment.amountPaid !== preview.amount && preview.amount != null && !preview.loading && (
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

// ─── Marcar como pago (período do filtro) ───────────────────────────────────

/** Um item da folha no período, pra lista colorida do "Marcar como pago". */
export interface PeriodItem {
  key: string;
  date: string;
  label: string;
  amount: number;
  /** Desconto (abate do valor). */
  negative?: boolean;
}

interface MarkPaidModalProps {
  employeeId: number;
  employeeName: string;
  /** Período do filtro do topo — as datas do pagamento saem daqui, não são digitadas. */
  filterFrom: string;
  filterTo: string;
  /** Pagamentos que tocam o filtro (período real de cada um). */
  paidRanges: CoverageRange[];
  /** Dias do filtro que ainda não foram pagos e têm algo a pagar. */
  unpaidRanges: CoverageRange[];
  /** Salário fixo de cada mês do filtro, pago ou pendente. */
  salaryMonths: SalaryMonthStatus[];
  items: PeriodItem[];
  isSaving: boolean;
  onClose: () => void;
  onSubmit: (values: PaymentFormValues) => void;
}

const inRange = (date: string, r: { start: string; end: string }) =>
  date >= r.start && date <= r.end;
const clip = (r: CoverageRange, from: string, to: string) => ({
  start: r.start < from ? from : r.start,
  end: r.end > to ? to : r.end,
});

/**
 * "Marcar como pago" do período filtrado. Os dias não são digitados: vêm do filtro, e se parte
 * dele já foi paga o período é encurtado sozinho pros dias pendentes (com escolha quando os dias
 * pendentes ficam separados). Mostra só o que entra AGORA; o que já foi pago aparece resumido no
 * topo. O salário fixo é MENSAL: cada mês pendente vem marcado e pode ser desmarcado pra ficar
 * pra outro pagamento. Dá pra registrar só salário. O valor é sempre o calculado.
 */
export const MarkPaidModal = ({
  employeeId,
  employeeName,
  filterFrom,
  filterTo,
  paidRanges,
  unpaidRanges,
  salaryMonths,
  items,
  isSaving,
  onClose,
  onSubmit,
}: MarkPaidModalProps) => {
  const pendingSalaries = salaryMonths.filter((s) => !s.paid);
  const paidSalaries = salaryMonths.filter((s) => s.paid);
  const [selected, setSelected] = useState(0);
  const [months, setMonths] = useState<string[]>(pendingSalaries.map((s) => s.month));
  const [paidAt, setPaidAt] = useState(todayIso());
  const [paymentMethod, setPaymentMethod] = useState('PIX');
  const [notes, setNotes] = useState('');
  const [preview, setPreview] = useState<{
    amount: number | null;
    conflict: string | null;
    loading: boolean;
  }>({
    amount: null,
    conflict: null,
    loading: true,
  });

  const target = unpaidRanges[selected] ?? null;
  const nothingToPay = unpaidRanges.length === 0 && pendingSalaries.length === 0;
  const emptySelection = !target && months.length === 0;
  const monthsKey = months.join(',');

  useEffect(() => {
    if (!target && months.length === 0) {
      setPreview({ amount: null, conflict: null, loading: false });
      return;
    }
    let cancelled = false;
    setPreview((prev) => ({ ...prev, loading: true }));
    payrollApi
      .previewPayment(employeeId, {
        periodStart: target?.start ?? null,
        periodEnd: target?.end ?? null,
        salaryMonths: months,
      })
      .then(
        (p) =>
          !cancelled &&
          setPreview({ amount: p.calculatedAmount, conflict: p.conflict, loading: false })
      )
      .catch(() => !cancelled && setPreview({ amount: null, conflict: null, loading: false }));
    return () => {
      cancelled = true;
    };
  }, [employeeId, target?.start, target?.end, monthsKey]); // eslint-disable-line react-hooks/exhaustive-deps

  // Só o que entra AGORA: itens dentro dos dias escolhidos. O que já foi pago fica fora da lista
  // (aparece resumido no topo como "já pagos").
  const includedItems = target
    ? items
        .filter((i) => inRange(i.date, target) && !paidRanges.some((r) => inRange(i.date, r)))
        .sort((a, b) => a.date.localeCompare(b.date))
    : [];

  const blocked =
    nothingToPay ||
    emptySelection ||
    !!preview.conflict ||
    preview.loading ||
    preview.amount == null ||
    !paidAt;

  const toggleMonth = (month: string) =>
    setMonths((prev) =>
      prev.includes(month) ? prev.filter((m) => m !== month) : [...prev, month].sort()
    );

  const alreadyPaid = [
    ...paidRanges.map((r) => {
      const c = clip(r, filterFrom, filterTo);
      return `Dias ${formatShortDate(c.start)} a ${formatShortDate(c.end)}`;
    }),
    ...paidSalaries.map(
      (s) =>
        `Salário de ${salaryMonthLabel(s.month)}${s.paidAt ? ` (pago em ${formatShortDate(s.paidAt)})` : ''}`
    ),
  ];

  return (
    <ModalShell
      title="Marcar como pago"
      subtitle={employeeName}
      onClose={onClose}
      footer={
        <>
          <CancelButton onClick={onClose} />
          <button
            type="button"
            disabled={isSaving || blocked}
            onClick={() =>
              onSubmit({
                periodStart: target?.start ?? null,
                periodEnd: target?.end ?? null,
                salaryMonths: months,
                paidAt,
                paymentMethod,
                notes,
              })
            }
            className="btn-premium text-sm px-5 py-2.5 disabled:opacity-50"
          >
            {isSaving ? 'Salvando...' : 'Registrar pagamento'}
          </button>
        </>
      }
    >
      <div className="text-xs text-[#7a7074] space-y-1">
        <p>
          Período filtrado:{' '}
          <strong className="text-[#3b3036]">
            {formatShortDate(filterFrom)} a {formatShortDate(filterTo)}
          </strong>
        </p>
        {alreadyPaid.length > 0 && (
          <div className="rounded-lg border border-emerald-200 bg-emerald-50 px-3 py-2 text-emerald-700">
            <p className="font-semibold">Já pago (não entra neste pagamento):</p>
            {alreadyPaid.map((text) => (
              <p key={text}>✓ {text}</p>
            ))}
          </div>
        )}
      </div>

      {nothingToPay ? (
        <p className="rounded-lg border border-emerald-200 bg-emerald-50 px-3 py-2 text-sm font-semibold text-emerald-700">
          Todo o período filtrado já está pago.
        </p>
      ) : (
        <>
          {unpaidRanges.length > 1 && (
            <div className="space-y-1.5">
              <span className={labelCls}>
                Os dias pendentes estão separados — escolha qual pagar agora
              </span>
              {unpaidRanges.map((r, i) => (
                <label
                  key={r.start}
                  className={`flex items-center gap-2 rounded-lg border px-3 py-2 text-sm cursor-pointer ${
                    i === selected ? 'border-[#be8a83] bg-[#fdf6f5]' : 'border-[#eae1e1]'
                  }`}
                >
                  <input
                    type="radio"
                    name="unpaid-range"
                    checked={i === selected}
                    onChange={() => setSelected(i)}
                  />
                  {formatShortDate(r.start)} a {formatShortDate(r.end)}
                </label>
              ))}
            </div>
          )}

          {/* O que entra neste pagamento */}
          <div className="space-y-1.5">
            <span className={labelCls}>O que entra neste pagamento</span>
            <ul className="rounded-xl border border-[#eae1e1] divide-y divide-[#eae1e1]/60">
              <li
                className="px-3 py-2 text-xs text-[#7a7074] bg-[#fcf9f9]/60"
                data-testid="mark-paid-period"
              >
                {target
                  ? `Dias ${formatShortDate(target.start)} a ${formatShortDate(target.end)}`
                  : 'Nenhum dia com valor a pagar — só salário.'}
              </li>
              {includedItems.map((item) => (
                <li
                  key={item.key}
                  className="flex items-center justify-between gap-2 px-3 py-2 text-xs"
                >
                  <span className="text-[#3b3036] min-w-0">
                    {formatShortDate(item.date)} · {item.label}
                  </span>
                  <span className="font-semibold whitespace-nowrap text-[#3b3036]">
                    {item.negative ? '− ' : ''}
                    {formatBRL(item.amount)}
                  </span>
                </li>
              ))}
              {target && includedItems.length === 0 && (
                <li className="px-3 py-2 text-xs text-[#7a7074]">
                  Sem comissões, diárias ou lançamentos nesses dias.
                </li>
              )}
              {pendingSalaries.map((s) => (
                <li key={s.month} className="px-3 py-2 text-xs">
                  <label className="flex items-center justify-between gap-2 cursor-pointer">
                    <span className="flex items-center gap-2 text-[#3b3036]">
                      <input
                        type="checkbox"
                        aria-label={`Salário de ${salaryMonthLabel(s.month)}`}
                        checked={months.includes(s.month)}
                        onChange={() => toggleMonth(s.month)}
                      />
                      Salário de {salaryMonthLabel(s.month)}
                    </span>
                    <span
                      className={`font-semibold whitespace-nowrap ${
                        months.includes(s.month) ? 'text-[#3b3036]' : 'text-[#7a7074] line-through'
                      }`}
                    >
                      {formatBRL(s.amount)}
                    </span>
                  </label>
                </li>
              ))}
            </ul>
            {pendingSalaries.some((s) => !months.includes(s.month)) && (
              <p className="text-[11px] text-[#7a7074]">
                O salário desmarcado fica pendente pra outro pagamento.
              </p>
            )}
          </div>

          <div className="rounded-xl border border-[#be8a83]/40 bg-[#fdf6f5] px-4 py-3 flex items-center justify-between gap-3">
            <div>
              <p className="text-[11px] font-semibold uppercase tracking-wider text-[#7a7074]">
                Total deste pagamento
              </p>
              <p className="text-[11px] text-[#7a7074]">
                {emptySelection ? 'Nada selecionado pra pagar.' : 'Calculado pelo sistema.'}
              </p>
            </div>
            <p
              className="text-2xl font-extrabold text-[#3b3036] whitespace-nowrap"
              data-testid="payment-preview-amount"
            >
              {emptySelection
                ? '—'
                : preview.loading
                  ? '…'
                  : preview.amount != null
                    ? formatBRL(preview.amount)
                    : '—'}
            </p>
          </div>
          <p className="text-[11px] text-[#7a7074] -mt-2">
            Pra pagar diferente, lance um bônus ou desconto antes de registrar.
          </p>
        </>
      )}
      {preview.conflict && (
        <p className="text-xs text-rose-600 rounded-lg bg-rose-50 border border-rose-200 px-3 py-2">
          {preview.conflict}
        </p>
      )}

      <div className="grid grid-cols-2 gap-3">
        <div>
          <label htmlFor="mark-paid-date" className={labelCls}>
            Pago em
          </label>
          <input
            id="mark-paid-date"
            type="date"
            className={inputCls}
            value={paidAt}
            onChange={(e) => setPaidAt(e.target.value)}
          />
        </div>
        <div>
          <label htmlFor="mark-paid-method" className={labelCls}>
            Forma
          </label>
          <select
            id="mark-paid-method"
            className={inputCls}
            value={paymentMethod}
            onChange={(e) => setPaymentMethod(e.target.value)}
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
        <label htmlFor="mark-paid-notes" className={labelCls}>
          Observação (opcional)
        </label>
        <textarea
          id="mark-paid-notes"
          rows={2}
          maxLength={1000}
          className={`${inputCls} resize-none`}
          value={notes}
          onChange={(e) => setNotes(e.target.value)}
        />
      </div>
    </ModalShell>
  );
};
