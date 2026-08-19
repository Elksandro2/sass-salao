import api from '../../../services/api';
import { normalizePage, type SpringPageResponse } from '../../../utils/pagination';
export type { PageResponse } from '../../../utils/pagination';

export interface ServiceProductUsageRequest {
  productId: number;
  quantityUsed: number;
}

export interface ServiceProductUsageResponse {
  productId: number;
  productName: string;
  quantityUsed: number;
  unit: string | null;
  estimatedCost: number | null;
}

export interface SalonServiceData {
  id?: number;
  name: string;
  description: string;
  /** Opcional: referência &quot;a partir de&quot; */
  price?: number | null;
  active: boolean;
  /** Receita: quanto de cada produto este serviço consome por execução. */
  productUsages?: ServiceProductUsageRequest[] | ServiceProductUsageResponse[] | null;
  estimatedProductCost?: number | null;
}

export interface SalonServiceFilter {
  name?: string;
  active?: boolean;
}

export const salonServicesApi = {
  findAll: async (filter: SalonServiceFilter = {}, page = 0, size = 10) => {
    const { data } = await api.get<SpringPageResponse<SalonServiceData>>('/services', {
      params: { ...filter, page, size },
    });
    return normalizePage(data);
  },

  findById: async (id: number) => {
    const { data } = await api.get<SalonServiceData>(`/services/${id}`);
    return data;
  },

  create: async (salonServiceData: SalonServiceData) => {
    const { data } = await api.post<SalonServiceData>('/services', salonServiceData);
    return data;
  },

  update: async (id: number, salonServiceData: SalonServiceData) => {
    const { data } = await api.put<SalonServiceData>(`/services/${id}`, salonServiceData);
    return data;
  },

  delete: async (id: number) => {
    await api.delete(`/services/${id}`);
  },

  reactivate: async (id: number) => {
    const { data } = await api.patch<SalonServiceData>(`/services/${id}/reactivate`);
    return data;
  },
};

