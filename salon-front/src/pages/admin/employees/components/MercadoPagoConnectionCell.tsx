import { useEffect, useState } from 'react';
import { Link2, Link2Off, Loader2 } from 'lucide-react';
import { employeeMercadoPagoApi } from '../services/mercadoPago';
import { PermissionGate } from '../../../../components/permissions/PermissionGate';
import { useAlert } from '../../../../hooks/useAlert';
import { getApiErrorMessage } from '../../../../utils/apiError';

interface MercadoPagoConnectionCellProps {
  employeeId: number;
  /** Só faz sentido dividir por atendimento pra quem tem comissão individual — pra
   * salário fixo/comissão global, a conexão não muda nada no fluxo de pagamento. */
  splitApplicable: boolean;
}

export const MercadoPagoConnectionCell = ({ employeeId, splitApplicable }: MercadoPagoConnectionCellProps) => {
  const [connected, setConnected] = useState<boolean | null>(null);
  const [isBusy, setIsBusy] = useState(false);
  const { error: showError, confirm } = useAlert();

  const loadStatus = async () => {
    try {
      const status = await employeeMercadoPagoApi.status(employeeId);
      setConnected(status.connected);
    } catch {
      // Não trava a tela por causa disso — só não mostra o status.
      setConnected(null);
    }
  };

  useEffect(() => {
    loadStatus();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [employeeId]);

  const handleConnect = async () => {
    setIsBusy(true);
    try {
      const { authorizationUrl } = await employeeMercadoPagoApi.connect(employeeId);
      window.location.href = authorizationUrl;
    } catch (err) {
      await showError(getApiErrorMessage(err, 'Erro ao gerar link de conexão com o Mercado Pago'));
      setIsBusy(false);
    }
  };

  const handleDisconnect = async () => {
    await confirm(
      'Os próximos pagamentos dela vão parar de dividir automaticamente — volta pro fluxo manual (comissão + PIX avulso). Continuar?',
      async () => {
        setIsBusy(true);
        try {
          await employeeMercadoPagoApi.disconnect(employeeId);
          setConnected(false);
        } catch (err) {
          await showError(getApiErrorMessage(err, 'Erro ao desconectar'));
        } finally {
          setIsBusy(false);
        }
      },
      { title: 'Desconectar Mercado Pago', isDangerous: true, confirmText: 'Desconectar' }
    );
  };

  if (!splitApplicable) {
    return <span className="text-3xs text-gray-400">—</span>;
  }

  if (connected === null) {
    return <span className="text-3xs text-gray-400">...</span>;
  }

  if (connected) {
    return (
      <div className="flex items-center gap-1.5">
        <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-3xs font-bold uppercase bg-emerald-50 text-emerald-700 border border-emerald-200">
          <Link2 size={11} /> Conectada
        </span>
        <PermissionGate method="DELETE" endpoint={`/v1/employees/${employeeId}/mercadopago`}>
          <button
            onClick={handleDisconnect}
            disabled={isBusy}
            title="Desconectar Mercado Pago"
            className="p-1 text-rose-500 hover:bg-rose-50 rounded transition-all cursor-pointer disabled:opacity-50"
          >
            {isBusy ? <Loader2 size={13} className="animate-spin" /> : <Link2Off size={13} />}
          </button>
        </PermissionGate>
      </div>
    );
  }

  return (
    <PermissionGate
      method="GET"
      endpoint={`/v1/employees/${employeeId}/mercadopago/connect`}
      fallback={<span className="text-3xs text-gray-400">Não conectada</span>}
    >
      <button
        onClick={handleConnect}
        disabled={isBusy}
        className="inline-flex items-center gap-1 px-2 py-1 text-3xs font-semibold text-[#be8a83] border border-[#be8a83]/40 hover:bg-[#be8a83]/5 rounded-lg transition-all cursor-pointer disabled:opacity-50"
      >
        {isBusy ? <Loader2 size={12} className="animate-spin" /> : <Link2 size={12} />}
        Conectar Mercado Pago
      </button>
    </PermissionGate>
  );
};
