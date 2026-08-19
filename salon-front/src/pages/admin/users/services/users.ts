import api from '../../../../services/api';
import { normalizePage, type SpringPageResponse } from '../../../../utils/pagination';
export type { PageResponse } from '../../../../utils/pagination';

export interface UserData {
  id: number;
  name: string;
  /** Nula pra cliente cadastrado só com nome — email é funcionalidade desligada por feature flag. */
  email: string | null;
  phone: string;
  cpf?: string | null;
  role: string;
  active: boolean;
  createdAt: string;
}

export interface UserFilter {
  name?: string;
  email?: string;
  phone?: string;
  active?: boolean;
  roleId?: number;
}

export interface UserUpdateRequest {
  name?: string;
  email?: string;
  password?: string;
  phone?: string;
  cpf?: string;
  active?: boolean;
  roleId?: number;
}

export interface UserCreateRequest {
  name: string;
  email?: string | null;
  password?: string;
  phone?: string;
  cpf?: string;
  active?: boolean;
  roleId: number;
}

export interface UpdateCpfRequest {
  cpf: string;
}

export interface UserCpfInfoResponse {
  hasSavedCpf: boolean;
  cpfMasked: string;
}

export const usersApi = {
  findAll: async (filter: UserFilter, page: number, size: number) => {
    const { data } = await api.get<SpringPageResponse<UserData>>('/users', {
      params: {
        ...filter,
        page,
        size,
      },
    });
    return normalizePage(data);
  },

  create: async (createData: UserCreateRequest) => {
    const { data } = await api.post<UserData>('/users', createData);
    return data;
  },

  findById: async (id: number) => {
    const { data } = await api.get<UserData>(`/users/details/id/${id}`);
    return data;
  },

  update: async (id: number, updateData: UserUpdateRequest) => {
    const { data } = await api.patch<UserData>(`/users/${id}`, updateData);
    return data;
  },

  delete: async (id: number) => {
    await api.delete(`/users/${id}`);
  },

  restore: async (id: number) => {
    const { data } = await api.patch<UserData>(`/users/${id}/restore`);
    return data;
  },

  updateMyCpf: async (cpf: string) => {
    const { data } = await api.patch<UserData>('/users/me/cpf', { cpf });
    return data;
  },

  getMyCpfInfo: async () => {
    const { data } = await api.get<UserCpfInfoResponse>('/users/me/cpf-info');
    return data;
  },
};
