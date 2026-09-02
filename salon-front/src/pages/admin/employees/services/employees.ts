import api from '../../../../services/api';
import { normalizePage, type SpringPageResponse } from '../../../../utils/pagination';
import type { RemunerationType } from '../../../../utils/remuneration';
export type { PageResponse } from '../../../../utils/pagination';

export interface EmployeeData {
  id?: number;
  userId: number;
  name?: string;
  email?: string;
  roleName?: 'FUNCIONARIA' | 'GERENTE_DE_ATENDIMENTO' | 'ADMIN';
  remunerationType?: RemunerationType;
  /** Salário base (fixo) ou valor da diária (diarista). Não se aplica a COMISSIONADO. */
  remunerationValue?: number;
}

export interface EmployeeFilter {
  name?: string;
  active?: boolean;
}

/** Estado da "atuação como profissional" do usuário logado (Meu Perfil do admin). */
export interface EmployeeActingState {
  /** Já existe um cadastro de colaborador vinculado ao usuário. */
  hasProfile: boolean;
  /** Esse cadastro está agendável — aparece no seletor de profissional dos agendamentos. */
  acting: boolean;
  remunerationType: RemunerationType | null;
  remunerationValue: number | null;
}

export const employeesApi = {
  findAll: async (filter: EmployeeFilter = {}, page = 0, size = 10) => {
    const { data } = await api.get<SpringPageResponse<EmployeeData>>('/employees', {
      params: { ...filter, page, size },
    });
    return normalizePage(data);
  },

  /** Lista funcionárias para o fluxo de agendamento público (sem expor email). */
  findAllForBooking: async () => {
    const { data } = await api.get<EmployeeData[]>('/employees/booking');
    return data;
  },

  findById: async (id: number) => {
    const { data } = await api.get<EmployeeData>(`/employees/${id}`);
    return data;
  },

  /** Atuação do admin logado como profissional em agendamentos. */
  getMyActing: async () => {
    const { data } = await api.get<EmployeeActingState>('/employees/me/acting');
    return data;
  },

  setMyActing: async (acting: boolean) => {
    const { data } = await api.put<EmployeeActingState>('/employees/me/acting', { acting });
    return data;
  },

  create: async (employeeData: EmployeeData) => {
    const { data } = await api.post<EmployeeData>('/employees', employeeData);
    return data;
  },

  update: async (id: number, employeeData: EmployeeData) => {
    const { data } = await api.put<EmployeeData>(`/employees/${id}`, employeeData);
    return data;
  },

  delete: async (id: number) => {
    await api.delete(`/employees/${id}`);
  },
};
