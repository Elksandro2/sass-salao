import api from './api';

/**
 * Comissão única (%) sobre produtos vendidos, válida pro salão inteiro — qualquer funcionária
 * que vender qualquer produto recebe essa %, inclusive Salário Fixo (exceção deliberada,
 * incentivo à venda). Comissão de serviço é configurada por serviço, não aqui.
 */
export interface SalonBusinessSettingsData {
  productCommissionPercent: number | null;
  updatedAt: string | null;
}

export interface SalonBusinessSettingsUpdatePayload {
  productCommissionPercent: number | null;
}

export const businessSettingsService = {
  get: async () => {
    const { data } = await api.get<SalonBusinessSettingsData>('/admin/business-settings');
    return data;
  },

  update: async (payload: SalonBusinessSettingsUpdatePayload) => {
    const { data } = await api.put<SalonBusinessSettingsData>('/admin/business-settings', payload);
    return data;
  },
};
