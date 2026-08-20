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

  // Self-service (Meu Perfil): a própria funcionária/gerente logada conecta/desconecta a
  // conta dela, sem depender de outra pessoa clicar por ela em Admin → Equipe.
  statusMe: async () => {
    const { data } = await api.get<MercadoPagoStatus>('/employees/me/mercadopago/status');
    return data;
  },

  connectMe: async () => {
    const { data } = await api.get<{ authorizationUrl: string }>('/employees/me/mercadopago/connect');
    return data;
  },

  disconnectMe: async () => {
    await api.delete('/employees/me/mercadopago');
  },
};
