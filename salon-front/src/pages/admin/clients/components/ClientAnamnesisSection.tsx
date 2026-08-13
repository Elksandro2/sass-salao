import { useEffect, useState } from 'react';
import type { AxiosError } from 'axios';
import { ShieldCheck, Trash2 } from 'lucide-react';
import { clientAnamnesisApi } from '../services/clientAnamnesis';
import type { ClientAnamnesisData } from '../services/clientAnamnesis';
import { PermissionGate } from '../../../../components/permissions/PermissionGate';
import { useAlert } from '../../../../hooks/useAlert';
import { getApiErrorMessage } from '../../../../utils/apiError';

const labelCls = 'label-premium';
const inputCls = 'input-premium';

const SKIN_TYPES: { value: ClientAnamnesisData['skinType']; label: string }[] = [
  { value: 'NORMAL', label: 'Normal' },
  { value: 'OLEOSA', label: 'Oleosa' },
  { value: 'SECA', label: 'Seca' },
  { value: 'MISTA', label: 'Mista' },
  { value: 'SENSIVEL', label: 'Sensível' },
];

const HAIR_TYPES: { value: ClientAnamnesisData['hairType']; label: string }[] = [
  { value: 'LISO', label: 'Liso' },
  { value: 'ONDULADO', label: 'Ondulado' },
  { value: 'CACHEADO', label: 'Cacheado' },
  { value: 'CRESPO', label: 'Crespo' },
];

const emptyForm: ClientAnamnesisData = {
  allergies: '',
  healthConditions: '',
  medications: '',
  additionalNotes: '',
  skinType: null,
  hairType: null,
  consentGiven: false,
};

interface ClientAnamnesisSectionProps {
  clientId: number;
}

export const ClientAnamnesisSection = ({ clientId }: ClientAnamnesisSectionProps) => {
  const [anamnesis, setAnamnesis] = useState<ClientAnamnesisData | null>(null);
  const [form, setForm] = useState<ClientAnamnesisData>(emptyForm);
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const { error: showError, success: showSuccess, confirm } = useAlert();

  const load = async () => {
    setIsLoading(true);
    try {
      const data = await clientAnamnesisApi.findByClientId(clientId);
      setAnamnesis(data);
      setForm({ ...data, consentGiven: true });
    } catch (err) {
      const status = (err as AxiosError)?.response?.status;
      if (status === 404) {
        setAnamnesis(null);
        setForm(emptyForm);
      } else {
        await showError(getApiErrorMessage(err, 'Erro ao carregar ficha de anamnese'));
      }
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [clientId]);

  const handleSave = async () => {
    if (!form.consentGiven) {
      await showError('É necessário marcar o consentimento da cliente para salvar a ficha.');
      return;
    }
    setIsSaving(true);
    try {
      const saved = await clientAnamnesisApi.upsert(clientId, form);
      setAnamnesis(saved);
      setForm({ ...saved, consentGiven: true });
      await showSuccess('Ficha de anamnese salva');
    } catch (err) {
      await showError(getApiErrorMessage(err, 'Erro ao salvar ficha de anamnese'));
    } finally {
      setIsSaving(false);
    }
  };

  const handleDelete = async () => {
    await confirm(
      'Isso apaga permanentemente a ficha de anamnese desta cliente — inclusive alergias e condições de saúde registradas. Esta ação não pode ser desfeita.',
      async () => {
        try {
          await clientAnamnesisApi.delete(clientId);
          setAnamnesis(null);
          setForm(emptyForm);
          await showSuccess('Ficha de anamnese apagada');
        } catch (err) {
          await showError(getApiErrorMessage(err, 'Erro ao apagar ficha de anamnese'));
        }
      },
      { title: 'Apagar ficha de anamnese', isDangerous: true, confirmText: 'Apagar' }
    );
  };

  if (isLoading) {
    return (
      <div className="flex items-center gap-2 text-sm text-[#3b3036]/60 dark:text-gray-400 py-10 justify-center">
        <div className="animate-spin rounded-full h-5 w-5 border-t-2 border-b-2 border-[#be8a83]" />
        <span>Carregando ficha de anamnese...</span>
      </div>
    );
  }

  return (
    <PermissionGate
      method="GET"
      endpoint={`/v1/clients/${clientId}/anamnesis`}
      fallback={
        <p className="text-sm text-[#7a7074] dark:text-gray-400 text-center py-10">
          Você não tem permissão para ver a ficha de anamnese deste cliente.
        </p>
      }
    >
      <div className="space-y-4">
        <div className="flex items-start gap-2 p-3 bg-amber-50 dark:bg-amber-500/10 border border-amber-100 dark:border-amber-500/20 rounded-xl text-3xs text-amber-700 dark:text-amber-400">
          <ShieldCheck size={14} className="shrink-0 mt-0.5" />
          <span>
            Dado de saúde protegido pela LGPD. Só a equipe administrativa acessa esta ficha. Peça o
            consentimento da cliente antes de registrar qualquer informação aqui.
          </span>
        </div>

        {anamnesis?.updatedAt && (
          <p className="text-3xs text-[#7a7074] dark:text-gray-400">
            Última atualização em {new Date(anamnesis.updatedAt).toLocaleDateString('pt-BR')}
            {anamnesis.updatedByName ? ` por ${anamnesis.updatedByName}` : ''}.
          </p>
        )}

        <div>
          <label className={labelCls}>Alergias</label>
          <textarea
            rows={2}
            className={`${inputCls} resize-none`}
            value={form.allergies ?? ''}
            onChange={(e) => setForm((prev) => ({ ...prev, allergies: e.target.value }))}
            placeholder="Ex.: alergia a níquel, produtos com amônia..."
          />
        </div>

        <div>
          <label className={labelCls}>Condições de saúde</label>
          <textarea
            rows={2}
            className={`${inputCls} resize-none`}
            value={form.healthConditions ?? ''}
            onChange={(e) => setForm((prev) => ({ ...prev, healthConditions: e.target.value }))}
            placeholder="Ex.: gravidez, diabetes, pressão alta..."
          />
        </div>

        <div>
          <label className={labelCls}>Medicamentos em uso</label>
          <textarea
            rows={2}
            className={`${inputCls} resize-none`}
            value={form.medications ?? ''}
            onChange={(e) => setForm((prev) => ({ ...prev, medications: e.target.value }))}
          />
        </div>

        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className={labelCls}>Tipo de pele</label>
            <select
              className={inputCls}
              value={form.skinType ?? ''}
              onChange={(e) =>
                setForm((prev) => ({
                  ...prev,
                  skinType: (e.target.value || null) as ClientAnamnesisData['skinType'],
                }))
              }
            >
              <option value="">Não informado</option>
              {SKIN_TYPES.map((t) => (
                <option key={t.value} value={t.value ?? ''}>
                  {t.label}
                </option>
              ))}
            </select>
          </div>
          <div>
            <label className={labelCls}>Tipo de cabelo</label>
            <select
              className={inputCls}
              value={form.hairType ?? ''}
              onChange={(e) =>
                setForm((prev) => ({
                  ...prev,
                  hairType: (e.target.value || null) as ClientAnamnesisData['hairType'],
                }))
              }
            >
              <option value="">Não informado</option>
              {HAIR_TYPES.map((t) => (
                <option key={t.value} value={t.value ?? ''}>
                  {t.label}
                </option>
              ))}
            </select>
          </div>
        </div>

        <div>
          <label className={labelCls}>Observações adicionais</label>
          <textarea
            rows={2}
            className={`${inputCls} resize-none`}
            value={form.additionalNotes ?? ''}
            onChange={(e) => setForm((prev) => ({ ...prev, additionalNotes: e.target.value }))}
          />
        </div>

        <PermissionGate method="PUT" endpoint={`/v1/clients/${clientId}/anamnesis`}>
          <label className="flex items-start gap-2 text-xs text-[#3b3036] dark:text-gray-300 cursor-pointer">
            <input
              type="checkbox"
              className="mt-0.5 accent-[#be8a83]"
              checked={!!form.consentGiven}
              onChange={(e) => setForm((prev) => ({ ...prev, consentGiven: e.target.checked }))}
            />
            <span>
              A cliente consentiu com o registro deste dado de saúde para uso exclusivo do
              atendimento.
            </span>
          </label>

          <div className="flex justify-between items-center pt-2">
            {anamnesis ? (
              <button
                type="button"
                onClick={handleDelete}
                className="inline-flex items-center gap-1 text-xs font-semibold text-rose-600 hover:text-rose-700 cursor-pointer"
              >
                <Trash2 size={13} /> Apagar ficha
              </button>
            ) : (
              <span />
            )}
            <button
              type="button"
              onClick={handleSave}
              disabled={isSaving}
              className="btn-premium text-xs px-4 py-2 disabled:opacity-50"
            >
              {isSaving ? 'Salvando...' : 'Salvar ficha'}
            </button>
          </div>
        </PermissionGate>
      </div>
    </PermissionGate>
  );
};
