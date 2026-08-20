import api from '../../../../services/api';
import { normalizePage, type SpringPageResponse } from '../../../../utils/pagination';
export type { PageResponse } from '../../../../utils/pagination';

export interface CashFlowItemData {
  productId: number;
  quantity: number;
}

export interface CashFlowData {
  id?: number;
  type: 'INCOME' | 'EXPENSE';
  amount: number;
  description: string;
  date: string;
  appointmentId?: number | null;
  items?: CashFlowItemData[];
  /** Quem vendeu, numa venda avulsa de produto — opcional, usado só pra calcular comissão. */
  employeeId?: number | null;
  employeeName?: string | null;
  /** Comissão da venda avulsa, calculada pelo backend quando um vendedor é informado. */
  commissionAmount?: number | null;
}

export const cashFlowApi = {
  findByPeriod: async (from?: string, to?: string, page = 0, size = 20) => {
    const params: Record<string, string | number> = { page, size };
    if (from) params.from = from;
    if (to) params.to = to;
    const { data } = await api.get<SpringPageResponse<CashFlowData>>('/cashflow', { params });
    return normalizePage(data);
  },

  create: async (cashFlowData: CashFlowData) => {
    const { data } = await api.post<CashFlowData>('/cashflow', cashFlowData);
    return data;
  },

  delete: async (id: number) => {
    await api.delete(`/cashflow/${id}`);
  },
};
