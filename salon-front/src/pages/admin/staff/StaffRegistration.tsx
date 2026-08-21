import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import {
  Plus,
  X,
  User as UserIcon,
  MapPin,
  Landmark,
  Wallet,
  FileText,
  ArrowLeft,
  ArrowRight,
  ShieldCheck,
  QrCode,
} from 'lucide-react';
import { QRCodeSVG } from 'qrcode.react';
import { DataTable } from '../../../components/table/DataTable';
import { PermissionGate } from '../../../components/permissions/PermissionGate';
import { useAlert } from '../../../hooks/useAlert';
import { getApiErrorMessage } from '../../../utils/apiError';
import { staffApi } from './services/staff';
import type { StaffProfileResponse, StaffRoleName, StaffPixQrCodeResponse } from './services/staff';
import { staffFormSchema, BRAZILIAN_STATES } from './staff.schema';
import type { StaffFormValues } from './staff.schema';
import { RoleSelector } from './components/RoleSelector';

const inputCls = 'input-premium';
const labelCls = 'label-premium';
const sectionTitleCls = 'flex items-center gap-2 font-heading text-sm font-bold text-[#3b3036] mt-2';

const ROLE_LABELS: Record<string, string> = {
  FUNCIONARIA: 'Funcionária',
  GERENTE_DE_ATENDIMENTO: 'Gerente de Atendimento',
};

async function tryAutofillAddressFromCep(
  cep: string,
  setValue: ReturnType<typeof useForm<StaffFormValues>>['setValue']
) {
  const digits = cep.replace(/\D/g, '');
  if (digits.length !== 8) return;
  try {
    const resp = await fetch(`https://viacep.com.br/ws/${digits}/json/`);
    if (!resp.ok) return;
    const data = await resp.json();
    if (data.erro) return;
    if (data.logradouro) setValue('street', data.logradouro);
    if (data.bairro) setValue('district', data.bairro);
    if (data.localidade) setValue('city', data.localidade);
    if (data.uf) setValue('stateUf', data.uf);
  } catch {
    // Autopreenchimento é conveniência, não requisito — falha silenciosa e o usuário digita.
  }
}

export const StaffRegistration = () => {
  const [refreshTrigger, setRefreshTrigger] = useState(0);
  const [showModal, setShowModal] = useState(false);
  const [step, setStep] = useState<1 | 2>(1);
  const [detailTarget, setDetailTarget] = useState<StaffProfileResponse | null>(null);
  const [pixAmount, setPixAmount] = useState('');
  const [pixQrCode, setPixQrCode] = useState<StaffPixQrCodeResponse | null>(null);
  const [isGeneratingPix, setIsGeneratingPix] = useState(false);

  const { error: showError, success: showSuccess } = useAlert();

  const {
    register,
    handleSubmit,
    watch,
    setValue,
    setError,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<StaffFormValues>({ resolver: zodResolver(staffFormSchema) });

  const roleName = watch('roleName');
  const remunerationType = watch('remunerationType');
  const isCommissioned = remunerationType === 'COMISSIONADO' || remunerationType === 'FIXO_E_COMISSIONADO';

  const openModal = () => {
    reset({});
    setStep(1);
    setShowModal(true);
  };

  const closeModal = () => {
    setShowModal(false);
    setStep(1);
    reset({});
  };

  const chooseRole = (role: StaffRoleName) => {
    setValue('roleName', role);
    setStep(2);
  };

  const onSubmit = async (values: StaffFormValues) => {
    try {
      await staffApi.create({
        name: values.name,
        email: values.email,
        password: values.password,
        roleName: values.roleName,
        fullName: values.fullName,
        socialName: values.socialName || null,
        cpf: values.cpf,
        birthDate: values.birthDate,
        gender: values.gender || null,
        phone: values.phone,
        emergencyContactName: values.emergencyContactName || null,
        emergencyContactPhone: values.emergencyContactPhone || null,
        zipCode: values.zipCode,
        street: values.street,
        streetNumber: values.streetNumber,
        complement: values.complement || null,
        district: values.district,
        city: values.city,
        stateUf: values.stateUf as StaffFormValues['stateUf'] & string,
        pixKeyType: values.pixKeyType || null,
        pixKey: values.pixKey || null,
        hiredAt: values.hiredAt || null,
        notes: values.notes || null,
        remunerationType: values.remunerationType || null,
        remunerationValue: values.remunerationValue ? Number(values.remunerationValue) : null,
      } as Parameters<typeof staffApi.create>[0]);

      await showSuccess('Cadastro de equipe criado com sucesso');
      closeModal();
      setRefreshTrigger((prev) => prev + 1);
    } catch (error: any) {
      if (error?.response?.status === 409) {
        const msg = getApiErrorMessage(error, 'Email ou CPF já cadastrado');
        if (msg.toLowerCase().includes('cpf')) {
          setError('cpf', { message: msg });
        } else {
          setError('email', { message: msg });
        }
        return;
      }
      const msg = getApiErrorMessage(error, 'Erro ao criar cadastro de equipe');
      await showError(msg);
    }
  };

  const closeDetailModal = () => {
    setDetailTarget(null);
    setPixAmount('');
    setPixQrCode(null);
  };

  const handleGeneratePix = async () => {
    if (!detailTarget || !pixAmount) return;
    setIsGeneratingPix(true);
    try {
      const result = await staffApi.generatePixQrCode(detailTarget.id, Number(pixAmount));
      setPixQrCode(result);
    } catch (error) {
      const msg = getApiErrorMessage(error, 'Erro ao gerar QR Code PIX');
      await showError(msg);
    } finally {
      setIsGeneratingPix(false);
    }
  };

  const columns = [
    { key: 'displayName', label: 'Nome' },
    {
      key: 'roleName',
      label: 'Papel',
      render: (item: StaffProfileResponse) => ROLE_LABELS[item.roleName] || item.roleName,
    },
    { key: 'email', label: 'Email' },
    { key: 'phone', label: 'Telefone' },
    { key: 'city', label: 'Cidade' },
    {
      key: 'actions',
      label: 'Detalhes',
      render: (item: StaffProfileResponse) => (
        <button
          type="button"
          onClick={() => setDetailTarget(item)}
          className="text-xs font-semibold text-[#be8a83] hover:underline cursor-pointer"
        >
          Ver mais
        </button>
      ),
    },
  ];

  return (
    <>
      <div className="space-y-6 animate-fade-in-up">
        <div className="flex justify-between items-center">
          <div>
            <h2 className="font-heading text-2xl font-bold text-[#3b3036]">Cadastro de Equipe</h2>
            <p className="text-xs text-[#3b3036]/60 mt-1">
              Cadastro completo de funcionárias e gerentes de atendimento — restrito a
              administradores.
            </p>
          </div>
          <PermissionGate method="POST" endpoint="/v1/staff">
            <button onClick={openModal} className="btn-premium font-semibold shadow-md shadow-[#be8a83]/10">
              <Plus size={18} /> Novo Cadastro
            </button>
          </PermissionGate>
        </div>

        <DataTable<StaffProfileResponse, Record<string, never>>
          columns={columns}
          fetchData={(_filter, page, size) => staffApi.findAll({}, page, size)}
          keyExtractor={(item) => item.id}
          refreshTrigger={refreshTrigger}
          initialFilters={{}}
        />
      </div>

      {showModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-[#261f23]/40 backdrop-blur-md">
          <div className="bg-white rounded-2xl shadow-xl w-full max-w-3xl border border-[#eae1e1]/85 overflow-hidden animate-scale-up max-h-[90vh] flex flex-col">
            <div className="flex items-center justify-between px-6 py-4 border-b border-[#eae1e1] bg-[#fcf9f9]/50 shrink-0">
              <h3 className="font-heading text-lg font-bold text-[#3b3036]">
                {step === 1 ? 'Novo cadastro de equipe — escolha o papel' : `Novo cadastro — ${ROLE_LABELS[roleName]}`}
              </h3>
              <button
                onClick={closeModal}
                className="p-1.5 text-gray-400 hover:text-gray-600 hover:bg-gray-100 rounded-lg transition-all cursor-pointer"
              >
                <X size={20} />
              </button>
            </div>

            {step === 1 && (
              <div className="p-6 space-y-4 overflow-y-auto">
                <RoleSelector value={roleName ?? null} onChange={chooseRole} />
              </div>
            )}

            {step === 2 && (
              <form onSubmit={handleSubmit(onSubmit)} className="flex flex-col overflow-hidden">
                <div className="p-6 space-y-5 overflow-y-auto">
                  <div className="p-3.5 bg-amber-50 border border-amber-100 rounded-xl text-xs text-amber-700 flex items-start gap-2">
                    <ShieldCheck size={16} className="shrink-0 mt-0.5" />
                    <span>
                      CPF e chave PIX são cifrados no banco e nunca retornam em texto — a tela
                      sempre mostra versões mascaradas.
                    </span>
                  </div>

                  {/* Acesso */}
                  <h4 className={sectionTitleCls}>
                    <UserIcon size={16} className="text-[#be8a83]" /> Acesso
                  </h4>
                  <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                    <div>
                      <label htmlFor="staff-name" className={labelCls}>Nome de exibição</label>
                      <input id="staff-name" maxLength={150} className={inputCls} {...register('name')} />
                      {errors.name && <span className="text-xs text-rose-500 font-semibold">{errors.name.message}</span>}
                    </div>
                    <div>
                      <label htmlFor="staff-email" className={labelCls}>Email</label>
                      <input id="staff-email" type="email" maxLength={150} className={inputCls} {...register('email')} />
                      {errors.email && <span className="text-xs text-rose-500 font-semibold">{errors.email.message}</span>}
                    </div>
                    <div>
                      <label htmlFor="staff-password" className={labelCls}>Senha</label>
                      <input id="staff-password" type="password" className={inputCls} {...register('password')} />
                      {errors.password && <span className="text-xs text-rose-500 font-semibold">{errors.password.message}</span>}
                    </div>
                    <div>
                      <label htmlFor="staff-confirm-password" className={labelCls}>Confirmar senha</label>
                      <input id="staff-confirm-password" type="password" className={inputCls} {...register('confirmPassword')} />
                      {errors.confirmPassword && (
                        <span className="text-xs text-rose-500 font-semibold">{errors.confirmPassword.message}</span>
                      )}
                    </div>
                  </div>

                  {/* Dados pessoais */}
                  <h4 className={sectionTitleCls}>
                    <UserIcon size={16} className="text-[#be8a83]" /> Dados pessoais
                  </h4>
                  <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                    <div>
                      <label htmlFor="staff-fullName" className={labelCls}>Nome completo</label>
                      <input id="staff-fullName" maxLength={150} className={inputCls} {...register('fullName')} />
                      {errors.fullName && <span className="text-xs text-rose-500 font-semibold">{errors.fullName.message}</span>}
                    </div>
                    <div>
                      <label htmlFor="staff-socialName" className={labelCls}>Nome social (opcional)</label>
                      <input id="staff-socialName" maxLength={150} className={inputCls} {...register('socialName')} />
                    </div>
                    <div>
                      <label htmlFor="staff-cpf" className={labelCls}>CPF</label>
                      <input id="staff-cpf" className={inputCls} placeholder="000.000.000-00" {...register('cpf')} />
                      {errors.cpf && <span className="text-xs text-rose-500 font-semibold">{errors.cpf.message}</span>}
                    </div>
                    <div>
                      <label htmlFor="staff-birthDate" className={labelCls}>Data de nascimento</label>
                      <input id="staff-birthDate" type="date" className={inputCls} {...register('birthDate')} />
                      {errors.birthDate && <span className="text-xs text-rose-500 font-semibold">{errors.birthDate.message}</span>}
                    </div>
                    <div>
                      <label htmlFor="staff-gender" className={labelCls}>Gênero (opcional)</label>
                      <select id="staff-gender" className={inputCls} {...register('gender')}>
                        <option value="">Prefiro não informar</option>
                        <option value="FEMININO">Feminino</option>
                        <option value="MASCULINO">Masculino</option>
                        <option value="NAO_BINARIO">Não-binário</option>
                        <option value="OUTRO">Outro</option>
                        <option value="PREFIRO_NAO_INFORMAR">Prefiro não informar</option>
                      </select>
                    </div>
                  </div>

                  {/* Contato */}
                  <h4 className={sectionTitleCls}>
                    <UserIcon size={16} className="text-[#be8a83]" /> Contato
                  </h4>
                  <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                    <div>
                      <label htmlFor="staff-phone" className={labelCls}>Telefone</label>
                      <input id="staff-phone" className={inputCls} placeholder="(81) 99999-9999" {...register('phone')} />
                      {errors.phone && <span className="text-xs text-rose-500 font-semibold">{errors.phone.message}</span>}
                    </div>
                    <div />
                    <div>
                      <label htmlFor="staff-emergencyContactName" className={labelCls}>Contato de emergência (opcional)</label>
                      <input id="staff-emergencyContactName" maxLength={150} className={inputCls} {...register('emergencyContactName')} />
                    </div>
                    <div>
                      <label htmlFor="staff-emergencyContactPhone" className={labelCls}>Telefone de emergência (opcional)</label>
                      <input id="staff-emergencyContactPhone" className={inputCls} placeholder="(81) 99999-9999" {...register('emergencyContactPhone')} />
                      {errors.emergencyContactPhone && (
                        <span className="text-xs text-rose-500 font-semibold">{errors.emergencyContactPhone.message}</span>
                      )}
                    </div>
                  </div>

                  {/* Endereço */}
                  <h4 className={sectionTitleCls}>
                    <MapPin size={16} className="text-[#be8a83]" /> Endereço
                  </h4>
                  <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                    <div>
                      <label htmlFor="staff-zipCode" className={labelCls}>CEP</label>
                      <input
                        id="staff-zipCode"
                        className={inputCls}
                        placeholder="50000-000"
                        {...register('zipCode', {
                          onBlur: (e) => tryAutofillAddressFromCep(e.target.value, setValue),
                        })}
                      />
                      {errors.zipCode && <span className="text-xs text-rose-500 font-semibold">{errors.zipCode.message}</span>}
                    </div>
                    <div className="md:col-span-2">
                      <label htmlFor="staff-street" className={labelCls}>Logradouro</label>
                      <input id="staff-street" maxLength={200} className={inputCls} {...register('street')} />
                      {errors.street && <span className="text-xs text-rose-500 font-semibold">{errors.street.message}</span>}
                    </div>
                    <div>
                      <label htmlFor="staff-streetNumber" className={labelCls}>Número</label>
                      <input id="staff-streetNumber" maxLength={20} className={inputCls} {...register('streetNumber')} />
                      {errors.streetNumber && (
                        <span className="text-xs text-rose-500 font-semibold">{errors.streetNumber.message}</span>
                      )}
                    </div>
                    <div>
                      <label htmlFor="staff-complement" className={labelCls}>Complemento (opcional)</label>
                      <input id="staff-complement" maxLength={100} className={inputCls} {...register('complement')} />
                    </div>
                    <div>
                      <label htmlFor="staff-district" className={labelCls}>Bairro</label>
                      <input id="staff-district" maxLength={100} className={inputCls} {...register('district')} />
                      {errors.district && <span className="text-xs text-rose-500 font-semibold">{errors.district.message}</span>}
                    </div>
                    <div>
                      <label htmlFor="staff-city" className={labelCls}>Cidade</label>
                      <input id="staff-city" maxLength={100} className={inputCls} {...register('city')} />
                      {errors.city && <span className="text-xs text-rose-500 font-semibold">{errors.city.message}</span>}
                    </div>
                    <div>
                      <label htmlFor="staff-stateUf" className={labelCls}>UF</label>
                      <select id="staff-stateUf" className={inputCls} {...register('stateUf')}>
                        <option value="">Selecione</option>
                        {BRAZILIAN_STATES.map((uf) => (
                          <option key={uf} value={uf}>
                            {uf}
                          </option>
                        ))}
                      </select>
                      {errors.stateUf && <span className="text-xs text-rose-500 font-semibold">{errors.stateUf.message}</span>}
                    </div>
                  </div>

                  {/* PIX */}
                  <h4 className={sectionTitleCls}>
                    <Wallet size={16} className="text-[#be8a83]" /> Recebimento via PIX (opcional)
                  </h4>
                  <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                    <div>
                      <label htmlFor="staff-pixKeyType" className={labelCls}>Tipo de chave</label>
                      <select id="staff-pixKeyType" className={inputCls} {...register('pixKeyType')}>
                        <option value="">Sem chave PIX cadastrada</option>
                        <option value="CPF">CPF</option>
                        <option value="CNPJ">CNPJ</option>
                        <option value="EMAIL">Email</option>
                        <option value="TELEFONE">Telefone</option>
                        <option value="ALEATORIA">Chave aleatória</option>
                      </select>
                      {errors.pixKeyType && <span className="text-xs text-rose-500 font-semibold">{errors.pixKeyType.message}</span>}
                    </div>
                    <div>
                      <label htmlFor="staff-pixKey" className={labelCls}>Chave PIX</label>
                      <input id="staff-pixKey" maxLength={150} className={inputCls} {...register('pixKey')} />
                      {errors.pixKey && <span className="text-xs text-rose-500 font-semibold">{errors.pixKey.message}</span>}
                      <p className="text-xs text-gray-400 mt-1">
                        Cifrada no banco. Para pagar, um QR Code é gerado sem expor a chave.
                      </p>
                    </div>
                  </div>

                  {/* Remuneração — FUNCIONARIA e GERENTE_DE_ATENDIMENTO (gerente só Salário Fixo) */}
                  {(roleName === 'FUNCIONARIA' || roleName === 'GERENTE_DE_ATENDIMENTO') && (
                    <>
                      <h4 className={sectionTitleCls}>
                        <Landmark size={16} className="text-[#be8a83]" /> Remuneração
                      </h4>
                      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                        <div>
                          <label htmlFor="staff-remunerationType" className={labelCls}>Tipo de remuneração</label>
                          <select id="staff-remunerationType" className={inputCls} {...register('remunerationType')}>
                            <option value="">Selecione</option>
                            <option value="SALARIO_FIXO">Salário fixo</option>
                            {roleName === 'FUNCIONARIA' && (
                              <>
                                <option value="COMISSIONADO">Comissionado</option>
                                <option value="FIXO_E_COMISSIONADO">Fixo + comissão</option>
                              </>
                            )}
                          </select>
                          {roleName === 'GERENTE_DE_ATENDIMENTO' && (
                            <p className="text-xs text-gray-400 mt-1">
                              Gerente não presta serviço ao cliente, então só recebe salário fixo — sem comissão.
                            </p>
                          )}
                          {errors.remunerationType && (
                            <span className="text-xs text-rose-500 font-semibold">{errors.remunerationType.message}</span>
                          )}
                        </div>
                        {(remunerationType === 'SALARIO_FIXO' || remunerationType === 'FIXO_E_COMISSIONADO') && (
                          <div>
                            <label htmlFor="staff-remunerationValue" className={labelCls}>Valor do salário fixo (R$)</label>
                            <input id="staff-remunerationValue" type="number" step="0.01" min="0" className={inputCls} {...register('remunerationValue')} />
                            {errors.remunerationValue && (
                              <span className="text-xs text-rose-500 font-semibold">{errors.remunerationValue.message}</span>
                            )}
                          </div>
                        )}
                        {roleName === 'FUNCIONARIA' && isCommissioned && (
                          <p className="text-xs text-gray-400 md:col-span-2">
                            A comissão de serviço não é cadastrada aqui — é o % configurado em
                            cada serviço do catálogo (tela de Serviços). A comissão sobre produtos
                            vendidos usa a porcentagem única do salão (Perfil do Salão) — vale até
                            pra Salário Fixo, como incentivo de venda.
                          </p>
                        )}
                      </div>
                    </>
                  )}

                  {/* Metadados */}
                  <h4 className={sectionTitleCls}>
                    <FileText size={16} className="text-[#be8a83]" /> Outras informações
                  </h4>
                  <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                    <div>
                      <label htmlFor="staff-hiredAt" className={labelCls}>Data de admissão (opcional)</label>
                      <input id="staff-hiredAt" type="date" className={inputCls} {...register('hiredAt')} />
                    </div>
                    <div className="md:col-span-2">
                      <label htmlFor="staff-notes" className={labelCls}>Observações (opcional)</label>
                      <textarea id="staff-notes" rows={2} maxLength={2000} className={inputCls} {...register('notes')} />
                    </div>
                  </div>
                </div>

                <div className="flex justify-between gap-3 px-6 py-4 border-t border-[#eae1e1] bg-[#fcf9f9]/50 shrink-0">
                  <button
                    type="button"
                    onClick={() => setStep(1)}
                    className="flex items-center gap-2 px-5 py-2.5 border border-[#eae1e1] font-semibold text-sm text-[#3b3036] hover:bg-white hover:border-[#be8a83]/50 rounded-xl transition-all"
                  >
                    <ArrowLeft size={16} /> Trocar papel
                  </button>
                  <div className="flex gap-3">
                    <button
                      type="button"
                      onClick={closeModal}
                      className="px-5 py-2.5 border border-[#eae1e1] font-semibold text-sm text-[#3b3036] hover:bg-white hover:border-[#be8a83]/50 rounded-xl transition-all"
                    >
                      Cancelar
                    </button>
                    <button
                      type="submit"
                      disabled={isSubmitting}
                      className="flex items-center gap-2 px-5 py-2.5 bg-[#be8a83] text-white hover:bg-[#a6726b] font-semibold text-sm rounded-xl transition-all shadow-md shadow-[#be8a83]/10 disabled:opacity-50"
                    >
                      {isSubmitting ? 'Salvando...' : 'Criar Cadastro'} <ArrowRight size={16} />
                    </button>
                  </div>
                </div>
              </form>
            )}
          </div>
        </div>
      )}

      {detailTarget && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-[#261f23]/40 backdrop-blur-md">
          <div className="bg-white rounded-2xl shadow-xl w-full max-w-md border border-[#eae1e1]/85 overflow-hidden animate-scale-up max-h-[90vh] flex flex-col">
            <div className="flex items-center justify-between px-6 py-4 border-b border-[#eae1e1] bg-[#fcf9f9]/50 shrink-0">
              <h3 className="font-heading text-lg font-bold text-[#3b3036]">{detailTarget.displayName}</h3>
              <button
                onClick={closeDetailModal}
                className="p-1.5 text-gray-400 hover:text-gray-600 hover:bg-gray-100 rounded-lg transition-all cursor-pointer"
              >
                <X size={20} />
              </button>
            </div>
            <div className="p-6 space-y-4 text-sm overflow-y-auto">
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <span className={labelCls}>Papel</span>
                  <p className="text-[#3b3036]">{ROLE_LABELS[detailTarget.roleName] || detailTarget.roleName}</p>
                </div>
                <div>
                  <span className={labelCls}>CPF</span>
                  <p className="text-[#3b3036]">{detailTarget.cpfMasked}</p>
                </div>
                <div>
                  <span className={labelCls}>Email</span>
                  <p className="text-[#3b3036]">{detailTarget.email}</p>
                </div>
                <div>
                  <span className={labelCls}>Telefone</span>
                  <p className="text-[#3b3036]">{detailTarget.phone}</p>
                </div>
                <div className="col-span-2">
                  <span className={labelCls}>Endereço</span>
                  <p className="text-[#3b3036]">
                    {detailTarget.street}, {detailTarget.streetNumber} — {detailTarget.district},{' '}
                    {detailTarget.city}/{detailTarget.stateUf}
                  </p>
                </div>
                <div className="col-span-2">
                  <span className={labelCls}>Chave PIX</span>
                  <p className="text-[#3b3036]">
                    {detailTarget.hasPixKey
                      ? `${detailTarget.pixKeyType}: ${detailTarget.pixKeyMasked}`
                      : 'Não cadastrada'}
                  </p>
                </div>
              </div>

              {detailTarget.hasPixKey && (
                <div className="border border-[#eae1e1] rounded-xl p-4 space-y-3">
                  <h4 className="flex items-center gap-2 font-heading text-sm font-bold text-[#3b3036]">
                    <QrCode size={16} className="text-[#be8a83]" /> Pagar via PIX
                  </h4>
                  <p className="text-xs text-gray-400">
                    Gera um QR Code para pagamento. A chave PIX não é exibida em nenhum momento
                    — escaneie com o app do banco.
                  </p>
                  {pixQrCode ? (
                    <div className="flex flex-col items-center gap-3 py-2">
                      <QRCodeSVG value={pixQrCode.brCodePayload} size={180} level="M" includeMargin />
                      <p className="text-sm font-semibold text-[#3b3036]">
                        R$ {pixQrCode.amount.toFixed(2)} — {pixQrCode.recipientName}
                      </p>
                      <button
                        type="button"
                        onClick={() => setPixQrCode(null)}
                        className="text-xs text-[#be8a83] hover:underline cursor-pointer"
                      >
                        Gerar outro valor
                      </button>
                    </div>
                  ) : (
                    <div className="flex gap-2">
                      <input
                        type="number"
                        step="0.01"
                        min="0.01"
                        placeholder="Valor (R$)"
                        value={pixAmount}
                        onChange={(e) => setPixAmount(e.target.value)}
                        className={inputCls}
                      />
                      <button
                        type="button"
                        onClick={handleGeneratePix}
                        disabled={!pixAmount || isGeneratingPix}
                        className="shrink-0 px-4 py-2.5 bg-[#be8a83] text-white hover:bg-[#a6726b] font-semibold text-xs rounded-xl transition-all disabled:opacity-50 cursor-pointer"
                      >
                        {isGeneratingPix ? 'Gerando...' : 'Gerar QR Code'}
                      </button>
                    </div>
                  )}
                </div>
              )}
            </div>
            <div className="flex justify-end px-6 py-4 border-t border-[#eae1e1] bg-[#fcf9f9]/50 shrink-0">
              <button
                onClick={closeDetailModal}
                className="px-5 py-2.5 border border-[#eae1e1] font-semibold text-sm text-[#3b3036] hover:bg-white hover:border-[#be8a83]/50 rounded-xl transition-all cursor-pointer"
              >
                Fechar
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  );
};
