import { useEffect, useState } from 'react';
import { Table } from '../../../components/table/Table';
import { reportsApi } from './services/reports';
import type { AppointmentFinancialResponse, EmployeeFinanceResponse } from './services/reports';
import { useAlert } from '../../../hooks/useAlert';
import { getApiErrorMessage } from '../../../utils/apiError';
import { PERIOD_LABELS } from '../../../utils/appointmentPeriod';

const inputCls = 'input-premium';
const labelCls = 'label-premium';

const STATUS_LABELS: Record<string, string> = {
  PENDING: 'Pendente',
  REQUESTED: 'Solicitado',
  CONFIRMED: 'Confirmado',
  DECLINED: 'Recusado',
  DONE: 'Concluído',
  CANCELLED: 'Cancelado',
};

const PAYMENT_STATUS_LABELS: Record<string, string> = {
  PENDING: 'Pendente',
  PAID: 'Pago',
  FAILED: 'Falhou',
};

function formatDate(item: AppointmentFinancialResponse): string {
  if (item.scheduledAt) return new Date(item.scheduledAt).toLocaleDateString('pt-BR');
  if (item.scheduledDate) {
    const period = item.scheduledPeriod ? ` — ${PERIOD_LABELS[item.scheduledPeriod]}` : '';
    return `${new Date(item.scheduledDate + 'T12:00:00').toLocaleDateString('pt-BR')}${period}`;
  }
  if (item.preferredDate) return new Date(item.preferredDate + 'T12:00:00').toLocaleDateString('pt-BR');
  return '—';
}

function formatBRL(value: number | null): string {
  if (value == null) return '—';
  return new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(value);
}

interface Props {
  employees: EmployeeFinanceResponse[];
  dateFrom?: string;
  dateTo?: string;
}

export const EmployeeFinancialHistorySection = ({ employees, dateFrom, dateTo }: Props) => {
  const [selectedEmployeeId, setSelectedEmployeeId] = useState<number | ''>('');
  const [history, setHistory] = useState<AppointmentFinancialResponse[]>([]);
  const [currentPage, setCurrentPage] = useState(1);
  const [totalPages, setTotalPages] = useState(1);
  const [isLoading, setIsLoading] = useState(false);
  const { error: showError } = useAlert();

  useEffect(() => {
    setCurrentPage(1);
  }, [selectedEmployeeId, dateFrom, dateTo]);

  useEffect(() => {
    if (!selectedEmployeeId) {
      setHistory([]);
      setTotalPages(1);
      return;
    }
    const loadHistory = async () => {
      setIsLoading(true);
      try {
        const response = await reportsApi.getEmployeeFinancialHistory(
          Number(selectedEmployeeId),
          dateFrom || undefined,
          dateTo || undefined,
          currentPage - 1,
          10
        );
        setHistory(response.content);
        setTotalPages(response.totalPages || 1);
      } catch (err) {
        const msg = getApiErrorMessage(err, 'Erro ao carregar histórico financeiro do profissional.');
        await showError(msg);
      } finally {
        setIsLoading(false);
      }
    };
    loadHistory();
  }, [selectedEmployeeId, dateFrom, dateTo, currentPage]);

  const columns = [
    { key: 'date', label: 'Data', render: formatDate },
    { key: 'serviceName', label: 'Serviço' },
    { key: 'price', label: 'Preço', render: (item: AppointmentFinancialResponse) => formatBRL(item.price) },
    {
      key: 'status',
      label: 'Status',
      render: (item: AppointmentFinancialResponse) => STATUS_LABELS[item.status] ?? item.status,
    },
    {
      key: 'paymentStatus',
      label: 'Pagamento',
      render: (item: AppointmentFinancialResponse) =>
        item.paymentStatus ? (PAYMENT_STATUS_LABELS[item.paymentStatus] ?? item.paymentStatus) : '—',
    },
  ];

  return (
    <div className="space-y-4">
      <div className="flex flex-col gap-2">
        <h4 className="font-heading font-bold text-lg text-[#3b3036]">
          Histórico Financeiro por Profissional
        </h4>
        <p className="text-xs text-[#7a7074]">
          Selecione um(a) profissional para ver o histórico individual de agendamentos (preço, data,
          status e pagamento).
        </p>
      </div>

      <div className="max-w-xs space-y-1">
        <label className={labelCls}>Profissional</label>
        <select
          value={selectedEmployeeId}
          onChange={(e) => setSelectedEmployeeId(e.target.value ? Number(e.target.value) : '')}
          className={inputCls}
        >
          <option value="">Selecione um(a) profissional...</option>
          {employees.map((emp) => (
            <option key={emp.employeeId} value={emp.employeeId}>
              {emp.employeeName}
            </option>
          ))}
        </select>
      </div>

      {selectedEmployeeId === '' ? (
        <div className="w-full bg-white/80 backdrop-blur-md rounded-2xl p-10 border border-[#eae1e1]/80 shadow-sm text-center text-sm text-[#7a7074]">
          Selecione um(a) profissional acima para ver o histórico.
        </div>
      ) : isLoading ? (
        <div className="flex items-center gap-2 text-sm text-[#3b3036]/60 py-8">
          <div className="animate-spin rounded-full h-5 w-5 border-t-2 border-b-2 border-[#be8a83]"></div>
          Carregando histórico...
        </div>
      ) : (
        <Table
          columns={columns}
          data={history}
          keyExtractor={(item) => item.id}
          currentPage={currentPage}
          totalPages={totalPages}
          onPageChange={setCurrentPage}
          emptyMessage="Nenhum agendamento encontrado para este profissional no período selecionado."
        />
      )}
    </div>
  );
};
