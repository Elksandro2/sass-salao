import { useState, useEffect } from 'react';
import { formatApiDate, formatApiDateTime } from '../../../utils/datetime';
import { Plus, Clock, User as UserIcon, Calendar as CalendarIcon, X, PencilLine } from 'lucide-react';
import { Table } from '../../../components/table/Table';
import { ConfirmDialog } from '../../../components/modal/ConfirmDialog';
import { PixPaymentModal } from '../../../components/modal/PixPaymentModal';
import { PermissionGate } from '../../../components/permissions/PermissionGate';
import { appointmentsApi } from '../../appointments/services/appointments';
import type { AppointmentResponse } from '../../appointments/services/appointments';
import { salonServicesApi } from '../../services/services/services';
import type { SalonServiceData } from '../../services/services/services';
import { productsApi } from '../products/services/products';
import type { ProductData } from '../products/services/products';
import { employeesApi } from '../employees/services/employees';
import type { EmployeeData } from '../employees/services/employees';
import type { UserData } from '../users/services/users';
import { clientsApi } from '../clients/services/clients';
import { usePermission } from '../../../hooks/usePermission';
import { useAuth } from '../../../hooks/useAuth';
import { useAlert } from '../../../hooks/useAlert';
import { getApiErrorMessage } from '../../../utils/apiError';
import {
  AppointmentFiltersBar,
  emptyAppointmentFilters,
  type AppointmentFiltersState,
} from './components/AppointmentFiltersBar';
import {
  ServiceCustomizationPanel,
  type ServiceCustomizationValues,
} from './components/ServiceCustomizationPanel';
import { AppointmentDetailModal } from './components/AppointmentDetailModal';
import {
  canCancel,
  getCancelBlockReason,
  canChangeStatus,
  getStatusChangeBlockReason,
  getValidStatusOptions,
  canChangePaymentStatus,
  getPaymentStatusChangeBlockReason,
  canGeneratePix
} from '../../../utils/appointmentRules';

const selectCls = 'input-premium';
const labelCls = 'label-premium';

function toLocalDateTimeIso(dtLocal: string): string {
  if (!dtLocal) return '';
  return dtLocal.length === 16 ? `${dtLocal}:00` : dtLocal;
}

function formatServiceOption(s: SalonServiceData): string {
  const ref = s.price != null ? ` — a partir de R$ ${s.price.toFixed(2)}` : '';
  return `${s.name}${ref}`;
}

export const AdminAppointments = () => {
  const [appointments, setAppointments] = useState<AppointmentResponse[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [currentPage, setCurrentPage] = useState(1);
  const [totalPages, setTotalPages] = useState(1);
  const [filters, setFilters] = useState<AppointmentFiltersState>(emptyAppointmentFilters);

  const [showModal, setShowModal] = useState(false);
  const [isSaving, setIsSaving] = useState(false);
  const [clients, setClients] = useState<UserData[]>([]);
  const [services, setServices] = useState<SalonServiceData[]>([]);
  const [allServices, setAllServices] = useState<SalonServiceData[]>([]);
  const [employees, setEmployees] = useState<EmployeeData[]>([]);

  const [products, setProducts] = useState<ProductData[]>([]);
  const [selectedClient, setSelectedClient] = useState('');
  const [selectedServiceIds, setSelectedServiceIds] = useState<number[]>([]);
  const [serviceSearch, setServiceSearch] = useState('');
  const [selectedProductIds, setSelectedProductIds] = useState<number[]>([]);
  const [productQuantities, setProductQuantities] = useState<Record<number, string>>({});
  const [selectedEmployee, setSelectedEmployee] = useState('');
  const [selectedDateTime, setSelectedDateTime] = useState('');
  const [customizations, setCustomizations] = useState<Record<number, ServiceCustomizationValues>>({});
  const [detailTarget, setDetailTarget] = useState<AppointmentResponse | null>(null);

  const [showConfirm, setShowConfirm] = useState(false);
  const [appointmentToCancel, setAppointmentToCancel] = useState<number | null>(null);

  const [confirmTarget, setConfirmTarget] = useState<AppointmentResponse | null>(null);
  const [confirmDateTime, setConfirmDateTime] = useState('');
  const [confirmSaving, setConfirmSaving] = useState(false);

  const [showPixModal, setShowPixModal] = useState(false);
  const [currentPixCode, setCurrentPixCode] = useState<string | null>(null);
  const [currentServiceName, setCurrentServiceName] = useState('');
  const [currentPrice, setCurrentPrice] = useState<number | null>(null);
  const [currentPixAppointmentId, setCurrentPixAppointmentId] = useState<number | null>(null);
  const [isGeneratingPix, setIsGeneratingPix] = useState(false);

  const parseDate = (dateValue: string | number[] | null | undefined): number => {
    if (!dateValue) return 0;
    if (Array.isArray(dateValue)) {
      const [year, month, day, hour, minute] = dateValue as number[];
      return new Date(year, month - 1, day, hour, minute).getTime();
    }
    return new Date(dateValue as string).getTime();
  };

  const { error: showError, confirm } = useAlert();
  const canCreateAppointment = usePermission('POST', '/v1/appointments');
  const { user } = useAuth();
  const isFuncionaria = user?.role === 'FUNCIONARIA';
  // FUNCIONARIA só pode criar agendamento pra si mesma (imposto no backend também) — trava o
  // seletor pra ela nem tentar escolher uma colega e levar erro.
  const ownEmployeeId = isFuncionaria
    ? employees.find((e) => e.userId === user?.userId)?.id
    : undefined;

  useEffect(() => {
    if (showModal && isFuncionaria && ownEmployeeId != null) {
      setSelectedEmployee(String(ownEmployeeId));
    }
  }, [showModal, isFuncionaria, ownEmployeeId]);

  const loadAppointments = async () => {
    setIsLoading(true);
    try {
      const response = await appointmentsApi.findAll(
        {
          status: filters.status || undefined,
          employeeId: filters.employeeId ? Number(filters.employeeId) : undefined,
          clientName: filters.clientName || undefined,
          startDate: filters.startDate || undefined,
          endDate: filters.endDate || undefined,
        },
        currentPage - 1,
        20
      );
      const data = response.content;
      data.sort((a, b) => {
        const ta = a.scheduledAt
          ? parseDate(a.scheduledAt)
          : a.preferredDate
            ? new Date(a.preferredDate + 'T12:00:00').getTime()
            : 0;
        const tb = b.scheduledAt
          ? parseDate(b.scheduledAt)
          : b.preferredDate
            ? new Date(b.preferredDate + 'T12:00:00').getTime()
            : 0;
        return tb - ta;
      });
      setAppointments(data);
      setTotalPages(response.totalPages || 1);
    } catch (err) {
      await showError('Erro ao carregar agendamentos');
    } finally {
      setIsLoading(false);
    }
  };

  const loadFormData = async () => {
    try {
      const [clientsResponse, servicesResponse, productsResponse] = await Promise.all([
        clientsApi.findAll({ active: true }, 0, 1000),
        salonServicesApi.findAll({}, 0, 1000),
        productsApi.findAll({ active: true }, 0, 1000),
      ]);
      const servicesData = servicesResponse.content;
      setClients(clientsResponse.content);
      setServices(servicesData.filter((s) => s.active));
      setAllServices(servicesData);
      setProducts(productsResponse.content);
    } catch (err) {
      await showError('Erro ao carregar dados do formulário');
    }
  };

  useEffect(() => {
    loadAppointments();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [currentPage, filters]);

  useEffect(() => {
    // Endpoint de booking (permission-safe) — usado tanto pro filtro de profissional
    // quanto pelo modal de criação, evita 403 pra quem só visualiza (ex.: FUNCIONARIA).
    employeesApi.findAllForBooking().then(setEmployees).catch(() => setEmployees([]));
  }, []);

  const toggleService = (serviceId: number) => {
    setSelectedServiceIds((prev) => {
      if (prev.includes(serviceId)) {
        return prev.filter((id) => id !== serviceId);
      }
      return [...prev, serviceId];
    });
    // Serviço como template: ao selecionar um serviço, os campos de personalização
    // partem dos valores padrão do catálogo (o usuário decide se quer editá-los).
    setCustomizations((prev) => {
      if (prev[serviceId]) return prev;
      const service = allServices.find((s) => s.id === serviceId);
      return {
        ...prev,
        [serviceId]: {
          price: service?.price != null ? String(service.price) : '',
          durationMin: service?.durationMin != null ? String(service.durationMin) : '',
          notes: '',
        },
      };
    });
  };

  const toggleProduct = (productId: number) => {
    setSelectedProductIds((prev) => {
      if (prev.includes(productId)) {
        return prev.filter((id) => id !== productId);
      }
      return [...prev, productId];
    });
    setProductQuantities((prev) => (prev[productId] ? prev : { ...prev, [productId]: '1' }));
  };

  useEffect(() => {
    // Dados do formulário (clientes/serviços) só são úteis para quem pode abrir o modal
    // de "Novo Agendamento" — evita 403 para quem só visualiza (ex.: FUNCIONARIA).
    if (canCreateAppointment) {
      loadFormData();
    }
  }, [canCreateAppointment]);

  const handleFilterChange = (patch: Partial<AppointmentFiltersState>) => {
    setFilters((prev) => ({ ...prev, ...patch }));
    setCurrentPage(1);
  };

  const handleClearFilters = () => {
    setFilters(emptyAppointmentFilters);
    setCurrentPage(1);
  };

  const handleStatusChange = async (id: number, newStatus: string) => {
    try {
      await appointmentsApi.updateStatus(id, newStatus);
      loadAppointments();
    } catch (error) {
      await showError('Erro ao atualizar status');
    }
  };

  const handleCreateAppointment = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedClient || selectedServiceIds.length === 0 || !selectedEmployee || !selectedDateTime) {
      await showError('Preencha todos os campos, incluindo ao menos um serviço, data e hora');
      return;
    }
    setIsSaving(true);
    try {
      // Serviço como template: só manda override se o valor realmente difere do
      // catálogo — se o usuário não mexeu no campo, ele fica null (usa o padrão).
      const services = selectedServiceIds.map((serviceId) => {
        const service = allServices.find((s) => s.id === serviceId);
        const values = customizations[serviceId] ?? { price: '', durationMin: '', notes: '' };
        const priceNum = values.price === '' ? null : Number(values.price);
        const durationNum = values.durationMin === '' ? null : Number(values.durationMin);
        const customPrice = priceNum != null && priceNum !== service?.price ? priceNum : null;
        const customDurationMin =
          durationNum != null && durationNum !== service?.durationMin ? durationNum : null;
        return {
          serviceId,
          customPrice,
          customDurationMin,
          customServiceNotes: values.notes || null,
        };
      });

      const productsPayload = selectedProductIds.map((productId) => ({
        productId,
        quantity: Number(productQuantities[productId] ?? '1') || 1,
      }));

      await appointmentsApi.create({
        clientId: Number(selectedClient),
        services,
        products: productsPayload,
        employeeId: Number(selectedEmployee),
        scheduledAt: toLocalDateTimeIso(selectedDateTime),
      });
      setShowModal(false);
      loadAppointments();
      setSelectedClient('');
      setSelectedServiceIds([]);
      setCustomizations({});
      setServiceSearch('');
      setSelectedProductIds([]);
      setProductQuantities({});
      setSelectedEmployee('');
      setSelectedDateTime('');
    } catch (error) {
      const msg = getApiErrorMessage(error, 'Erro ao criar agendamento');
      await showError(msg);
    } finally {
      setIsSaving(false);
    }
  };

  const confirmCancel = async () => {
    if (!appointmentToCancel) return;
    try {
      await appointmentsApi.cancel(appointmentToCancel);
      setShowConfirm(false);
      loadAppointments();
    } catch (error) {
      await showError('Erro ao cancelar agendamento');
    }
  };

  const submitConfirm = async () => {
    if (!confirmTarget || !confirmDateTime) return;
    setConfirmSaving(true);
    try {
      await appointmentsApi.confirm(confirmTarget.id, toLocalDateTimeIso(confirmDateTime));
      setConfirmTarget(null);
      loadAppointments();
    } catch (error) {
      const msg = getApiErrorMessage(error, 'Erro ao confirmar horário');
      await showError(msg);
    } finally {
      setConfirmSaving(false);
    }
  };

  const handleDecline = async (id: number) => {
    const confirmed = await confirm('Recusar esta solicitação?');
    if (!confirmed) return;
    try {
      await appointmentsApi.decline(id);
      loadAppointments();
    } catch (error) {
      const msg = getApiErrorMessage(error, 'Erro ao recusar');
      await showError(msg);
    }
  };

  const handlePaymentStatusChange = async (id: number, newPaymentStatus: string) => {
    try {
      await appointmentsApi.updatePaymentStatus(id, newPaymentStatus);
      loadAppointments();
    } catch (error) {
      await showError('Erro ao atualizar status de pagamento');
    }
  };

  const handleOpenPixModal = (id: number, serviceName: string, price: number | null, existingQrCode?: string | null) => {
    setCurrentPixAppointmentId(id);
    setCurrentServiceName(serviceName);
    setCurrentPrice(price);
    setCurrentPixCode(existingQrCode ?? null);
    setShowPixModal(true);
  };

  const handleGeneratePix = async (payload: { useSavedCpf: boolean; cpf?: string }) => {
    if (!currentPixAppointmentId) return;
    setIsGeneratingPix(true);
    try {
      const data = await appointmentsApi.generatePix(currentPixAppointmentId, payload);
      if (data.pixQrCode) {
        setCurrentPixCode(data.pixQrCode);
        setAppointments((prev) =>
          prev.map((apt) =>
            apt.id === currentPixAppointmentId
              ? {
                  ...apt,
                  paymentStatus: data.paymentStatus || 'PENDING',
                  pixQrCode: data.pixQrCode,
                  clientHasSavedCpf: data.clientHasSavedCpf,
                  clientCpfMasked: data.clientCpfMasked,
                }
              : apt
          )
        );
      } else {
        await showError('O código PIX não pôde ser gerado.');
      }
    } catch (error) {
      const msg = getApiErrorMessage(error, 'Erro ao gerar pagamento via PIX');
      await showError(msg);
      throw error;
    } finally {
      setIsGeneratingPix(false);
    }
  };

  const getPaymentStatusBadge = (
    paymentStatus: string | null | undefined,
    pixQrCode: string | null | undefined
  ) => {
    if (!paymentStatus) return null;
    const styles: Record<string, string> = {
      PENDING: 'bg-amber-50 text-amber-700 border border-amber-200',
      PAID: 'bg-emerald-50 text-emerald-700 border border-emerald-200',
      CANCELLED: 'bg-rose-50 text-rose-700 border border-rose-200',
      MANUAL: 'bg-blue-50 text-blue-700 border border-blue-200',
    };
    const labels: Record<string, string> = {
      PENDING: pixQrCode ? 'PIX gerado (Aguardando)' : 'Pagamento Pendente',
      PAID: 'Pago',
      CANCELLED: 'Pagamento Cancelado',
      MANUAL: 'Pago Manualmente',
    };
    return (
      <span
        className={`inline-flex items-center px-2 py-0.5 rounded-md text-[10px] font-bold ${styles[paymentStatus] || 'bg-gray-100 text-gray-600 border border-gray-200'}`}
      >
        {labels[paymentStatus] || paymentStatus}
      </span>
    );
  };

  const getStatusBadge = (status: string) => {
    const styles: Record<string, string> = {
      PENDING: 'bg-amber-50 text-amber-700 border border-amber-200',
      REQUESTED: 'bg-sky-50 text-sky-700 border border-sky-200',
      CONFIRMED: 'bg-[#be8a83]/10 text-[#a6726b] border border-[#be8a83]/20',
      DECLINED: 'bg-gray-100 text-gray-600 border border-gray-200',
      DONE: 'bg-emerald-50 text-emerald-700 border border-emerald-200',
      CANCELLED: 'bg-rose-50 text-rose-700 border border-rose-200',
    };
    const labels: Record<string, string> = {
      PENDING: 'Pendente',
      REQUESTED: 'Solicitado',
      CONFIRMED: 'Confirmado',
      DECLINED: 'Recusado',
      DONE: 'Concluído',
      CANCELLED: 'Cancelado',
    };
    return (
      <span
        className={`inline-flex items-center px-2.5 py-1 rounded-full text-xs font-semibold whitespace-nowrap ${styles[status] || 'bg-gray-100 text-gray-600 border border-gray-200'}`}
      >
        {labels[status] || status}
      </span>
    );
  };

  const columns = [
    {
      key: 'scheduledAt',
      label: 'Data / hora',
      render: (item: AppointmentResponse) =>
        item.scheduledAt
          ? formatApiDateTime(item.scheduledAt)
          : item.preferredDate
            ? `Pref.: ${formatApiDate(item.preferredDate)} (a combinar)`
            : 'A combinar',
    },
    { key: 'clientName', label: 'Cliente' },
    { key: 'employeeName', label: 'Profissional' },
    {
      key: 'serviceName',
      label: 'Serviço',
      render: (item: AppointmentResponse) => {
        const isCustomized = item.services.some(
          (s) => s.customPrice != null || s.customDurationMin != null || !!s.customServiceNotes
        );
        const names = item.services.map((s) => s.serviceName).join(', ');
        return (
          <button
            type="button"
            onClick={() => setDetailTarget(item)}
            className="flex items-center gap-1.5 text-left hover:underline cursor-pointer"
            title="Ver detalhes do agendamento"
          >
            <span>{names}</span>
            {isCustomized && (
              <span title="Serviço personalizado para este agendamento" className="shrink-0 inline-flex">
                <PencilLine size={13} className="text-[#be8a83]" />
              </span>
            )}
          </button>
        );
      },
    },
    {
      key: 'notes',
      label: 'Obs.',
      render: (item: AppointmentResponse) => (
        <span className="text-xs text-gray-500 max-w-[200px] inline-block truncate">
          {item.clientNotes
            ? `${item.clientNotes.slice(0, 60)}${item.clientNotes.length > 60 ? '…' : ''}`
            : '—'}
        </span>
      ),
    },
    {
      key: 'status',
      label: 'Status / Pagamento',
      render: (item: AppointmentResponse) => (
        <div className="flex flex-col items-start gap-2.5">
          <div className="flex flex-wrap gap-1">
            {getStatusBadge(item.status)}
            {getPaymentStatusBadge(item.paymentStatus, item.pixQrCode)}
          </div>

          {item.status === 'REQUESTED' && (
            <div className="flex flex-col gap-1.5 mt-1" style={{ width: '150px' }}>
              <PermissionGate method="PATCH" endpoint={`/v1/appointments/${item.id}/confirm`}>
                <button
                  onClick={() => {
                    setConfirmTarget(item);
                    setConfirmDateTime('');
                  }}
                  className="w-full text-center px-2.5 py-1.5 bg-[#be8a83] text-white hover:bg-[#a6726b] text-xs font-semibold rounded-lg transition-all cursor-pointer"
                >
                  Definir horário
                </button>
              </PermissionGate>
              <PermissionGate method="PATCH" endpoint={`/v1/appointments/${item.id}/decline`}>
                <button
                  onClick={() => handleDecline(item.id)}
                  className="w-full text-center px-2.5 py-1.5 border border-rose-200 text-rose-600 hover:bg-rose-50 text-xs font-semibold rounded-lg transition-all cursor-pointer"
                >
                  Recusar
                </button>
              </PermissionGate>
            </div>
          )}

          <div className="flex flex-col gap-2 mt-1">
            <PermissionGate method="PATCH" endpoint={`/v1/appointments/${item.id}/status`}>
              {item.status !== 'REQUESTED' && (
                <div className="flex flex-col gap-0.5">
                  <span className="text-[10px] font-bold text-gray-400 uppercase tracking-wider">Status Agendamento</span>
                  <select
                    value={item.status}
                    disabled={!canChangeStatus(item)}
                    title={getStatusChangeBlockReason(item) || undefined}
                    onChange={(e) => handleStatusChange(item.id, e.target.value)}
                    className={`text-xs px-2.5 py-1.5 border rounded-lg outline-none focus:ring-1 focus:ring-[#be8a83]/20 focus:border-[#be8a83] transition-all ${
                      !canChangeStatus(item)
                        ? 'bg-gray-100 border-gray-200 text-gray-400 cursor-not-allowed opacity-60'
                        : 'bg-gray-50 border-gray-200 cursor-pointer'
                    }`}
                    style={{ width: '150px' }}
                  >
                    {!getValidStatusOptions(item).some(opt => opt.value === item.status) && (
                      <option value={item.status}>
                        {item.status === 'CANCELLED' ? 'Cancelado' : item.status === 'DECLINED' ? 'Recusado' : item.status}
                      </option>
                    )}
                    {getValidStatusOptions(item).map((opt) => (
                      <option key={opt.value} value={opt.value}>
                        {opt.label}
                      </option>
                    ))}
                  </select>
                </div>
              )}
            </PermissionGate>

            <PermissionGate method="PATCH" endpoint={`/v1/appointments/${item.id}/payment-status`}>
              {item.paymentStatus !== 'PAID' && (
                <div className="flex flex-col gap-0.5">
                  <span className="text-[10px] font-bold text-gray-400 uppercase tracking-wider">Status Pagamento</span>
                  <select
                    value={item.paymentStatus || 'PENDING'}
                    disabled={!canChangePaymentStatus(item)}
                    title={getPaymentStatusChangeBlockReason(item) || undefined}
                    onChange={(e) => handlePaymentStatusChange(item.id, e.target.value)}
                    className={`text-xs px-2.5 py-1.5 border rounded-lg outline-none focus:ring-1 focus:ring-[#be8a83]/20 focus:border-[#be8a83] transition-all ${
                      !canChangePaymentStatus(item)
                        ? 'bg-gray-100 border-gray-200 text-gray-400 cursor-not-allowed opacity-60'
                        : 'bg-gray-50 border-gray-200 cursor-pointer'
                    }`}
                    style={{ width: '150px' }}
                  >
                    <option value="PENDING">Pendente</option>
                    <option value="MANUAL">Pago Manualmente</option>
                    <option value="CANCELLED">Cancelado</option>
                  </select>
                </div>
              )}
            </PermissionGate>
          </div>
        </div>
      ),
    },
    {
      key: 'actions',
      label: 'Ações',
      render: (item: AppointmentResponse) => {
        const price = item.totalPrice;
        const serviceNames = item.services.map((s) => s.serviceName).join(', ');
        const cancelDisabled = !canCancel(item);
        const cancelReason = getCancelBlockReason(item);

        return (
          <div className="flex flex-col gap-1.5">
            <PermissionGate method="PATCH" endpoint={`/v1/appointments/${item.id}/cancel`}>
              <button
                onClick={() => {
                  if (!cancelDisabled) {
                    setAppointmentToCancel(item.id);
                    setShowConfirm(true);
                  }
                }}
                disabled={cancelDisabled}
                title={cancelReason || undefined}
                className={`w-full text-center px-2.5 py-1.5 border text-xs font-semibold rounded-lg transition-all whitespace-nowrap ${
                  cancelDisabled
                    ? 'border-gray-200 text-gray-400 bg-gray-50 cursor-not-allowed opacity-60'
                    : 'border-rose-200 text-rose-600 hover:bg-rose-50 cursor-pointer hover:border-rose-300'
                }`}
              >
                Cancelar
              </button>
            </PermissionGate>

            {canGeneratePix(item) && (
              <>
                {item.pixQrCode ? (
                  <button
                    type="button"
                    onClick={() => handleOpenPixModal(item.id, serviceNames, price, item.pixQrCode)}
                    className="w-full text-center px-2.5 py-1.5 bg-emerald-50 text-emerald-700 border border-emerald-200 hover:bg-emerald-100 rounded-lg text-xs font-semibold transition-all whitespace-nowrap cursor-pointer"
                  >
                    Ver PIX
                  </button>
                ) : (
                  <button
                    type="button"
                    onClick={() => handleOpenPixModal(item.id, serviceNames, price, null)}
                    className="w-full text-center px-2.5 py-1.5 bg-[#be8a83] text-white hover:bg-[#a6726b] rounded-lg text-xs font-semibold transition-all whitespace-nowrap cursor-pointer"
                  >
                    Pagar com PIX
                  </button>
                )}
              </>
            )}
          </div>
        );
      },
    },
  ];

  return (
    <>
      <div className="space-y-6 animate-fade-in-up">
        <div className="flex justify-between items-center">
          <h2 className="font-heading text-2xl font-bold text-[#3b3036]">Agendamentos (Admin)</h2>
          <PermissionGate method="POST" endpoint="/v1/appointments">
            <button
              onClick={() => setShowModal(true)}
              className="btn-premium font-semibold shadow-md shadow-[#be8a83]/10"
            >
              <Plus size={18} /> Novo Agendamento
            </button>
          </PermissionGate>
        </div>

        <AppointmentFiltersBar
          filters={filters}
          employees={employees}
          onChange={handleFilterChange}
          onClear={handleClearFilters}
        />

        {isLoading ? (
          <div className="flex items-center gap-3 text-sm text-[#3b3036]/60 py-10 justify-center">
            <div className="animate-spin rounded-full h-6 w-6 border-t-2 border-b-2 border-[#be8a83]"></div>
            <span>Carregando agendamentos...</span>
          </div>
        ) : (
          <Table
            columns={columns}
            data={appointments}
            keyExtractor={(item) => item.id?.toString() || Math.random().toString()}
            currentPage={currentPage}
            totalPages={totalPages}
            onPageChange={setCurrentPage}
          />
        )}
      </div>

      {/* Create Appointment Modal */}
      {showModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-[#261f23]/40 backdrop-blur-md">
          <div className="bg-white rounded-2xl shadow-xl w-full max-w-2xl border border-[#eae1e1]/85 overflow-hidden animate-scale-up">
            <div className="flex items-center justify-between px-6 py-4 border-b border-[#eae1e1] bg-[#fcf9f9]/50">
              <h3 className="font-heading text-lg font-bold text-[#3b3036]">Novo Agendamento</h3>
              <button
                onClick={() => setShowModal(false)}
                className="p-1.5 text-gray-400 hover:text-gray-600 hover:bg-gray-100 rounded-lg transition-all"
              >
                <X size={20} />
              </button>
            </div>
            <form onSubmit={handleCreateAppointment}>
              <div className="p-6 space-y-4">
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                  <div>
                    <label className={labelCls}>
                      <UserIcon size={14} className="inline mr-1" />
                      Cliente
                    </label>
                    <select
                      value={selectedClient}
                      onChange={(e) => setSelectedClient(e.target.value)}
                      required
                      className={selectCls}
                    >
                      <option value="">Selecione o cliente</option>
                      {clients.map((c) => (
                        <option key={c.id} value={c.id}>
                          {c.name}
                        </option>
                      ))}
                    </select>
                  </div>
                  <div>
                    <label className={labelCls}>
                      <UserIcon size={14} className="inline mr-1" />
                      Profissional
                    </label>
                    <select
                      value={selectedEmployee}
                      onChange={(e) => setSelectedEmployee(e.target.value)}
                      required
                      disabled={isFuncionaria}
                      className={`${selectCls} ${isFuncionaria ? 'opacity-60 cursor-not-allowed' : ''}`}
                    >
                      <option value="">Selecione a profissional</option>
                      {employees.map((e) => (
                        <option key={e.id} value={e.id}>
                          {e.name}
                        </option>
                      ))}
                    </select>
                    {isFuncionaria && (
                      <p className="text-xs text-gray-400 mt-1">
                        Você só pode criar agendamentos pra você mesma.
                      </p>
                    )}
                  </div>
                  <div>
                    <label htmlFor="create-datetime" className={labelCls}>
                      <CalendarIcon size={14} className="inline mr-1" />
                      Data e hora
                    </label>
                    <input
                      id="create-datetime"
                      type="datetime-local"
                      value={selectedDateTime}
                      onChange={(e) => setSelectedDateTime(e.target.value)}
                      required
                      className={selectCls}
                    />
                    <p className="text-xs text-gray-400 mt-1">
                      Horário livre — sem grade fixa no sistema.
                    </p>
                  </div>
                </div>

                <div>
                  <label className={labelCls}>
                    <Clock size={14} className="inline mr-1" />
                    Serviços
                  </label>
                  {services.length > 6 && (
                    <input
                      type="text"
                      value={serviceSearch}
                      onChange={(e) => setServiceSearch(e.target.value)}
                      placeholder="Buscar serviço por nome..."
                      className={`${selectCls} mb-2`}
                    />
                  )}
                  <div className="border border-[#eae1e1] rounded-xl max-h-40 overflow-y-auto divide-y divide-[#eae1e1]/70">
                    {services
                      .filter((s) => s.name.toLowerCase().includes(serviceSearch.trim().toLowerCase()))
                      .map((s) => (
                        <label
                          key={s.id}
                          className="flex items-center gap-2.5 px-3.5 py-2.5 text-sm text-[#3b3036] hover:bg-[#fdf6f5] cursor-pointer transition-all"
                        >
                          <input
                            type="checkbox"
                            checked={selectedServiceIds.includes(s.id!)}
                            onChange={() => toggleService(s.id!)}
                            className="accent-[#be8a83]"
                          />
                          {formatServiceOption(s)}
                        </label>
                      ))}
                  </div>
                  {selectedServiceIds.length === 0 && (
                    <p className="text-xs text-gray-400 mt-1">Selecione ao menos um serviço.</p>
                  )}
                </div>

                {selectedServiceIds.map((serviceId) => {
                  const service = allServices.find((s) => s.id === serviceId);
                  return (
                    <ServiceCustomizationPanel
                      key={serviceId}
                      serviceName={service?.name}
                      defaultPrice={service?.price ?? null}
                      defaultDurationMin={service?.durationMin ?? null}
                      values={customizations[serviceId] ?? { price: '', durationMin: '', notes: '' }}
                      onChange={(values) =>
                        setCustomizations((prev) => ({ ...prev, [serviceId]: values }))
                      }
                    />
                  );
                })}

                {products.length > 0 && (
                  <div>
                    <label className={labelCls}>Produtos vendidos (opcional)</label>
                    <div className="border border-[#eae1e1] rounded-xl max-h-40 overflow-y-auto divide-y divide-[#eae1e1]/70">
                      {products.map((p) => (
                        <div
                          key={p.id}
                          className="flex items-center gap-2.5 px-3.5 py-2.5 text-sm text-[#3b3036] hover:bg-[#fdf6f5] transition-all"
                        >
                          <label className="flex items-center gap-2.5 flex-1 cursor-pointer">
                            <input
                              type="checkbox"
                              checked={selectedProductIds.includes(p.id!)}
                              onChange={() => toggleProduct(p.id!)}
                              className="accent-[#be8a83]"
                            />
                            {p.name} — R$ {p.price.toFixed(2)}
                          </label>
                          {selectedProductIds.includes(p.id!) && (
                            <input
                              type="number"
                              min={1}
                              className={`${selectCls} w-16 py-1`}
                              value={productQuantities[p.id!] ?? '1'}
                              onChange={(e) =>
                                setProductQuantities((prev) => ({ ...prev, [p.id!]: e.target.value }))
                              }
                            />
                          )}
                        </div>
                      ))}
                    </div>
                  </div>
                )}

                <div className="p-3.5 bg-amber-50 border border-amber-100 rounded-xl text-xs text-amber-700">
                  O agendamento nasce já <strong>confirmado</strong>. Clientes pelo site enviam uma{' '}
                  <strong>solicitação</strong> para você aceitar e marcar o horário.
                </div>
              </div>
              <div className="flex justify-end gap-3 px-6 py-4 border-t border-[#eae1e1] bg-[#fcf9f9]/50">
                <button
                  type="button"
                  onClick={() => setShowModal(false)}
                  className="px-5 py-2.5 border border-[#eae1e1] font-semibold text-sm text-[#3b3036] hover:bg-white hover:border-[#be8a83]/50 rounded-xl transition-all"
                >
                  Fechar
                </button>
                <button
                  type="submit"
                  disabled={isSaving}
                  className="px-5 py-2.5 bg-[#be8a83] text-white hover:bg-[#a6726b] font-semibold text-sm rounded-xl transition-all shadow-md shadow-[#be8a83]/10 disabled:opacity-50"
                >
                  {isSaving ? 'Salvando...' : 'Criar Agendamento'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Confirm DateTime Modal */}
      {confirmTarget && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-[#261f23]/40 backdrop-blur-md">
          <div className="bg-white rounded-2xl shadow-xl w-full max-w-md border border-[#eae1e1]/85 overflow-hidden animate-scale-up">
            <div className="flex items-center justify-between px-6 py-4 border-b border-[#eae1e1] bg-[#fcf9f9]/50">
              <h3 className="font-heading text-lg font-bold text-[#3b3036]">Confirmar horário</h3>
              {!confirmSaving && (
                <button
                  onClick={() => setConfirmTarget(null)}
                  className="p-1.5 text-gray-400 hover:text-gray-600 hover:bg-gray-100 rounded-lg transition-all"
                >
                  <X size={20} />
                </button>
              )}
            </div>
            <div className="p-6 space-y-4">
              <p className="text-xs text-gray-500 leading-relaxed">
                Defina data e hora para <strong>{confirmTarget.clientName}</strong>. Conflitos com
                outros agendamentos confirmados do mesmo profissional serão bloqueados.
              </p>
              <div>
                <label htmlFor="confirm-datetime" className={labelCls}>Data e hora</label>
                <input
                  id="confirm-datetime"
                  type="datetime-local"
                  value={confirmDateTime}
                  onChange={(e) => setConfirmDateTime(e.target.value)}
                  className={selectCls}
                />
              </div>
            </div>
            <div className="flex justify-end gap-3 px-6 py-4 border-t border-[#eae1e1] bg-[#fcf9f9]/50">
              <button
                type="button"
                onClick={() => setConfirmTarget(null)}
                disabled={confirmSaving}
                className="px-5 py-2.5 border border-[#eae1e1] font-semibold text-sm text-[#3b3036] hover:bg-white hover:border-[#be8a83]/50 rounded-xl transition-all disabled:opacity-50"
              >
                Cancelar
              </button>
              <button
                onClick={submitConfirm}
                disabled={confirmSaving || !confirmDateTime}
                className="px-5 py-2.5 bg-[#be8a83] text-white hover:bg-[#a6726b] font-semibold text-sm rounded-xl transition-all shadow-md shadow-[#be8a83]/10 disabled:opacity-50"
              >
                {confirmSaving ? 'Salvando...' : 'Confirmar solicitação'}
              </button>
            </div>
          </div>
        </div>
      )}

      <AppointmentDetailModal
        appointment={detailTarget}
        onClose={() => setDetailTarget(null)}
        onNotesSaved={(updated) => {
          setDetailTarget(updated);
          setAppointments((prev) => prev.map((a) => (a.id === updated.id ? updated : a)));
        }}
      />

      <ConfirmDialog
        show={showConfirm}
        onHide={() => setShowConfirm(false)}
        onConfirm={confirmCancel}
        title="Cancelar Agendamento"
        message="Tem certeza que deseja cancelar este agendamento? Esta ação não pode ser desfeita."
      />

      <PixPaymentModal
        show={showPixModal}
        onHide={() => {
          setShowPixModal(false);
          setCurrentPixCode(null);
        }}
        onGeneratePix={handleGeneratePix}
        pixQrCode={currentPixCode}
        serviceName={currentServiceName}
        price={currentPrice}
        isGenerating={isGeneratingPix}
        clientHasSavedCpf={appointments.find((apt) => apt.id === currentPixAppointmentId)?.clientHasSavedCpf}
        clientCpfMasked={appointments.find((apt) => apt.id === currentPixAppointmentId)?.clientCpfMasked}
        appointmentId={currentPixAppointmentId}
        onPaymentSuccess={(updatedApt) => {
          setAppointments((prev) =>
            prev.map((apt) => (apt.id === updatedApt.id ? updatedApt : apt))
          );
        }}
      />
    </>
  );
};
