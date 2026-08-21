import api from '../../../../services/api';
import { normalizePage, type SpringPageResponse } from '../../../../utils/pagination';
export type { PageResponse } from '../../../../utils/pagination';

export type StaffRoleName = 'FUNCIONARIA' | 'GERENTE_DE_ATENDIMENTO';

export type Gender = 'FEMININO' | 'MASCULINO' | 'NAO_BINARIO' | 'OUTRO' | 'PREFIRO_NAO_INFORMAR';

export type PixKeyType = 'CPF' | 'CNPJ' | 'EMAIL' | 'TELEFONE' | 'ALEATORIA';

export type BrazilianState =
  | 'AC' | 'AL' | 'AP' | 'AM' | 'BA' | 'CE' | 'DF' | 'ES' | 'GO' | 'MA' | 'MT' | 'MS' | 'MG'
  | 'PA' | 'PB' | 'PR' | 'PE' | 'PI' | 'RJ' | 'RN' | 'RS' | 'RO' | 'RR' | 'SC' | 'SP' | 'SE' | 'TO';

export type RemunerationType = 'SALARIO_FIXO' | 'COMISSIONADO' | 'FIXO_E_COMISSIONADO';

/**
 * Payload de criação. Note que `cpf` e `pixKey` só existem aqui (na ida) — a API nunca os
 * devolve de volta. O componente que monta este objeto deve descartar a referência após o
 * envio (não guardar em estado de longa duração).
 */
export interface StaffProfileCreatePayload {
  name: string;
  email: string;
  password: string;
  roleName: StaffRoleName;

  fullName: string;
  socialName?: string | null;
  cpf: string;
  birthDate: string;
  gender?: Gender | null;

  phone: string;
  emergencyContactName?: string | null;
  emergencyContactPhone?: string | null;

  zipCode: string;
  street: string;
  streetNumber: string;
  complement?: string | null;
  district: string;
  city: string;
  stateUf: BrazilianState;

  pixKeyType?: PixKeyType | null;
  pixKey?: string | null;

  hiredAt?: string | null;
  notes?: string | null;

  remunerationType?: RemunerationType | null;
  /** Salário base — só se aplica a SALARIO_FIXO/FIXO_E_COMISSIONADO. */
  remunerationValue?: number | null;
}

/** Resposta da API. Nunca contém CPF nem chave PIX em texto claro — só versões mascaradas. */
export interface StaffProfileResponse {
  id: number;
  userId: number;
  name: string;
  email: string;
  roleName: string;
  active: boolean;

  fullName: string;
  socialName?: string | null;
  displayName: string;
  cpfMasked: string;
  birthDate: string;
  gender?: Gender | null;

  phone: string;
  emergencyContactName?: string | null;
  emergencyContactPhone?: string | null;

  zipCode: string;
  street: string;
  streetNumber: string;
  complement?: string | null;
  district: string;
  city: string;
  stateUf: BrazilianState;

  pixKeyType?: PixKeyType | null;
  pixKeyMasked?: string | null;
  hasPixKey: boolean;

  hiredAt?: string | null;
  notes?: string | null;
  createdAt: string;
  updatedAt?: string | null;
}

export interface StaffFilter {
  name?: string;
  roleName?: string;
  active?: boolean;
}

export interface StaffPixQrCodeResponse {
  brCodePayload: string;
  amount: number;
  recipientName: string;
}

export const staffApi = {
  create: async (payload: StaffProfileCreatePayload) => {
    const { data } = await api.post<StaffProfileResponse>('/staff', payload);
    return data;
  },

  findAll: async (filter: StaffFilter = {}, page = 0, size = 20) => {
    const { data } = await api.get<SpringPageResponse<StaffProfileResponse>>('/staff', {
      params: { ...filter, page, size },
    });
    return normalizePage(data);
  },

  findById: async (id: number) => {
    const { data } = await api.get<StaffProfileResponse>(`/staff/${id}`);
    return data;
  },

  /**
   * Gera o payload do QR Code PIX para pagar esta pessoa. A chave PIX nunca trafega nesta
   * chamada nem em nenhuma outra — só o payload pronto para o app do banco escanear.
   */
  generatePixQrCode: async (id: number, amount: number, description?: string) => {
    const { data } = await api.post<StaffPixQrCodeResponse>(`/staff/${id}/pix-qrcode`, {
      amount,
      description,
    });
    return data;
  },
};
