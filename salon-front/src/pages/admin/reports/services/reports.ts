import api from '../../../../services/api';
import { normalizePage, type SpringPageResponse } from '../../../../utils/pagination';
export type { PageResponse } from '../../../../utils/pagination';

export interface EmployeeFinanceResponse {
  employeeId: number;
  employeeName: string;
  remunerationType?: 'SALARIO_FIXO' | 'COMISSIONADO' | 'FIXO_E_COMISSIONADO';
  remunerationValue?: number;
  commissionScope?: 'INDIVIDUAL' | 'GLOBAL';
  commissionValue?: number;
  doneAppointmentsCount: number;
  doneAppointmentsValue: number;
  calculatedPayout: number;
}

export interface FinancialReportResponse {
  totalIncome: number;
  totalExpense: number;
  totalSalaryPaid: number;
  totalCommissionPaid: number;
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
  remunerationType?: 'SALARIO_FIXO' | 'COMISSIONADO' | 'FIXO_E_COMISSIONADO';
  commissionScope?: 'INDIVIDUAL' | 'GLOBAL';
  baseAmount: number;
  calculatedPay: number;
}

export interface PayrollReportResponse {
  items: PayrollItem[];
  period: string;
}

export interface AppointmentProfitResponse {
  appointmentId: number;
  grossRevenue: number;
  serviceRecipeCost: number;
  productsSoldCost: number;
  commissionCost: number;
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
