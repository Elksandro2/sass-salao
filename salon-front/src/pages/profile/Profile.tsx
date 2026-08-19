import { useState, useEffect } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { profileApi } from './services/profile';
import { useAuth } from '../../hooks/useAuth';
import type { UserUpdateRequest } from '../admin/users/services/users';
import { Save, User as UserIcon, Eye, EyeOff } from 'lucide-react';
import { useAlert } from '../../hooks/useAlert';
import { getApiErrorMessage } from '../../utils/apiError';
import { profileFormSchema } from './profile.schema';
import type { ProfileFormValues } from './profile.schema';

// Aplica máscara ###.###.###-## enquanto o usuário digita
const formatCpf = (value: string) => {
  const digits = value.replace(/\D/g, '').slice(0, 11);
  return digits
    .replace(/(\d{3})(\d)/, '$1.$2')
    .replace(/(\d{3})(\d)/, '$1.$2')
    .replace(/(\d{3})(\d{1,2})$/, '$1-$2');
};

export const Profile = () => {
  const { user } = useAuth();
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);

  const {
    register,
    handleSubmit,
    setValue,
    watch,
    setError,
    formState: { errors },
  } = useForm<ProfileFormValues>({ resolver: zodResolver(profileFormSchema) });

  const { error: showError, success: showSuccess } = useAlert();

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

          {/* CPF — opcional, coletado preferencialmente no momento do PIX */}
          <div className="space-y-1.5">
            <label className="label-premium">
              CPF{' '}
              <span className="text-xs text-[#7a7074] font-normal">(Opcional — necessário para pagamentos via PIX)</span>
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
                  className="absolute right-3 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600 dark:hover:text-gray-200 focus:outline-none cursor-pointer flex items-center"
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
                  className="absolute right-3 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600 dark:hover:text-gray-200 focus:outline-none cursor-pointer flex items-center"
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
    </div>
  );
};
