import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { Plus, Edit, Trash2, RotateCcw } from 'lucide-react';
import { DataTable } from '../../../components/table/DataTable';
import type { FilterField } from '../../../components/table/DataTable';
import { ModalForm } from '../../../components/modal/ModalForm';
import { ConfirmDialog } from '../../../components/modal/ConfirmDialog';
import { PermissionGate } from '../../../components/permissions/PermissionGate';
import { productsApi } from './services/products';
import type { ProductData, ProductFilter } from './services/products';
import { productFormSchema } from './product.schema';
import type { ProductFormValues } from './product.schema';
import { getApiErrorMessage } from '../../../utils/apiError';
import { useAlert } from '../../../hooks/useAlert';
import { PRODUCT_UNITS } from '../../../utils/productUnit';

const inputCls = 'input-premium';
const labelCls = 'label-premium';

interface ProductsProps {
  /**
   * 'sale' = tela "Produtos (Venda)": tem preço de venda, disponível pra venda por padrão.
   * 'use' = tela "Produtos (Uso)": só custeio interno, sem preço — pode opcionalmente marcar
   * "também disponível para venda" pra ganhar o campo de preço e aparecer nas duas telas.
   */
  mode: 'sale' | 'use';
}

export const Products = ({ mode }: ProductsProps) => {
  const isSaleMode = mode === 'sale';
  const [refreshTrigger, setRefreshTrigger] = useState(0);

  const [showForm, setShowForm] = useState(false);
  const [editingProduct, setEditingProduct] = useState<ProductData | null>(null);

  const [showConfirm, setShowConfirm] = useState(false);
  const [productTargetId, setProductTargetId] = useState<number | null>(null);
  const [confirmAction, setConfirmAction] = useState<'delete' | 'reactivate'>('delete');


  const {
    register,
    handleSubmit,
    reset,
    setValue,
    watch,
    formState: { errors },
  } = useForm<ProductFormValues>({ resolver: zodResolver(productFormSchema) });
  const { error: showError } = useAlert();

  // No modo Venda o produto é sempre disponível pra venda (o preço sempre aparece). No modo Uso
  // o preço só aparece se a pessoa marcar "também disponível para venda".
  const showPriceField = isSaleMode || watch('availableForSale');

  const fetchProductsData = async (filter: ProductFilter, page: number, size: number) => {
    return productsApi.findAll(filter, page, size);
  };

  const handleOpenForm = (product?: ProductData) => {
    reset();
    if (product) {
      setEditingProduct(product);
      setValue('name', product.name);
      setValue('price', product.price != null ? String(product.price) : '');
      setValue('active', product.active !== false);
      setValue('brand', product.brand ?? '');
      setValue('costPrice', product.costPrice != null ? String(product.costPrice) : '');
      setValue('capacity', product.capacity != null ? String(product.capacity) : '');
      setValue('unit', product.unit ?? '');
      setValue('availableForSale', product.availableForSale !== false);
      setValue('usedInServiceRecipe', product.usedInServiceRecipe !== false);
    } else {
      setEditingProduct(null);
      setValue('active', true);
      // O flag "principal" desta tela nasce ligado; o outro nasce desligado — a pessoa liga
      // manualmente se o produto for dos dois tipos.
      setValue('availableForSale', isSaleMode);
      setValue('usedInServiceRecipe', !isSaleMode);
    }
    setShowForm(true);
  };

  const onSubmit = async (data: ProductFormValues) => {
    try {
      const payload: ProductData = {
        name: data.name,
        price: data.price ? Number(data.price) : null,
        active: data.active,
        brand: data.brand?.trim() || null,
        costPrice: data.costPrice ? Number(data.costPrice) : null,
        capacity: data.capacity ? Number(data.capacity) : null,
        unit: data.unit ? (data.unit as ProductData['unit']) : null,
        availableForSale: data.availableForSale,
        usedInServiceRecipe: data.usedInServiceRecipe,
      };
      if (editingProduct?.id) {
        await productsApi.update(editingProduct.id, payload);
      } else {
        await productsApi.create(payload);
      }
      setShowForm(false);
      setRefreshTrigger((prev) => prev + 1);
    } catch (err) {
      const msg = getApiErrorMessage(
        err,
        'Erro ao salvar produto. Verifique os dados e tente novamente.'
      );
      await showError(msg);
    }
  };

  const handleConfirmAction = async () => {
    if (!productTargetId) return;
    try {
      if (confirmAction === 'delete') {
        await productsApi.delete(productTargetId);
      } else {
        await productsApi.reactivate(productTargetId);
      }
      setShowConfirm(false);
      setRefreshTrigger((prev) => prev + 1);
    } catch (err) {
      const fallbackMsg =
        confirmAction === 'delete' ? 'Erro ao excluir produto.' : 'Erro ao reativar produto.';
      const msg = getApiErrorMessage(err, fallbackMsg);
      await showError(msg);
    }
  };


  const columns = [
    { key: 'name', label: 'Nome do Produto' },
    { key: 'brand', label: 'Marca', render: (item: ProductData) => item.brand?.trim() || '—' },
    isSaleMode
      ? {
          key: 'price',
          label: 'Preço de Venda',
          render: (item: ProductData) =>
            item.price != null ? `R$ ${item.price.toFixed(2)}` : '—',
        }
      : {
          key: 'costPrice',
          label: 'Quanto Pagou',
          render: (item: ProductData) =>
            item.costPrice != null ? `R$ ${item.costPrice.toFixed(2)}` : '—',
        },
    {
      key: 'active',
      label: 'Status',
      render: (item: ProductData) => (item.active !== false ? 'Ativo' : 'Inativo'),
    },
    {
      key: 'actions',
      label: 'Ações',
      render: (item: ProductData) => (
        <div className="flex gap-2">
          {item.active !== false ? (
            <>
              <PermissionGate method="PUT" endpoint={`/v1/products/${item.id}`}>
                <button
                  onClick={() => handleOpenForm(item)}
                  className="p-1.5 text-indigo-600 hover:bg-indigo-50 border border-indigo-200 rounded-lg transition-all cursor-pointer"
                  title="Editar Produto"
                >
                  <Edit size={15} />
                </button>
              </PermissionGate>
              <PermissionGate method="DELETE" endpoint={`/v1/products/${item.id}`}>
                <button
                  onClick={() => {
                    setProductTargetId(item.id!);
                    setConfirmAction('delete');
                    setShowConfirm(true);
                  }}
                  className="p-1.5 text-rose-600 hover:bg-rose-50 border border-rose-200 rounded-lg transition-all cursor-pointer"
                  title="Excluir Produto"
                >
                  <Trash2 size={15} />
                </button>
              </PermissionGate>
            </>
          ) : (
            <PermissionGate method="PATCH" endpoint={`/v1/products/${item.id}/reactivate`}>
              <button
                onClick={() => {
                  setProductTargetId(item.id!);
                  setConfirmAction('reactivate');
                  setShowConfirm(true);
                }}
                className="p-1.5 text-emerald-600 hover:bg-emerald-50 border border-emerald-200 rounded-lg transition-all cursor-pointer flex items-center gap-1 text-xs font-semibold"
                title="Reativar Produto"
              >
                <RotateCcw size={15} />
                <span>Reativar</span>
              </button>
            </PermissionGate>
          )}
        </div>
      ),
    },
  ];

  const filtersConfig: FilterField[] = [
    { key: 'name', label: 'Nome', type: 'text' },
    { key: 'active', label: 'Status', type: 'boolean' },
  ];

  const initialFilters: ProductFilter = isSaleMode
    ? { name: '', active: undefined, availableForSale: true }
    : { name: '', active: undefined, usedInServiceRecipe: true };

  const pageTitle = isSaleMode ? 'Produtos (Venda)' : 'Produtos (Uso)';
  const newButtonLabel = isSaleMode ? 'Novo Produto de Venda' : 'Novo Produto de Uso';

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4">
        <div>
          <h2 className="font-heading text-2xl font-bold text-[#3b3036]">Gerenciar {pageTitle}</h2>
          <p className="text-xs text-[#3b3036]/60 mt-1">
            {isSaleMode
              ? 'Produtos vendidos ao cliente — no Fluxo de Caixa e nos atendimentos.'
              : 'Produtos de uso interno consumidos na receita de um serviço (ex.: tintura, óleo).'}
          </p>
        </div>
        <PermissionGate method="POST" endpoint="/v1/products">
          <button onClick={() => handleOpenForm()} className="btn-premium">
            <Plus size={18} /> {newButtonLabel}
          </button>
        </PermissionGate>
      </div>

      <DataTable
        columns={columns}
        fetchData={fetchProductsData}
        filtersConfig={filtersConfig}
        keyExtractor={(item) => item.id!}
        refreshTrigger={refreshTrigger}
        initialFilters={initialFilters}
      />

      <ModalForm
        show={showForm}
        onHide={() => setShowForm(false)}
        title={
          editingProduct
            ? isSaleMode ? 'Editar Produto de Venda' : 'Editar Produto de Uso'
            : newButtonLabel
        }
        onSubmit={handleSubmit(onSubmit)}
      >
        <div className="space-y-4">
          <div>
            <label className={labelCls}>Nome do Produto *</label>
            <input
              type="text"
              maxLength={150}
              className={`${inputCls} ${errors.name ? 'border-rose-300' : ''}`}
              {...register('name')}
            />
            {errors.name && (
              <span className="text-xs text-rose-500 font-semibold">{errors.name.message}</span>
            )}
          </div>

          {showPriceField && (
            <div>
              <label className={labelCls}>
                Preço de Venda (R$) {isSaleMode && '*'}
              </label>
              <input
                type="number"
                step="0.01"
                className={`${inputCls} ${errors.price ? 'border-rose-300' : ''}`}
                {...register('price')}
              />
              <p className="text-xs text-gray-400 mt-1">
                Este é o valor cobrado da cliente — aparece no Fluxo de Caixa e no seletor de
                produtos vendidos no atendimento.
              </p>
              {errors.price && (
                <span className="text-xs text-rose-500 font-semibold">{errors.price.message}</span>
              )}
            </div>
          )}

          <div className="border-t border-[#eae1e1]/50 pt-4">
            <h4 className="font-heading font-semibold text-sm text-[#3b3036] mb-3">
              {isSaleMode ? 'Custeio (opcional)' : 'Quanto o salão pagou'}
            </h4>
            <p className="text-xs text-gray-400 -mt-2 mb-3">
              Usado só internamente pra calcular custo por atendimento nos relatórios — não
              aparece pra cliente.
            </p>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div>
                <label className={labelCls}>Marca</label>
                <input
                  type="text"
                  maxLength={100}
                  className={inputCls}
                  {...register('brand')}
                />
              </div>
              <div>
                <label className={labelCls}>Quanto o salão pagou (R$)</label>
                <input
                  type="number"
                  step="0.01"
                  min="0"
                  className={`${inputCls} ${errors.costPrice ? 'border-rose-300' : ''}`}
                  {...register('costPrice')}
                />
                {errors.costPrice && (
                  <span className="text-xs text-rose-500 font-semibold">{errors.costPrice.message}</span>
                )}
              </div>
              <div>
                <label className={labelCls}>Capacidade da embalagem</label>
                <input
                  type="number"
                  step="0.01"
                  min="0"
                  placeholder="Ex.: 1000"
                  className={`${inputCls} ${errors.capacity ? 'border-rose-300' : ''}`}
                  {...register('capacity')}
                />
                {errors.capacity && (
                  <span className="text-xs text-rose-500 font-semibold">{errors.capacity.message}</span>
                )}
              </div>
              <div>
                <label className={labelCls}>Unidade</label>
                <select className={inputCls} {...register('unit')}>
                  <option value="">Não informado</option>
                  {PRODUCT_UNITS.map((u) => (
                    <option key={u.value} value={u.value}>
                      {u.label}
                    </option>
                  ))}
                </select>
              </div>
            </div>
          </div>

          <div className="border-t border-[#eae1e1]/50 pt-4 space-y-2">
            <h4 className="font-heading font-semibold text-sm text-[#3b3036] mb-1">
              Onde este produto também aparece
            </h4>
            {isSaleMode ? (
              <label className="flex items-center gap-2 cursor-pointer">
                <input type="checkbox" className="rounded" {...register('usedInServiceRecipe')} />
                <span className="text-sm text-[#3b3036]">
                  Também usado na receita de serviço (uso interno)
                </span>
              </label>
            ) : (
              <label className="flex items-center gap-2 cursor-pointer">
                <input type="checkbox" className="rounded" {...register('availableForSale')} />
                <span className="text-sm text-[#3b3036]">Também disponível para venda</span>
              </label>
            )}
          </div>
        </div>
      </ModalForm>

      <ConfirmDialog
        show={showConfirm}
        onHide={() => setShowConfirm(false)}
        onConfirm={handleConfirmAction}
        title={confirmAction === 'delete' ? 'Excluir Produto' : 'Reativar Produto'}
        message={
          confirmAction === 'delete'
            ? 'Tem certeza que deseja excluir este produto? Esta ação não pode ser desfeita.'
            : 'Tem certeza que deseja reativar este produto? Ele aparecerá novamente nas listagens públicas.'
        }
        confirmLabel={confirmAction === 'delete' ? 'Excluir' : 'Reativar'}
        variant={confirmAction === 'delete' ? 'danger' : 'primary'}
      />
    </div>
  );
};
