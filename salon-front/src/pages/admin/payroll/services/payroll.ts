import api from '../../../../services/api';

export type PeriodStatus = 'PAGO' | 'PARCIAL' | 'EM_ABERTO';

export type PayrollAdjustmentType =
  | 'BONIFICACAO'
  | 'DECIMO_TERCEIRO'
  | 'FERIAS'
  | 'HORA_EXTRA'
  | 'OUTRO_ACRESCIMO'
  | 'VALE'
  | 'DESCONTO';

export const ADJUSTMENT_LABELS: Record<PayrollAdjustmentType, string> = {
  BONIFICACAO: 'Bonificação',
  DECIMO_TERCEIRO: '13º salário',
  FERIAS: 'Férias',
  HORA_EXTRA: 'Hora extra',
  OUTRO_ACRESCIMO: 'Outro acréscimo',
  VALE: 'Vale / adiantamento (já pago antes)',
  DESCONTO: 'Desconto (ela deixa de receber)',
};

export const ADJUSTMENT_HINTS: Record<PayrollAdjustmentType, string> = {
  BONIFICACAO: 'Soma no valor a pagar.',
  DECIMO_TERCEIRO: 'Soma no valor a pagar.',
  FERIAS: 'Soma no valor a pagar.',
  HORA_EXTRA: 'Soma no valor a pagar.',
  OUTRO_ACRESCIMO: 'Soma no valor a pagar.',
  VALE:
    'Dinheiro que ela JÁ recebeu antes do dia do pagamento (ex.: pediu R$ 200 adiantado no dia 10). '
    + 'Sai do valor a pagar pra ela não receber duas vezes. O custo da folha não muda: o salão gastou '
    + 'esse dinheiro do mesmo jeito, só que antes.',
  DESCONTO:
    'Valor que ela deixa de receber de fato (ex.: falta não justificada, produto quebrado). Diminui o '
    + 'valor a pagar e também o custo da folha, porque esse dinheiro nunca sai do salão.',
};

export const PAYMENT_METHODS: Record<string, string> = {
  PIX: 'PIX',
  DINHEIRO: 'Dinheiro',
  TRANSFERENCIA: 'Transferência',
  OUTRO: 'Outro',
};

export interface PayrollBreakdown {
  fixedSalary: number;
  workDaysCount: number;
  dailyTotal: number;
  serviceCommission: number;
  appointmentProductCommission: number;
  saleProductCommission: number;
  totalCommission: number;
  additions: number;
  discounts: number;
  advances: number;
  /** Quanto a folha dela custa ao salão no período (vale não abate). */
  salonCost: number;
  /** Quanto falta entregar a ela: custo − vales. */
  netPayable: number;
}

export interface PayrollEmployeeSummary {
  employeeId: number;
  employeeName: string;
  remunerationType: string | null;
  remunerationValue: number | null;
  active: boolean;
  doneAppointmentsCount: number;
  breakdown: PayrollBreakdown;
  periodStatus: PeriodStatus;
  coveredDays: number;
  totalDays: number;
  paidInPeriod: number;
  outstanding: number;
  /** Pagamento mais recente (null = nunca pago) — não depende do filtro de datas. */
  lastPayment: { paidAt: string; periodStart: string; periodEnd: string; amountPaid: number } | null;
  hasPaymentGaps: boolean;
}

export interface PayrollSummary {
  periodStart: string;
  periodEnd: string;
  items: PayrollEmployeeSummary[];
  totalSalonCost: number;
  totalNetPayable: number;
  totalPaidInPeriod: number;
  totalOutstanding: number;
}

export interface PayrollAppointmentLine {
  appointmentId: number;
  date: string;
  clientName: string | null;
  serviceNames: string;
  servicesValue: number;
  productsValue: number;
  serviceCommission: number;
  productCommission: number;
  commissionManual: boolean;
  paymentStatus: string | null;
}

export interface PayrollSaleLine {
  cashFlowId: number;
  date: string;
  description: string;
  amount: number;
  commission: number;
}

export interface WorkDay {
  /** null = dia automático (teve atendimento e não tem registro próprio). */
  id: number | null;
  employeeId: number;
  date: string;
  amount: number;
  justification: string | null;
  defaultRate: number | null;
  /** Contou sozinho por ter atendimento concluído no dia. */
  automatic: boolean;
}

export interface PayrollAdjustment {
  id: number;
  employeeId: number;
  date: string;
  type: PayrollAdjustmentType;
  amount: number;
  description: string | null;
  addition: boolean;
}

export interface PayrollPayment {
  id: number;
  employeeId: number;
  periodStart: string;
  periodEnd: string;
  calculatedAmount: number;
  amountPaid: number;
  paidAt: string;
  paymentMethod: string | null;
  notes: string | null;
  recalculatedAmount: number | null;
  changedSincePayment: boolean;
}

export interface PayrollEmployeeDetail {
  summary: PayrollEmployeeSummary;
  periodStart: string;
  periodEnd: string;
  appointments: PayrollAppointmentLine[];
  productSales: PayrollSaleLine[];
  workDays: WorkDay[];
  /** Dias com atendimento que foram desmarcados (não contam como diária). */
  unmarkedAppointmentDates: string[];
  adjustments: PayrollAdjustment[];
  payments: PayrollPayment[];
}

export interface WorkDayInput {
  employeeId: number;
  date: string;
  amount?: number | null;
  justification?: string | null;
}

export interface AdjustmentInput {
  employeeId: number;
  date: string;
  type: PayrollAdjustmentType;
  amount: number;
  description?: string | null;
}

export interface PaymentInput {
  employeeId: number;
  periodStart: string;
  periodEnd: string;
  amountPaid: number;
  paidAt: string;
  paymentMethod?: string | null;
  notes?: string | null;
}

const periodParams = (from?: string, to?: string) => {
  const params: Record<string, string> = {};
  if (from) params.from = from;
  if (to) params.to = to;
  return params;
};

export const payrollApi = {
  getSummary: async (from?: string, to?: string) => {
    const { data } = await api.get<PayrollSummary>('/payroll', { params: periodParams(from, to) });
    return data;
  },

  getEmployeeDetail: async (employeeId: number, from?: string, to?: string) => {
    const { data } = await api.get<PayrollEmployeeDetail>(`/payroll/employees/${employeeId}`, {
      params: periodParams(from, to),
    });
    return data;
  },

  createWorkDay: async (input: WorkDayInput) => {
    const { data } = await api.post<WorkDay>('/payroll/work-days', input);
    return data;
  },

  createWorkDaysBulk: async (employeeId: number, dates: string[]) => {
    const { data } = await api.post<WorkDay[]>('/payroll/work-days/bulk', { employeeId, dates });
    return data;
  },

  updateWorkDay: async (id: number, input: WorkDayInput) => {
    const { data } = await api.put<WorkDay>(`/payroll/work-days/${id}`, input);
    return data;
  },

  /** Desmarca um dia — inclusive um dia com atendimento, que senão contaria sozinho. */
  unmarkWorkDay: async (employeeId: number, date: string) => {
    await api.post('/payroll/work-days/unmark', { employeeId, date });
  },

  createAdjustment: async (input: AdjustmentInput) => {
    const { data } = await api.post<PayrollAdjustment>('/payroll/adjustments', input);
    return data;
  },

  updateAdjustment: async (id: number, input: AdjustmentInput) => {
    const { data } = await api.put<PayrollAdjustment>(`/payroll/adjustments/${id}`, input);
    return data;
  },

  deleteAdjustment: async (id: number) => {
    await api.delete(`/payroll/adjustments/${id}`);
  },

  createPayment: async (input: PaymentInput) => {
    const { data } = await api.post<PayrollPayment>('/payroll/payments', input);
    return data;
  },

  updatePayment: async (id: number, input: PaymentInput) => {
    const { data } = await api.put<PayrollPayment>(`/payroll/payments/${id}`, input);
    return data;
  },

  deletePayment: async (id: number) => {
    await api.delete(`/payroll/payments/${id}`);
  },
};
