import { useEffect, useState } from 'react';
import { Plus, Trash2 } from 'lucide-react';
import { appointmentsApi } from '../../../appointments/services/appointments';
import type { AppointmentResponse, AppointmentServiceRequestItem } from '../../../appointments/services/appointments';
import { salonServicesApi } from '../../../services/services/services';
import type { SalonServiceData } from '../../../services/services/services';
import { PermissionGate } from '../../../../components/permissions/PermissionGate';
import { SearchableSelect } from '../../../../components/SearchableSelect';
import { CurrencyInput } from '../../../../components/CurrencyInput';
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

/** Cartões de leitura dos serviços já lançados — mesma exibição de antes, só que agora é a
 * única exibição (a seção "Editar" troca pra este componente por dentro, sem duplicar). */
const StaticServicesList = ({ appointment }: { appointment: AppointmentResponse }) => (
  <>
    <div className="space-y-3">
      {appointment.services.map((svc) => {
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
    {appointment.services.length > 1 && (
      <div className="flex items-center justify-between px-4 py-3 border border-[#eae1e1] rounded-xl bg-[#fcf9f9]/50 mt-3">
        <span className="text-sm font-semibold text-[#3b3036]">Total</span>
        <div className="text-right">
          <p className="font-bold text-[#3b3036]">{formatMoney(appointment.totalPrice)}</p>
        </div>
      </div>
    )}
  </>
);

interface AppointmentServicesEditorProps {
  appointment: AppointmentResponse;
  onSaved: (updated: AppointmentResponse) => void;
  /** Só mostra o formulário de edição quando o modal de detalhes está em modo "Editar". */
  isEditing: boolean;
}

export const AppointmentServicesEditor = ({ appointment, onSaved, isEditing }: AppointmentServicesEditorProps) => {
  const [catalog, setCatalog] = useState<SalonServiceData[]>([]);
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

  const showEditForm = isEditing && !readOnly;

  return (
    <PermissionGate
      method="PATCH"
      endpoint={`/v1/appointments/${appointment.id}/services`}
      fallback={<StaticServicesList appointment={appointment} />}
    >
      {showEditForm ? (
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
            Os serviços já lançados aparecem abaixo, prontos pra editar (inclusive o preço). A
            cliente decidiu fazer mais alguma coisa? Use "Adicionar serviço".
          </p>
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
                <SearchableSelect
                  value={row.serviceId}
                  onSelect={(id) => updateRow(index, { serviceId: id })}
                  options={catalog.map((s) => ({
                    id: s.id!,
                    label: s.name + (s.price != null ? ` — R$ ${s.price.toFixed(2)}` : ''),
                  }))}
                  placeholder="Buscar e selecionar o serviço..."
                  noResultsLabel="Nenhum serviço encontrado"
                  className={`${inputCls} block w-full`}
                />
                <div>
                  <CurrencyInput
                    className={`${inputCls} block w-full`}
                    placeholder="Preço customizado (opcional)"
                    value={row.customPrice}
                    onValueChange={(v) => updateRow(index, { customPrice: v })}
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
      ) : (
        <StaticServicesList appointment={appointment} />
      )}
    </PermissionGate>
  );
};
