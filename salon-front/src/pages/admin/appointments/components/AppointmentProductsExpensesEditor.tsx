import { useEffect, useState } from 'react';
import { Plus, Trash2 } from 'lucide-react';
import { appointmentsApi } from '../../../appointments/services/appointments';
import type {
  AppointmentExpenseRequestItem,
  AppointmentProductRequestItem,
  AppointmentResponse,
} from '../../../appointments/services/appointments';
import { productsApi } from '../../products/services/products';
import type { ProductData } from '../../products/services/products';
import { PermissionGate } from '../../../../components/permissions/PermissionGate';
import { useAlert } from '../../../../hooks/useAlert';
import { getApiErrorMessage } from '../../../../utils/apiError';

const labelCls = 'label-premium';
const inputCls = 'input-premium';

function formatMoney(value: number | null | undefined): string {
  return value != null ? `R$ ${value.toFixed(2)}` : '—';
}

interface ProductRow {
  productId: string;
  quantity: string;
  customPrice: string;
}

interface ExpenseRow {
  description: string;
  valueType: 'FIXED' | 'PERCENTAGE';
  value: string;
}

interface AppointmentProductsExpensesEditorProps {
  appointment: AppointmentResponse;
  onSaved: (updated: AppointmentResponse) => void;
}

export const AppointmentProductsExpensesEditor = ({
  appointment,
  onSaved,
}: AppointmentProductsExpensesEditorProps) => {
  const [catalog, setCatalog] = useState<ProductData[]>([]);
  const [productSearch, setProductSearch] = useState('');
  const [productRows, setProductRows] = useState<ProductRow[]>([]);
  const [expenseRows, setExpenseRows] = useState<ExpenseRow[]>([]);
  const [isSavingProducts, setIsSavingProducts] = useState(false);
  const [isSavingExpenses, setIsSavingExpenses] = useState(false);
  const { error: showError, success: showSuccess } = useAlert();

  // Trava só quando o PAGAMENTO já aconteceu (PAID ou MANUAL) — não pelo status do
  // agendamento em si. Um atendimento concluído mas ainda não pago continua editável
  // (ex.: cliente comprou mais um produto na saída, antes de fechar a conta).
  const readOnly =
    appointment.status === 'CANCELLED' ||
    appointment.paymentStatus === 'PAID' ||
    appointment.paymentStatus === 'MANUAL';

  useEffect(() => {
    productsApi
      .findAll({ active: true }, 0, 1000)
      .then((page) => setCatalog(page.content.filter((p) => p.availableForSale !== false)))
      .catch(() => setCatalog([]));
  }, []);

  useEffect(() => {
    setProductRows(
      (appointment.products ?? []).map((p) => ({
        productId: String(p.productId),
        quantity: String(p.quantity),
        customPrice: p.customPrice != null ? String(p.customPrice) : '',
      }))
    );
    setExpenseRows(
      (appointment.expenses ?? []).map((e) => ({
        description: e.description,
        valueType: e.valueType,
        value: String(e.value),
      }))
    );
  }, [appointment.id, appointment.products, appointment.expenses]);

  const addProductRow = () => {
    setProductRows((prev) => [...prev, { productId: '', quantity: '1', customPrice: '' }]);
  };

  const removeProductRow = (index: number) => {
    setProductRows((prev) => prev.filter((_, i) => i !== index));
  };

  const updateProductRow = (index: number, patch: Partial<ProductRow>) => {
    setProductRows((prev) => prev.map((row, i) => (i === index ? { ...row, ...patch } : row)));
  };

  const addExpenseRow = () => {
    setExpenseRows((prev) => [...prev, { description: '', valueType: 'FIXED', value: '' }]);
  };

  const removeExpenseRow = (index: number) => {
    setExpenseRows((prev) => prev.filter((_, i) => i !== index));
  };

  const updateExpenseRow = (index: number, patch: Partial<ExpenseRow>) => {
    setExpenseRows((prev) => prev.map((row, i) => (i === index ? { ...row, ...patch } : row)));
  };

  const handleSaveProducts = async () => {
    const invalid = productRows.some((r) => !r.productId || !r.quantity || Number(r.quantity) <= 0);
    if (invalid) {
      await showError('Selecione o produto e informe uma quantidade válida em todas as linhas.');
      return;
    }
    setIsSavingProducts(true);
    try {
      const payload: AppointmentProductRequestItem[] = productRows.map((r) => ({
        productId: Number(r.productId),
        quantity: Number(r.quantity),
        customPrice: r.customPrice === '' ? null : Number(r.customPrice),
      }));
      const updated = await appointmentsApi.updateProducts(appointment.id, payload);
      onSaved(updated);
      await showSuccess('Produtos do agendamento atualizados');
    } catch (err) {
      await showError(getApiErrorMessage(err, 'Erro ao salvar produtos do agendamento'));
    } finally {
      setIsSavingProducts(false);
    }
  };

  const handleSaveExpenses = async () => {
    const invalid = expenseRows.some((r) => !r.description.trim() || r.value === '' || Number(r.value) < 0);
    if (invalid) {
      await showError('Preencha a descrição e um valor válido em todas as despesas.');
      return;
    }
    setIsSavingExpenses(true);
    try {
      const payload: AppointmentExpenseRequestItem[] = expenseRows.map((r) => ({
        description: r.description.trim(),
        valueType: r.valueType,
        value: Number(r.value),
      }));
      const updated = await appointmentsApi.updateExpenses(appointment.id, payload);
      onSaved(updated);
      await showSuccess('Despesas do agendamento atualizadas');
    } catch (err) {
      await showError(getApiErrorMessage(err, 'Erro ao salvar despesas do agendamento'));
    } finally {
      setIsSavingExpenses(false);
    }
  };

  return (
    <div className="space-y-5">
      <PermissionGate
        method="PATCH"
        endpoint={`/v1/appointments/${appointment.id}/products`}
        fallback={
          (appointment.products?.length ?? 0) > 0 ? (
            <div>
              <span className={labelCls}>Produtos vendidos</span>
              <ul className="text-sm text-[#3b3036] mt-1 space-y-1">
                {appointment.products!.map((p) => (
                  <li key={p.productId} className="flex justify-between">
                    <span>
                      {p.quantity}× {p.productName}
                    </span>
                    <span className="font-semibold">{formatMoney(p.effectiveTotalPrice)}</span>
                  </li>
                ))}
              </ul>
            </div>
          ) : null
        }
      >
        <div>
          <div className="flex items-center justify-between mb-2">
            <span className={labelCls}>Produtos vendidos</span>
            {!readOnly && (
              <button
                type="button"
                onClick={addProductRow}
                className="inline-flex items-center gap-1 text-xs font-semibold text-[#be8a83] hover:text-[#a6726b] cursor-pointer"
              >
                <Plus size={13} /> Adicionar produto
              </button>
            )}
          </div>

          {readOnly && productRows.length === 0 && (
            <p className="text-xs text-gray-400">Nenhum produto vendido neste atendimento.</p>
          )}

          {readOnly ? (
            <ul className="text-sm text-[#3b3036] space-y-1">
              {(appointment.products ?? []).map((p) => (
                <li key={p.productId} className="flex justify-between">
                  <span>
                    {p.quantity}× {p.productName}
                  </span>
                  <span className="font-semibold">{formatMoney(p.effectiveTotalPrice)}</span>
                </li>
              ))}
            </ul>
          ) : (
            <div className="space-y-2">
              {productRows.length === 0 && (
                <p className="text-xs text-gray-400">Nenhum produto adicionado.</p>
              )}
              {catalog.length > 6 && (
                <input
                  type="text"
                  value={productSearch}
                  onChange={(e) => setProductSearch(e.target.value)}
                  placeholder="Buscar produto por nome..."
                  className={`${inputCls} mb-1`}
                />
              )}
              {productRows.map((row, index) => (
                <div key={index} className="flex items-center gap-2">
                  <select
                    className={`${inputCls} flex-1`}
                    value={row.productId}
                    onChange={(e) => updateProductRow(index, { productId: e.target.value })}
                  >
                    <option value="">Selecione o produto...</option>
                    {catalog
                      .filter(
                        (p) =>
                          String(p.id) === row.productId ||
                          p.name.toLowerCase().includes(productSearch.trim().toLowerCase())
                      )
                      .map((p) => (
                        <option key={p.id} value={p.id}>
                          {p.name} — R$ {p.price.toFixed(2)}
                        </option>
                      ))}
                  </select>
                  <input
                    type="number"
                    min={1}
                    className={`${inputCls} w-16`}
                    placeholder="Qtd"
                    value={row.quantity}
                    onChange={(e) => updateProductRow(index, { quantity: e.target.value })}
                  />
                  <input
                    type="number"
                    step="0.01"
                    className={`${inputCls} w-24`}
                    placeholder="Preço custom."
                    value={row.customPrice}
                    onChange={(e) => updateProductRow(index, { customPrice: e.target.value })}
                  />
                  <button
                    type="button"
                    onClick={() => removeProductRow(index)}
                    className="p-1.5 text-rose-500 hover:bg-rose-50 rounded-lg transition-all cursor-pointer"
                  >
                    <Trash2 size={14} />
                  </button>
                </div>
              ))}
              <div className="flex justify-between items-center pt-1">
                <span className="text-xs text-gray-400">
                  Total em produtos: {formatMoney(appointment.totalProductsPrice)}
                </span>
                <button
                  type="button"
                  onClick={handleSaveProducts}
                  disabled={isSavingProducts}
                  className="btn-premium text-xs px-4 py-2 disabled:opacity-50"
                >
                  {isSavingProducts ? 'Salvando...' : 'Salvar produtos'}
                </button>
              </div>
            </div>
          )}
        </div>
      </PermissionGate>

      <PermissionGate
        method="PATCH"
        endpoint={`/v1/appointments/${appointment.id}/expenses`}
        fallback={
          (appointment.expenses?.length ?? 0) > 0 ? (
            <div>
              <span className={labelCls}>Despesas do atendimento</span>
              <ul className="text-sm text-[#3b3036] mt-1 space-y-1">
                {appointment.expenses!.map((e) => (
                  <li key={e.id} className="flex justify-between">
                    <span>{e.description}</span>
                    <span className="font-semibold">{formatMoney(e.effectiveAmount)}</span>
                  </li>
                ))}
              </ul>
            </div>
          ) : null
        }
      >
        <div className="border-t border-[#eae1e1] pt-4">
          <div className="flex items-center justify-between mb-2">
            <span className={labelCls}>Despesas do atendimento</span>
            {!readOnly && (
              <button
                type="button"
                onClick={addExpenseRow}
                className="inline-flex items-center gap-1 text-xs font-semibold text-[#be8a83] hover:text-[#a6726b] cursor-pointer"
              >
                <Plus size={13} /> Adicionar despesa
              </button>
            )}
          </div>

          {readOnly ? (
            <>
              {expenseRows.length === 0 && (
                <p className="text-xs text-gray-400">Nenhuma despesa lançada neste atendimento.</p>
              )}
              <ul className="text-sm text-[#3b3036] space-y-1">
                {(appointment.expenses ?? []).map((e) => (
                  <li key={e.id} className="flex justify-between">
                    <span>{e.description}</span>
                    <span className="font-semibold">{formatMoney(e.effectiveAmount)}</span>
                  </li>
                ))}
              </ul>
            </>
          ) : (
            <div className="space-y-2">
              {expenseRows.length === 0 && (
                <p className="text-xs text-gray-400">Nenhuma despesa adicionada.</p>
              )}
              {expenseRows.map((row, index) => (
                <div key={index} className="flex items-center gap-2">
                  <input
                    type="text"
                    className={`${inputCls} flex-1`}
                    placeholder="Descrição (ex.: material extra)"
                    value={row.description}
                    onChange={(e) => updateExpenseRow(index, { description: e.target.value })}
                  />
                  <select
                    className={`${inputCls} w-24`}
                    value={row.valueType}
                    onChange={(e) =>
                      updateExpenseRow(index, { valueType: e.target.value as 'FIXED' | 'PERCENTAGE' })
                    }
                  >
                    <option value="FIXED">R$</option>
                    <option value="PERCENTAGE">%</option>
                  </select>
                  <input
                    type="number"
                    step="0.01"
                    className={`${inputCls} w-20`}
                    placeholder="Valor"
                    value={row.value}
                    onChange={(e) => updateExpenseRow(index, { value: e.target.value })}
                  />
                  <button
                    type="button"
                    onClick={() => removeExpenseRow(index)}
                    className="p-1.5 text-rose-500 hover:bg-rose-50 rounded-lg transition-all cursor-pointer"
                  >
                    <Trash2 size={14} />
                  </button>
                </div>
              ))}
              <div className="flex justify-between items-center pt-1">
                <span className="text-xs text-gray-400">
                  Total em despesas: {formatMoney(appointment.totalExpensesAmount)}
                </span>
                <button
                  type="button"
                  onClick={handleSaveExpenses}
                  disabled={isSavingExpenses}
                  className="btn-premium text-xs px-4 py-2 disabled:opacity-50"
                >
                  {isSavingExpenses ? 'Salvando...' : 'Salvar despesas'}
                </button>
              </div>
            </div>
          )}
        </div>
      </PermissionGate>

      {(appointment.totalProductsPrice ?? 0) > 0 || (appointment.totalExpensesAmount ?? 0) > 0 ? (
        <div className="flex items-center justify-between px-4 py-3 border border-[#eae1e1] rounded-xl bg-[#fcf9f9]/50">
          <span className="text-sm font-semibold text-[#3b3036]">Total geral do atendimento</span>
          <span className="font-bold text-[#3b3036]">{formatMoney(appointment.grandTotal)}</span>
        </div>
      ) : null}
    </div>
  );
};
