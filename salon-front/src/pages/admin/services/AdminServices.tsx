import { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { Plus, Edit, Trash2, RotateCcw } from 'lucide-react';
import { DataTable } from '../../../components/table/DataTable';
import type { FilterField } from '../../../components/table/DataTable';
import { ModalForm } from '../../../components/modal/ModalForm';
import { ConfirmDialog } from '../../../components/modal/ConfirmDialog';
import { PermissionGate } from '../../../components/permissions/PermissionGate';
import { salonServicesApi } from '../../services/services/services';
import type {
  SalonServiceData,
  SalonServiceFilter,
  ServiceProductUsageResponse,
} from '../../services/services/services';
import { productsApi } from '../products/services/products';
import type { ProductData } from '../products/services/products';
import { salonServiceFormSchema } from './adminService.schema';
import type { SalonServiceFormValues } from './adminService.schema';
import { useAlert } from '../../../hooks/useAlert';
import { getApiErrorMessage } from '../../../utils/apiError';

const inputCls = 'input-premium';
const labelCls = 'label-premium';

interface UsageRow {
  productId: string;
  quantityUsed: string;
}

export const AdminServices = () => {
  const [refreshTrigger, setRefreshTrigger] = useState(0);

  const [showForm, setShowForm] = useState(false);
  const [editingService, setEditingService] = useState<SalonServiceData | null>(null);

  const [showConfirm, setShowConfirm] = useState(false);
  const [serviceTargetId, setServiceTargetId] = useState<number | null>(null);
  const [confirmAction, setConfirmAction] = useState<'delete' | 'reactivate'>('delete');

  const [products, setProducts] = useState<ProductData[]>([]);
  const [usageRows, setUsageRows] = useState<UsageRow[]>([]);

  useEffect(() => {
    productsApi
      .findAll({ active: true }, 0, 1000)
      .then((page) => setProducts(page.content.filter((p) => p.usedInServiceRecipe !== false)))
      .catch(() => setProducts([]));
  }, []);

  const {
    register,
    handleSubmit,
    reset,
    setValue,
    formState: { errors },
  } = useForm<SalonServiceFormValues>({ resolver: zodResolver(salonServiceFormSchema) });
  const { error: showError } = useAlert();

  const fetchServicesData = async (filter: SalonServiceFilter, page: number, size: number) => {
    return salonServicesApi.findAll(filter, page, size);
  };

  const handleOpenForm = (service?: SalonServiceData) => {
    reset();
    if (service) {
      setEditingService(service);
      setValue('name', service.name);
      setValue('description', service.description);
      setValue('price', service.price ?? undefined);
      setValue('active', service.active);
      setUsageRows(
        ((service.productUsages as ServiceProductUsageResponse[]) ?? []).map((u) => ({
          productId: String(u.productId),
          quantityUsed: String(u.quantityUsed),
        }))
      );
    } else {
      setEditingService(null);
      setValue('active', true);
      setUsageRows([]);
    }
    setShowForm(true);
  };

  const addUsageRow = () => {
    setUsageRows((prev) => [...prev, { productId: '', quantityUsed: '' }]);
  };

  const removeUsageRow = (index: number) => {
    setUsageRows((prev) => prev.filter((_, i) => i !== index));
  };

  const updateUsageRow = (index: number, patch: Partial<UsageRow>) => {
    setUsageRows((prev) => prev.map((row, i) => (i === index ? { ...row, ...patch } : row)));
  };

  const onSubmit = async (data: SalonServiceFormValues) => {
    if (usageRows.some((r) => !r.productId || !r.quantityUsed)) {
      await showError('Selecione o produto e a quantidade em todas as linhas da receita.');
      return;
    }
    try {
      const payload: SalonServiceData = {
        ...data,
        price: data.price ?? null,
        productUsages: usageRows.map((r) => ({
          productId: Number(r.productId),
          quantityUsed: Number(r.quantityUsed),
        })),
      };
      if (editingService?.id) {
        await salonServicesApi.update(editingService.id, payload);
      } else {
        await salonServicesApi.create(payload);
      }
      setShowForm(false);
      setRefreshTrigger((prev) => prev + 1);
    } catch (err) {
      const msg = getApiErrorMessage(
        err,
        'Erro ao salvar serviço. Verifique os dados e tente novamente.'
      );
      await showError(msg);
    }
  };

  const handleConfirmAction = async () => {
    if (!serviceTargetId) return;
    try {
      if (confirmAction === 'delete') {
        await salonServicesApi.delete(serviceTargetId);
      } else {
        await salonServicesApi.reactivate(serviceTargetId);
      }
      setShowConfirm(false);
      setRefreshTrigger((prev) => prev + 1);
    } catch (err) {
      const fallbackMsg =
        confirmAction === 'delete' ? 'Erro ao excluir serviço.' : 'Erro ao reativar serviço.';
      const msg = getApiErrorMessage(err, fallbackMsg);
      await showError(msg);
    }
  };


  const columns = [
    { key: 'name', label: 'Nome' },
    {
      key: 'price',
      label: 'Referência',
      render: (item: SalonServiceData) =>
        item.price != null ? `A partir de R$ ${item.price.toFixed(2)}` : '—',
    },
    {
      key: 'estimatedProductCost',
      label: 'Custo estimado',
      render: (item: SalonServiceData) =>
        item.estimatedProductCost != null && item.estimatedProductCost > 0
          ? `R$ ${item.estimatedProductCost.toFixed(2)}`
          : '—',
    },
    {
      key: 'active',
      label: 'Status',
      render: (item: SalonServiceData) => (item.active ? 'Ativo' : 'Inativo'),
    },
    {
      key: 'actions',
      label: 'Ações',
      render: (item: SalonServiceData) => (
        <div className="flex gap-2">
          {item.active ? (
            <>
              <PermissionGate method="PUT" endpoint={`/v1/services/${item.id}`}>
                <button
                  onClick={() => handleOpenForm(item)}
                  className="p-1.5 text-indigo-600 hover:bg-indigo-50 border border-indigo-200 rounded-lg transition-all cursor-pointer"
                  title="Editar Serviço"
                >
                  <Edit size={15} />
                </button>
              </PermissionGate>
              <PermissionGate method="DELETE" endpoint={`/v1/services/${item.id}`}>
                <button
                  onClick={() => {
                    setServiceTargetId(item.id!);
                    setConfirmAction('delete');
                    setShowConfirm(true);
                  }}
                  className="p-1.5 text-rose-600 hover:bg-rose-50 border border-rose-200 rounded-lg transition-all cursor-pointer"
                  title="Excluir Serviço"
                >
                  <Trash2 size={15} />
                </button>
              </PermissionGate>
            </>
          ) : (
            <PermissionGate method="PATCH" endpoint={`/v1/services/${item.id}/reactivate`}>
              <button
                onClick={() => {
                  setServiceTargetId(item.id!);
                  setConfirmAction('reactivate');
                  setShowConfirm(true);
                }}
                className="p-1.5 text-emerald-600 hover:bg-emerald-50 border border-emerald-200 rounded-lg transition-all cursor-pointer flex items-center gap-1 text-xs font-semibold"
                title="Reativar Serviço"
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

  const initialFilters: SalonServiceFilter = {
    name: '',
    active: undefined,
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4">
        <h2 className="font-heading text-2xl font-bold text-[#3b3036]">Gerenciar Serviços</h2>
        <PermissionGate method="POST" endpoint="/v1/services">
          <button onClick={() => handleOpenForm()} className="btn-premium">
            <Plus size={18} /> Novo Serviço
          </button>
        </PermissionGate>
      </div>

      <DataTable
        columns={columns}
        fetchData={fetchServicesData}
        filtersConfig={filtersConfig}
        keyExtractor={(item) => item.id!}
        refreshTrigger={refreshTrigger}
        initialFilters={initialFilters}
      />

      <ModalForm
        show={showForm}
        onHide={() => setShowForm(false)}
        title={editingService ? 'Editar Serviço' : 'Novo Serviço'}
        onSubmit={handleSubmit(onSubmit)}
      >
        <div className="space-y-4">
          <div>
            <label className={labelCls}>Nome do Serviço *</label>
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
          <div>
            <label className={labelCls}>Descrição</label>
            <textarea rows={3} maxLength={2000} className={`${inputCls} resize-none`} {...register('description')} />
          </div>
          <div>
            <label className={labelCls}>Valor de referência — "a partir de" (opcional)</label>
            <input
              type="number"
              step="0.01"
              min="0"
              placeholder="Deixe em branco se o valor for combinado"
              className={inputCls}
              {...register('price', {
                setValueAs: (v) =>
                  v === '' || v === undefined || v === null ? undefined : Number(v),
              })}
            />
            <p className="text-xs text-gray-400 mt-1">
              O preço final pode ser registrado no fluxo de caixa ao concluir o atendimento.
            </p>
          </div>
          <div className="border-t border-[#eae1e1]/50 pt-4">
            <div className="flex items-center justify-between mb-2">
              <h4 className="font-heading font-semibold text-sm text-[#3b3036]">
                Receita (opcional)
              </h4>
              <button
                type="button"
                onClick={addUsageRow}
                className="inline-flex items-center gap-1 text-xs font-semibold text-[#be8a83] hover:text-[#a6726b] cursor-pointer"
              >
                <Plus size={13} /> Adicionar produto
              </button>
            </div>
            <p className="text-xs text-gray-400 -mt-1 mb-3">
              Quanto deste produto o serviço consome por execução — usado só pra calcular custo
              interno nos relatórios.
            </p>
            <div className="space-y-3">
              {usageRows.map((row, index) => {
                const product = products.find((p) => String(p.id) === row.productId);
                return (
                  <div key={index} className="flex flex-col gap-2 p-2.5 bg-[#fcf9f9]/50 border border-[#eae1e1]/60 rounded-lg">
                    <select
                      className={`${inputCls} block w-full`}
                      value={row.productId}
                      onChange={(e) => updateUsageRow(index, { productId: e.target.value })}
                    >
                      <option value="">Selecione o produto...</option>
                      {products.map((p) => (
                        <option key={p.id} value={p.id}>
                          {p.name}
                        </option>
                      ))}
                    </select>
                    <div className="flex items-center gap-2">
                      <input
                        type="number"
                        step="0.01"
                        min="0"
                        className={`${inputCls} flex-1`}
                        placeholder={product?.unit ? `Qtd. (${product.unit.toLowerCase()})` : 'Qtd.'}
                        value={row.quantityUsed}
                        onChange={(e) => updateUsageRow(index, { quantityUsed: e.target.value })}
                      />
                      <button
                        type="button"
                        onClick={() => removeUsageRow(index)}
                        className="p-1.5 text-rose-500 hover:bg-rose-50 rounded-lg transition-all cursor-pointer shrink-0"
                      >
                        <Trash2 size={14} />
                      </button>
                    </div>
                  </div>
                );
              })}
            </div>
          </div>
        </div>
      </ModalForm>

      <ConfirmDialog
        show={showConfirm}
        onHide={() => setShowConfirm(false)}
        onConfirm={handleConfirmAction}
        title={confirmAction === 'delete' ? 'Excluir Serviço' : 'Reativar Serviço'}
        message={
          confirmAction === 'delete'
            ? 'Tem certeza que deseja excluir este serviço? Esta ação não pode ser desfeita.'
            : 'Tem certeza que deseja reativar este serviço? Ele aparecerá novamente nas listagens públicas.'
        }
        confirmLabel={confirmAction === 'delete' ? 'Excluir' : 'Reativar'}
        variant={confirmAction === 'delete' ? 'danger' : 'primary'}
      />
    </div>
  );
};
