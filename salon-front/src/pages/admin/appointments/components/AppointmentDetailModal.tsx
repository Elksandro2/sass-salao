import { useEffect, useState } from 'react';
import { X, Pencil } from 'lucide-react';
import { appointmentsApi } from '../../../appointments/services/appointments';
import type { AppointmentResponse } from '../../../appointments/services/appointments';
import { formatApiDate, formatApiDateTime } from '../../../../utils/datetime';
import { PERIOD_LABELS, type AppointmentPeriod } from '../../../../utils/appointmentPeriod';
import { employeesApi } from '../../employees/services/employees';
import type { EmployeeData } from '../../employees/services/employees';
import { PermissionGate } from '../../../../components/permissions/PermissionGate';
import { useAlert } from '../../../../hooks/useAlert';
import { getApiErrorMessage } from '../../../../utils/apiError';
import { AppointmentProductsExpensesEditor } from './AppointmentProductsExpensesEditor';
import { AppointmentServicesEditor } from './AppointmentServicesEditor';
import { AppointmentProfitSection } from './AppointmentProfitSection';

const labelCls = 'label-premium';
const inputCls = 'input-premium';

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

  const [isEditing, setIsEditing] = useState(false);
  const [employees, setEmployees] = useState<EmployeeData[]>([]);
  const [editEmployeeId, setEditEmployeeId] = useState('');
  const [editDate, setEditDate] = useState('');
  const [editPeriod, setEditPeriod] = useState<AppointmentPeriod | ''>('');
  const [isSavingDetails, setIsSavingDetails] = useState(false);

  useEffect(() => {
    employeesApi.findAllForBooking().then(setEmployees).catch(() => setEmployees([]));
  }, []);

  useEffect(() => {
    setInternalNotes(appointment?.internalNotes ?? '');
    // Fecha o modo de edição sempre que troca de agendamento (ou reabre o mesmo) — nunca deve
    // abrir os detalhes já com os campos editáveis expostos.
    setIsEditing(false);
  }, [appointment?.id, appointment?.internalNotes]);

  if (!appointment) return null;

  // Mesma trava financeira usada nos editores de serviços/produtos/despesas: só bloqueia quando
  // o pagamento já aconteceu de verdade (PAID/MANUAL) ou o agendamento foi cancelado.
  const detailsReadOnly =
    appointment.status === 'CANCELLED' ||
    appointment.paymentStatus === 'PAID' ||
    appointment.paymentStatus === 'MANUAL';

  const handleToggleEdit = () => {
    if (!isEditing) {
      setEditEmployeeId(String(appointment.employeeId));
      setEditDate(appointment.scheduledDate ?? appointment.scheduledAt?.slice(0, 10) ?? '');
      setEditPeriod(appointment.scheduledPeriod ?? '');
    }
    setIsEditing((prev) => !prev);
  };

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

  const handleSaveDetails = async () => {
    if (!editEmployeeId) {
      await showError('Selecione a profissional responsável.');
      return;
    }
    if ((editDate && !editPeriod) || (!editDate && editPeriod)) {
      await showError('Informe a data e o período (manhã ou tarde) juntos, ou deixe os dois em branco.');
      return;
    }
    setIsSavingDetails(true);
    try {
      const updated = await appointmentsApi.updateDetails(appointment.id, {
        employeeId: Number(editEmployeeId),
        scheduledDate: editDate || undefined,
        scheduledPeriod: (editPeriod || undefined) as AppointmentPeriod | undefined,
      });
      onNotesSaved?.(updated);
      await showSuccess('Dados do agendamento atualizados');
      setIsEditing(false);
    } catch (err) {
      const msg = getApiErrorMessage(err, 'Erro ao salvar dados do agendamento');
      await showError(msg);
    } finally {
      setIsSavingDetails(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-[#261f23]/40 backdrop-blur-md">
      <div className="bg-white rounded-2xl shadow-xl w-full max-w-md border border-[#eae1e1]/85 overflow-hidden animate-scale-up max-h-[85vh] flex flex-col">
        <div className="flex items-center justify-between px-6 py-4 border-b border-[#eae1e1] bg-[#fcf9f9]/50 shrink-0">
          <h3 className="font-heading text-lg font-bold text-[#3b3036]">Detalhes do agendamento</h3>
          <div className="flex items-center gap-1">
            <PermissionGate method="PATCH" endpoint={`/v1/appointments/${appointment.id}/details`}>
              {!detailsReadOnly && (
                <button
                  onClick={handleToggleEdit}
                  className={`p-1.5 rounded-lg transition-all cursor-pointer flex items-center gap-1 text-xs font-semibold ${
                    isEditing
                      ? 'text-[#a6726b] bg-[#be8a83]/10 hover:bg-[#be8a83]/20'
                      : 'text-gray-500 hover:text-[#be8a83] hover:bg-[#fdf6f5]'
                  }`}
                  title={isEditing ? 'Cancelar edição' : 'Editar agendamento'}
                >
                  <Pencil size={15} />
                  <span>{isEditing ? 'Cancelar edição' : 'Editar'}</span>
                </button>
              )}
            </PermissionGate>
            <button
              onClick={onClose}
              className="p-1.5 text-gray-400 hover:text-gray-600 hover:bg-gray-100 rounded-lg transition-all cursor-pointer"
            >
              <X size={20} />
            </button>
          </div>
        </div>

        <div className="p-6 space-y-4 overflow-y-auto flex-1 min-h-0">
          <div className="grid grid-cols-2 gap-3 text-sm">
            <div>
              <span className={labelCls}>Cliente</span>
              <p className="text-[#3b3036]">{appointment.clientName}</p>
            </div>
            <div>
              <span className={labelCls}>Profissional</span>
              {isEditing ? (
                <select
                  aria-label="Profissional"
                  className={inputCls}
                  value={editEmployeeId}
                  onChange={(e) => setEditEmployeeId(e.target.value)}
                >
                  <option value="">Selecione...</option>
                  {employees.map((e) => (
                    <option key={e.id} value={e.id}>
                      {e.name}
                    </option>
                  ))}
                </select>
              ) : (
                <p className="text-[#3b3036]">{appointment.employeeName}</p>
              )}
            </div>
          </div>

          {/* Antes o modal de detalhes não mostrava data nenhuma — justamente o dado que a
              pessoa quer conferir ao abrir os detalhes. Enquanto o horário não foi definido
              pela equipe, o que existe é só a data de preferência do cliente. */}
          {isEditing ? (
            <div className="space-y-2">
              <div>
                <label htmlFor="edit-date" className={labelCls}>Data</label>
                <input
                  id="edit-date"
                  type="date"
                  className={inputCls}
                  value={editDate}
                  onChange={(e) => setEditDate(e.target.value)}
                />
              </div>
              <div>
                <span className={labelCls}>Período</span>
                <div className="grid grid-cols-2 gap-2">
                  {(['MORNING', 'AFTERNOON'] as const).map((period) => (
                    <button
                      key={period}
                      type="button"
                      onClick={() => setEditPeriod(period)}
                      className={`px-3 py-2 rounded-xl text-sm font-semibold border transition-all cursor-pointer ${
                        editPeriod === period
                          ? 'bg-[#be8a83] border-[#be8a83] text-white'
                          : 'bg-white border-[#eae1e1] text-[#3b3036] hover:border-[#be8a83]/50'
                      }`}
                    >
                      {PERIOD_LABELS[period]}
                    </button>
                  ))}
                </div>
              </div>
              <p className="text-xs text-gray-400">
                Editar aqui converte pro modelo de manhã/tarde, mesmo se o agendamento era com hora exata.
              </p>
              <div className="flex justify-end">
                <button
                  type="button"
                  onClick={handleSaveDetails}
                  disabled={isSavingDetails}
                  className="btn-premium text-xs px-4 py-2 disabled:opacity-50"
                >
                  {isSavingDetails ? 'Salvando...' : 'Salvar dados do agendamento'}
                </button>
              </div>
            </div>
          ) : (
            <div className="grid grid-cols-2 gap-3 text-sm">
              {appointment.scheduledAt ? (
                <div>
                  <span className={labelCls}>Data e hora</span>
                  <p className="text-[#3b3036] font-semibold">
                    {formatApiDateTime(appointment.scheduledAt)}
                  </p>
                </div>
              ) : appointment.scheduledDate ? (
                <div>
                  <span className={labelCls}>Data e período</span>
                  <p className="text-[#3b3036] font-semibold">
                    {formatApiDate(appointment.scheduledDate)}
                    {appointment.scheduledPeriod ? ` — ${PERIOD_LABELS[appointment.scheduledPeriod]}` : ''}
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
                  <p className="text-[#3b3036]">
                    {formatApiDate(appointment.preferredDate)}
                    {appointment.preferredPeriod ? ` (${PERIOD_LABELS[appointment.preferredPeriod]})` : ''}
                  </p>
                </div>
              )}
            </div>
          )}

          <AppointmentServicesEditor
            appointment={appointment}
            onSaved={(updated) => onNotesSaved?.(updated)}
            isEditing={isEditing}
          />

          <div className="border-t border-[#eae1e1] pt-4">
            <AppointmentProductsExpensesEditor
              appointment={appointment}
              onSaved={(updated) => onNotesSaved?.(updated)}
              isEditing={isEditing}
            />
          </div>

          {/* Cancelado/recusado nunca vai gerar receita de verdade — mostrar "lucro" aqui
              induziria a dona a contar como real um valor que nunca vai entrar. */}
          {appointment.status !== 'CANCELLED' && appointment.status !== 'DECLINED' && (
            <AppointmentProfitSection appointmentId={appointment.id} />
          )}

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
              {isEditing ? (
                <>
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
                </>
              ) : (
                <p className="text-sm text-[#3b3036] italic">
                  {appointment.internalNotes || 'Nenhuma observação registrada.'}
                </p>
              )}
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
