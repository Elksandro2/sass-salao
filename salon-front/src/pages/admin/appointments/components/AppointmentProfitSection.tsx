import { useState } from 'react';
import { TrendingUp, ChevronDown, ChevronUp } from 'lucide-react';
import { reportsApi } from '../../reports/services/reports';
import type { AppointmentProfitResponse } from '../../reports/services/reports';
import { PermissionGate } from '../../../../components/permissions/PermissionGate';
import { getApiErrorMessage } from '../../../../utils/apiError';
import { useAlert } from '../../../../hooks/useAlert';

function formatMoney(value: number | null | undefined): string {
  return value != null ? `R$ ${value.toFixed(2)}` : '—';
}

interface AppointmentProfitSectionProps {
  appointmentId: number;
}

export const AppointmentProfitSection = ({ appointmentId }: AppointmentProfitSectionProps) => {
  const [expanded, setExpanded] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [profit, setProfit] = useState<AppointmentProfitResponse | null>(null);
  const { error: showError } = useAlert();

  const handleToggle = async () => {
    if (expanded) {
      setExpanded(false);
      return;
    }
    setExpanded(true);
    if (profit) return;
    setIsLoading(true);
    try {
      const data = await reportsApi.getAppointmentProfit(appointmentId);
      setProfit(data);
    } catch (err) {
      await showError(getApiErrorMessage(err, 'Erro ao calcular lucro do atendimento'));
      setExpanded(false);
    } finally {
      setIsLoading(false);
    }
  };

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
            ) : profit ? (
              <>
                <div className="flex justify-between text-sm">
                  <span className="text-[#7a7074]">Receita bruta</span>
                  <span className="font-semibold">{formatMoney(profit.grossRevenue)}</span>
                </div>
                <div className="flex justify-between text-sm">
                  <span className="text-[#7a7074]">Custo de produtos (receita do serviço)</span>
                  <span className="text-rose-600">− {formatMoney(profit.serviceRecipeCost)}</span>
                </div>
                <div className="flex justify-between text-sm">
                  <span className="text-[#7a7074]">Custo de produtos vendidos</span>
                  <span className="text-rose-600">− {formatMoney(profit.productsSoldCost)}</span>
                </div>
                <div className="flex justify-between text-sm">
                  <span className="text-[#7a7074]">Comissão estimada</span>
                  <span className="text-rose-600">− {formatMoney(profit.commissionCost)}</span>
                </div>
                <div className="flex justify-between text-sm font-bold pt-2 border-t border-[#eae1e1]">
                  <span>Lucro líquido</span>
                  <span className={profit.positive ? 'text-emerald-600' : 'text-rose-600'}>
                    {formatMoney(profit.netProfit)}
                  </span>
                </div>
                <p className="text-3xs text-gray-400 pt-1">
                  Estimativa — não inclui rateio de gastos fixos (aluguel, água, luz...).
                </p>
              </>
            ) : null}
          </div>
        )}
      </div>
    </PermissionGate>
  );
};
