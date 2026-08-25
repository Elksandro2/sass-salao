import { useState, useEffect } from 'react';
import { appointmentsApi } from './services/appointments';
import type { AppointmentResponse } from './services/appointments';
import { ConfirmDialog } from '../../components/modal/ConfirmDialog';
import { PixPaymentModal } from '../../components/modal/PixPaymentModal';
import { useAlert } from '../../hooks/useAlert';
import { getApiErrorMessage } from '../../utils/apiError';
import { CalendarX } from 'lucide-react';
import { canCancel, canGeneratePix } from '../../utils/appointmentRules';
import { useFeatureFlag } from '../../hooks/useFeatureFlag';

export const MyAppointments = () => {
  const { enabled: mercadoPagoEnabled } = useFeatureFlag('ENABLE_MERCADO_PAGO');
  const [appointments, setAppointments] = useState<AppointmentResponse[]>([]);
  const [isLoading, setIsLoading] = useState(true);

  const [showConfirm, setShowConfirm] = useState(false);
  const [appointmentToCancel, setAppointmentToCancel] = useState<number | null>(null);

  const [showPixModal, setShowPixModal] = useState(false);
  const [currentPixCode, setCurrentPixCode] = useState<string | null>(null);
  const [currentServiceName, setCurrentServiceName] = useState('');
  const [currentPixAppointmentId, setCurrentPixAppointmentId] = useState<number | null>(null);
  const [isGeneratingPix, setIsGeneratingPix] = useState(false);

  const parseInstant = (dateValue: string | number[] | null | undefined): number => {
    if (!dateValue) return 0;
    if (Array.isArray(dateValue)) {
      const [year, month, day, hour, minute] = dateValue as number[];
      return new Date(year, month - 1, day, hour, minute).getTime();
    }
    return new Date(dateValue as string).getTime();
  };

  const sortKey = (apt: AppointmentResponse): number => {
    if (apt.scheduledAt) return parseInstant(apt.scheduledAt);
    if (apt.preferredDate) return new Date(apt.preferredDate + 'T12:00:00').getTime();
    return 0;
  };

  const { error: showError } = useAlert();

  const loadAppointments = async () => {
    setIsLoading(true);
    try {
      const data = await appointmentsApi.getMyAppointments();
      data.sort((a, b) => sortKey(b) - sortKey(a));
      setAppointments(data);
    } catch (error) {
      const msg = getApiErrorMessage(error, 'Erro ao carregar agendamentos');
      await showError(msg);
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    loadAppointments();
  }, []);

  const confirmCancel = async () => {
    if (!appointmentToCancel) return;
    try {
      await appointmentsApi.cancel(appointmentToCancel);
      setShowConfirm(false);
      loadAppointments();
    } catch (error) {
      const msg = getApiErrorMessage(error, 'Erro ao cancelar agendamento.');
      await showError(msg);
    }
  };

  const handleOpenPixModal = (id: number, serviceName: string, existingQrCode?: string | null) => {
    setCurrentPixAppointmentId(id);
    setCurrentServiceName(serviceName);
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
      REQUESTED: 'bg-blue-50 text-blue-700 border border-blue-200',
      CONFIRMED: 'bg-[#be8a83]/10 text-[#a6726b] border border-[#be8a83]/20',
      DECLINED: 'bg-gray-100 text-gray-600 border border-gray-200',
      DONE: 'bg-emerald-50 text-emerald-700 border border-emerald-200',
      CANCELLED: 'bg-rose-50 text-rose-700 border border-rose-200',
    };
    const labels: Record<string, string> = {
      PENDING: 'Pendente',
      REQUESTED: 'Aguardando salão',
      CONFIRMED: 'Confirmado',
      DECLINED: 'Não atendido',
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

  const getCardBorderColor = (status: string) => {
    const borders: Record<string, string> = {
      PENDING: 'border-l-amber-400',
      REQUESTED: 'border-l-blue-400',
      CONFIRMED: 'border-l-[#be8a83]',
      DECLINED: 'border-l-gray-400 opacity-80',
      DONE: 'border-l-emerald-500 opacity-80',
      CANCELLED: 'border-l-rose-400 opacity-60',
    };
    return borders[status] || 'border-l-gray-300';
  };

  const formatDate = (dateValue: string | number[] | null | undefined) => {
    let date: Date;
    if (!dateValue) return { dayStr: '--', timeStr: '--', yearStr: '----' };
    if (Array.isArray(dateValue)) {
      const [year, month, day, hour, minute] = dateValue as number[];
      date = new Date(year, month - 1, day, hour, minute);
    } else {
      date = new Date(dateValue);
    }
    if (isNaN(date.getTime())) return { dayStr: '--', timeStr: '--', yearStr: '----' };
    return {
      dayStr: new Intl.DateTimeFormat('pt-BR', { day: '2-digit', month: 'short' }).format(date),
      timeStr: new Intl.DateTimeFormat('pt-BR', { hour: '2-digit', minute: '2-digit' }).format(
        date
      ),
      yearStr: new Intl.DateTimeFormat('pt-BR', { year: 'numeric' }).format(date),
    };
  };

  const DateColumn = ({ apt }: { apt: AppointmentResponse }) => {
    if (apt.scheduledAt) {
      const d = formatDate(apt.scheduledAt);
      return (
        <>
          <span className="font-heading text-xl font-bold text-[#3b3036]">{d.timeStr}</span>
          <div className="flex flex-col items-center mt-0.5">
            <span className="text-xs font-bold uppercase tracking-widest text-[#be8a83]">
              {d.dayStr}
            </span>
            <span className="text-xs text-gray-400">{d.yearStr}</span>
          </div>
        </>
      );
    }
    if (apt.preferredDate) {
      const d = new Date(apt.preferredDate + 'T12:00:00');
      return (
        <>
          <span className="text-base font-bold text-[#3b3036]">Pref.</span>
          <div className="flex flex-col items-center mt-0.5">
            <span className="text-xs font-bold uppercase tracking-widest text-[#be8a83]">
              {d.toLocaleDateString('pt-BR', { day: '2-digit', month: 'short' })}
            </span>
            <span className="text-xs text-gray-400">{d.getFullYear()}</span>
          </div>
        </>
      );
    }
    return (
      <>
        <span className="text-base font-bold text-gray-400">—</span>
        <span className="text-xs text-gray-400 mt-0.5">A combinar</span>
      </>
    );
  };

  return (
    <div className="space-y-8">
      {/* Header */}
      <div className="text-center">
        <h2 className="font-heading text-3xl font-bold text-[#3b3036]">Minha Agenda</h2>
        <p className="text-sm text-[#3b3036]/60 mt-1">
          Acompanhe solicitações e horários confirmados
        </p>
      </div>

      {isLoading ? (
        <div className="flex flex-col items-center justify-center py-20 gap-3">
          <div className="animate-spin rounded-full h-8 w-8 border-t-2 border-b-2 border-[#be8a83]"></div>
          <p className="text-sm text-[#3b3036]/60 font-medium">Buscando seus horários...</p>
        </div>
      ) : appointments.length === 0 ? (
        <div className="text-center py-16 bg-white rounded-2xl border border-dashed border-gray-200 shadow-xs">
          <CalendarX size={48} className="mx-auto text-gray-300 mb-3" />
          <h3 className="font-semibold text-[#3b3036] text-lg">Nenhum agendamento encontrado</h3>
          <p className="text-sm text-[#3b3036]/60 mt-1">
            Você ainda não marcou nenhum serviço conosco.
          </p>
        </div>
      ) : (
        <div className="grid grid-cols-1 lg:grid-cols-2 xl:grid-cols-3 gap-5">
          {appointments.map((apt) => (
            <div
              key={apt.id}
              className={`bg-white rounded-2xl border border-gray-100 border-l-4 ${getCardBorderColor(apt.status)} flex overflow-hidden shadow-xs transition-all duration-300`}
            >
              {/* Date Column */}
              <div className="bg-gray-50/70 px-4 py-5 flex flex-col justify-center items-center min-w-[110px] border-r border-dashed border-gray-100 text-center space-y-1">
                <DateColumn apt={apt} />
              </div>

              {/* Content */}
              <div className="flex-1 p-4 flex flex-col">
                <div className="flex justify-between items-start gap-2 mb-2">
                  <h4 className="font-bold text-[#3b3036] text-sm leading-tight">
                    {apt.services.map((s) => s.serviceName).join(', ')}
                  </h4>
                  <div className="flex flex-col items-end gap-1 shrink-0">
                    {getStatusBadge(apt.status)}
                    {getPaymentStatusBadge(apt.paymentStatus, apt.pixQrCode)}
                  </div>
                </div>

                {apt.clientNotes && (
                  <p className="text-xs text-[#3b3036]/60 mb-2 leading-relaxed">
                    {apt.clientNotes}
                  </p>
                )}

                <div className="mt-auto space-y-0.5">
                  <span className="block text-xs font-bold uppercase tracking-widest text-gray-400">
                    Profissional
                  </span>
                  <span className="text-sm font-semibold text-[#3b3036]">
                    {apt.employeeName || 'Não especificado'}
                  </span>
                </div>

                {(mercadoPagoEnabled && canGeneratePix(apt)) || canCancel(apt) ? (
                  <div className="mt-3 pt-3 border-t border-dashed border-gray-100 flex flex-col gap-2">
                    {mercadoPagoEnabled && canGeneratePix(apt) && (
                      <>
                        {apt.pixQrCode ? (
                          <button
                            type="button"
                            onClick={() =>
                              handleOpenPixModal(
                                apt.id,
                                apt.services.map((s) => s.serviceName).join(', '),
                                apt.pixQrCode
                              )
                            }
                            className="w-full py-2 bg-emerald-50 text-emerald-700 border border-emerald-200 hover:bg-emerald-100 rounded-xl text-xs font-semibold transition-all cursor-pointer"
                          >
                            Ver QR Code PIX
                          </button>
                        ) : (
                          <button
                            type="button"
                            onClick={() =>
                              handleOpenPixModal(apt.id, apt.services.map((s) => s.serviceName).join(', '), null)
                            }
                            className="w-full py-2 bg-[#be8a83] text-white hover:bg-[#a6726b] rounded-xl text-xs font-semibold transition-all cursor-pointer"
                          >
                            Pagar com PIX
                          </button>
                        )}
                      </>
                    )}

                    {canCancel(apt) && (
                      <button
                        type="button"
                        onClick={() => {
                          setAppointmentToCancel(apt.id);
                          setShowConfirm(true);
                        }}
                        className="w-full py-2 text-rose-600 border border-dashed border-rose-200 bg-transparent hover:bg-rose-50 hover:border-rose-400 rounded-xl text-xs font-semibold transition-all cursor-pointer"
                      >
                        Cancelar Agendamento
                      </button>
                    )}
                  </div>
                ) : null}
              </div>
            </div>
          ))}
        </div>
      )}

      <ConfirmDialog
        show={showConfirm}
        onHide={() => setShowConfirm(false)}
        onConfirm={confirmCancel}
        title="Cancelar Horário"
        message="Puxa, tem certeza que deseja cancelar este agendamento? Esta ação não pode ser desfeita."
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
    </div>
  );
};
