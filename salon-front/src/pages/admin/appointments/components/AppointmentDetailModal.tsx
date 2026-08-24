import { useEffect, useState } from 'react';
import { X } from 'lucide-react';
import { appointmentsApi } from '../../../appointments/services/appointments';
import type { AppointmentResponse } from '../../../appointments/services/appointments';
import { formatApiDate, formatApiDateTime } from '../../../../utils/datetime';
import { PermissionGate } from '../../../../components/permissions/PermissionGate';
import { useAlert } from '../../../../hooks/useAlert';
import { getApiErrorMessage } from '../../../../utils/apiError';
import { AppointmentProductsExpensesEditor } from './AppointmentProductsExpensesEditor';
import { AppointmentServicesEditor } from './AppointmentServicesEditor';
import { AppointmentProfitSection } from './AppointmentProfitSection';

const labelCls = 'label-premium';
const inputCls = 'input-premium';

function formatMoney(value: number | null | undefined): string {
  return value != null ? `R$ ${value.toFixed(2)}` : '—';
}

interface AppointmentDetailModalProps {
  appointment: AppointmentResponse | null;
  onClose: () => void;
  /** Avisa o pai que o agendamento mudou (notas, produtos, despesas...), pra refletir na listagem/histórico. */
  onNotesSaved?: (updated: AppointmentResponse) => void;
}

export const AppointmentDetailModal = ({ appointment, onClose, onNotesSaved }: AppointmentDetailModalProps) => {
  const [internalNotes, setInternalNotes] = useState('');
  const [isSaving, setIsSaving] = useState(false);
  const { error: showError, success: showSuccess } = useAlert();

  useEffect(() => {
    setInternalNotes(appointment?.internalNotes ?? '');
  }, [appointment?.id, appointment?.internalNotes]);

  if (!appointment) return null;

  const services = appointment.services;

  const handleSaveNotes = async () => {
    setIsSaving(true);
    try {
      const updated = await appointmentsApi.updateInternalNotes(appointment.id, internalNotes.trim());
      onNotesSaved?.(updated);
      await showSuccess('Observação interna salva');
    } catch (err) {
      const msg = getApiErrorMessage(err, 'Erro ao salvar observação interna');
      await showError(msg);
    } finally {
      setIsSaving(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-[#261f23]/40 backdrop-blur-md">
      <div className="bg-white rounded-2xl shadow-xl w-full max-w-md border border-[#eae1e1]/85 overflow-hidden animate-scale-up max-h-[85vh] flex flex-col">
        <div className="flex items-center justify-between px-6 py-4 border-b border-[#eae1e1] bg-[#fcf9f9]/50 shrink-0">
          <h3 className="font-heading text-lg font-bold text-[#3b3036]">Detalhes do agendamento</h3>
          <button
            onClick={onClose}
            className="p-1.5 text-gray-400 hover:text-gray-600 hover:bg-gray-100 rounded-lg transition-all cursor-pointer"
          >
            <X size={20} />
          </button>
        </div>

        <div className="p-6 space-y-4 overflow-y-auto flex-1 min-h-0">
          <div className="grid grid-cols-2 gap-3 text-sm">
            <div>
              <span className={labelCls}>Cliente</span>
              <p className="text-[#3b3036]">{appointment.clientName}</p>
            </div>
            <div>
              <span className={labelCls}>Profissional</span>
              <p className="text-[#3b3036]">{appointment.employeeName}</p>
            </div>
          </div>

          {/* Antes o modal de detalhes não mostrava data nenhuma — justamente o dado que a
              pessoa quer conferir ao abrir os detalhes. Enquanto o horário não foi definido
              pela equipe, o que existe é só a data de preferência do cliente. */}
          <div className="grid grid-cols-2 gap-3 text-sm">
            {appointment.scheduledAt ? (
              <div>
                <span className={labelCls}>Data e hora</span>
                <p className="text-[#3b3036] font-semibold">
                  {formatApiDateTime(appointment.scheduledAt)}
                </p>
              </div>
            ) : (
              <div>
                <span className={labelCls}>Data e hora</span>
                <p className="text-[#7a7074] italic">A combinar</p>
              </div>
            )}
            {appointment.preferredDate && (
              <div>
                <span className={labelCls}>Preferência do cliente</span>
                <p className="text-[#3b3036]">{formatApiDate(appointment.preferredDate)}</p>
              </div>
            )}
          </div>

          <div className="space-y-3">
            {services.map((svc) => {
              const isCustomized = svc.customPrice != null || !!svc.customServiceNotes;
              return (
                <div
                  key={svc.serviceId}
                  className="border border-[#eae1e1] rounded-xl divide-y divide-[#eae1e1]/70"
                >
                  <div className="px-4 py-2.5 bg-[#fdf6f5]/60">
                    <span className={labelCls}>Serviço</span>
                    <p className="text-[#3b3036] font-semibold">{svc.serviceName}</p>
                  </div>
                  <div className="flex items-center justify-between px-4 py-3">
                    <span className="text-xs font-semibold text-[#7a7074]">Preço</span>
                    <div className="text-right">
                      {svc.customPrice != null && svc.catalogPrice != null && (
                        <p className="text-xs text-gray-400 line-through">
                          Catálogo: {formatMoney(svc.catalogPrice)}
                        </p>
                      )}
                      <p className="font-semibold text-[#3b3036]">{formatMoney(svc.effectivePrice)}</p>
                    </div>
                  </div>
                  {svc.customServiceNotes && (
                    <div className="px-4 py-3">
                      <span className="text-xs font-semibold text-[#7a7074]">Observações do serviço</span>
                      <p className="text-sm text-[#3b3036] mt-1">{svc.customServiceNotes}</p>
                    </div>
                  )}
                  {isCustomized && (
                    <div className="px-4 py-2 bg-[#fdf6f5]">
                      <p className="text-[11px] text-[#a6726b]">
                        Personalizado para este agendamento — o cadastro do serviço não foi alterado.
                      </p>
                    </div>
                  )}
                </div>
              );
            })}
          </div>

          {services.length > 1 && (
            <div className="flex items-center justify-between px-4 py-3 border border-[#eae1e1] rounded-xl bg-[#fcf9f9]/50">
              <span className="text-sm font-semibold text-[#3b3036]">Total</span>
              <div className="text-right">
                <p className="font-bold text-[#3b3036]">{formatMoney(appointment.totalPrice)}</p>
              </div>
            </div>
          )}

          <AppointmentServicesEditor
            appointment={appointment}
            onSaved={(updated) => onNotesSaved?.(updated)}
          />

          <div className="border-t border-[#eae1e1] pt-4">
            <AppointmentProductsExpensesEditor
              appointment={appointment}
              onSaved={(updated) => onNotesSaved?.(updated)}
            />
          </div>

          <AppointmentProfitSection appointmentId={appointment.id} />

          {appointment.clientNotes && (
            <div>
              <span className={labelCls}>Observações do cliente</span>
              <p className="text-sm text-[#3b3036] mt-1">{appointment.clientNotes}</p>
            </div>
          )}

          <div className="border-t border-[#eae1e1] pt-4">
            <label htmlFor="internal-notes" className={labelCls}>
              Observação interna da equipe
            </label>
            <p className="text-xs text-gray-400 mb-2">
              Só a equipe vê isso — não aparece pro cliente. Fica salva no histórico dele.
            </p>
            <PermissionGate
              method="PATCH"
              endpoint={`/v1/appointments/${appointment.id}/internal-notes`}
              fallback={
                <p className="text-sm text-[#3b3036] italic">
                  {appointment.internalNotes || 'Nenhuma observação registrada.'}
                </p>
              }
            >
              <textarea
                id="internal-notes"
                rows={3}
                maxLength={4000}
                className={`${inputCls} resize-none`}
                value={internalNotes}
                onChange={(e) => setInternalNotes(e.target.value)}
                placeholder="Ex.: cliente atrasou 15min, trouxe foto de referência..."
              />
              <div className="flex justify-end mt-2">
                <button
                  type="button"
                  onClick={handleSaveNotes}
                  disabled={isSaving || internalNotes === (appointment.internalNotes ?? '')}
                  className="btn-premium text-xs px-4 py-2 disabled:opacity-50"
                >
                  {isSaving ? 'Salvando...' : 'Salvar observação'}
                </button>
              </div>
            </PermissionGate>
          </div>
        </div>

        <div className="flex justify-end px-6 py-4 border-t border-[#eae1e1] bg-[#fcf9f9]/50 shrink-0">
          <button
            onClick={onClose}
            className="px-5 py-2.5 border border-[#eae1e1] font-semibold text-sm text-[#3b3036] hover:bg-white hover:border-[#be8a83]/50 rounded-xl transition-all cursor-pointer"
          >
            Fechar
          </button>
        </div>
      </div>
    </div>
  );
};
