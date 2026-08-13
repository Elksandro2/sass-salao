import api from '../../../../services/api';

export type SkinType = 'NORMAL' | 'OLEOSA' | 'SECA' | 'MISTA' | 'SENSIVEL';
export type HairType = 'LISO' | 'ONDULADO' | 'CACHEADO' | 'CRESPO';

export interface ClientAnamnesisData {
  id?: number;
  clientId?: number;
  allergies?: string | null;
  healthConditions?: string | null;
  medications?: string | null;
  additionalNotes?: string | null;
  skinType?: SkinType | null;
  hairType?: HairType | null;
  consentGiven?: boolean;
  consentGivenAt?: string | null;
  consentGivenByName?: string | null;
  createdAt?: string | null;
  updatedAt?: string | null;
  updatedByName?: string | null;
}

export const clientAnamnesisApi = {
  findByClientId: async (clientId: number) => {
    const { data } = await api.get<ClientAnamnesisData>(`/clients/${clientId}/anamnesis`);
    return data;
  },

  upsert: async (clientId: number, payload: ClientAnamnesisData) => {
    const { data } = await api.put<ClientAnamnesisData>(`/clients/${clientId}/anamnesis`, payload);
    return data;
  },

  delete: async (clientId: number) => {
    await api.delete(`/clients/${clientId}/anamnesis`);
  },
};
