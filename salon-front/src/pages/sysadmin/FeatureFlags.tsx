import { useState, useEffect, useCallback } from 'react';
import { featureFlagsService, type FeatureFlag } from '../../services/featureFlags';
import { getApiErrorMessage } from '../../utils/apiError';
import { useAlert } from '../../hooks/useAlert';
import { ShieldAlert, AlertCircle } from 'lucide-react';

// Nomes legíveis em pt-BR pras flags conhecidas — só cosmético, pra não mostrar o
// nome técnico (ex.: "ENABLE_SELF_REGISTRATION") como título do card. Uma flag nova
// que ainda não está aqui continua aparecendo normalmente, só que com o nome técnico
// como título (fallback abaixo) — a tela não deixa de ser dinâmica por causa disso.
const FLAG_LABELS: Record<string, string> = {
  EMAIL_NOTIFICATIONS: 'Notificações por E-mail',
  CLIENT_BOOKING: 'Agendamento pelo Cliente',
  ENABLE_CUSTOMER_PORTAL: 'Portal do Cliente',
  ENABLE_AI_RECOMMENDATIONS: 'Recomendações de IA',
  ENABLE_SELF_REGISTRATION: 'Auto-cadastro na Tela de Login',
};

const getFlagLabel = (name: string) => FLAG_LABELS[name] || name;

export const FeatureFlags = () => {
  const [flags, setFlags] = useState<FeatureFlag[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [togglingName, setTogglingName] = useState<string | null>(null);

  const { error: showError, success: showSuccess } = useAlert();

  const loadFlags = useCallback(async () => {
    setIsLoading(true);
    try {
      const data = await featureFlagsService.getAllFlags();
      setFlags(data);
    } catch (err) {
      const msg = getApiErrorMessage(err, 'Erro ao carregar as feature flags.');
      showError(msg);
    } finally {
      setIsLoading(false);
    }
  }, [showError]);

  useEffect(() => {
    loadFlags();
  }, [loadFlags]);

  const handleToggle = async (name: string, currentStatus: boolean) => {
    setTogglingName(name);
    try {
      await featureFlagsService.toggleFlag(name);
      setFlags((prev) =>
        prev.map((flag) => (flag.name === name ? { ...flag, enabled: !currentStatus } : flag))
      );
      showSuccess(
        `A feature flag ${name} foi ${!currentStatus ? 'ativada' : 'desativada'} com sucesso.`
      );
    } catch (err) {
      const msg = getApiErrorMessage(err, 'Erro ao alternar o estado da feature flag.');
      showError(msg);
    } finally {
      setTogglingName(null);
    }
  };

  return (
    <div className="max-w-7xl mx-auto space-y-6 animate-fade-in-up">
      {/* Header */}
      <div className="flex items-center gap-3">
        <ShieldAlert size={32} className="text-[#be8a83]" />
        <div>
          <h2 className="font-heading text-2xl font-bold text-[#3b3036] tracking-wide">
            Gerenciar Feature Flags
          </h2>
          <p className="text-sm text-[#3b3036]/60 mt-1">
            Controle as funcionalidades do sistema em tempo real. Habilitar ou desabilitar flags
            afeta instantaneamente a experiência do usuário e os fluxos do backend.
          </p>
        </div>
      </div>

      {isLoading ? (
        <div className="flex flex-col items-center justify-center py-20 gap-3">
          <div className="animate-spin rounded-full h-8 w-8 border-t-2 border-b-2 border-[#be8a83]"></div>
          <span className="text-sm text-[#3b3036]/60 font-medium font-sans">
            Buscando feature flags...
          </span>
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          {flags.map((flag) => (
            <div
              key={flag.name}
              className="bg-white rounded-2xl border border-[#eae1e1]/80 p-6 flex flex-col justify-between shadow-sm transition-all duration-300 relative overflow-hidden group"
            >
              <div>
                <div className="flex justify-between items-start gap-4 mb-3">
                  <span className="font-semibold text-[#3b3036] text-base break-words">
                    {getFlagLabel(flag.name)}
                  </span>
                  <span
                    className={`inline-flex items-center px-2.5 py-1 rounded-full text-xs font-semibold shrink-0 ${
                      flag.enabled
                        ? 'bg-emerald-50 text-emerald-700 border border-emerald-100'
                        : 'bg-gray-100 text-gray-600 border border-gray-200'
                    }`}
                  >
                    {flag.enabled ? 'Ativo' : 'Inativo'}
                  </span>
                </div>
                <p className="text-sm text-[#3b3036]/70 min-h-[40px] mb-6">
                  {flag.description || 'Nenhuma descrição fornecida.'}
                </p>
              </div>

              <div className="flex justify-between items-center pt-4 border-t border-[#eae1e1]/80">
                <span className="text-xs font-semibold text-[#3b3036]/60 uppercase tracking-wider">
                  Alternar Status
                </span>
                {togglingName === flag.name ? (
                  <div className="animate-spin rounded-full h-5 w-5 border-t-2 border-b-2 border-[#be8a83]"></div>
                ) : (
                  <label className="relative inline-flex items-center cursor-pointer">
                    <input
                      type="checkbox"
                      className="sr-only peer"
                      checked={flag.enabled}
                      onChange={() => handleToggle(flag.name, flag.enabled)}
                    />
                    <div className="w-11 h-6 bg-gray-200 peer-focus:outline-none rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-gray-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-[#be8a83] shadow-inner"></div>
                  </label>
                )}
              </div>
            </div>
          ))}

          {flags.length === 0 && (
            <div className="col-span-full flex flex-col items-center justify-center py-20 gap-2">
              <AlertCircle size={40} className="text-gray-300" />
              <span className="text-sm font-semibold text-[#3b3036]/80">
                Nenhuma feature flag cadastrada
              </span>
              <span className="text-xs text-[#3b3036]/50">
                Entre em contato com o administrador do sistema.
              </span>
            </div>
          )}
        </div>
      )}
    </div>
  );
};
