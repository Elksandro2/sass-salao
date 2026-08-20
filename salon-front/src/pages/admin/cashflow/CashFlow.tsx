import { useState, useEffect } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { Plus, Trash2 } from 'lucide-react';
import { Table } from '../../../components/table/Table';
import { ModalForm } from '../../../components/modal/ModalForm';
import { ConfirmDialog } from '../../../components/modal/ConfirmDialog';
import { PermissionGate } from '../../../components/permissions/PermissionGate';
import { cashFlowApi } from './services/cashflow';
import type { CashFlowData } from './services/cashflow';
import { cashFlowFormSchema } from './cashflow.schema';
import type { CashFlowFormValues } from './cashflow.schema';
import { productsApi } from '../products/services/products';
import type { ProductData } from '../products/services/products';
import { salonServicesApi } from '../../services/services/services';
import type { SalonServiceData } from '../../services/services/services';
import { employeesApi } from '../employees/services/employees';
import type { EmployeeData } from '../employees/services/employees';
import { getApiErrorMessage } from '../../../utils/apiError';
import { useAlert } from '../../../hooks/useAlert';

const inputCls = 'input-premium';
const labelCls = 'label-premium';

export const CashFlow = () => {
  const [cashFlows, setCashFlows] = useState<CashFlowData[]>([]);
  const [isLoading, setIsLoading] = useState(true);

  const [showForm, setShowForm] = useState(false);
  const [showConfirm, setShowConfirm] = useState(false);
  const [itemToDelete, setItemToDelete] = useState<number | null>(null);

  const [dateFrom, setDateFrom] = useState('');
  const [dateTo, setDateTo] = useState('');
  const [currentPage, setCurrentPage] = useState(1);
  const [totalPages, setTotalPages] = useState(1);

  const {
    register,
    handleSubmit,
    reset,
    setValue,
    watch,
    formState: { errors },
  } = useForm<CashFlowFormValues>({ resolver: zodResolver(cashFlowFormSchema) });
  const { error: showError } = useAlert();

  // New States for Products/Services Sales
  const [products, setProducts] = useState<ProductData[]>([]);
  const [services, setServices] = useState<SalonServiceData[]>([]);
  const [sourceType, setSourceType] = useState<'OTHER' | 'PRODUCT' | 'SERVICE'>('OTHER');
  const [cart, setCart] = useState<{ product: ProductData; quantity: number }[]>([]);
  const [productSearch, setProductSearch] = useState('');
  const [showProdDropdown, setShowProdDropdown] = useState(false);

  const [serviceSearch, setServiceSearch] = useState('');
  const [showSvcDropdown, setShowSvcDropdown] = useState(false);

  const [employees, setEmployees] = useState<EmployeeData[]>([]);
  const [sellerEmployeeId, setSellerEmployeeId] = useState('');

  const loadCashFlows = async () => {
    setIsLoading(true);
    try {
      const response = await cashFlowApi.findByPeriod(
        dateFrom || undefined,
        dateTo || undefined,
        currentPage - 1,
        20
      );
      setCashFlows(response.content);
      setTotalPages(response.totalPages || 1);
    } catch (err) {
      const msg = getApiErrorMessage(err, 'Erro ao carregar fluxo de caixa');
      await showError(msg);
    } finally {
      setIsLoading(false);
    }
  };

  const loadSuggestions = async () => {
    try {
      const [prodsData, svcsData, empsData] = await Promise.all([
        productsApi.findAll({ active: true }, 0, 1000),
        salonServicesApi.findAll({ active: true }, 0, 1000),
        employeesApi.findAll({ active: true }, 0, 1000),
      ]);
      setProducts(prodsData.content);
      setServices(svcsData.content);
      setEmployees(empsData.content);
    } catch (err) {
      console.error('Erro ao carregar produtos/serviços/funcionárias para sugestão', err);
    }
  };

  useEffect(() => {
    loadCashFlows();
  }, [dateFrom, dateTo, currentPage]);
  useEffect(() => {
    loadSuggestions();
  }, []);

  const watchedType = watch('type');
  useEffect(() => {
    if (watchedType === 'EXPENSE') {
      setSourceType('OTHER');
    }
  }, [watchedType]);

  const getLocalDateString = () => {
    const d = new Date();
    const year = d.getFullYear();
    const month = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
  };

  const handleOpenForm = () => {
    reset({ type: 'INCOME', date: getLocalDateString(), amount: '0', description: '' });
    setSourceType('OTHER');
    setCart([]);
    setProductSearch('');
    setServiceSearch('');
    setSellerEmployeeId('');
    loadSuggestions(); // Refresh product/service suggestions from backend
    setShowForm(true);
  };

  // Cart Helper Operations
  const addToCart = (product: ProductData) => {
    if (!product.id) return;
    setCart((prev) => {
      const existing = prev.find((item) => item.product.id === product.id);
      if (existing) {
        return prev.map((item) =>
          item.product.id === product.id ? { ...item, quantity: item.quantity + 1 } : item
        );
      }
      return [...prev, { product, quantity: 1 }];
    });
    setProductSearch('');
    setShowProdDropdown(false);
  };

  const removeFromCart = (productId: number) => {
    setCart((prev) => prev.filter((item) => item.product.id !== productId));
  };

  const updateCartQty = (productId: number, qty: number) => {
    setCart((prev) =>
      prev.map((item) => {
        if (item.product.id === productId) {
          const newQty = Math.max(1, qty);
          return { ...item, quantity: newQty };
        }
        return item;
      })
    );
  };

  const handleSelectService = (service: SalonServiceData) => {
    setValue('amount', String(service.price || 0));
    setValue('description', `Serviço: ${service.name}`);
    setServiceSearch(service.name);
    setShowSvcDropdown(false);
  };

  const cartTotal = cart.reduce((sum, item) => sum + item.product.price * item.quantity, 0);

  // Sync React Hook Form values reactively when product cart updates
  useEffect(() => {
    if (sourceType === 'PRODUCT') {
      setValue('amount', String(cartTotal));
      const summary = cart.map((item) => `${item.quantity}x ${item.product.name}`).join(', ');
      setValue('description', cart.length > 0 ? `Venda de Produtos: ${summary}` : '');
    }
  }, [cart, cartTotal, sourceType, setValue]);

  const onSubmit = async (data: CashFlowFormValues) => {
    try {
      let payload: CashFlowData = {
        type: data.type,
        amount: Number(data.amount),
        description: data.description,
        date: data.date,
      };

      if (data.type === 'INCOME' && sourceType === 'PRODUCT') {
        if (cart.length === 0) {
          await showError('Por favor, adicione pelo menos um produto ao carrinho.');
          return;
        }
        payload = {
          type: 'INCOME',
          amount: cartTotal,
          description:
            data.description ||
            `Venda de Produtos: ${cart.map((i) => `${i.quantity}x ${i.product.name}`).join(', ')}`,
          date: data.date,
          items: cart.map((item) => ({
            productId: item.product.id!,
            quantity: item.quantity,
          })),
          employeeId: sellerEmployeeId ? Number(sellerEmployeeId) : undefined,
        };
      }

      await cashFlowApi.create(payload);
      setShowForm(false);
      loadCashFlows();
    } catch (err) {
      const msg = getApiErrorMessage(err, 'Erro ao salvar registro no fluxo de caixa.');
      await showError(msg);
    }
  };

  const confirmDelete = async () => {
    if (!itemToDelete) return;
    try {
      await cashFlowApi.delete(itemToDelete);
      setShowConfirm(false);
      loadCashFlows();
    } catch (err) {
      const msg = getApiErrorMessage(err, 'Erro ao excluir registro.');
      await showError(msg);
    }
  };

  // Autocomplete suggestions lists
  const filteredProducts = products.filter((p) => {
    if (!productSearch) return false;
    const matchesName = p.name.toLowerCase().includes(productSearch.toLowerCase());
    return matchesName && p.active && p.availableForSale !== false;
  });

  const filteredServices = services.filter((s) => {
    if (!serviceSearch) return false;
    const matchesName = s.name.toLowerCase().includes(serviceSearch.toLowerCase());
    return matchesName && s.active;
  });

  const columns = [
    {
      key: 'date',
      label: 'Data',
      render: (item: CashFlowData) => new Date(item.date).toLocaleDateString('pt-BR'),
    },
    {
      key: 'description',
      label: 'Descrição',
      render: (item: CashFlowData) => (
        <div>
          <span>{item.description}</span>
          {item.employeeName && (
            <div className="text-xs text-[#7a7074] mt-0.5">
              Vendido por <span className="font-semibold">{item.employeeName}</span>
              {item.commissionAmount != null && (
                <>
                  {' '}
                  · comissão{' '}
                  <span className="font-semibold text-[#be8a83]">
                    R$ {item.commissionAmount.toFixed(2)}
                  </span>
                </>
              )}
            </div>
          )}
        </div>
      ),
    },
    {
      key: 'type',
      label: 'Tipo',
      render: (item: CashFlowData) => (
        <span
          className={`inline-flex items-center px-2.5 py-1 rounded-full text-xs font-semibold ${
            item.type === 'INCOME'
              ? 'bg-emerald-50 text-emerald-700 border border-emerald-200'
              : 'bg-rose-50 text-rose-700 border border-rose-200'
          }`}
        >
          {item.type === 'INCOME' ? 'Entrada' : 'Saída'}
        </span>
      ),
    },
    {
      key: 'amount',
      label: 'Valor',
      render: (item: CashFlowData) => (
        <span
          className={`font-semibold ${item.type === 'INCOME' ? 'text-emerald-600' : 'text-rose-600'}`}
        >
          {item.type === 'INCOME' ? '+' : '-'} R$ {item.amount.toFixed(2)}
        </span>
      ),
    },
    {
      key: 'actions',
      label: 'Ações',
      render: (item: CashFlowData) => (
        <PermissionGate method="DELETE" endpoint={`/v1/cashflow/${item.id}`}>
          <button
            onClick={() => {
              setItemToDelete(item.id!);
              setShowConfirm(true);
            }}
            className="p-2 text-rose-600 hover:bg-rose-50 hover:text-rose-700 border border-[#eae1e1] hover:border-rose-200 rounded-xl transition-all cursor-pointer"
            title="Excluir Registro"
          >
            <Trash2 size={15} />
          </button>
        </PermissionGate>
      ),
    },
  ];

  return (
    <>
      <div className="space-y-6 animate-fade-in-up">
        <div className="flex justify-between items-center">
          <h2 className="font-heading text-2xl font-bold text-[#3b3036]">Fluxo de Caixa</h2>
          <PermissionGate method="POST" endpoint="/v1/cashflow">
            <button
              onClick={handleOpenForm}
              className="btn-premium font-semibold shadow-md shadow-[#be8a83]/10"
            >
              <Plus size={18} /> Novo Registro
            </button>
          </PermissionGate>
        </div>

        <div className="flex flex-wrap gap-4 items-end bg-white/80 backdrop-blur-md rounded-2xl border border-[#eae1e1]/80 p-5 shadow-sm">
          <div className="space-y-1">
            <label className={labelCls}>De</label>
            <input
              type="date"
              value={dateFrom}
              max={dateTo || undefined}
              onChange={(e) => {
                setDateFrom(e.target.value);
                setCurrentPage(1);
              }}
              className={inputCls}
            />
          </div>
          <div className="space-y-1">
            <label className={labelCls}>Até</label>
            <input
              type="date"
              value={dateTo}
              min={dateFrom || undefined}
              onChange={(e) => {
                setDateTo(e.target.value);
                setCurrentPage(1);
              }}
              className={inputCls}
            />
          </div>
          <button
            onClick={() => {
              setDateFrom('');
              setDateTo('');
              setCurrentPage(1);
            }}
            className="px-5 py-2.5 border border-[#eae1e1] text-sm font-semibold text-[#3b3036] hover:text-[#be8a83] hover:border-[#be8a83] bg-white rounded-xl transition-all duration-200 cursor-pointer"
          >
            Limpar Filtros
          </button>
        </div>

        {isLoading ? (
          <div className="flex items-center gap-3 text-sm text-[#3b3036]/60 py-10 justify-center">
            <div className="animate-spin rounded-full h-6 w-6 border-t-2 border-b-2 border-[#be8a83]"></div>
            <span>Carregando registros...</span>
          </div>
        ) : (
          <Table
            columns={columns}
            data={cashFlows}
            keyExtractor={(item) => item.id!}
            currentPage={currentPage}
            totalPages={totalPages}
            onPageChange={setCurrentPage}
          />
        )}
      </div>

      <ModalForm
        show={showForm}
        onHide={() => setShowForm(false)}
        title="Novo Registro"
        onSubmit={handleSubmit(onSubmit)}
      >
        <div className="space-y-4">
          <div>
            <label className={labelCls}>Tipo *</label>
            <select className={inputCls} {...register('type')}>
              <option value="INCOME">Entrada (Receita)</option>
              <option value="EXPENSE">Saída (Despesa)</option>
            </select>
          </div>

          {watchedType === 'INCOME' && (
            <div>
              <label className={labelCls}>Origem do Lançamento</label>
              <div className="grid grid-cols-3 gap-2 mt-1">
                {(['OTHER', 'PRODUCT', 'SERVICE'] as const).map((type) => {
                  const labels = {
                    OTHER: 'Geral / Outros',
                    PRODUCT: 'Venda de Produtos',
                    SERVICE: 'Serviço',
                  };
                  return (
                    <button
                      key={type}
                      type="button"
                      onClick={() => {
                        setSourceType(type);
                        setCart([]);
                        setProductSearch('');
                        setServiceSearch('');
                        setValue('amount', '0');
                        setValue('description', '');
                      }}
                      className={`px-4 py-2 text-xs font-semibold rounded-xl border transition-all duration-200 cursor-pointer ${
                        sourceType === type
                          ? 'bg-[#be8a83] text-white border-[#be8a83] shadow-md shadow-[#be8a83]/10'
                          : 'bg-white text-[#3b3036] border-[#eae1e1] hover:border-[#be8a83]/50'
                      }`}
                    >
                      {labels[type]}
                    </button>
                  );
                })}
              </div>
            </div>
          )}

          {sourceType === 'PRODUCT' && watchedType === 'INCOME' && (
            <div className="space-y-4 pt-2 border-t border-[#eae1e1]/50 relative">
              <div className="relative">
                <label className={labelCls}>Buscar Produto</label>
                <input
                  type="text"
                  placeholder="Digite o nome do produto..."
                  value={productSearch}
                  onChange={(e) => {
                    setProductSearch(e.target.value);
                    setShowProdDropdown(true);
                  }}
                  onFocus={() => setShowProdDropdown(true)}
                  className={inputCls}
                />
                {showProdDropdown && productSearch && (
                  <div className="absolute top-full left-0 right-0 mt-1 max-h-60 overflow-y-auto bg-white/95 backdrop-blur-md border border-[#eae1e1] shadow-xl rounded-xl z-50 animate-scale-up">
                    {filteredProducts.length > 0 ? (
                      filteredProducts.map((p) => (
                        <button
                          key={p.id}
                          type="button"
                          onClick={() => addToCart(p)}
                          className="w-full px-4 py-3 text-left text-sm text-[#3b3036] hover:bg-[#be8a83]/5 hover:text-[#be8a83] border-b border-[#eae1e1]/30 last:border-0 transition-colors flex justify-between items-center group cursor-pointer"
                        >
                          <span className="font-medium">{p.name}</span>
                          <span className="text-xs text-[#7a7074] group-hover:text-[#be8a83]">
                            R$ {p.price.toFixed(2)}
                          </span>
                        </button>
                      ))
                    ) : (
                      <div className="p-4 text-xs text-[#7a7074] text-center">
                        Nenhum produto ativo encontrado
                      </div>
                    )}
                  </div>
                )}
              </div>

              {/* Cart List */}
              {cart.length > 0 ? (
                <div className="space-y-3">
                  <label className={labelCls}>Produtos Selecionados</label>
                  <div className="border border-[#eae1e1]/80 rounded-xl overflow-hidden bg-[#fcf9f9]/50">
                    <div className="divide-y divide-[#eae1e1]/60">
                      {cart.map((item) => (
                        <div
                          key={item.product.id}
                          className="p-3.5 flex justify-between items-center bg-white"
                        >
                          <div className="min-w-0 flex-1 pr-4">
                            <div className="font-semibold text-sm text-[#3b3036] truncate">
                              {item.product.name}
                            </div>
                            <div className="text-xs text-[#7a7074] mt-0.5">
                              Preço Unit.: R$ {item.product.price.toFixed(2)}
                            </div>
                          </div>

                          <div className="flex items-center gap-3">
                            <div className="flex items-center gap-1.5 bg-[#fcf9f9] border border-[#eae1e1] rounded-lg p-1">
                              <button
                                type="button"
                                onClick={() => updateCartQty(item.product.id!, item.quantity - 1)}
                                className="w-6 h-6 flex items-center justify-center text-xs font-bold text-[#3b3036]/70 hover:bg-[#be8a83]/10 hover:text-[#be8a83] rounded-md transition-colors cursor-pointer"
                              >
                                -
                              </button>
                              <input
                                type="number"
                                value={item.quantity}
                                min={1}
                                onChange={(e) =>
                                  updateCartQty(item.product.id!, parseInt(e.target.value) || 1)
                                }
                                className="w-10 text-center bg-transparent border-0 text-sm font-semibold focus:ring-0 p-0 text-[#3b3036]"
                              />
                              <button
                                type="button"
                                onClick={() => updateCartQty(item.product.id!, item.quantity + 1)}
                                className="w-6 h-6 flex items-center justify-center text-xs font-bold text-[#3b3036]/70 hover:bg-[#be8a83]/10 hover:text-[#be8a83] rounded-md transition-colors cursor-pointer"
                              >
                                +
                              </button>
                            </div>

                            <div className="text-sm font-bold text-[#3b3036] min-w-[70px] text-right">
                              R$ {(item.product.price * item.quantity).toFixed(2)}
                            </div>

                            <button
                              type="button"
                              onClick={() => removeFromCart(item.product.id!)}
                              className="p-1.5 text-rose-500 hover:bg-rose-50 rounded-lg transition-colors cursor-pointer border border-transparent hover:border-rose-100"
                            >
                              <Trash2 size={15} />
                            </button>
                          </div>
                        </div>
                      ))}
                    </div>
                  </div>

                  <div className="flex justify-between items-center p-4 bg-emerald-50/50 border border-emerald-100 rounded-xl">
                    <span className="text-sm font-semibold text-emerald-800">Total da Venda</span>
                    <span className="text-base font-bold text-emerald-700">
                      R$ {cartTotal.toFixed(2)}
                    </span>
                  </div>

                  <div>
                    <label className={labelCls}>Quem vendeu (opcional)</label>
                    <select
                      className={inputCls}
                      value={sellerEmployeeId}
                      onChange={(e) => setSellerEmployeeId(e.target.value)}
                    >
                      <option value="">Não informar</option>
                      {employees.map((emp) => (
                        <option key={emp.id} value={emp.id}>
                          {emp.name}
                        </option>
                      ))}
                    </select>
                    {sellerEmployeeId &&
                      (() => {
                        const seller = employees.find((e) => String(e.id) === sellerEmployeeId);
                        const pct = seller?.productCommissionValue;
                        if (pct == null) {
                          return (
                            <p className="text-xs text-gray-400 mt-1">
                              Essa funcionária não tem comissão sobre produtos configurada.
                            </p>
                          );
                        }
                        const commission = (cartTotal * pct) / 100;
                        return (
                          <p className="text-xs text-[#be8a83] font-semibold mt-1">
                            Comissão estimada ({pct}%): R$ {commission.toFixed(2)}
                          </p>
                        );
                      })()}
                  </div>
                </div>
              ) : (
                <div className="p-6 border border-dashed border-[#eae1e1] rounded-xl text-center text-xs text-[#7a7074]">
                  Nenhum produto adicionado ao carrinho. Busque e adicione produtos acima.
                </div>
              )}
            </div>
          )}

          {sourceType === 'SERVICE' && watchedType === 'INCOME' && (
            <div className="space-y-4 pt-2 border-t border-[#eae1e1]/50 relative">
              <div className="relative">
                <label className={labelCls}>Buscar Serviço</label>
                <input
                  type="text"
                  placeholder="Digite o nome do serviço..."
                  value={serviceSearch}
                  onChange={(e) => {
                    setServiceSearch(e.target.value);
                    setShowSvcDropdown(true);
                  }}
                  onFocus={() => setShowSvcDropdown(true)}
                  className={inputCls}
                />
                {showSvcDropdown && serviceSearch && (
                  <div className="absolute top-full left-0 right-0 mt-1 max-h-60 overflow-y-auto bg-white/95 backdrop-blur-md border border-[#eae1e1] shadow-xl rounded-xl z-50 animate-scale-up">
                    {filteredServices.length > 0 ? (
                      filteredServices.map((s) => (
                        <button
                          key={s.id}
                          type="button"
                          onClick={() => handleSelectService(s)}
                          className="w-full px-4 py-3 text-left text-sm text-[#3b3036] hover:bg-[#be8a83]/5 hover:text-[#be8a83] border-b border-[#eae1e1]/30 last:border-0 transition-colors flex justify-between items-center group cursor-pointer"
                        >
                          <span className="font-medium">{s.name}</span>
                          <span className="text-xs text-[#7a7074] group-hover:text-[#be8a83]">
                            {s.price ? `R$ ${s.price.toFixed(2)}` : 'Preço sob consulta'}
                          </span>
                        </button>
                      ))
                    ) : (
                      <div className="p-4 text-xs text-[#7a7074] text-center">
                        Nenhum serviço ativo encontrado
                      </div>
                    )}
                  </div>
                )}
              </div>
            </div>
          )}

          <div>
            <label className={labelCls}>Valor (R$) *</label>
            <input
              type="number"
              step="0.01"
              disabled={sourceType === 'PRODUCT'}
              className={`${inputCls} ${errors.amount ? 'border-rose-300 focus:ring-rose-500/10 focus:border-rose-400' : ''}`}
              {...register('amount')}
            />
            {errors.amount && (
              <span className="text-xs text-rose-500 font-semibold mt-1 block">
                {errors.amount.message}
              </span>
            )}
          </div>

          <div>
            <label className={labelCls}>Descrição *</label>
            <input
              type="text"
              disabled={sourceType === 'PRODUCT'}
              maxLength={500}
              className={`${inputCls} ${errors.description ? 'border-rose-300 focus:ring-rose-500/10 focus:border-rose-400' : ''}`}
              {...register('description')}
            />
            {errors.description && (
              <span className="text-xs text-rose-500 font-semibold mt-1 block">
                {errors.description.message}
              </span>
            )}
          </div>

          <div>
            <label className={labelCls}>Data *</label>
            <input
              type="date"
              className={`${inputCls} ${errors.date ? 'border-rose-300 focus:ring-rose-500/10 focus:border-rose-400' : ''}`}
              {...register('date')}
            />
            {errors.date && (
              <span className="text-xs text-rose-500 font-semibold mt-1 block">
                {errors.date.message}
              </span>
            )}
          </div>
        </div>
      </ModalForm>

      <ConfirmDialog
        show={showConfirm}
        onHide={() => setShowConfirm(false)}
        onConfirm={confirmDelete}
        title="Excluir Registro"
        message="Tem certeza que deseja excluir este registro? Esta ação afetará os relatórios."
      />
    </>
  );
};
