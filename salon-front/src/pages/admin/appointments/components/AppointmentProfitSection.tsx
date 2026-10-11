import { useEffect, useState } from 'react';
import { TrendingUp, ChevronDown, ChevronUp, Pencil, RotateCcw } from 'lucide-react';
import { reportsApi } from '../../reports/services/reports';
import type { AppointmentProfitResponse } from '../../reports/services/reports';
import { PermissionGate } from '../../../../components/permissions/PermissionGate';
import { CurrencyInput } from '../../../../components/CurrencyInput';
import { getApiErrorMessage } from '../../../../utils/apiError';
import { useAlert } from '../../../../hooks/useAlert';

function formatMoney(value: number | null | undefined): string {
  return value != null ? `R$ ${value.toFixed(2)}` : '—';
}

type CostField = 'serviceRecipeCost' | 'productsSoldCost' | 'serviceCommissionCost' | 'productCommissionCost';

const COST_FIELDS: {
  key: CostField;
  label: string;
  auto: keyof AppointmentProfitResponse;
  manual: keyof AppointmentProfitResponse;
}[] = [
  { key: 'serviceRecipeCost', label: 'Custo de produtos (receita do serviço)', auto: 'autoServiceRecipeCost', manual: 'serviceRecipeCostManual' },
  { key: 'productsSoldCost', label: 'Custo de produtos vendidos', auto: 'autoProductsSoldCost', manual: 'productsSoldCostManual' },
  { key: 'serviceCommissionCost', label: 'Comissão sobre serviços', auto: 'autoServiceCommissionCost', manual: 'serviceCommissionCostManual' },
  { key: 'productCommissionCost', label: 'Comissão sobre produtos vendidos', auto: 'autoProductCommissionCost', manual: 'productCommissionCostManual' },
];

/** Rascunho do formulário: string = valor manual digitado; null = usar o automático. */
type Draft = Record<CostField, string | null> & { note: string };

const draftFrom = (p: AppointmentProfitResponse): Draft => ({
  serviceRecipeCost: p.serviceRecipeCostManual ? p.serviceRecipeCost.toFixed(2) : null,
  productsSoldCost: p.productsSoldCostManual ? p.productsSoldCost.toFixed(2) : null,
  serviceCommissionCost: p.serviceCommissionCostManual ? p.serviceCommissionCost.toFixed(2) : null,
  productCommissionCost: p.productCommissionCostManual ? p.productCommissionCost.toFixed(2) : null,
  note: p.overrideNote ?? '',
});

interface AppointmentProfitSectionProps {
  appointmentId: number;
  /** Muda quando o agendamento é editado (serviços/produtos...) — força recalcular o lucro. */
  refreshToken?: unknown;
}

export const AppointmentProfitSection = ({ appointmentId, refreshToken }: AppointmentProfitSectionProps) => {
  const [expanded, setExpanded] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [profit, setProfit] = useState<AppointmentProfitResponse | null>(null);
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState<Draft | null>(null);
  const [isSaving, setIsSaving] = useState(false);
  const { error: showError } = useAlert();

  const load = async () => {
    setIsLoading(true);
    try {
      setProfit(await reportsApi.getAppointmentProfit(appointmentId));
    } catch (err) {
      await showError(getApiErrorMessage(err, 'Erro ao calcular lucro do atendimento'));
      setExpanded(false);
    } finally {
      setIsLoading(false);
    }
  };

  // Agendamento editado (ou trocado): o lucro em cache ficou velho.
  useEffect(() => {
    setProfit(null);
    setEditing(false);
    if (expanded) load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [appointmentId, refreshToken]);

  const handleToggle = async () => {
    if (expanded) {
      setExpanded(false);
      setEditing(false);
      return;
    }
    setExpanded(true);
    if (!profit) await load();
  };

  const startEditing = () => {
    if (!profit) return;
    setDraft(draftFrom(profit));
    setEditing(true);
  };

  const save = async () => {
    if (!draft) return;
    const anyManual = COST_FIELDS.some((f) => draft[f.key] != null);
    setIsSaving(true);
    try {
      const updated = await reportsApi.updateAppointmentProfit(appointmentId, {
        serviceRecipeCost: draft.serviceRecipeCost != null ? Number(draft.serviceRecipeCost || 0) : null,
        productsSoldCost: draft.productsSoldCost != null ? Number(draft.productsSoldCost || 0) : null,
        serviceCommissionCost: draft.serviceCommissionCost != null ? Number(draft.serviceCommissionCost || 0) : null,
        productCommissionCost: draft.productCommissionCost != null ? Number(draft.productCommissionCost || 0) : null,
        note: anyManual ? draft.note.trim() || null : null,
      });
      setProfit(updated);
      setEditing(false);
    } catch (err) {
      await showError(getApiErrorMessage(err, 'Erro ao salvar os valores do lucro'));
    } finally {
      setIsSaving(false);
    }
  };

  const hasManual = profit && COST_FIELDS.some((f) => profit[f.manual]);

  return (
    <PermissionGate method="GET" endpoint={`/v1/reports/appointments/${appointmentId}/profit`} fallback={null}>
      <div className="border-t border-[#eae1e1] pt-4">
        <button
          type="button"
          onClick={handleToggle}
          className="w-full flex items-center justify-between px-4 py-3 text-sm font-semibold text-[#3b3036] bg-[#fdf6f5] hover:bg-[#fcf0ee] rounded-xl transition-all cursor-pointer"
        >
          <span className="flex items-center gap-2">
            <TrendingUp size={15} className="text-[#be8a83]" />
            Ver lucro deste atendimento
          </span>
          {expanded ? <ChevronUp size={16} /> : <ChevronDown size={16} />}
        </button>

        {expanded && (
          <div className="p-4 mt-2 bg-white border border-[#eae1e1] rounded-xl space-y-2">
            {isLoading ? (
              <p className="text-xs text-gray-400">Calculando...</p>
            ) : profit && editing && draft ? (
              <>
                <p className="text-xs text-[#7a7074]">
                  Digite o valor real só deste atendimento. O que ficar em "automático" continua
                  sendo calculado pelo sistema. Comissão digitada aqui é a que entra na folha da
                  profissional.
                </p>
                {COST_FIELDS.map((f) => {
                  const value = draft[f.key];
                  const autoValue = profit[f.auto] as number;
                  return (
                    <div key={f.key} className="space-y-1">
                      <div className="flex items-center justify-between gap-2">
                        <label htmlFor={`profit-${f.key}`} className="text-xs font-semibold text-[#3b3036]">
                          {f.label}
                        </label>
                        {value != null ? (
                          <button
                            type="button"
                            onClick={() => setDraft({ ...draft, [f.key]: null })}
                            className="flex items-center gap-1 text-[11px] text-[#7a7074] hover:text-[#be8a83] cursor-pointer"
                          >
                            <RotateCcw size={11} /> usar automático
                          </button>
                        ) : (
                          <span className="text-[11px] text-emerald-600">automático</span>
                        )}
                      </div>
                      <CurrencyInput
                        id={`profit-${f.key}`}
                        className="input-premium"
                        value={value ?? autoValue.toFixed(2)}
                        onValueChange={(v) => setDraft({ ...draft, [f.key]: v })}
                      />
                      {value != null && (
                        <p className="text-[11px] text-[#7a7074]">Automático seria {formatMoney(autoValue)}</p>
                      )}
                    </div>
                  );
                })}
                <div className="space-y-1">
                  <label htmlFor="profit-note" className="text-xs font-semibold text-[#3b3036]">
                    Justificativa (opcional)
                  </label>
                  <input
                    id="profit-note"
                    type="text"
                    maxLength={500}
                    className="input-premium"
                    value={draft.note}
                    onChange={(e) => setDraft({ ...draft, note: e.target.value })}
                    placeholder="Ex.: comissão combinada diferente nesse atendimento"
                  />
                </div>
                <div className="flex justify-end gap-2 pt-1">
                  <button
                    type="button"
                    onClick={() => setEditing(false)}
                    className="px-4 py-2 border border-[#eae1e1] text-xs font-semibold text-[#3b3036] rounded-xl hover:border-[#be8a83]/50 cursor-pointer"
                  >
                    Cancelar
                  </button>
                  <button
                    type="button"
                    onClick={save}
                    disabled={isSaving}
                    className="btn-premium text-xs px-4 py-2 disabled:opacity-50"
                  >
                    {isSaving ? 'Salvando...' : 'Salvar valores'}
                  </button>
                </div>
              </>
            ) : profit ? (
              <>
                <div className="flex justify-between text-sm">
                  <span className="text-[#7a7074]">Receita bruta</span>
                  <span className="font-semibold">{formatMoney(profit.grossRevenue)}</span>
                </div>
                {COST_FIELDS.map((f) => (
                  <div key={f.key} className="flex justify-between text-sm">
                    <span className="text-[#7a7074]">
                      {f.label}
                      {profit[f.manual] && (
                        <span className="ml-1.5 px-1.5 py-0.5 text-[10px] font-semibold rounded bg-amber-50 text-amber-700">
                          manual
                        </span>
                      )}
                    </span>
                    <span className="text-rose-600">− {formatMoney(profit[f.key] as number)}</span>
                  </div>
                ))}
                <div className="flex justify-between text-sm font-bold pt-2 border-t border-[#eae1e1]">
                  <span>Lucro líquido</span>
                  <span className={profit.positive ? 'text-emerald-600' : 'text-rose-600'}>
                    {formatMoney(profit.netProfit)}
                  </span>
                </div>
                {hasManual && profit.overrideNote && (
                  <p className="text-xs text-amber-700">Valores manuais: {profit.overrideNote}</p>
                )}
                <div className="flex items-center justify-between gap-2 pt-1">
                  <p className="text-[10px] text-gray-400">
                    Não inclui rateio de gastos fixos (aluguel, água, luz...).
                  </p>
                  <PermissionGate method="PUT" endpoint={`/v1/reports/appointments/${appointmentId}/profit`} fallback={null}>
                    <button
                      type="button"
                      onClick={startEditing}
                      className="flex items-center gap-1 text-xs font-semibold text-[#be8a83] hover:text-[#a6726b] cursor-pointer whitespace-nowrap"
                    >
                      <Pencil size={12} /> Editar valores
                    </button>
                  </PermissionGate>
                </div>
              </>
            ) : null}
          </div>
        )}
      </div>
    </PermissionGate>
  );
};
