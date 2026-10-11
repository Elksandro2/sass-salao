import { useEffect, useMemo, useState } from 'react';
import { createPortal } from 'react-dom';
import { Plus, RotateCcw, Trash2, X } from 'lucide-react';
import { CurrencyInput } from '../../../../components/CurrencyInput';
import { SearchableSelect } from '../../../../components/SearchableSelect';
import { productsApi, type ProductData } from '../../products/services/products';
import { employeesApi, type EmployeeData } from '../../employees/services/employees';
import { cashFlowApi, type CashFlowRecord } from '../services/cashflow';
import { useAlert } from '../../../../hooks/useAlert';
import { getApiErrorMessage } from '../../../../utils/apiError';

const inputCls = 'input-premium';
const labelCls = 'label-premium';

interface Row {
  key: number;
  productId: string;
  /** Nome congelado da venda — mostra o produto mesmo se ele saiu do catálogo. */
  productName: string;
  quantity: number;
  unitPrice: string;
}

interface SaleEditModalProps {
  sale: CashFlowRecord | null;
  onClose: () => void;
  onSaved: (updated: CashFlowRecord) => void;
}

const money = (v: number) => new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(v);

/**
 * Edição completa de uma venda avulsa de produto (Fluxo de Caixa): itens, quantidade, preço,
 * vendedora, data e comissão — como se edita um agendamento. Venda antiga, sem itens
 * registrados, é editada pelo valor total.
 */
export const SaleEditModal = ({ sale, onClose, onSaved }: SaleEditModalProps) => {
  const [products, setProducts] = useState<ProductData[]>([]);
  const [employees, setEmployees] = useState<EmployeeData[]>([]);
  const [rows, setRows] = useState<Row[]>([]);
  const [date, setDate] = useState('');
  const [employeeId, setEmployeeId] = useState('');
  const [amount, setAmount] = useState('');
  const [note, setNote] = useState('');
  /** null = comissão automática (pelo % da venda). */
  const [manualCommission, setManualCommission] = useState<string | null>(null);
  const [isSaving, setIsSaving] = useState(false);
  const { error: showError } = useAlert();

  useEffect(() => {
    Promise.all([
      productsApi.findAll({ active: true }, 0, 1000),
      employeesApi.findAll({ active: true }, 0, 1000),
    ])
      .then(([prods, emps]) => {
        setProducts(prods.content.filter((p) => p.availableForSale !== false));
        setEmployees(emps.content);
      })
      .catch(() => {
        setProducts([]);
        setEmployees([]);
      });
  }, []);

  useEffect(() => {
    if (!sale) return;
    setDate(sale.date);
    setEmployeeId(sale.employeeId != null ? String(sale.employeeId) : '');
    setAmount(sale.amount.toFixed(2));
    // Descrição automática ("Venda de Produtos: ...") é remontada pelo backend — só texto livre
    // vira observação editável.
    setNote(sale.description?.toLowerCase().startsWith('venda de produto') ? '' : sale.description ?? '');
    setManualCommission(sale.commissionManual && sale.commissionAmount != null ? sale.commissionAmount.toFixed(2) : null);
    setRows(
      (sale.items ?? []).map((item, i) => ({
        key: i,
        productId: item.productId != null ? String(item.productId) : '',
        productName: item.productName,
        quantity: item.quantity,
        unitPrice: item.unitPrice.toFixed(2),
      }))
    );
  }, [sale]);

  const hasItems = rows.length > 0 || (sale?.items?.length ?? 0) > 0;
  const total = hasItems
    ? rows.reduce((sum, r) => sum + Number(r.unitPrice || 0) * r.quantity, 0)
    : Number(amount || 0);
  const percent = sale?.commissionPercent ?? null;
  const autoCommission = percent != null ? Math.round(total * percent) / 100 : null;

  const productOptions = useMemo(
    () => products.filter((p) => p.id != null).map((p) => ({ id: p.id!, label: p.name })),
    [products]
  );

  if (!sale) return null;

  const updateRow = (key: number, patch: Partial<Row>) =>
    setRows((prev) => prev.map((r) => (r.key === key ? { ...r, ...patch } : r)));

  const selectProduct = (key: number, id: string) => {
    const product = products.find((p) => String(p.id) === id);
    updateRow(key, {
      productId: id,
      productName: product?.name ?? '',
      unitPrice: product?.price != null ? product.price.toFixed(2) : '',
    });
  };

  const addRow = () =>
    setRows((prev) => [...prev, { key: Date.now(), productId: '', productName: '', quantity: 1, unitPrice: '' }]);

  const save = async () => {
    if (hasItems) {
      if (rows.length === 0) {
        await showError('A venda precisa ter ao menos um produto.');
        return;
      }
      if (rows.some((r) => !r.productId)) {
        await showError('Escolha o produto de cada linha (ou remova a linha vazia).');
        return;
      }
    }
    setIsSaving(true);
    try {
      const updated = await cashFlowApi.updateSale(sale.id!, {
        date,
        employeeId: employeeId ? Number(employeeId) : null,
        items: rows.map((r) => ({
          productId: Number(r.productId),
          quantity: r.quantity,
          unitPrice: r.unitPrice !== '' ? Number(r.unitPrice) : null,
        })),
        amount: hasItems ? null : Number(amount || 0),
        description: note.trim() || null,
        commissionAmount: employeeId && manualCommission != null ? Number(manualCommission || 0) : null,
      });
      onSaved(updated);
    } catch (err) {
      await showError(getApiErrorMessage(err, 'Erro ao salvar a venda'));
    } finally {
      setIsSaving(false);
    }
  };

  return createPortal(
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-[#261f23]/40 backdrop-blur-md">
      <div className="bg-white rounded-2xl shadow-xl w-full max-w-lg border border-[#eae1e1]/85 overflow-hidden animate-scale-up max-h-[85vh] flex flex-col">
        <div className="flex items-center justify-between px-6 py-4 border-b border-[#eae1e1] bg-[#fcf9f9]/50 shrink-0">
          <h3 className="font-heading text-lg font-bold text-[#3b3036]">Editar venda de produto</h3>
          <button
            type="button"
            onClick={onClose}
            aria-label="Fechar"
            className="p-1.5 text-gray-400 hover:text-gray-600 hover:bg-gray-100 rounded-lg transition-all cursor-pointer"
          >
            <X size={20} />
          </button>
        </div>

        <div className="p-6 space-y-4 overflow-y-auto flex-1 min-h-0">
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label htmlFor="sale-date" className={labelCls}>Data</label>
              <input id="sale-date" type="date" className={inputCls} value={date} onChange={(e) => setDate(e.target.value)} />
            </div>
            <div>
              <label htmlFor="sale-seller" className={labelCls}>Vendido por</label>
              <select id="sale-seller" className={inputCls} value={employeeId} onChange={(e) => setEmployeeId(e.target.value)}>
                <option value="">Não informar</option>
                {employees.map((emp) => (
                  <option key={emp.id} value={emp.id}>{emp.name}</option>
                ))}
                {sale.employeeId != null && !employees.some((e) => e.id === sale.employeeId) && (
                  <option value={sale.employeeId}>{sale.employeeName}</option>
                )}
              </select>
            </div>
          </div>

          {hasItems ? (
            <div className="space-y-2">
              <span className={labelCls}>Produtos</span>
              {rows.map((row) => (
                <div key={row.key} className="rounded-xl border border-[#eae1e1] p-3 space-y-2">
                  <div className="flex items-start gap-2">
                    <div className="flex-1">
                      <SearchableSelect
                        value={row.productId}
                        onSelect={(id) => selectProduct(row.key, id)}
                        options={
                          row.productId && !productOptions.some((o) => String(o.id) === row.productId)
                            ? [...productOptions, { id: Number(row.productId), label: row.productName }]
                            : productOptions
                        }
                        placeholder="Buscar produto..."
                        noResultsLabel="Nenhum produto encontrado"
                      />
                    </div>
                    <button
                      type="button"
                      onClick={() => setRows((prev) => prev.filter((r) => r.key !== row.key))}
                      aria-label="Remover produto"
                      className="p-2 text-gray-400 hover:text-rose-600 hover:bg-rose-50 rounded-lg cursor-pointer"
                    >
                      <Trash2 size={15} />
                    </button>
                  </div>
                  <div className="grid grid-cols-3 gap-2 items-end">
                    <div>
                      <label className="text-[11px] text-[#7a7074]" htmlFor={`qty-${row.key}`}>Qtd.</label>
                      <input
                        id={`qty-${row.key}`}
                        type="number"
                        min={1}
                        className={inputCls}
                        value={row.quantity}
                        onChange={(e) => updateRow(row.key, { quantity: Math.max(1, parseInt(e.target.value) || 1) })}
                      />
                    </div>
                    <div>
                      <label className="text-[11px] text-[#7a7074]" htmlFor={`price-${row.key}`}>Preço unit.</label>
                      <CurrencyInput
                        id={`price-${row.key}`}
                        className={inputCls}
                        value={row.unitPrice}
                        onValueChange={(v) => updateRow(row.key, { unitPrice: v })}
                      />
                    </div>
                    <p className="text-sm font-semibold text-[#3b3036] text-right pb-2">
                      {money(Number(row.unitPrice || 0) * row.quantity)}
                    </p>
                  </div>
                </div>
              ))}
              <button
                type="button"
                onClick={addRow}
                className="flex items-center gap-1.5 text-xs font-semibold text-[#be8a83] hover:text-[#a6726b] cursor-pointer"
              >
                <Plus size={14} /> Adicionar produto
              </button>
            </div>
          ) : (
            <div>
              <label htmlFor="sale-amount" className={labelCls}>Valor total</label>
              <CurrencyInput id="sale-amount" className={inputCls} value={amount} onValueChange={setAmount} />
              <p className="text-[11px] text-[#7a7074] mt-1">
                Venda registrada antes da edição por produto existir — só o total pode ser ajustado.
              </p>
            </div>
          )}

          <div>
            <label htmlFor="sale-note" className={labelCls}>
              {hasItems ? 'Observação (opcional)' : 'Descrição'}
            </label>
            <input
              id="sale-note"
              type="text"
              maxLength={300}
              className={inputCls}
              value={note}
              onChange={(e) => setNote(e.target.value)}
              placeholder={hasItems ? 'Ex.: cliente fiel, combinado preço especial' : sale.description}
            />
          </div>

          {employeeId && (
            <div className="rounded-xl border border-[#eae1e1] bg-[#fcf9f9]/50 p-3 space-y-2">
              <div className="flex items-center justify-between gap-2">
                <label htmlFor="sale-commission" className="text-xs font-semibold text-[#3b3036]">
                  Comissão da vendedora
                </label>
                {manualCommission != null ? (
                  <button
                    type="button"
                    onClick={() => setManualCommission(null)}
                    className="flex items-center gap-1 text-[11px] text-[#7a7074] hover:text-[#be8a83] cursor-pointer"
                  >
                    <RotateCcw size={11} /> usar automático
                  </button>
                ) : (
                  <span className="text-[11px] text-emerald-600">
                    automático{percent != null ? ` (${percent}%)` : ' — sem % configurado'}
                  </span>
                )}
              </div>
              <CurrencyInput
                id="sale-commission"
                className={inputCls}
                value={manualCommission ?? (autoCommission ?? 0).toFixed(2)}
                onValueChange={(v) => setManualCommission(v)}
              />
            </div>
          )}

          <div className="flex justify-between text-sm font-bold pt-2 border-t border-[#eae1e1]">
            <span className="text-[#3b3036]">Total da venda</span>
            <span className="text-emerald-600">{money(total)}</span>
          </div>
        </div>

        <div className="flex justify-end gap-2 px-6 py-4 border-t border-[#eae1e1] bg-[#fcf9f9]/50 shrink-0">
          <button
            type="button"
            onClick={onClose}
            className="px-5 py-2.5 border border-[#eae1e1] font-semibold text-sm text-[#3b3036] hover:bg-white hover:border-[#be8a83]/50 rounded-xl transition-all cursor-pointer"
          >
            Cancelar
          </button>
          <button
            type="button"
            onClick={save}
            disabled={isSaving}
            className="btn-premium text-sm px-5 py-2.5 disabled:opacity-50"
          >
            {isSaving ? 'Salvando...' : 'Salvar venda'}
          </button>
        </div>
      </div>
    </div>,
    document.body
  );
};
