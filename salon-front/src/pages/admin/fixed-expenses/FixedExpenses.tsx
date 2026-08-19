import { useEffect, useState } from 'react';
import { Plus, Trash2 } from 'lucide-react';
import { Table } from '../../../components/table/Table';
import { ConfirmDialog } from '../../../components/modal/ConfirmDialog';
import { PermissionGate } from '../../../components/permissions/PermissionGate';
import { fixedExpensesApi } from './services/fixedExpenses';
import type { FixedExpenseData } from './services/fixedExpenses';
import { useAlert } from '../../../hooks/useAlert';
import { getApiErrorMessage } from '../../../utils/apiError';

const inputCls = 'input-premium';
const labelCls = 'label-premium';

function getLocalDateString(): string {
  const d = new Date();
  const year = d.getFullYear();
  const month = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

export const FixedExpenses = () => {
  const [expenses, setExpenses] = useState<FixedExpenseData[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [currentPage, setCurrentPage] = useState(1);
  const [totalPages, setTotalPages] = useState(1);

  const [dateFrom, setDateFrom] = useState('');
  const [dateTo, setDateTo] = useState('');

  const [description, setDescription] = useState('');
  const [amount, setAmount] = useState('');
  const [date, setDate] = useState(getLocalDateString());
  const [isSaving, setIsSaving] = useState(false);

  const [deleteTargetId, setDeleteTargetId] = useState<number | null>(null);

  const { error: showError, success: showSuccess } = useAlert();

  const load = async () => {
    setIsLoading(true);
    try {
      const response = await fixedExpensesApi.findByPeriod(
        dateFrom || undefined,
        dateTo || undefined,
        currentPage - 1,
        20
      );
      setExpenses(response.content);
      setTotalPages(response.totalPages || 1);
    } catch (err) {
      await showError(getApiErrorMessage(err, 'Erro ao carregar gastos fixos'));
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [dateFrom, dateTo, currentPage]);

  const totalInPage = expenses.reduce((sum, e) => sum + e.amount, 0);

  const handleCreate = async () => {
    const amountNum = Number(amount);
    if (!description.trim() || !amount || Number.isNaN(amountNum) || amountNum < 0 || !date) {
      await showError('Preencha a descrição, um valor válido e a data.');
      return;
    }
    setIsSaving(true);
    try {
      await fixedExpensesApi.create({ description: description.trim(), amount: amountNum, date });
      setDescription('');
      setAmount('');
      setDate(getLocalDateString());
      await showSuccess('Gasto fixo adicionado');
      setCurrentPage(1);
      load();
    } catch (err) {
      await showError(getApiErrorMessage(err, 'Erro ao adicionar gasto fixo'));
    } finally {
      setIsSaving(false);
    }
  };

  const handleDelete = async () => {
    if (deleteTargetId == null) return;
    try {
      await fixedExpensesApi.delete(deleteTargetId);
      await showSuccess('Gasto fixo apagado');
      load();
    } catch (err) {
      await showError(getApiErrorMessage(err, 'Erro ao apagar gasto fixo'));
    } finally {
      setDeleteTargetId(null);
    }
  };

  const columns = [
    {
      key: 'date',
      label: 'Data',
      render: (item: FixedExpenseData) => new Date(item.date + 'T00:00:00').toLocaleDateString('pt-BR'),
    },
    { key: 'description', label: 'Descrição' },
    {
      key: 'amount',
      label: 'Valor',
      render: (item: FixedExpenseData) => `R$ ${item.amount.toFixed(2)}`,
    },
    {
      key: 'actions',
      label: 'Ações',
      render: (item: FixedExpenseData) => (
        <PermissionGate method="DELETE" endpoint={`/v1/fixed-expenses/${item.id}`}>
          <button
            onClick={() => setDeleteTargetId(item.id!)}
            title="Apagar"
            className="p-1.5 text-rose-600 hover:bg-rose-50 border border-rose-200 rounded-lg transition-all cursor-pointer"
          >
            <Trash2 size={15} />
          </button>
        </PermissionGate>
      ),
    },
  ];

  return (
    <div className="space-y-6">
      <div>
        <h2 className="font-heading text-2xl font-bold text-[#3b3036]">Gastos Fixos</h2>
        <p className="text-xs text-[#3b3036]/60 mt-1">
          Aluguel, água, luz, salários e qualquer outro custo fixo do salão — separado das vendas
          e recebimentos do Fluxo de Caixa. Vira base dos relatórios de saúde financeira.
        </p>
      </div>

      <PermissionGate method="POST" endpoint="/v1/fixed-expenses">
        <div className="p-4 bg-white border border-[#eae1e1] rounded-2xl grid grid-cols-1 md:grid-cols-4 gap-3 items-end">
          <div className="md:col-span-2">
            <label className={labelCls}>Descrição</label>
            <input
              type="text"
              maxLength={200}
              className={inputCls}
              placeholder="Ex.: Aluguel de agosto"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
            />
          </div>
          <div>
            <label className={labelCls}>Valor (R$)</label>
            <input
              type="number"
              step="0.01"
              min="0"
              className={inputCls}
              value={amount}
              onChange={(e) => setAmount(e.target.value)}
            />
          </div>
          <div>
            <label className={labelCls}>Data</label>
            <input
              type="date"
              className={inputCls}
              value={date}
              onChange={(e) => setDate(e.target.value)}
            />
          </div>
          <div className="md:col-span-4 flex justify-end">
            <button
              onClick={handleCreate}
              disabled={isSaving}
              className="btn-premium disabled:opacity-50"
            >
              <Plus size={18} /> Adicionar gasto
            </button>
          </div>
        </div>
      </PermissionGate>

      <div className="flex flex-wrap gap-3 items-end">
        <div>
          <label className={labelCls}>De</label>
          <input
            type="date"
            className={inputCls}
            value={dateFrom}
            onChange={(e) => {
              setDateFrom(e.target.value);
              setCurrentPage(1);
            }}
          />
        </div>
        <div>
          <label className={labelCls}>Até</label>
          <input
            type="date"
            className={inputCls}
            value={dateTo}
            onChange={(e) => {
              setDateTo(e.target.value);
              setCurrentPage(1);
            }}
          />
        </div>
        <div className="ml-auto text-sm text-[#3b3036]">
          <span className="text-xs text-gray-400 block">Total nesta página</span>
          <span className="font-bold">R$ {totalInPage.toFixed(2)}</span>
        </div>
      </div>

      {isLoading ? (
        <div className="flex justify-center py-10">
          <div className="animate-spin rounded-full h-8 w-8 border-t-2 border-b-2 border-[#be8a83]"></div>
        </div>
      ) : (
        <Table
          columns={columns}
          data={expenses}
          keyExtractor={(item) => item.id!}
          currentPage={currentPage}
          totalPages={totalPages}
          onPageChange={setCurrentPage}
        />
      )}

      <ConfirmDialog
        show={deleteTargetId != null}
        onHide={() => setDeleteTargetId(null)}
        onConfirm={handleDelete}
        title="Apagar Gasto Fixo"
        message="Tem certeza que deseja apagar este gasto? Essa ação não pode ser desfeita."
        confirmLabel="Apagar"
        variant="danger"
      />
    </div>
  );
};
