import api from '../../../../services/api';

export interface MercadoPagoStatus {
  connected: boolean;
  connectedAt?: string | null;
}

export const employeeMercadoPagoApi = {
  status: async (employeeId: number) => {
    const { data } = await api.get<MercadoPagoStatus>(`/employees/${employeeId}/mercadopago/status`);
    return data;
  },

  connect: async (employeeId: number) => {
    const { data } = await api.get<{ authorizationUrl: string }>(`/employees/${employeeId}/mercadopago/connect`);
    return data;
  },

  disconnect: async (employeeId: number) => {
    await api.delete(`/employees/${employeeId}/mercadopago`);
  },
};
