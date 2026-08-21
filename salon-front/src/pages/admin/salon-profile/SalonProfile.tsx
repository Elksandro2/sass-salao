import { useState, useEffect, useCallback } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { Store, Clock, Percent } from 'lucide-react';
import {
  salonProfileService,
  DAY_ORDER,
  DAY_LABELS,
  type SalonProfileData,
  type SalonProfileUpdatePayload,
} from '../../../services/salonProfile';
import { businessSettingsService } from '../../../services/businessSettings';
import { useAlert } from '../../../hooks/useAlert';
import { getApiErrorMessage } from '../../../utils/apiError';
import { salonProfileFormSchema } from './salonProfile.schema';
import type { SalonProfileFormValues } from './salonProfile.schema';

const inputCls = 'input-premium';
const labelCls = 'label-premium';

const emptyBusinessHours = () =>
  DAY_ORDER.map((dayOfWeek) => ({
    dayOfWeek,
    open: dayOfWeek !== 'SUNDAY',
    openTime: dayOfWeek !== 'SUNDAY' ? '08:00' : null,
    closeTime: dayOfWeek !== 'SUNDAY' ? '18:00' : null,
  }));

// Backend espera LocalTime — normaliza "HH:mm" (o que o <input type="time"> devolve) pra
// "HH:mm:ss" antes de enviar.
const withSeconds = (time: string | null) => (time && time.length === 5 ? `${time}:00` : time);

export const SalonProfile = () => {
  const [profile, setProfile] = useState<SalonProfileData | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);

  const [productCommissionPercent, setProductCommissionPercent] = useState('');
  const [businessSettingsUpdatedAt, setBusinessSettingsUpdatedAt] = useState<string | null>(null);
  const [isSavingCommission, setIsSavingCommission] = useState(false);

  const { error: showError, success: showSuccess } = useAlert();

  const {
    register,
    handleSubmit,
    reset,
    watch,
    formState: { errors },
  } = useForm<SalonProfileFormValues>({
    resolver: zodResolver(salonProfileFormSchema),
    defaultValues: { businessHours: emptyBusinessHours() },
  });

  const loadProfile = useCallback(async () => {
    setIsLoading(true);
    try {
      const data = await salonProfileService.getPublic();
      setProfile(data);
      reset({
        name: data.name,
        description: data.description ?? '',
        address: data.address ?? '',
        phone: data.phone ?? '',
        instagram: data.instagram ?? '',
        whatsapp: data.whatsapp ?? '',
        businessHours: DAY_ORDER.map((dayOfWeek) => {
          const found = data.businessHours.find((bh) => bh.dayOfWeek === dayOfWeek);
          return {
            dayOfWeek,
            open: found?.open ?? false,
            openTime: found?.openTime?.slice(0, 5) ?? null,
            closeTime: found?.closeTime?.slice(0, 5) ?? null,
          };
        }),
      });
    } catch (err) {
      showError(getApiErrorMessage(err, 'Erro ao carregar o perfil do salão.'));
    } finally {
      setIsLoading(false);
    }
  }, [reset, showError]);

  useEffect(() => {
    loadProfile();
  }, [loadProfile]);

  useEffect(() => {
    businessSettingsService
      .get()
      .then((data) => {
        setProductCommissionPercent(
          data.productCommissionPercent != null ? String(data.productCommissionPercent) : ''
        );
        setBusinessSettingsUpdatedAt(data.updatedAt);
      })
      .catch((err) => showError(getApiErrorMessage(err, 'Erro ao carregar a comissão de produtos.')));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const handleSaveCommission = async () => {
    setIsSavingCommission(true);
    try {
      const updated = await businessSettingsService.update({
        productCommissionPercent: productCommissionPercent ? Number(productCommissionPercent) : null,
      });
      setProductCommissionPercent(
        updated.productCommissionPercent != null ? String(updated.productCommissionPercent) : ''
      );
      setBusinessSettingsUpdatedAt(updated.updatedAt);
      showSuccess('Comissão sobre produtos atualizada com sucesso.');
    } catch (err) {
      showError(getApiErrorMessage(err, 'Erro ao salvar a comissão sobre produtos.'));
    } finally {
      setIsSavingCommission(false);
    }
  };

  const onSubmit = async (data: SalonProfileFormValues) => {
    setIsSaving(true);
    try {
      const payload: SalonProfileUpdatePayload = {
        name: data.name,
        description: data.description || null,
        address: data.address || null,
        phone: data.phone || null,
        instagram: data.instagram || null,
        whatsapp: data.whatsapp || null,
        businessHours: data.businessHours.map((bh) => ({
          dayOfWeek: bh.dayOfWeek,
          open: bh.open,
          openTime: bh.open ? withSeconds(bh.openTime) : null,
          closeTime: bh.open ? withSeconds(bh.closeTime) : null,
        })),
      };
      const updated = await salonProfileService.update(payload);
      setProfile(updated);
      showSuccess('Perfil do salão atualizado com sucesso.');
    } catch (err) {
      showError(getApiErrorMessage(err, 'Erro ao salvar o perfil do salão.'));
    } finally {
      setIsSaving(false);
    }
  };

  const businessHours = watch('businessHours');

  if (isLoading) {
    return (
      <div className="flex flex-col items-center justify-center py-20 gap-3">
        <div className="animate-spin rounded-full h-8 w-8 border-t-2 border-b-2 border-[#be8a83]"></div>
        <span className="text-sm text-[#3b3036]/60 font-medium font-sans">
          Carregando perfil do salão...
        </span>
      </div>
    );
  }

  return (
    <div className="max-w-3xl mx-auto space-y-6 animate-fade-in-up">
      <div className="flex items-center gap-3">
        <Store size={32} className="text-[#be8a83]" />
        <div>
          <h2 className="font-heading text-2xl font-bold text-[#3b3036] tracking-wide">
            Perfil do Salão
          </h2>
          <p className="text-sm text-[#3b3036]/60 mt-1">
            Essas informações aparecem na página inicial pública, sem precisar de novo deploy.
          </p>
        </div>
      </div>

      <form
        onSubmit={handleSubmit(onSubmit)}
        className="bg-white rounded-2xl border border-[#eae1e1]/80 p-6 space-y-5 shadow-sm"
      >
        <div className="space-y-1.5">
          <label htmlFor="salon-name" className={labelCls}>Nome *</label>
          <input id="salon-name" type="text" maxLength={150} {...register('name')}
            className={`${inputCls} ${errors.name ? 'border-rose-300 focus:border-rose-500' : ''}`} />
          {errors.name && <span className="text-xs text-rose-500 font-semibold">{errors.name.message}</span>}
        </div>

        <div className="space-y-1.5">
          <label htmlFor="salon-description" className={labelCls}>Sobre</label>
          <textarea id="salon-description" rows={4} maxLength={2000} {...register('description')} className={inputCls} />
          {errors.description && (
            <span className="text-xs text-rose-500 font-semibold">{errors.description.message}</span>
          )}
        </div>

        <div className="space-y-1.5">
          <label htmlFor="salon-address" className={labelCls}>Endereço</label>
          <input id="salon-address" type="text" maxLength={300} {...register('address')} className={inputCls} />
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
          <div className="space-y-1.5">
            <label htmlFor="salon-phone" className={labelCls}>Telefone</label>
            <input id="salon-phone" type="text" maxLength={20} {...register('phone')} className={inputCls} />
          </div>
          <div className="space-y-1.5">
            <label htmlFor="salon-instagram" className={labelCls}>Instagram</label>
            <input id="salon-instagram" type="text" placeholder="@seusalao" maxLength={150} {...register('instagram')} className={inputCls} />
          </div>
          <div className="space-y-1.5">
            <label htmlFor="salon-whatsapp" className={labelCls}>WhatsApp</label>
            <input id="salon-whatsapp" type="text" maxLength={20} {...register('whatsapp')} className={inputCls} />
          </div>
        </div>

        <div className="pt-2 border-t border-[#eae1e1]/80">
          <div className="flex items-center gap-2 mb-3">
            <Clock size={18} className="text-[#be8a83]" />
            <span className="text-sm font-bold text-[#3b3036]">Horário de funcionamento</span>
          </div>
          <div className="space-y-2">
            {DAY_ORDER.map((day, index) => {
              const dayValue = businessHours?.[index];
              return (
                <div
                  key={day}
                  className="flex flex-wrap items-center gap-3 p-3 rounded-xl bg-gray-50/60 border border-gray-100"
                >
                  <span className="text-sm font-semibold text-[#3b3036] w-32 shrink-0">
                    {DAY_LABELS[day]}
                  </span>
                  <label className="flex items-center gap-1.5 text-xs font-semibold text-[#3b3036]/70">
                    <input
                      type="checkbox"
                      id={`business-hour-${day}-open`}
                      {...register(`businessHours.${index}.open` as const)}
                    />
                    Aberto
                  </label>
                  <input
                    type="time"
                    aria-label={`Horário de abertura em ${DAY_LABELS[day]}`}
                    disabled={!dayValue?.open}
                    {...register(`businessHours.${index}.openTime` as const)}
                    className={`${inputCls} !w-32 disabled:opacity-40`}
                  />
                  <span className="text-xs text-gray-400">até</span>
                  <input
                    type="time"
                    aria-label={`Horário de fechamento em ${DAY_LABELS[day]}`}
                    disabled={!dayValue?.open}
                    {...register(`businessHours.${index}.closeTime` as const)}
                    className={`${inputCls} !w-32 disabled:opacity-40`}
                  />
                  {errors.businessHours?.[index]?.closeTime && (
                    <span className="text-xs text-rose-500 font-semibold w-full">
                      {errors.businessHours[index]?.closeTime?.message}
                    </span>
                  )}
                  {errors.businessHours?.[index]?.openTime && (
                    <span className="text-xs text-rose-500 font-semibold w-full">
                      {errors.businessHours[index]?.openTime?.message}
                    </span>
                  )}
                </div>
              );
            })}
          </div>
        </div>

        {profile?.updatedAt && (
          <p className="text-xs text-[#3b3036]/50">
            Última atualização em {new Date(profile.updatedAt).toLocaleString('pt-BR')}
          </p>
        )}

        <button
          type="submit"
          disabled={isSaving}
          className="w-full py-3 bg-[#be8a83] hover:bg-[#a1706a] text-[#fcf9f9] font-semibold rounded-xl text-sm transition-all disabled:opacity-50 disabled:pointer-events-none cursor-pointer"
        >
          {isSaving ? 'Salvando...' : 'Salvar perfil'}
        </button>
      </form>

      <div className="bg-white rounded-2xl border border-[#eae1e1]/80 p-6 space-y-4 shadow-sm">
        <div className="flex items-center gap-2">
          <Percent size={18} className="text-[#be8a83]" />
          <span className="text-sm font-bold text-[#3b3036]">Comissão sobre produtos vendidos</span>
        </div>
        <p className="text-xs text-[#3b3036]/60">
          Porcentagem única, válida pro salão inteiro — qualquer funcionária que vender qualquer
          produto recebe essa %. É uma exceção deliberada à regra geral: vale até para quem é
          <strong> Salário Fixo</strong>, como incentivo à venda (diferente da comissão de
          serviços, que é configurada por serviço na tela de Serviços).
        </p>
        <div className="flex flex-wrap items-end gap-3">
          <div className="space-y-1.5">
            <label htmlFor="product-commission-percent" className={labelCls}>Comissão (%)</label>
            <input
              id="product-commission-percent"
              type="number"
              step="0.01"
              min="0"
              max="100"
              placeholder="Deixe em branco para não pagar comissão sobre produtos"
              value={productCommissionPercent}
              onChange={(e) => setProductCommissionPercent(e.target.value)}
              className={`${inputCls} !w-64`}
            />
          </div>
          <button
            type="button"
            onClick={handleSaveCommission}
            disabled={isSavingCommission}
            className="px-6 py-2.5 bg-[#be8a83] hover:bg-[#a1706a] text-[#fcf9f9] font-semibold rounded-xl text-sm transition-all disabled:opacity-50 disabled:pointer-events-none cursor-pointer"
          >
            {isSavingCommission ? 'Salvando...' : 'Salvar comissão'}
          </button>
        </div>
        {businessSettingsUpdatedAt && (
          <p className="text-xs text-[#3b3036]/50">
            Última atualização em {new Date(businessSettingsUpdatedAt).toLocaleString('pt-BR')}
          </p>
        )}
      </div>
    </div>
  );
};
