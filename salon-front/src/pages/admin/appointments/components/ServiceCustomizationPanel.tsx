import { useState } from 'react';
import { ChevronDown, ChevronUp, PencilLine } from 'lucide-react';

const inputCls = 'input-premium';
const labelCls = 'label-premium';

export interface ServiceCustomizationValues {
  price: string;
  notes: string;
}

interface ServiceCustomizationPanelProps {
  serviceName?: string;
  defaultPrice: number | null;
  values: ServiceCustomizationValues;
  onChange: (values: ServiceCustomizationValues) => void;
}

export const ServiceCustomizationPanel = ({
  serviceName,
  defaultPrice,
  values,
  onChange,
}: ServiceCustomizationPanelProps) => {
  const [expanded, setExpanded] = useState(false);

  return (
    <div className="border border-[#eae1e1] rounded-xl overflow-hidden">
      <button
        type="button"
        onClick={() => setExpanded((prev) => !prev)}
        className="w-full flex items-center justify-between px-4 py-3 text-sm font-semibold text-[#3b3036] bg-[#fdf6f5] hover:bg-[#fcf0ee] transition-all cursor-pointer"
      >
        <span className="flex items-center gap-2">
          <PencilLine size={15} className="text-[#be8a83]" />
          Personalizar{serviceName ? ` "${serviceName}"` : ' para este cliente'}
        </span>
        {expanded ? <ChevronUp size={16} /> : <ChevronDown size={16} />}
      </button>

      {expanded && (
        <div className="p-4 space-y-4 bg-white">
          <div>
            <label className={labelCls}>Preço (R$)</label>
            <input
              type="number"
              step="0.01"
              min="0"
              placeholder={defaultPrice != null ? defaultPrice.toFixed(2) : '—'}
              value={values.price}
              onChange={(e) => onChange({ ...values, price: e.target.value })}
              className={inputCls}
            />
            <p className="text-xs text-gray-400 mt-1">
              Padrão: {defaultPrice != null ? `R$ ${defaultPrice.toFixed(2)}` : 'não definido'}
            </p>
          </div>
          <div>
            <label className={labelCls}>Observações do serviço (opcional)</label>
            <textarea
              rows={2}
              placeholder="Ex.: cabelo mais longo, precisa de mais tempo"
              value={values.notes}
              onChange={(e) => onChange({ ...values, notes: e.target.value })}
              className={inputCls}
            />
          </div>
        </div>
      )}
    </div>
  );
};
