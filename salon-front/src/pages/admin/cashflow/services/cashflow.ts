import api from '../../../../services/api';
import { normalizePage, type SpringPageResponse } from '../../../../utils/pagination';
export type { PageResponse } from '../../../../utils/pagination';

export interface CashFlowItemData {
  productId: number;
  quantity: number;
  /** Sobrescreve o preço unitário do produto só nesta venda (opcional; padrão = preço de catálogo). */
  customPrice?: number;
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

/** Item já registrado numa venda avulsa (resposta da API). */
export interface CashFlowSaleItem {
  /** null se o produto foi excluído de vez — o nome congelado continua valendo. */
  productId: number | null;
  productName: string;
  quantity: number;
  unitPrice: number;
}

/** Lançamento como vem da API (listagem/consulta). */
export interface CashFlowRecord extends Omit<CashFlowData, 'items'> {
  /** Itens da venda avulsa — vazio em vendas antigas e em lançamentos que não são venda. */
  items?: CashFlowSaleItem[];
  /** % de comissão congelado na venda. */
  commissionPercent?: number | null;
  /** Comissão digitada à mão na edição da venda. */
  commissionManual?: boolean;
}

export interface SaleUpdateInput {
  date: string;
  employeeId: number | null;
  /** Vazio = venda antiga sem itens, editada pelo total (amount). */
  items: { productId: number; quantity: number; unitPrice: number | null }[];
  amount: number | null;
  description: string | null;
  /** null = calcula pelo % da venda. */
  commissionAmount: number | null;
}

/** Venda avulsa de produto: tem itens registrados ou uma vendedora. */
export const isProductSale = (record: CashFlowRecord) =>
  record.appointmentId == null && ((record.items?.length ?? 0) > 0 || record.employeeId != null);

export const cashFlowApi = {
  findByPeriod: async (from?: string, to?: string, page = 0, size = 20) => {
    const params: Record<string, string | number> = { page, size };
    if (from) params.from = from;
    if (to) params.to = to;
    const { data } = await api.get<SpringPageResponse<CashFlowRecord>>('/cashflow', { params });
    return normalizePage(data);
  },

  create: async (cashFlowData: CashFlowData) => {
    const { data } = await api.post<CashFlowData>('/cashflow', cashFlowData);
    return data;
  },

  /** Só funciona pra lançamento manual (sem appointmentId) — o gerado por agendamento se edita
   * pelo próprio agendamento. */
  update: async (id: number, cashFlowData: CashFlowData) => {
    const { data } = await api.put<CashFlowData>(`/cashflow/${id}`, cashFlowData);
    return data;
  },

  findById: async (id: number) => {
    const { data } = await api.get<CashFlowRecord>(`/cashflow/${id}`);
    return data;
  },

  /** Edita uma venda avulsa por completo (itens, vendedora, data, comissão). */
  updateSale: async (id: number, input: SaleUpdateInput) => {
    const { data } = await api.put<CashFlowRecord>(`/cashflow/${id}/sale`, input);
    return data;
  },

  delete: async (id: number) => {
    await api.delete(`/cashflow/${id}`);
  },
};
