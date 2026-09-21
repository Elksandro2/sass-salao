import { useEffect, useState } from 'react';
import { Plus, Trash2 } from 'lucide-react';
import { appointmentsApi } from '../../../appointments/services/appointments';
import type { AppointmentResponse, AppointmentServiceRequestItem } from '../../../appointments/services/appointments';
import { salonServicesApi } from '../../../services/services/services';
import type { SalonServiceData } from '../../../services/services/services';
import { PermissionGate } from '../../../../components/permissions/PermissionGate';
import { useAlert } from '../../../../hooks/useAlert';
import { getApiErrorMessage } from '../../../../utils/apiError';

const labelCls = 'label-premium';
const inputCls = 'input-premium';

function formatMoney(value: number | null | undefined): string {
  return value != null ? `R$ ${value.toFixed(2)}` : '—';
}

interface ServiceRow {
  serviceId: string;
  customPrice: string;
  customServiceNotes: string;
}

interface AppointmentServicesEditorProps {
  appointment: AppointmentResponse;
  onSaved: (updated: AppointmentResponse) => void;
  /** Só mostra o formulário de edição quando o modal de detalhes está em modo "Editar". */
  isEditing: boolean;
}

export const AppointmentServicesEditor = ({ appointment, onSaved, isEditing }: AppointmentServicesEditorProps) => {
  const [catalog, setCatalog] = useState<SalonServiceData[]>([]);
  const [serviceSearch, setServiceSearch] = useState('');
  const [rows, setRows] = useState<ServiceRow[]>([]);
  const [isSaving, setIsSaving] = useState(false);
  const { error: showError, success: showSuccess } = useAlert();

  // Trava só quando o PAGAMENTO já aconteceu (PAID ou MANUAL) — não pelo status do
  // agendamento em si. Um atendimento concluído mas ainda não pago continua editável.
  const readOnly =
    appointment.status === 'CANCELLED' ||
    appointment.paymentStatus === 'PAID' ||
    appointment.paymentStatus === 'MANUAL';

  useEffect(() => {
    salonServicesApi
      .findAll({ active: true }, 0, 1000)
      .then((page) => setCatalog(page.content))
      .catch(() => setCatalog([]));
  }, []);

  useEffect(() => {
    setRows(
      appointment.services.map((s) => ({
        serviceId: String(s.serviceId),
        customPrice: s.customPrice != null ? String(s.customPrice) : '',
        customServiceNotes: s.customServiceNotes ?? '',
      }))
    );
  }, [appointment.id, appointment.services]);

  const addRow = () => {
    setRows((prev) => [...prev, { serviceId: '', customPrice: '', customServiceNotes: '' }]);
  };

  const removeRow = (index: number) => {
    setRows((prev) => prev.filter((_, i) => i !== index));
  };

  const updateRow = (index: number, patch: Partial<ServiceRow>) => {
    setRows((prev) => prev.map((row, i) => (i === index ? { ...row, ...patch } : row)));
  };

  const handleSave = async () => {
    if (rows.length === 0) {
      await showError('Mantenha ao menos um serviço no agendamento.');
      return;
    }
    if (rows.some((r) => !r.serviceId)) {
      await showError('Selecione o serviço em todas as linhas.');
      return;
    }
    setIsSaving(true);
    try {
      const payload: AppointmentServiceRequestItem[] = rows.map((r) => ({
        serviceId: Number(r.serviceId),
        customPrice: r.customPrice === '' ? null : Number(r.customPrice),
        customServiceNotes: r.customServiceNotes.trim() || null,
      }));
      const updated = await appointmentsApi.updateServices(appointment.id, payload);
      onSaved(updated);
      await showSuccess('Serviços do agendamento atualizados');
    } catch (err) {
      await showError(getApiErrorMessage(err, 'Erro ao salvar serviços do agendamento'));
    } finally {
      setIsSaving(false);
    }
  };

  return (
    <PermissionGate
      method="PATCH"
      endpoint={`/v1/appointments/${appointment.id}/services`}
      fallback={null}
    >
      {isEditing && !readOnly && (
        <div className="border-t border-[#eae1e1] pt-4">
          <div className="flex items-center justify-between mb-2">
            <span className={labelCls}>Editar serviços</span>
            <button
              type="button"
              onClick={addRow}
              className="inline-flex items-center gap-1 text-xs font-semibold text-[#be8a83] hover:text-[#a6726b] cursor-pointer"
            >
              <Plus size={13} /> Adicionar serviço
            </button>
          </div>
          <p className="text-xs text-gray-400 mb-2">
            A cliente decidiu fazer mais alguma coisa? Adicione aqui, mesmo com o agendamento já
            confirmado.
          </p>
          {catalog.length > 6 && (
            <input
              type="text"
              value={serviceSearch}
              onChange={(e) => setServiceSearch(e.target.value)}
              placeholder="Buscar serviço por nome..."
              className={`${inputCls} mb-2`}
            />
          )}
          <div className="space-y-3">
            {rows.map((row, index) => (
              <div
                key={index}
                className="flex flex-col gap-2 p-2.5 bg-[#fcf9f9]/50 border border-[#eae1e1]/60 rounded-lg"
              >
                <div className="flex items-center justify-between">
                  <span className="text-xs font-semibold text-[#7a7074]">Serviço {index + 1}</span>
                  <button
                    type="button"
                    onClick={() => removeRow(index)}
                    className="p-1 text-rose-500 hover:bg-rose-50 rounded-lg transition-all cursor-pointer"
                    title="Remover este serviço"
                  >
                    <Trash2 size={14} />
                  </button>
                </div>
                <select
                  className={`${inputCls} block w-full`}
                  value={row.serviceId}
                  onChange={(e) => updateRow(index, { serviceId: e.target.value })}
                >
                  <option value="">Selecione o serviço...</option>
                  {catalog
                    .filter(
                      (s) =>
                        String(s.id) === row.serviceId ||
                        s.name.toLowerCase().includes(serviceSearch.trim().toLowerCase())
                    )
                    .map((s) => (
                      <option key={s.id} value={s.id}>
                        {s.name}
                        {s.price != null ? ` — R$ ${s.price.toFixed(2)}` : ''}
                      </option>
                    ))}
                </select>
                <div>
                  <input
                    type="number"
                    step="0.01"
                    className={`${inputCls} block w-full`}
                    placeholder="Preço customizado (opcional)"
                    value={row.customPrice}
                    onChange={(e) => updateRow(index, { customPrice: e.target.value })}
                  />
                  <p className="text-[11px] text-gray-400 mt-1">
                    Deixe em branco pra cobrar o preço do serviço selecionado acima.
                  </p>
                </div>
              </div>
            ))}
          </div>
          <div className="flex justify-between items-center pt-2">
            <span className="text-xs text-gray-400">
              Total em serviços: {formatMoney(appointment.totalPrice)}
            </span>
            <button
              type="button"
              onClick={handleSave}
              disabled={isSaving}
              className="btn-premium text-xs px-4 py-2 disabled:opacity-50"
            >
              {isSaving ? 'Salvando...' : 'Salvar serviços'}
            </button>
          </div>
        </div>
      )}
    </PermissionGate>
  );
};
