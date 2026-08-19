import api from '../../../../services/api';
import { normalizePage, type SpringPageResponse } from '../../../../utils/pagination';
export type { PageResponse } from '../../../../utils/pagination';

export interface FixedExpenseData {
  id?: number;
  description: string;
  amount: number;
  date: string;
}

export const fixedExpensesApi = {
  findByPeriod: async (from?: string, to?: string, page = 0, size = 20) => {
    const params: Record<string, string | number> = { page, size };
    if (from) params.from = from;
    if (to) params.to = to;
    const { data } = await api.get<SpringPageResponse<FixedExpenseData>>('/fixed-expenses', { params });
    return normalizePage(data);
  },

  create: async (expense: FixedExpenseData) => {
    const { data } = await api.post<FixedExpenseData>('/fixed-expenses', expense);
    return data;
  },

  delete: async (id: number) => {
    await api.delete(`/fixed-expenses/${id}`);
  },
};
