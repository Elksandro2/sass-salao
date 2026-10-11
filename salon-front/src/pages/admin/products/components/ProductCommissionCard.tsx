import { useEffect, useState } from 'react';
import { Percent } from 'lucide-react';
import { businessSettingsService } from '../../../../services/businessSettings';
import { PermissionGate } from '../../../../components/permissions/PermissionGate';
import { useAlert } from '../../../../hooks/useAlert';
import { getApiErrorMessage } from '../../../../utils/apiError';

/**
 * Comissão única sobre produtos vendidos. Mora na tela de Produtos (Venda) — antes ficava
 * escondida no Perfil do Salão, longe de onde se pensa em produto e preço.
 */
export const ProductCommissionCard = () => {
  const [percent, setPercent] = useState('');
  const [saved, setSaved] = useState<string>('');
  const [updatedAt, setUpdatedAt] = useState<string | null>(null);
  const [isSaving, setIsSaving] = useState(false);
  const { error: showError, success: showSuccess } = useAlert();

  useEffect(() => {
    businessSettingsService
      .get()
      .then((data) => {
        const value = data.productCommissionPercent != null ? String(data.productCommissionPercent) : '';
        setPercent(value);
        setSaved(value);
        setUpdatedAt(data.updatedAt);
      })
      .catch((err) => showError(getApiErrorMessage(err, 'Erro ao carregar a comissão de produtos.')));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const handleSave = async () => {
    const value = percent.trim();
    if (value && (Number(value) < 0 || Number(value) > 100)) {
      await showError('A comissão deve ficar entre 0 e 100%.');
      return;
    }
    setIsSaving(true);
    try {
      const updated = await businessSettingsService.update({
        productCommissionPercent: value ? Number(value) : null,
      });
      const next = updated.productCommissionPercent != null ? String(updated.productCommissionPercent) : '';
      setPercent(next);
      setSaved(next);
      setUpdatedAt(updated.updatedAt);
      showSuccess('Comissão sobre produtos atualizada.');
    } catch (err) {
      showError(getApiErrorMessage(err, 'Erro ao salvar a comissão sobre produtos.'));
    } finally {
      setIsSaving(false);
    }
  };

  return (
    <div className="bg-white rounded-2xl border border-[#eae1e1]/80 p-5 shadow-sm flex flex-col md:flex-row md:items-end gap-4">
      <div className="flex-1 space-y-1">
        <div className="flex items-center gap-2">
          <Percent size={16} className="text-[#be8a83]" />
          <span className="text-sm font-bold text-[#3b3036]">Comissão sobre produtos vendidos</span>
        </div>
        <p className="text-xs text-[#3b3036]/60">
          Porcentagem única pro salão inteiro: quem vender um produto (num atendimento ou em venda
          avulsa no Fluxo de Caixa) recebe essa % do valor vendido — inclusive quem é Salário Fixo,
          como incentivo. Vale para as vendas feitas depois de salvar; vendas antigas mantêm a %
          da época. A comissão de serviço é configurada em cada serviço.
        </p>
        {updatedAt && (
          <p className="text-[11px] text-[#3b3036]/50">
            Última atualização em {new Date(updatedAt).toLocaleString('pt-BR')}
          </p>
        )}
      </div>
      <PermissionGate
        method="PUT"
        endpoint="/v1/admin/business-settings"
        fallback={<span className="text-sm font-semibold text-[#3b3036]">{saved ? `${saved}%` : 'Sem comissão'}</span>}
      >
        <div className="flex items-end gap-2">
          <div className="space-y-1">
            <label htmlFor="product-commission-percent" className="label-premium">Comissão (%)</label>
            <input
              id="product-commission-percent"
              type="number"
              step="0.01"
              min="0"
              max="100"
              placeholder="Sem comissão"
              value={percent}
              onChange={(e) => setPercent(e.target.value)}
              className="input-premium !w-36"
            />
          </div>
          <button
            type="button"
            onClick={handleSave}
            disabled={isSaving || percent === saved}
            className="btn-premium text-sm px-5 py-2.5 disabled:opacity-50"
          >
            {isSaving ? 'Salvando...' : 'Salvar'}
          </button>
        </div>
      </PermissionGate>
    </div>
  );
};
