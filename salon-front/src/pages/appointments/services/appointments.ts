import api from '../../../services/api';
import { normalizePage, type SpringPageResponse } from '../../../utils/pagination';
export type { PageResponse } from '../../../utils/pagination';

export interface AppointmentServiceRequestItem {
  serviceId: number;
  /** Serviço como template: sobrescreve preço/duração/observações só para este item. */
  customPrice?: number | null;
  customDurationMin?: number | null;
  customServiceNotes?: string | null;
}

export interface AppointmentProductRequestItem {
  productId: number;
  quantity: number;
  /** Sobrescreve o preço unitário do produto só para este item. */
  customPrice?: number | null;
}

export interface AppointmentExpenseRequestItem {
  description: string;
  /** 'FIXED' (valor em R$) ou 'PERCENTAGE' (% sobre serviços+produtos do agendamento). */
  valueType: 'FIXED' | 'PERCENTAGE';
  value: number;
}

export interface AppointmentRequestBody {
  employeeId: number;
  services: AppointmentServiceRequestItem[];
  /** Produtos vendidos junto do atendimento (só tem efeito no fluxo admin). */
  products?: AppointmentProductRequestItem[];
  /** Fluxo admin: horário já definido */
  scheduledAt?: string | null;
  /** Fluxo cliente: dia preferido */
  preferredDate?: string | null;
  clientNotes?: string | null;
  clientId?: number;
}

export interface AppointmentServiceResponse {
  serviceId: number;
  serviceName: string;
  catalogPrice: number | null;
  catalogDurationMin: number | null;
  customPrice: number | null;
  customDurationMin: number | null;
  customServiceNotes: string | null;
  effectivePrice: number | null;
  effectiveDurationMin: number | null;
}

export interface AppointmentProductResponse {
  productId: number;
  productName: string;
  catalogPrice: number | null;
  quantity: number;
  customPrice: number | null;
  effectiveUnitPrice: number | null;
  effectiveTotalPrice: number | null;
}

export interface AppointmentExpenseResponse {
  id: number;
  description: string;
  valueType: 'FIXED' | 'PERCENTAGE';
  value: number;
  effectiveAmount: number;
}

export interface AppointmentResponse {
  id: number;
  clientId: number;
  clientName: string;
  employeeId: number;
  employeeName: string;
  services: AppointmentServiceResponse[];
  products?: AppointmentProductResponse[];
  expenses?: AppointmentExpenseResponse[];
  totalPrice: number | null;
  totalDurationMin: number | null;
  totalProductsPrice?: number | null;
  totalExpensesAmount?: number | null;
  grandTotal?: number | null;
  scheduledAt: string | null;
  preferredDate?: string | null;
  clientNotes?: string | null;
  internalNotes?: string | null;
  status: string;
  paymentStatus?: string | null;
  paymentId?: number | null;
  pixQrCode?: string | null;
  clientHasSavedCpf?: boolean;
  clientCpfMasked?: string;
}

interface AppointmentCreatePayload {
  employeeId: number;
  services: AppointmentServiceRequestItem[];
  products?: AppointmentProductRequestItem[];
  scheduledAt?: string | null;
  clientId?: number | null;
  preferredDate?: string | null;
  clientNotes?: string | null;
}

function buildCreatePayload(request: AppointmentRequestBody): AppointmentCreatePayload {
  const body: AppointmentCreatePayload = {
    employeeId: request.employeeId,
    services: request.services.map((s) => {
      const item: AppointmentServiceRequestItem = { serviceId: s.serviceId };
      if (s.customPrice != null) {
        item.customPrice = s.customPrice;
      }
      if (s.customDurationMin != null) {
        item.customDurationMin = s.customDurationMin;
      }
      if (s.customServiceNotes != null && s.customServiceNotes.trim() !== '') {
        item.customServiceNotes = s.customServiceNotes.trim();
      }
      return item;
    }),
  };
  if (request.products != null && request.products.length > 0) {
    body.products = request.products.map((p) => {
      const item: AppointmentProductRequestItem = { productId: p.productId, quantity: p.quantity };
      if (p.customPrice != null) {
        item.customPrice = p.customPrice;
      }
      return item;
    });
  }
  if (request.scheduledAt != null && String(request.scheduledAt).trim() !== '') {
    body.scheduledAt = request.scheduledAt;
  }
  if (request.clientId != null) {
    body.clientId = request.clientId;
  }
  if (request.preferredDate != null && String(request.preferredDate).trim() !== '') {
    body.preferredDate = request.preferredDate;
  }
  if (request.clientNotes != null && request.clientNotes.trim() !== '') {
    body.clientNotes = request.clientNotes.trim();
  }
  return body;
}

export interface GeneratePixRequest {
  useSavedCpf: boolean;
  cpf?: string;
}

export interface AppointmentFilter {
  status?: string;
  paymentStatus?: string;
  employeeId?: number;
  clientId?: number;
  clientName?: string;
  startDate?: string;
  endDate?: string;
}

export const appointmentsApi = {
  create: async (request: AppointmentRequestBody) => {
    const { data } = await api.post<AppointmentResponse>(
      '/appointments',
      buildCreatePayload(request)
    );
    return data;
  },

  confirm: async (id: number, scheduledAtIso: string) => {
    const { data } = await api.patch<AppointmentResponse>(`/appointments/${id}/confirm`, {
      scheduledAt: scheduledAtIso,
    });
    return data;
  },

  decline: async (id: number) => {
    const { data } = await api.patch<AppointmentResponse>(`/appointments/${id}/decline`);
    return data;
  },

  updateInternalNotes: async (id: number, internalNotes: string) => {
    const { data } = await api.patch<AppointmentResponse>(`/appointments/${id}/internal-notes`, {
      internalNotes,
    });
    return data;
  },

  getMyAppointments: async () => {
    const { data } = await api.get<AppointmentResponse[]>('/appointments/my');
    return data;
  },

  findAll: async (filter: AppointmentFilter = {}, page = 0, size = 20) => {
    const { data } = await api.get<SpringPageResponse<AppointmentResponse>>('/appointments', {
      params: { ...filter, page, size },
    });
    return normalizePage(data);
  },

  cancel: async (id: number) => {
    const { data } = await api.patch<AppointmentResponse>(`/appointments/${id}/cancel`);
    return data;
  },

  updateStatus: async (id: number, status: string) => {
    const { data } = await api.patch<AppointmentResponse>(`/appointments/${id}/status`, null, {
      params: { status },
    });
    return data;
  },

  updatePaymentStatus: async (id: number, paymentStatus: string) => {
    const { data } = await api.patch<AppointmentResponse>(`/appointments/${id}/payment-status`, null, {
      params: { paymentStatus },
    });
    return data;
  },

  generatePix: async (id: number, payload: GeneratePixRequest) => {
    const { data } = await api.post<AppointmentResponse>(`/appointments/${id}/pix`, payload);
    return data;
  },

  findById: async (id: number) => {
    const { data } = await api.get<AppointmentResponse>(`/appointments/${id}`);
    return data;
  },

  updateProducts: async (id: number, products: AppointmentProductRequestItem[]) => {
    const { data } = await api.patch<AppointmentResponse>(`/appointments/${id}/products`, {
      products,
    });
    return data;
  },

  updateExpenses: async (id: number, expenses: AppointmentExpenseRequestItem[]) => {
    const { data } = await api.patch<AppointmentResponse>(`/appointments/${id}/expenses`, {
      expenses,
    });
    return data;
  },
};
