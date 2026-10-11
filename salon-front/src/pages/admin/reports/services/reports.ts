import api from '../../../../services/api';
import { normalizePage, type SpringPageResponse } from '../../../../utils/pagination';
import type { RemunerationType } from '../../../../utils/remuneration';
export type { PageResponse } from '../../../../utils/pagination';

export interface EmployeeFinanceResponse {
  employeeId: number;
  employeeName: string;
  remunerationType?: RemunerationType;
  /** Salário base (fixo) ou valor da diária (diarista). Não se aplica a COMISSIONADO. */
  remunerationValue?: number;
  doneAppointmentsCount: number;
  doneAppointmentsValue: number;
  doneProductsValue: number;
  calculatedPayout: number;
  /** Dias trabalhados no período — só para Diarista/Diária+Comissão. */
  daysWorked?: number | null;
  /** O que a plataforma calcula que ela tem a receber no período filtrado. */
  estimatedPayment: number;
  /** Pagamentos que cobrem o filtro, com o período real de cada um (contíguos unidos). */
  paidRanges: PaidRange[];
  /** Dias do filtro sem nenhum pagamento registrado. */
  unpaidRanges: PaidRange[];
}

export interface PaidRange {
  start: string;
  end: string;
  /** Soma paga na faixa (null nas faixas sem pagamento). */
  amount: number | null;
}

export interface FinancialReportResponse {
  totalIncome: number;
  /** Saídas lançadas no Fluxo de Caixa (livre) — não inclui os Gastos Fixos, contados à parte. */
  totalExpense: number;
  totalSalaryPaid: number;
  totalCommissionPaid: number;
  /** Aluguel, água, luz, etc. — tela dedicada de Gastos Fixos. */
  totalFixedExpenses: number;
  netProfit: number;
  employeeFinanceDetails: EmployeeFinanceResponse[];
  period: string;
}

export interface AppointmentReportResponse {
  totalAppointments: number;
  pending: number;
  confirmed: number;
  done: number;
  cancelled: number;
  byEmployee: Record<string, number>;
  byService: Record<string, number>;
  period: string;
}

export interface AppointmentProfitResponse {
  appointmentId: number;
  grossRevenue: number;
  serviceRecipeCost: number;
  productsSoldCost: number;
  serviceCommissionCost: number;
  productCommissionCost: number;
  netProfit: number;
  positive: boolean;
  /** Valores que o sistema calcularia — referência quando algum custo foi digitado à mão. */
  autoServiceRecipeCost: number;
  autoProductsSoldCost: number;
  autoServiceCommissionCost: number;
  autoProductCommissionCost: number;
  serviceRecipeCostManual: boolean;
  productsSoldCostManual: boolean;
  serviceCommissionCostManual: boolean;
  productCommissionCostManual: boolean;
  overrideNote: string | null;
}

/** Cada campo null = volta ao cálculo automático. */
export interface AppointmentProfitOverrideInput {
  serviceRecipeCost: number | null;
  productsSoldCost: number | null;
  serviceCommissionCost: number | null;
  productCommissionCost: number | null;
  note: string | null;
}

export interface ServicePricingItemResponse {
  serviceId: number;
  serviceName: string;
  catalogPrice: number | null;
  timesPerformed: number;
  totalRevenue: number;
  recipeCostTotal: number;
  commissionCostTotal: number;
  fixedExpenseShare: number;
  netProfit: number;
  marginPercent: number;
  healthy: boolean;
}

export interface ServicePricingAnalysisResponse {
  items: ServicePricingItemResponse[];
  totalFixedExpenses: number;
  period: string;
}

export interface AppointmentFinancialResponse {
  id: number;
  scheduledAt: string | null;
  scheduledDate?: string | null;
  scheduledPeriod?: 'MORNING' | 'AFTERNOON' | null;
  preferredDate: string | null;
  serviceName: string;
  price: number | null;
  status: string;
  paymentStatus: string | null;
}

export const reportsApi = {
  getFinancialReport: async (from?: string, to?: string) => {
    const params: Record<string, string> = {};
    if (from) params.from = from;
    if (to) params.to = to;
    const { data } = await api.get<FinancialReportResponse>('/reports/financial', { params });
    return data;
  },

  getAppointmentReport: async (from?: string, to?: string) => {
    const params: Record<string, string> = {};
    if (from) params.from = from;
    if (to) params.to = to;
    const { data } = await api.get<AppointmentReportResponse>('/reports/appointments', { params });
    return data;
  },

  getServicePricingAnalysis: async (from?: string, to?: string) => {
    const params: Record<string, string> = {};
    if (from) params.from = from;
    if (to) params.to = to;
    const { data } = await api.get<ServicePricingAnalysisResponse>('/reports/service-pricing', { params });
    return data;
  },

  getAppointmentProfit: async (appointmentId: number) => {
    const { data } = await api.get<AppointmentProfitResponse>(
      `/reports/appointments/${appointmentId}/profit`
    );
    return data;
  },

  updateAppointmentProfit: async (appointmentId: number, input: AppointmentProfitOverrideInput) => {
    const { data } = await api.put<AppointmentProfitResponse>(
      `/reports/appointments/${appointmentId}/profit`,
      input
    );
    return data;
  },

  getEmployeeFinancialHistory: async (
    employeeId: number,
    from: string | undefined,
    to: string | undefined,
    page: number,
    size: number
  ) => {
    const params: Record<string, string | number> = { page, size };
    if (from) params.from = from;
    if (to) params.to = to;
    const { data } = await api.get<SpringPageResponse<AppointmentFinancialResponse>>(
      `/reports/financial/employees/${employeeId}`,
      { params }
    );
    return normalizePage(data);
  },
};
