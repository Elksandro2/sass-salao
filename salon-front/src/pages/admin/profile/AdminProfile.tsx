import { useState, useEffect } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { profileApi } from '../../profile/services/profile';
import { useAuth } from '../../../hooks/useAuth';
import type { UserUpdateRequest } from '../users/services/users';
import {
  Save,
  User as UserIcon,
  Eye,
  EyeOff,
  Link2,
  Link2Off,
  Loader2,
  CalendarPlus,
} from 'lucide-react';
import { useAlert } from '../../../hooks/useAlert';
import { getApiErrorMessage } from '../../../utils/apiError';
import { profileFormSchema } from '../../profile/profile.schema';
import type { ProfileFormValues } from '../../profile/profile.schema';
import { employeeMercadoPagoApi } from '../employees/services/mercadoPago';
import { employeesApi } from '../employees/services/employees';
import type { EmployeeActingState } from '../employees/services/employees';
import { useFeatureFlag } from '../../../hooks/useFeatureFlag';

const REMUNERATION_LABELS: Record<string, string> = {
  SALARIO_FIXO: 'Salário fixo',
  COMISSIONADO: 'Comissionado',
  FIXO_E_COMISSIONADO: 'Salário fixo + comissionado',
};

// Aplica máscara ###.###.###-## enquanto o usuário digita
const formatCpf = (value: string) => {
  const digits = value.replace(/\D/g, '').slice(0, 11);
  return digits
    .replace(/(\d{3})(\d)/, '$1.$2')
    .replace(/(\d{3})(\d)/, '$1.$2')
    .replace(/(\d{3})(\d{1,2})$/, '$1-$2');
};

export const AdminProfile = () => {
  const { user } = useAuth();
  const { enabled: mercadoPagoEnabled } = useFeatureFlag('MERCADO_PAGO_ATIVO');
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);

  // null = ainda não sabe / usuário sem cadastro de funcionária (esconde a seção)
  const [mpConnected, setMpConnected] = useState<boolean | null>(null);
  const [isMpBusy, setIsMpBusy] = useState(false);

  const isAdmin = user?.role === 'ADMIN';
  const [acting, setActing] = useState<EmployeeActingState | null>(null);
  const [isActingBusy, setIsActingBusy] = useState(false);

  const {
    register,
    handleSubmit,
    setValue,
    watch,
    setError,
    formState: { errors },
  } = useForm<ProfileFormValues>({ resolver: zodResolver(profileFormSchema) });

  const { error: showError, success: showSuccess } = useAlert();
  const [searchParams, setSearchParams] = useSearchParams();

  // O Mercado Pago redireciona de volta pra cá depois do fluxo de OAuth quando é a própria
  // funcionária conectando (ver EmployeeMercadoPagoController#callback, redirectTarget="profile").
  useEffect(() => {
    if (searchParams.has('mp_connected')) {
      setMpConnected(true);
      showSuccess('Conta Mercado Pago conectada com sucesso.');
      setSearchParams({}, { replace: true });
    } else if (searchParams.has('mp_error')) {
      showError('Não foi possível conectar a conta Mercado Pago. Tente novamente.');
      setSearchParams({}, { replace: true });
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    const loadProfile = async () => {
      if (!user?.userId) return;

      try {
        const data = await profileApi.getProfileById(user.userId);
        setValue('name', data.name);
        setValue('email', data.email ?? '');
        setValue('phone', data.phone || '');
        if (data.cpf) {
          setValue('cpf', formatCpf(data.cpf));
        }
      } catch (err) {
        const msg = getApiErrorMessage(err, 'Erro ao carregar os dados do perfil.');
        await showError(msg);
      } finally {
        setIsLoading(false);
      }
    };

    loadProfile();
  }, [user, setValue]);

  useEffect(() => {
    if (!mercadoPagoEnabled) return;
    employeeMercadoPagoApi
      .statusMe()
      .then((status) => setMpConnected(status.connected))
      .catch(() => setMpConnected(null)); // sem cadastro de funcionária vinculado — some a seção
  }, [mercadoPagoEnabled]);

  useEffect(() => {
    if (!isAdmin) return;
    employeesApi
      .getMyActing()
      .then(setActing)
      .catch(() => setActing(null));
  }, [isAdmin]);

  const handleToggleActing = async () => {
    if (!acting) return;
    const next = !acting.acting;
    setIsActingBusy(true);
    try {
      const updated = await employeesApi.setMyActing(next);
      setActing(updated);
      await showSuccess(
        next
          ? 'Pronto! Você já aparece como profissional na criação de agendamentos.'
          : 'Você não aparece mais no seletor de profissional dos agendamentos.'
      );
    } catch (err) {
      await showError(getApiErrorMessage(err, 'Erro ao atualizar sua atuação em agendamentos.'));
    } finally {
      setIsActingBusy(false);
    }
  };

  const onSubmit = async (data: ProfileFormValues) => {
    if (!user?.userId) return;

    setIsSaving(true);
    try {
      const updateData: UserUpdateRequest = { ...data };
      delete (updateData as any).confirmPassword;
      if (!updateData.password) {
        delete updateData.password;
      }
      // Remove máscara antes de enviar ao backend (somente dígitos)
      if (updateData.cpf) {
        updateData.cpf = updateData.cpf.replace(/\D/g, '');
      }

      await profileApi.updateProfile(user.userId, updateData);
      await showSuccess('Perfil atualizado com sucesso!');
    } catch (error: any) {
      if (error.response?.status === 400 && error.response.data?.errors) {
        const fieldErrors = error.response.data.errors;
        Object.keys(fieldErrors).forEach((field) => {
          setError(field as any, { type: 'server', message: fieldErrors[field] });
        });
      } else if (error.response?.status === 409) {
        const msg = error.response.data?.message || 'E-mail ou CPF já cadastrado.';
        if (msg.toLowerCase().includes('cpf')) {
          setError('cpf', { type: 'server', message: msg });
        } else {
          setError('email', { type: 'server', message: msg });
        }
      } else {
        const msg = getApiErrorMessage(error, 'Erro ao atualizar perfil.');
        await showError(msg);
      }
    } finally {
      setIsSaving(false);
    }
  };

  const handleMpConnect = async () => {
    setIsMpBusy(true);
    try {
      const { authorizationUrl } = await employeeMercadoPagoApi.connectMe();
      window.location.href = authorizationUrl;
    } catch (err) {
      await showError(getApiErrorMessage(err, 'Erro ao gerar link de conexão com o Mercado Pago'));
      setIsMpBusy(false);
    }
  };

  const handleMpDisconnect = async () => {
    setIsMpBusy(true);
    try {
      await employeeMercadoPagoApi.disconnectMe();
      setMpConnected(false);
      await showSuccess('Conta Mercado Pago desconectada.');
    } catch (err) {
      await showError(getApiErrorMessage(err, 'Erro ao desconectar'));
    } finally {
      setIsMpBusy(false);
    }
  };

  const cpfValue = watch('cpf') || '';

  if (isLoading) {
    return (
      <div className="flex flex-col items-center justify-center py-20 gap-3">
        <div className="animate-spin rounded-full h-8 w-8 border-t-2 border-b-2 border-[#be8a83]"></div>
        <p className="text-sm text-[#3b3036]/60 font-medium">Carregando perfil...</p>
      </div>
    );
  }

  return (
    <div className="max-w-3xl mx-auto space-y-6">
      <h2 className="font-heading text-2xl font-bold text-[#3b3036] tracking-wide">Meu Perfil</h2>

      <div className="bg-white rounded-2xl border border-gray-100 p-6 shadow-xs space-y-6">
        <div className="flex items-center gap-4 pb-5 border-b border-gray-100">
          <div className="bg-[#be8a83]/10 text-[#be8a83] rounded-full p-4 shrink-0">
            <UserIcon size={32} />
          </div>
          <div>
            <h4 className="font-semibold text-[#3b3036] text-lg">{user?.email}</h4>
            <p className="text-sm text-[#3b3036]/60">Atualize suas informações pessoais</p>
          </div>
        </div>

        <form onSubmit={handleSubmit(onSubmit)} className="space-y-5">
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div className="space-y-1.5">
              <label className="label-premium">
                Nome Completo <span className="text-rose-500">*</span>
              </label>
              <input
                type="text"
                maxLength={150}
                {...register('name')}
                className={`input-premium ${errors.name ? 'border-rose-300 focus:border-rose-500' : ''}`}
              />
              {errors.name && (
                <span className="text-xs text-rose-500 font-semibold">{errors.name.message}</span>
              )}
            </div>

            <div className="space-y-1.5">
              <label className="label-premium">Telefone</label>
              <input
                type="tel"
                maxLength={20}
                {...register('phone')}
                placeholder="(11) 99999-9999"
                className={`input-premium ${errors.phone ? 'border-rose-300 focus:border-rose-500' : ''}`}
              />
              {errors.phone && (
                <span className="text-xs text-rose-500 font-semibold">{errors.phone.message}</span>
              )}
            </div>
          </div>

          <div className="space-y-1.5">
            <label className="label-premium">
              E-mail <span className="text-rose-500">*</span>
            </label>
            <input
              type="email"
              {...register('email', { required: true })}
              disabled
              className="input-premium bg-gray-100 text-gray-500 cursor-not-allowed opacity-60"
            />
            <p className="text-xs text-gray-400">
              O email não pode ser alterado, pois é usado para login.
            </p>
          </div>

          <div className="space-y-1.5">
            <label className="label-premium">
              CPF <span className="text-xs text-[#7a7074] font-normal">(Opcional)</span>
            </label>
            <input
              type="text"
              placeholder="000.000.000-00"
              value={cpfValue}
              {...register('cpf')}
              onChange={(e) => setValue('cpf', formatCpf(e.target.value), { shouldValidate: true })}
              className={`input-premium ${errors.cpf ? 'border-rose-300 focus:border-rose-500' : ''}`}
              maxLength={14}
            />
            {errors.cpf && (
              <span className="text-xs text-rose-500 font-semibold">{errors.cpf.message}</span>
            )}
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div className="space-y-1.5">
              <label className="label-premium">Nova Senha</label>
              <div className="relative">
                <input
                  type={showPassword ? 'text' : 'password'}
                  placeholder="Deixe em branco para não alterar"
                  {...register('password')}
                  className={`input-premium pr-10 ${errors.password ? 'border-rose-300 focus:border-rose-500' : ''}`}
                />
                <button
                  type="button"
                  onClick={() => setShowPassword(!showPassword)}
                  className="absolute right-3 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600 focus:outline-none cursor-pointer flex items-center"
                >
                  {showPassword ? <EyeOff size={18} /> : <Eye size={18} />}
                </button>
              </div>
              {errors.password && (
                <span className="text-xs text-rose-500 font-semibold">{errors.password.message}</span>
              )}
            </div>

            <div className="space-y-1.5">
              <label className="label-premium">Confirmar Nova Senha</label>
              <div className="relative">
                <input
                  type={showConfirmPassword ? 'text' : 'password'}
                  placeholder="Confirme sua nova senha"
                  {...register('confirmPassword')}
                  className={`input-premium pr-10 ${errors.confirmPassword ? 'border-rose-300 focus:border-rose-500' : ''}`}
                />
                <button
                  type="button"
                  onClick={() => setShowConfirmPassword(!showConfirmPassword)}
                  className="absolute right-3 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600 focus:outline-none cursor-pointer flex items-center"
                >
                  {showConfirmPassword ? <EyeOff size={18} /> : <Eye size={18} />}
                </button>
              </div>
              {errors.confirmPassword && (
                <span className="text-xs text-rose-500 font-semibold">{errors.confirmPassword.message}</span>
              )}
            </div>
          </div>

          <div className="flex justify-end pt-4">
            <button type="submit" disabled={isSaving} className="btn-premium disabled:opacity-50">
              {isSaving ? (
                <div className="animate-spin rounded-full h-4 w-4 border-t-2 border-b-2 border-white"></div>
              ) : (
                <Save size={18} />
              )}
              Salvar Alterações
            </button>
          </div>
        </form>
      </div>

      {isAdmin && acting && (
        <div className="bg-white rounded-2xl border border-gray-100 p-6 shadow-xs space-y-4">
          <div className="flex items-start gap-3">
            <div className="bg-[#be8a83]/10 text-[#be8a83] rounded-full p-2.5 shrink-0">
              <CalendarPlus size={20} />
            </div>
            <div>
              <h4 className="font-semibold text-[#3b3036] text-lg">Atuar como profissional</h4>
              <p className="text-sm text-[#3b3036]/60">
                Ligando isto, você entra no seletor de profissional ao criar um agendamento e pode
                marcar atendimentos para si. É criado um cadastro de colaborador com remuneração{' '}
                <strong>Comissionado</strong> por padrão — o modelo de pagamento é editável em{' '}
                <strong>Equipe → Colaboradores(as) &amp; Gerentes</strong>.
              </p>
            </div>
          </div>

          <div className="flex flex-wrap items-center gap-3">
            {acting.acting ? (
              <span className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full text-xs font-bold uppercase bg-emerald-50 text-emerald-700 border border-emerald-200">
                Ativo
              </span>
            ) : (
              <span className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full text-xs font-bold uppercase bg-gray-100 text-gray-500 border border-gray-200">
                Desativado
              </span>
            )}
            {acting.acting && acting.remunerationType && (
              <span className="text-xs text-[#3b3036]/60">
                Remuneração atual: {REMUNERATION_LABELS[acting.remunerationType] ?? acting.remunerationType}
              </span>
            )}
            <button
              type="button"
              onClick={handleToggleActing}
              disabled={isActingBusy}
              className="inline-flex items-center gap-1.5 px-4 py-2 text-sm font-semibold text-[#be8a83] border border-[#be8a83]/40 hover:bg-[#be8a83]/5 rounded-xl transition-all cursor-pointer disabled:opacity-50"
            >
              {isActingBusy && <Loader2 size={16} className="animate-spin" />}
              {acting.acting ? 'Desativar atuação' : 'Ativar atuação'}
            </button>
          </div>
        </div>
      )}

      {mercadoPagoEnabled && mpConnected !== null && (
        <div className="bg-white rounded-2xl border border-gray-100 p-6 shadow-xs space-y-4">
          <div>
            <h4 className="font-semibold text-[#3b3036] text-lg">Mercado Pago</h4>
            <p className="text-sm text-[#3b3036]/60">
              Conectando sua conta, sua comissão de cada atendimento pago via PIX cai direto na sua
              conta Mercado Pago — sem passar pela conta do salão.
            </p>
          </div>

          {mpConnected ? (
            <div className="flex items-center gap-3">
              <span className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full text-xs font-bold uppercase bg-emerald-50 text-emerald-700 border border-emerald-200">
                <Link2 size={14} /> Conectada
              </span>
              <button
                type="button"
                onClick={handleMpDisconnect}
                disabled={isMpBusy}
                className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-semibold text-rose-600 border border-rose-200 hover:bg-rose-50 rounded-lg transition-all cursor-pointer disabled:opacity-50"
              >
                {isMpBusy ? <Loader2 size={14} className="animate-spin" /> : <Link2Off size={14} />}
                Desconectar
              </button>
            </div>
          ) : (
            <button
              type="button"
              onClick={handleMpConnect}
              disabled={isMpBusy}
              className="inline-flex items-center gap-1.5 px-4 py-2 text-sm font-semibold text-[#be8a83] border border-[#be8a83]/40 hover:bg-[#be8a83]/5 rounded-xl transition-all cursor-pointer disabled:opacity-50"
            >
              {isMpBusy ? <Loader2 size={16} className="animate-spin" /> : <Link2 size={16} />}
              Conectar minha conta Mercado Pago
            </button>
          )}
        </div>
      )}
    </div>
  );
};
