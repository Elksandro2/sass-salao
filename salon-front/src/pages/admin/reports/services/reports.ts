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

export interface PayrollItem {
  employeeId: number;
  employeeName: string;
  remunerationType?: RemunerationType;
  baseAmount: number;
  calculatedPay: number;
  /** Valor da diária — só para Diarista/Diária+Comissão, senão null. */
  dailyRate?: number | null;
  /** Dias trabalhados usados no cálculo — só para Diarista/Diária+Comissão, senão null. */
  daysWorked?: number | null;
  /** Contagem automática (dias com atendimento concluído) — referência. */
  daysWorkedAuto?: number | null;
  /** true = o número veio de um ajuste manual salvo; false = veio do automático. */
  daysWorkedIsOverride?: boolean | null;
}

export interface PayrollReportResponse {
  items: PayrollItem[];
  period: string;
  /** Período já resolvido (defaults aplicados) — usar ao salvar ajuste de dias da diarista. */
  periodStart?: string | null;
  periodEnd?: string | null;
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

  getPayrollReport: async (from?: string, to?: string) => {
    const params: Record<string, string> = {};
    if (from) params.from = from;
    if (to) params.to = to;
    const { data } = await api.get<PayrollReportResponse>('/reports/payroll', { params });
    return data;
  },

  /** Ajuste manual dos dias trabalhados de uma diarista para exatamente [periodStart, periodEnd]. */
  saveWorkedDays: async (input: {
    employeeId: number;
    periodStart: string;
    periodEnd: string;
    daysWorked: number;
  }) => {
    await api.put('/reports/payroll/worked-days', input);
  },

  /** Remove o ajuste manual — o período volta a contar os dias automaticamente. */
  resetWorkedDays: async (input: { employeeId: number; periodStart: string; periodEnd: string }) => {
    await api.delete('/reports/payroll/worked-days', { params: input });
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
