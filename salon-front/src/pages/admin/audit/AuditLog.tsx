import { useState, useEffect } from 'react';
import { Eye, X, Copy, Check } from 'lucide-react';
import api from '../../../services/api';
import { useAlert } from '../../../hooks/useAlert';
import { getApiErrorMessage } from '../../../utils/apiError';
import { Table } from '../../../components/table/Table';

interface AuditLogEntry {
  id: number;
  userId: number;
  userEmail: string;
  action: string;
  entityType: string;
  entityId?: number;
  details?: string;
  status: string;
  createdAt: string;
  ipAddress?: string;
}

export const AuditLog = () => {
  const PAGE_SIZE = 15;
  const [auditLogs, setAuditLogs] = useState<AuditLogEntry[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [totalItems, setTotalItems] = useState(0);
  const [page, setPage] = useState(0);

  // User input states (unapplied)
  const [inputUserId, setInputUserId] = useState('');
  const [inputAction, setInputAction] = useState('');
  const [inputEntityType, setInputEntityType] = useState('');
  const [inputStartDate, setInputStartDate] = useState('');
  const [inputEndDate, setInputEndDate] = useState('');

  // Applied query states
  const [queryUserId, setQueryUserId] = useState('');
  const [queryAction, setQueryAction] = useState('');
  const [queryEntityType, setQueryEntityType] = useState('');
  const [queryStartDate, setQueryStartDate] = useState('');
  const [queryEndDate, setQueryEndDate] = useState('');

  const [selectedLog, setSelectedLog] = useState<AuditLogEntry | null>(null);
  const [copiedFullJson, setCopiedFullJson] = useState(false);
  const { error: showError } = useAlert();

  const loadAuditLogs = async () => {
    setIsLoading(true);
    try {
      const params = new URLSearchParams();
      params.append('page', page.toString());
      params.append('size', PAGE_SIZE.toString());

      if (queryUserId) params.append('userId', queryUserId);
      if (queryAction) params.append('action', queryAction);
      if (queryEntityType) params.append('entityType', queryEntityType);
      if (queryStartDate) params.append('startDate', queryStartDate);
      if (queryEndDate) params.append('endDate', queryEndDate);

      const { data } = await api.get(`/audit?${params.toString()}`);

      setAuditLogs(data.content);
      setTotalItems(data.page.totalElements);
    } catch (err) {
      const msg = getApiErrorMessage(err, 'Erro ao carregar logs de auditoria');
      await showError(msg);
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    loadAuditLogs();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page, queryUserId, queryAction, queryEntityType, queryStartDate, queryEndDate]);

  const handleFilter = () => {
    setQueryUserId(inputUserId);
    setQueryAction(inputAction);
    setQueryEntityType(inputEntityType);
    setQueryStartDate(inputStartDate);
    setQueryEndDate(inputEndDate);
    setPage(0);
  };

  const handleClear = () => {
    setInputUserId('');
    setInputAction('');
    setInputEntityType('');
    setInputStartDate('');
    setInputEndDate('');

    setQueryUserId('');
    setQueryAction('');
    setQueryEntityType('');
    setQueryStartDate('');
    setQueryEndDate('');
    setPage(0);
  };

  const getStatusBadge = (status: string) => {
    const isSuccess = status === 'SUCCESS';
    return (
      <span
        className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-semibold ${
          isSuccess
            ? 'bg-emerald-50 text-emerald-700 border border-emerald-200'
            : 'bg-rose-50 text-rose-700 border border-rose-200'
        }`}
      >
        <span
          className={`h-1.5 w-1.5 rounded-full ${isSuccess ? 'bg-emerald-500' : 'bg-rose-500'}`}
        />
        {isSuccess ? 'Sucesso' : 'Falha'}
      </span>
    );
  };

  const getActionBadge = (action: string) => {
    let colorClasses = 'bg-gray-100 text-gray-700 border border-gray-200';
    const cleanAction = action.split(' ')[0].toUpperCase();
    switch (cleanAction) {
      case 'CREATE':
      case 'POST':
        colorClasses = 'bg-teal-50 text-teal-700 border border-teal-200';
        break;
      case 'UPDATE':
      case 'PUT':
      case 'PATCH':
        colorClasses = 'bg-indigo-50 text-indigo-700 border border-indigo-200';
        break;
      case 'DELETE':
        colorClasses = 'bg-rose-50 text-rose-700 border border-rose-200';
        break;
      case 'LOGIN':
        colorClasses = 'bg-amber-50 text-amber-700 border border-amber-200';
        break;
      case 'LOGOUT':
        colorClasses = 'bg-purple-50 text-purple-700 border border-purple-200';
        break;
      default:
        break;
    }
    return (
      <span
        className={`inline-flex items-center px-2.5 py-1 rounded-lg text-xs font-semibold tracking-wider ${colorClasses}`}
      >
        {action}
      </span>
    );
  };

  const formatDate = (dateString: string) => {
    return new Date(dateString).toLocaleString('pt-BR');
  };

  const formatUserEmail = (email: string | null) => {
    if (!email || email === 'anonymousUser') return 'Sistema / Visitante';
    const prefix = email.split('@')[0];
    return prefix.charAt(0).toUpperCase() + prefix.slice(1);
  };

  const getPrettyDetails = (detailsStr?: string) => {
    if (!detailsStr) return '';
    try {
      const parsed = JSON.parse(detailsStr);
      return JSON.stringify(parsed, null, 2);
    } catch {
      return detailsStr;
    }
  };

  const getFullLogJson = (log: AuditLogEntry) => {
    let parsedDetails = null;
    try {
      if (log.details) {
        parsedDetails = JSON.parse(log.details);
      }
    } catch {
      parsedDetails = log.details;
    }
    return JSON.stringify({ ...log, details: parsedDetails }, null, 2);
  };

  const syntaxHighlightJson = (jsonStr: string) => {
    if (!jsonStr) return '';
    const safeStr = jsonStr
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;');

    return safeStr.replace(
      /("(\\u[a-zA-Z0-9]{4}|\\[^u]|[^\\"])*"(\s*:)?|\b(true|false|null)\b|-?\d+(?:\.\d*)?(?:[eE][+-]?\d+)?)/g,
      (match) => {
        let cls = 'text-amber-400'; // Numbers / Booleans / Null
        if (/^"/.test(match)) {
          if (/:$/.test(match)) {
            cls = 'text-pink-400 font-semibold'; // Keys
          } else {
            cls = 'text-emerald-400'; // Strings
          }
        } else if (/true|false/.test(match)) {
          cls = 'text-teal-400'; // Booleans
        } else if (/null/.test(match)) {
          cls = 'text-rose-400'; // Null
        }
        return `<span class="${cls}">${match}</span>`;
      }
    );
  };

  const copyToClipboard = async (text: string) => {
    try {
      await navigator.clipboard.writeText(text);
      setCopiedFullJson(true);
      setTimeout(() => setCopiedFullJson(false), 2000);
    } catch {
      // ignore
    }
  };

  const columns = [
    {
      key: 'createdAt',
      label: 'Data / Hora',
      render: (item: AuditLogEntry) => formatDate(item.createdAt),
    },
    {
      key: 'userEmail',
      label: 'Usuário',
      render: (item: AuditLogEntry) => (
        <span title={item.userEmail}>{formatUserEmail(item.userEmail)}</span>
      ),
    },
    {
      key: 'action',
      label: 'Ação',
      render: (item: AuditLogEntry) => getActionBadge(item.action),
    },
    {
      key: 'entityType',
      label: 'Entidade',
    },
    {
      key: 'entityId',
      label: 'ID Ref',
      render: (item: AuditLogEntry) => item.entityId || '-',
    },
    {
      key: 'ipAddress',
      label: 'Endereço IP',
      render: (item: AuditLogEntry) => item.ipAddress || '-',
    },
    {
      key: 'status',
      label: 'Status',
      render: (item: AuditLogEntry) => getStatusBadge(item.status),
    },
    {
      key: 'actions',
      label: 'Ações',
      render: (item: AuditLogEntry) => (
        <button
          onClick={() => setSelectedLog(item)}
          className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-semibold text-[#be8a83] hover:text-white bg-[#be8a83]/5 hover:bg-[#be8a83] border border-[#be8a83]/20 hover:border-transparent rounded-lg transition-all cursor-pointer"
        >
          <Eye size={14} /> Detalhes
        </button>
      ),
    },
  ];

  return (
    <div className="max-w-7xl mx-auto space-y-6">
      {/* Header */}
      <div>
        <h2 className="font-heading text-2xl font-bold text-[#3b3036] tracking-wide">
          📊 Logs de Auditoria
        </h2>
        <p className="text-sm text-[#3b3036]/60 mt-1">
          Acompanhe e audite todas as atividades e operações realizadas no sistema.
        </p>
      </div>

      {/* Filters Card */}
      <div className="bg-white rounded-2xl border border-gray-100 p-5 shadow-xs">
        <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-6 gap-4 items-end">
          <div className="space-y-1">
            <label htmlFor="userIdInput" className="label-premium">ID do Usuário</label>
            <input
              id="userIdInput"
              type="number"
              placeholder="Ex: 1"
              value={inputUserId}
              onChange={(e) => setInputUserId(e.target.value)}
              className="input-premium"
            />
          </div>

          <div className="space-y-1">
            <label htmlFor="actionSelect" className="label-premium">Ação</label>
            <select
              id="actionSelect"
              value={inputAction}
              onChange={(e) => setInputAction(e.target.value)}
              className="input-premium"
            >
              <option value="">Todas as ações</option>
              <option value="CREATE">CREATE</option>
              <option value="UPDATE">UPDATE</option>
              <option value="DELETE">DELETE</option>
              <option value="LOGIN">LOGIN</option>
              <option value="LOGOUT">LOGOUT</option>
              <option value="RESTORE">RESTORE</option>
            </select>
          </div>

          <div className="space-y-1">
            <label htmlFor="entityTypeSelect" className="label-premium">Entidade</label>
            <select
              id="entityTypeSelect"
              value={inputEntityType}
              onChange={(e) => setInputEntityType(e.target.value)}
              className="input-premium"
            >
              <option value="">Todas as entidades</option>
              <option value="User">User</option>
              <option value="Appointment">Appointment</option>
              <option value="Service">Service</option>
              <option value="Product">Product</option>
              <option value="Employee">Employee</option>
              <option value="CashFlow">CashFlow</option>
              <option value="FeatureFlag">FeatureFlag</option>
            </select>
          </div>

          <div className="space-y-1">
            <label htmlFor="startDateInput" className="label-premium">Data Inicial</label>
            <input
              id="startDateInput"
              type="date"
              value={inputStartDate}
              max={inputEndDate || undefined}
              onChange={(e) => setInputStartDate(e.target.value)}
              className="input-premium"
            />
          </div>

          <div className="space-y-1">
            <label htmlFor="endDateInput" className="label-premium">Data Final</label>
            <input
              id="endDateInput"
              type="date"
              value={inputEndDate}
              min={inputStartDate || undefined}
              onChange={(e) => setInputEndDate(e.target.value)}
              className="input-premium"
            />
          </div>

          <div className="flex gap-2 w-full">
            <button
              onClick={handleFilter}
              className="flex items-center justify-center gap-2 px-4 py-2.5 bg-[#be8a83] text-white hover:bg-[#a6726b] text-sm font-semibold rounded-xl transition-all h-[42px] w-full cursor-pointer"
            >
              Filtrar
            </button>
            <button
              onClick={handleClear}
              className="flex items-center justify-center gap-2 px-4 py-2.5 border border-gray-200 text-sm font-semibold text-[#3b3036]/80 hover:bg-gray-50 hover:text-[#3b3036] rounded-xl transition-all h-[42px] w-full cursor-pointer"
            >
              Limpar
            </button>
          </div>
        </div>
      </div>

      {/* Table Container */}
      {isLoading ? (
        <div className="flex flex-col items-center justify-center py-20 gap-3 bg-white rounded-2xl border border-gray-100 shadow-xs">
          <div className="animate-spin rounded-full h-8 w-8 border-t-2 border-b-2 border-[#be8a83]"></div>
          <span className="text-sm text-[#3b3036]/60 font-medium">
            Buscando logs de auditoria...
          </span>
        </div>
      ) : (
        <Table
          columns={columns}
          data={auditLogs}
          keyExtractor={(item) => item.id}
          currentPage={page + 1}
          totalPages={Math.ceil(totalItems / PAGE_SIZE)}
          onPageChange={(p) => setPage(p - 1)}
          emptyMessage="Nenhum registro encontrado. Tente ajustar seus filtros de busca."
        />
      )}

      {/* Details Modal */}
      {selectedLog && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/40 backdrop-blur-xs animate-fadeIn">
          <div className="bg-white rounded-2xl shadow-xl w-full max-w-3xl border border-gray-100 flex flex-col max-h-[85vh] overflow-hidden transform scale-100 transition-all">
            {/* Modal Header */}
            <div className="flex items-center justify-between px-6 py-4 border-b border-gray-100 bg-gray-50/50">
              <div>
                <h3 className="font-heading text-lg font-bold text-[#3b3036]">
                  Detalhes do Log #{selectedLog.id}
                </h3>
                <p className="text-xs text-[#3b3036]/50">
                  Gerado em {formatDate(selectedLog.createdAt)}
                </p>
              </div>
              <button
                onClick={() => setSelectedLog(null)}
                className="p-1.5 text-gray-400 hover:text-gray-600 hover:bg-gray-100 rounded-lg transition-all cursor-pointer"
              >
                <X size={20} />
              </button>
            </div>

            {/* Modal Body */}
            <div className="p-6 overflow-y-auto space-y-6 flex-1 min-h-0">
              {/* Structured Metadata Fields */}
              <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 gap-4">
                <div className="bg-gray-50/60 p-3 rounded-xl border border-gray-100">
                  <span className="block text-xs font-bold text-[#3b3036]/50 uppercase tracking-wider">
                    Ação
                  </span>
                  <span className="mt-1 block">{getActionBadge(selectedLog.action)}</span>
                </div>
                <div className="bg-gray-50/60 p-3 rounded-xl border border-gray-100">
                  <span className="block text-xs font-bold text-[#3b3036]/50 uppercase tracking-wider">
                    Entidade Afetada
                  </span>
                  <span className="mt-1.5 block text-sm font-semibold text-[#3b3036]">
                    {selectedLog.entityType}
                  </span>
                </div>
                <div className="bg-gray-50/60 p-3 rounded-xl border border-gray-100">
                  <span className="block text-xs font-bold text-[#3b3036]/50 uppercase tracking-wider">
                    ID de Referência
                  </span>
                  <span className="mt-1.5 block text-sm font-mono text-gray-600">
                    {selectedLog.entityId || 'N/A'}
                  </span>
                </div>
                <div className="bg-gray-50/60 p-3 rounded-xl border border-gray-100">
                  <span className="block text-xs font-bold text-[#3b3036]/50 uppercase tracking-wider">
                    Usuário / Autor
                  </span>
                  <span
                    className="mt-1.5 block text-sm font-semibold text-[#3b3036] truncate"
                    title={selectedLog.userEmail}
                  >
                    {selectedLog.userEmail || 'Sistema / Visitante'}
                  </span>
                </div>
                <div className="bg-gray-50/60 p-3 rounded-xl border border-gray-100">
                  <span className="block text-xs font-bold text-[#3b3036]/50 uppercase tracking-wider">
                    Endereço IP
                  </span>
                  <span className="mt-1.5 block text-sm font-mono text-gray-600">
                    {selectedLog.ipAddress || 'Desconhecido'}
                  </span>
                </div>
                <div className="bg-gray-50/60 p-3 rounded-xl border border-gray-100">
                  <span className="block text-xs font-bold text-[#3b3036]/50 uppercase tracking-wider">
                    Status Execução
                  </span>
                  <span className="mt-1 block">{getStatusBadge(selectedLog.status)}</span>
                </div>
              </div>

              {/* Pretty parsed details string with custom JSON colorizer */}
              {selectedLog.details && (
                <div className="space-y-2">
                  <span className="block text-xs font-bold text-[#3b3036]/60 uppercase tracking-wider">
                    Resumo do Payload / Detalhes
                  </span>
                  <pre className="bg-[#1e191c] p-4 rounded-xl overflow-x-auto text-xs font-mono border border-black/20 max-h-48 shadow-inner text-[#f8f8f2]">
                    <code dangerouslySetInnerHTML={{ __html: syntaxHighlightJson(getPrettyDetails(selectedLog.details)) }} />
                  </pre>
                </div>
              )}

              {/* Complete JSON Payload with copy tool */}
              <div className="space-y-2">
                <div className="flex justify-between items-center">
                  <span className="block text-xs font-bold text-[#3b3036]/60 uppercase tracking-wider">
                    JSON Completo do Log
                  </span>
                  <button
                    onClick={() => copyToClipboard(getFullLogJson(selectedLog))}
                    className="flex items-center gap-1 text-xs text-[#be8a83] hover:text-[#a6726b] font-semibold bg-gray-50 hover:bg-gray-100 px-2.5 py-1 rounded-lg border border-gray-200 transition-all cursor-pointer"
                  >
                    {copiedFullJson ? (
                      <>
                        <Check size={12} /> Copiado!
                      </>
                    ) : (
                      <>
                        <Copy size={12} /> Copiar JSON
                      </>
                    )}
                  </button>
                </div>
                <pre className="bg-[#1e191c] p-4 rounded-xl overflow-x-auto text-xs font-mono border border-black/20 max-h-64 shadow-inner text-[#f8f8f2]">
                  <code dangerouslySetInnerHTML={{ __html: syntaxHighlightJson(getFullLogJson(selectedLog)) }} />
                </pre>
              </div>
            </div>

            {/* Modal Footer */}
            <div className="flex justify-end gap-3 px-6 py-4 border-t border-gray-100 bg-gray-50/50">
              <button
                onClick={() => setSelectedLog(null)}
                className="px-5 py-2 bg-[#be8a83] text-white hover:bg-[#a6726b] font-semibold text-sm rounded-xl transition-all shadow-xs cursor-pointer"
              >
                Fechar Detalhes
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
