import { useEffect, useState } from 'react';
import { X, Calendar, Clock, Inbox } from 'lucide-react';
import { clientsApi } from '../services/clients';
import type { ClientDetailsResponse } from '../services/clients';
import { getApiErrorMessage } from '../../../../utils/apiError';
import { useAlert } from '../../../../hooks/useAlert';
import { maskCPF } from '../../../../utils/formatters';
import { ClientAnamnesisSection } from './ClientAnamnesisSection';

type DrawerTab = 'history' | 'anamnesis';

interface ClientDrawerProps {
  isOpen: boolean;
  onClose: () => void;
  clientId: number | null;
}

export function ClientDrawer({ isOpen, onClose, clientId }: ClientDrawerProps) {
  const [client, setClient] = useState<ClientDetailsResponse | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [tab, setTab] = useState<DrawerTab>('history');
  const { error: showError } = useAlert();

  useEffect(() => {
    setTab('history');
    if (isOpen && clientId) {
      const loadDetails = async () => {
        setIsLoading(true);
        try {
          const details = await clientsApi.findById(clientId);
          setClient(details);
        } catch (error) {
          const msg = getApiErrorMessage(error, 'Erro ao carregar histórico do cliente.');
          await showError(msg);
          onClose();
        } finally {
          setIsLoading(false);
        }
      };
      loadDetails();
    } else {
      setClient(null);
    }
  }, [isOpen, clientId]);

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 overflow-hidden flex justify-end">
      {/* Backdrop with transition */}
      <div
        className="fixed inset-0 bg-black/45 backdrop-blur-xs transition-opacity duration-300 animate-fade-in"
        onClick={onClose}
      />

      {/* Drawer slide-over container */}
      <div className="relative w-full max-w-md bg-white dark:bg-[#161c2a] border-l border-[#eae1e1] dark:border-[#1e293b] shadow-2xl flex flex-col h-full z-10 transition-transform duration-300 transform translate-x-0 animate-scale-up">
        {/* Header */}
        <div className="flex justify-between items-center px-6 py-5 border-b border-[#eae1e1] dark:border-[#1e293b]">
          <h3 className="font-heading text-lg font-bold text-[#3b3036] dark:text-white m-0">
            Histórico do Cliente
          </h3>
          <button
            onClick={onClose}
            className="p-1.5 rounded-full text-[#7a7074] dark:text-gray-400 hover:bg-[#be8a83]/10 dark:hover:bg-gray-800 transition-colors cursor-pointer"
            aria-label="Fechar"
          >
            <X size={20} />
          </button>
        </div>

        {/* Content Area */}
        <div className="flex-1 overflow-y-auto p-6 space-y-6 bg-[#fcf9f9]/30 dark:bg-black/5">
          {isLoading || !client ? (
            <div className="flex flex-col items-center justify-center h-full gap-2 text-sm text-[#3b3036]/60 dark:text-gray-400">
              <div className="animate-spin rounded-full h-8 w-8 border-t-2 border-b-2 border-[#be8a83]"></div>
              <span>Carregando histórico...</span>
            </div>
          ) : (
            <div className="space-y-6">
              <div className="flex gap-1 p-1 bg-[#eae1e1]/50 dark:bg-[#0f172a]/60 rounded-xl">
                {(['history', 'anamnesis'] as DrawerTab[]).map((t) => (
                  <button
                    key={t}
                    type="button"
                    onClick={() => setTab(t)}
                    className={`flex-1 px-3 py-2 rounded-lg text-xs font-semibold transition-all cursor-pointer ${
                      tab === t
                        ? 'bg-white dark:bg-[#161c2a] text-[#be8a83] shadow-sm'
                        : 'text-[#7a7074] dark:text-gray-400 hover:text-[#3b3036] dark:hover:text-white'
                    }`}
                  >
                    {t === 'history' ? 'Histórico' : 'Anamnese'}
                  </button>
                ))}
              </div>

              {tab === 'anamnesis' ? (
                <ClientAnamnesisSection clientId={client.id} />
              ) : (
                <>
              {/* Profile card */}
              <div className="glass-card p-5 space-y-3 dark:bg-[#161c2a]/80">
                <div className="flex items-center gap-3">
                  <div className="h-12 w-12 rounded-full bg-gradient-to-tr from-[#be8a83] to-[#e5a49c] flex items-center justify-center text-white font-bold text-lg shadow-sm uppercase">
                    {client.name.charAt(0)}
                  </div>
                  <div>
                    <h4 className="text-base font-bold text-[#3b3036] dark:text-white m-0">
                      {client.name}
                    </h4>
                    <span className="text-xs text-[#7a7074] dark:text-gray-400">{client.email || 'Sem e-mail cadastrado'}</span>
                  </div>
                </div>

                <div className="grid grid-cols-2 gap-4 pt-3 border-t border-[#eae1e1]/50 dark:border-gray-800 text-xs">
                  <div>
                    <span className="block text-[#7a7074] dark:text-gray-400 font-semibold mb-1 uppercase tracking-wider">
                      Telefone
                    </span>
                    <span className="font-medium text-[#2a2528] dark:text-gray-200">
                      {client.phone || 'Não informado'}
                    </span>
                  </div>
                  <div>
                    <span className="block text-[#7a7074] dark:text-gray-400 font-semibold mb-1 uppercase tracking-wider">
                      CPF
                    </span>
                    <span className="font-medium text-[#2a2528] dark:text-gray-200">
                      {maskCPF(client.cpf) || 'Não informado'}
                    </span>
                  </div>
                </div>
              </div>

              {/* Aggregation statistics */}
              <div className="grid grid-cols-2 gap-4">
                <div className="bg-[#be8a83]/10 dark:bg-[#be8a83]/5 border border-[#be8a83]/20 rounded-2xl p-4 flex flex-col justify-between shadow-xs">
                  <span className="text-xs font-semibold text-[#be8a83] uppercase tracking-wider">
                    Total Agendamentos
                  </span>
                  <span className="text-3xl font-extrabold text-[#3b3036] dark:text-white mt-2">
                    {client.totalAppointments}
                  </span>
                </div>
                <div className="bg-white/50 dark:bg-[#161c2a]/40 border border-[#eae1e1] dark:border-[#1e293b] rounded-2xl p-4 flex flex-col justify-between shadow-xs">
                  <span className="text-xs font-semibold text-[#7a7074] dark:text-gray-400 uppercase tracking-wider">
                    Último Serviço
                  </span>
                  <span className="text-sm font-bold text-[#3b3036] dark:text-white mt-3">
                    {client.lastAppointmentDate
                      ? new Date(client.lastAppointmentDate).toLocaleDateString('pt-BR')
                      : 'Nenhum'}
                  </span>
                </div>
              </div>

              {/* Booking History List */}
              <div className="space-y-3">
                <h4 className="text-xs font-bold text-[#7a7074] dark:text-gray-400 uppercase tracking-wider">
                  Listagem de Agendamentos
                </h4>
                {client.appointments.length === 0 ? (
                  <div className="text-center py-10 px-4 text-sm text-[#7a7074]/80 bg-white/50 dark:bg-[#161c2a]/40 border border-[#eae1e1] dark:border-[#1e293b] rounded-2xl flex flex-col items-center gap-2">
                    <Inbox size={20} className="text-[#be8a83]" />
                    <span>Este cliente não possui nenhum agendamento registrado.</span>
                  </div>
                ) : (
                  <div className="space-y-3">
                    {client.appointments.map((appt) => (
                      <div
                        key={appt.id}
                        className="p-4 border border-[#eae1e1] dark:border-[#1e293b] bg-white/60 dark:bg-[#161c2a]/80 rounded-2xl hover:bg-white dark:hover:bg-[#161c2a] transition-all duration-200 shadow-2xs"
                      >
                        <div className="flex justify-between items-start">
                          <div>
                            <span className="text-sm font-bold text-[#3b3036] dark:text-white">
                              {appt.services.map((s) => s.serviceName).join(', ')}
                            </span>
                            <div className="text-xs text-[#7a7074] dark:text-gray-400 mt-1 flex items-center gap-1">
                              <span>Profissional:</span>
                              <span className="font-semibold">{appt.employeeName}</span>
                            </div>
                          </div>
                          <span
                            className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-3xs font-bold uppercase tracking-wider ${
                              appt.status === 'DONE' || appt.status === 'COMPLETED'
                                ? 'bg-emerald-50 text-emerald-700 dark:bg-emerald-500/10 dark:text-emerald-400 border border-emerald-100 dark:border-emerald-500/20'
                                : appt.status === 'CANCELLED' || appt.status === 'DECLINED'
                                  ? 'bg-rose-50 text-rose-700 dark:bg-rose-500/10 dark:text-rose-400 border border-rose-100 dark:border-rose-500/20'
                                  : 'bg-amber-50 text-amber-700 dark:bg-amber-500/10 dark:text-amber-400 border border-amber-100 dark:border-amber-500/20'
                            }`}
                          >
                            {appt.status === 'DONE' || appt.status === 'COMPLETED'
                              ? 'Concluído'
                              : appt.status === 'CANCELLED'
                                ? 'Cancelado'
                                : appt.status === 'DECLINED'
                                  ? 'Recusado'
                                  : appt.status === 'CONFIRMED'
                                    ? 'Confirmado'
                                    : 'Pendente'}
                          </span>
                        </div>

                        <div className="grid grid-cols-2 gap-2 mt-3 pt-3 border-t border-[#eae1e1]/50 dark:border-gray-800 text-3xs">
                          <div className="flex items-center gap-1.5 text-[#7a7074] dark:text-gray-400">
                            <Calendar size={12} className="text-[#be8a83]" />
                            <span>
                              {appt.scheduledAt
                                ? new Date(appt.scheduledAt).toLocaleDateString('pt-BR')
                                : appt.preferredDate
                                  ? new Date(appt.preferredDate + 'T00:00:00').toLocaleDateString(
                                      'pt-BR'
                                    )
                                  : 'Não definida'}
                            </span>
                          </div>
                          <div className="flex items-center gap-1.5 text-[#7a7074] dark:text-gray-400">
                            <Clock size={12} className="text-[#be8a83]" />
                            <span>
                              {appt.scheduledAt
                                ? new Date(appt.scheduledAt).toLocaleTimeString('pt-BR', {
                                    hour: '2-digit',
                                    minute: '2-digit',
                                  })
                                : 'Horário pendente'}
                            </span>
                          </div>
                        </div>

                        {appt.paymentStatus && (
                          <div className="mt-2.5 text-3xs flex items-center gap-1 border-t border-[#eae1e1]/20 dark:border-gray-800/40 pt-2">
                            <span className="text-gray-400">Status de Pagamento:</span>
                            <span
                              className={`font-bold ${
                                appt.paymentStatus === 'PAID'
                                  ? 'text-emerald-600 dark:text-emerald-400'
                                  : 'text-amber-600 dark:text-amber-400'
                              }`}
                            >
                              {appt.paymentStatus === 'PAID' ? 'PAGO' : 'PENDENTE'}
                            </span>
                          </div>
                        )}

                        {appt.internalNotes && (
                          <div className="mt-2.5 text-3xs border-t border-[#eae1e1]/20 dark:border-gray-800/40 pt-2">
                            <span className="text-gray-400 block mb-0.5">Observação interna:</span>
                            <span className="text-[#3b3036] dark:text-gray-200 italic">
                              {appt.internalNotes}
                            </span>
                          </div>
                        )}
                      </div>
                    ))}
                  </div>
                )}
              </div>
                </>
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
