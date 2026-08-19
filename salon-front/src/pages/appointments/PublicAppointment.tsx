import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Clock,
  User as UserIcon,
  Calendar,
  CheckCircle,
  ArrowLeft,
  ArrowRight,
  MessageSquare,
  CalendarHeart,
  AlertCircle,
  Scissors,
} from 'lucide-react';
import { salonServicesApi } from '../services/services/services';
import type { SalonServiceData } from '../services/services/services';
import { employeesApi } from '../admin/employees/services/employees';
import type { EmployeeData } from '../admin/employees/services/employees';
import { appointmentsApi } from './services/appointments';
import { useAuth } from '../../hooks/useAuth';
import { getApiErrorMessage } from '../../utils/apiError';
import { featureFlagsService } from '../../services/featureFlags';
import { salonProfileService, DAY_LABELS } from '../../services/salonProfile';
import type { DayOfWeek } from '../../services/salonProfile';

const JS_DAY_TO_DAY_OF_WEEK: DayOfWeek[] = [
  'SUNDAY', 'MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY',
];

function dateStringToDayOfWeek(dateStr: string): DayOfWeek {
  // Meio-dia evita o clássico bug de fuso horário ao converter "YYYY-MM-DD" pra Date.
  return JS_DAY_TO_DAY_OF_WEEK[new Date(`${dateStr}T12:00:00`).getDay()];
}

function priceTagLabel(price: number | null | undefined): string | null {
  if (price == null || Number.isNaN(price)) return null;
  return `A partir de R$ ${price.toFixed(2)}`;
}

function localTodayIso(): string {
  const n = new Date();
  const y = n.getFullYear();
  const m = String(n.getMonth() + 1).padStart(2, '0');
  const d = String(n.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
}

export const PublicAppointment = () => {
  const [step, setStep] = useState(1);
  const [services, setServices] = useState<SalonServiceData[]>([]);
  const [employees, setEmployees] = useState<EmployeeData[]>([]);

  const [selectedServiceIds, setSelectedServiceIds] = useState<number[]>([]);
  const [selectedEmployee, setSelectedEmployee] = useState<number | null>(null);
  const [preferredDate, setPreferredDate] = useState<string>('');
  const [clientNotes, setClientNotes] = useState<string>('');

  const [isLoading, setIsLoading] = useState(false);
  const [isInitialLoading, setIsInitialLoading] = useState(true);
  const [errorMsg, setErrorMsg] = useState('');
  const [isBookingEnabled, setIsBookingEnabled] = useState(true);
  const [closedDays, setClosedDays] = useState<Set<DayOfWeek>>(new Set());

  const { isAuthenticated } = useAuth();
  const navigate = useNavigate();

  useEffect(() => {
    const raw = localStorage.getItem('pending_appointment');
    if (raw) {
      try {
        const p = JSON.parse(raw) as {
          serviceIds?: number[];
          employeeId?: number;
          preferredDate?: string;
          clientNotes?: string;
        };
        if (p.serviceIds && p.serviceIds.length > 0) setSelectedServiceIds(p.serviceIds);
        if (p.employeeId) setSelectedEmployee(p.employeeId);
        if (p.preferredDate) setPreferredDate(p.preferredDate);
        if (p.clientNotes) setClientNotes(p.clientNotes);
        if (p.serviceIds?.length && p.employeeId) setStep(4);
        else if (p.serviceIds?.length) setStep(2);
      } catch {
        /* ignore */
      }
    }
  }, []);

  useEffect(() => {
    const fetchInitialData = async () => {
      try {
        const [servicesData, employeesData, flagsData, salonProfile] = await Promise.all([
          salonServicesApi.findAll({ active: true }, 0, 1000),
          employeesApi.findAllForBooking(),
          featureFlagsService.getPublicFlags().catch(() => [] as any[]),
          // Best-effort: se essa chamada falhar, o wizard segue sem bloquear dia nenhum (fail-open).
          salonProfileService.getPublic().catch(() => null),
        ]);
        setServices(servicesData.content);
        setEmployees(employeesData);
        const bookingFlag = flagsData.find((f) => f.name === 'CLIENT_BOOKING');
        if (bookingFlag && !bookingFlag.enabled) setIsBookingEnabled(false);
        if (salonProfile) {
          setClosedDays(new Set(salonProfile.businessHours.filter((bh) => !bh.open).map((bh) => bh.dayOfWeek)));
        }
      } catch (error) {
        const msg = getApiErrorMessage(
          error,
          'Não foi possível carregar serviços ou profissionais.'
        );
        setErrorMsg(msg);
      } finally {
        setIsInitialLoading(false);
      }
    };
    fetchInitialData();
  }, []);

  const toggleService = (serviceId: number) => {
    setSelectedServiceIds((prev) =>
      prev.includes(serviceId) ? prev.filter((id) => id !== serviceId) : [...prev, serviceId]
    );
  };

  const handleNext = () => {
    if (step === 1 && selectedServiceIds.length === 0) return;
    if (step === 2 && !selectedEmployee) return;
    if (step === 3) {
      if (preferredDate && preferredDate < localTodayIso()) {
        setErrorMsg('A data de preferência deve ser hoje ou uma data futura.');
        return;
      }
      if (preferredDate && closedDays.has(dateStringToDayOfWeek(preferredDate))) {
        setErrorMsg(
          `O salão está fechado em ${DAY_LABELS[dateStringToDayOfWeek(preferredDate)]}. Escolha outra data de preferência.`
        );
        return;
      }
      setErrorMsg('');
    }
    setStep(step + 1);
    window.scrollTo(0, 0);
  };

  const handleBack = () => {
    setStep(step - 1);
    window.scrollTo(0, 0);
  };

  const handleSubmit = async () => {
    if (!isAuthenticated) {
      localStorage.setItem(
        'pending_appointment',
        JSON.stringify({
          serviceIds: selectedServiceIds,
          employeeId: selectedEmployee,
          preferredDate: preferredDate || undefined,
          clientNotes: clientNotes || undefined,
        })
      );
      navigate('/login');
      return;
    }
    setIsLoading(true);
    setErrorMsg('');
    try {
      await appointmentsApi.create({
        services: selectedServiceIds.map((serviceId) => ({ serviceId })),
        employeeId: selectedEmployee!,
        preferredDate: preferredDate || undefined,
        clientNotes: clientNotes.trim() || undefined,
      });
      localStorage.removeItem('pending_appointment');
      navigate('/my-appointments');
    } catch (error) {
      const msg = getApiErrorMessage(error, 'Erro ao enviar solicitação.');
      setErrorMsg(msg);
    } finally {
      setIsLoading(false);
    }
  };

  if (isInitialLoading) {
    return (
      <div className="flex justify-center items-center min-h-[60vh]">
        <div className="animate-spin rounded-full h-10 w-10 border-t-2 border-b-2 border-[#be8a83]"></div>
      </div>
    );
  }

  if (!isBookingEnabled) {
    return (
      <div className="max-w-lg mx-auto text-center py-16 px-4">
        <div className="bg-white rounded-2xl border border-gray-100 shadow-xs p-10">
          <CalendarHeart size={56} className="mx-auto text-gray-400 mb-4" />
          <h3 className="font-heading text-xl font-bold text-[#3b3036] mb-2">
            Agendamentos Online Desativados
          </h3>
          <p className="text-sm text-[#3b3036]/60 mb-6 leading-relaxed">
            Os agendamentos online para clientes estão temporariamente desativados. Por favor, entre
            em contato direto com o salão para agendar seu horário.
          </p>
          <button
            className="px-6 py-2.5 bg-[#be8a83] text-white hover:bg-[#a6726b] font-semibold text-sm rounded-full transition-all"
            onClick={() => navigate('/')}
          >
            Voltar para o início
          </button>
        </div>
      </div>
    );
  }

  const steps = [
    { n: 1, label: 'Serviço', icon: <Clock size={16} /> },
    { n: 2, label: 'Profissional', icon: <UserIcon size={16} /> },
    { n: 3, label: 'Preferências', icon: <Calendar size={16} /> },
    { n: 4, label: 'Enviar', icon: <CheckCircle size={16} /> },
  ];

  const selectedServices = services.filter((s) => selectedServiceIds.includes(s.id!));
  const totalPrice = selectedServices.some((s) => s.price == null)
    ? null
    : selectedServices.reduce((sum, s) => sum + (s.price ?? 0), 0);
  const priceLabel = priceTagLabel(totalPrice);

  return (
    <div className="max-w-4xl mx-auto px-4 py-8 space-y-8 animate-fadeIn">
      {/* Header */}
      <div className="text-center">
        <h2 className="font-heading text-3xl font-bold text-[#3b3036] tracking-tight">
          Solicitar horário
        </h2>
        <p className="text-sm text-[#3b3036]/60 mt-1">
          Monte seu pedido abaixo. O salão confirma data e horário e avisa você por aqui.
        </p>
      </div>

      {!isAuthenticated && (
        <div className="p-4 bg-amber-50/80 backdrop-blur-md border border-amber-200 rounded-2xl text-amber-800 text-sm flex flex-col sm:flex-row items-center gap-3 shadow-xs">
          <div className="flex items-center gap-2.5 flex-1">
            <AlertCircle size={20} className="shrink-0 text-amber-600" />
            <span>
              <span className="font-bold">Atenção:</span> Você não está conectado. Você pode montar
              sua solicitação, mas precisará fazer login ou criar uma conta para finalizar.
            </span>
          </div>
          <button
            onClick={() => navigate('/login')}
            className="w-full sm:w-auto px-4 py-1.5 bg-amber-600 hover:bg-amber-700 text-white text-xs font-bold rounded-full transition-all shrink-0 cursor-pointer"
          >
            Entrar / Cadastrar
          </button>
        </div>
      )}

      {/* Stepper */}
      <div className="relative flex justify-between">
        <div className="absolute top-5 left-0 right-0 h-0.5 bg-gray-200 z-0" />
        {steps.map((s) => (
          <div key={s.n} className="relative z-10 flex flex-col items-center flex-1">
            <div
              className={`h-10 w-10 rounded-full flex items-center justify-center font-bold text-sm border-2 transition-all duration-300 ${
                step > s.n
                  ? 'bg-[#3b3036] border-[#3b3036] text-white'
                  : step === s.n
                    ? 'bg-[#be8a83] border-[#be8a83] text-white shadow-lg shadow-[#be8a83]/20'
                    : 'bg-white border-gray-200 text-gray-400'
              }`}
            >
              {step > s.n ? <CheckCircle size={18} /> : s.n}
            </div>
            <div
              className={`text-xs font-semibold mt-1.5 transition-colors ${step === s.n ? 'text-[#be8a83]' : step > s.n ? 'text-[#3b3036]' : 'text-gray-400'}`}
            >
              {s.label}
            </div>
          </div>
        ))}
      </div>

      {/* Content */}
      <div className="bg-white rounded-2xl border border-gray-100 p-6 md:p-8 shadow-xs">
        {errorMsg && (
          <div className="mb-6 p-4 bg-rose-50 border border-rose-100 rounded-xl text-rose-700 text-sm flex items-start gap-2.5">
            <AlertCircle size={18} className="shrink-0 mt-0.5" />
            <span>{errorMsg}</span>
          </div>
        )}

        {/* Step 1: Service */}
        {step === 1 && (
          <div className="space-y-4">
            <h4 className="font-heading text-lg font-bold text-center text-[#3b3036]">
              O que vamos fazer?
            </h4>
            <p className="text-xs text-[#3b3036]/60 text-center -mt-2">
              Você pode selecionar mais de um serviço.
            </p>
            {services.length === 0 ? (
              <div className="text-center py-12 px-4 bg-gray-50/50 rounded-2xl border border-dashed border-gray-200 max-w-md mx-auto">
                <Scissors size={36} className="mx-auto text-gray-300 mb-3" />
                <h5 className="font-bold text-[#3b3036] text-base mb-1">
                  Nenhum serviço disponível
                </h5>
                <p className="text-xs text-[#3b3036]/60 leading-relaxed">
                  Não existem serviços ativos cadastrados para agendamento no momento. Por favor,
                  tente novamente mais tarde.
                </p>
              </div>
            ) : (
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                {services.map((srv) => {
                  const tag = priceTagLabel(srv.price);
                  return (
                    <div
                      key={srv.id}
                      onClick={() => toggleService(srv.id!)}
                      className={`relative cursor-pointer p-5 rounded-2xl border-2 transition-all duration-200 ${
                        selectedServiceIds.includes(srv.id!)
                          ? 'border-[#be8a83] bg-[#be8a83]/5 shadow-md shadow-[#be8a83]/10'
                          : 'border-gray-100 bg-white hover:border-[#be8a83]/50'
                      }`}
                    >
                      {tag && (
                        <span className="inline-flex mb-2 px-2.5 py-1 bg-[#be8a83] text-white text-xs font-bold rounded-full">
                          {tag}
                        </span>
                      )}
                      <h5 className="font-bold text-[#3b3036] mb-1">{srv.name}</h5>
                      <p className="text-xs text-[#3b3036]/60 mb-3 leading-relaxed">
                        {srv.description || 'Tratamento especializado para você.'}
                      </p>
                      {selectedServiceIds.includes(srv.id!) && (
                        <CheckCircle size={20} className="absolute top-3 right-3 text-[#be8a83]" />
                      )}
                    </div>
                  );
                })}
              </div>
            )}
          </div>
        )}

        {/* Step 2: Employee */}
        {step === 2 && (
          <div className="space-y-4">
            <h4 className="font-heading text-lg font-bold text-center text-[#3b3036]">
              Com quem você prefere?
            </h4>
            {employees.length === 0 ? (
              <div className="text-center py-12 px-4 bg-gray-50/50 rounded-2xl border border-dashed border-gray-200 max-w-md mx-auto">
                <UserIcon size={36} className="mx-auto text-gray-300 mb-3" />
                <h5 className="font-bold text-[#3b3036] text-base mb-1">
                  Nenhum profissional disponível
                </h5>
                <p className="text-xs text-[#3b3036]/60 leading-relaxed">
                  Não há profissionais cadastrados ou disponíveis para agendamento online neste
                  momento. Por favor, tente novamente mais tarde.
                </p>
              </div>
            ) : (
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                {employees.map((emp) => (
                  <div
                    key={emp.id}
                    onClick={() => setSelectedEmployee(emp.id!)}
                    className={`relative cursor-pointer p-5 rounded-2xl border-2 transition-all duration-200 flex items-center gap-4 ${
                      selectedEmployee === emp.id
                        ? 'border-[#be8a83] bg-[#be8a83]/5 shadow-md shadow-[#be8a83]/10'
                        : 'border-gray-100 bg-white hover:border-[#be8a83]/50'
                    }`}
                  >
                    <div className="h-12 w-12 rounded-full bg-gradient-to-br from-[#be8a83] to-[#3b3036] text-white flex items-center justify-center font-bold text-xl shrink-0">
                      {(emp.name ?? 'P').charAt(0)}
                    </div>
                    <div className="flex-1 min-w-0">
                      <h5 className="font-bold text-[#3b3036] mb-0.5">{emp.name}</h5>
                    </div>
                    {selectedEmployee === emp.id && (
                      <CheckCircle size={20} className="shrink-0 text-[#be8a83]" />
                    )}
                  </div>
                ))}
              </div>
            )}
          </div>
        )}

        {/* Step 3: Preferences */}
        {step === 3 && (
          <div className="max-w-lg mx-auto space-y-6">
            <div className="text-center">
              <h4 className="font-heading text-lg font-bold text-[#3b3036]">
                Preferência de dia e observações
              </h4>
              <p className="text-xs text-[#3b3036]/60 mt-1">
                O horário exato será combinado pela equipe. Indique um dia de preferência, se
                quiser.
              </p>
            </div>
            <div className="space-y-1.5">
              <label
                htmlFor="preferred-date"
                className="flex items-center gap-2 text-xs font-semibold text-[#3b3036]/70 uppercase tracking-wider"
              >
                <Calendar size={16} /> Dia de preferência (opcional)
              </label>
              <input
                id="preferred-date"
                type="date"
                min={localTodayIso()}
                value={preferredDate}
                onChange={(e) => setPreferredDate(e.target.value)}
                className="w-full text-sm px-4 py-2.5 bg-gray-50 border border-gray-200 rounded-xl focus:bg-white focus:ring-2 focus:ring-[#be8a83]/20 focus:border-[#be8a83] outline-none transition-all"
              />
              {closedDays.size > 0 && (
                <p className="text-xs text-[#3b3036]/50">
                  Fechado: {Array.from(closedDays).map((day) => DAY_LABELS[day]).join(', ')}
                </p>
              )}
            </div>
            <div className="space-y-1.5">
              <label
                htmlFor="client-notes"
                className="flex items-center gap-2 text-xs font-semibold text-[#3b3036]/70 uppercase tracking-wider"
              >
                <MessageSquare size={16} /> Observações (opcional)
              </label>
              <textarea
                id="client-notes"
                rows={4}
                maxLength={1000}
                placeholder="Ex.: só de manhã, comentários sobre o cabelo, etc."
                value={clientNotes}
                onChange={(e) => setClientNotes(e.target.value)}
                className="w-full text-sm px-4 py-2.5 bg-gray-50 border border-gray-200 rounded-xl focus:bg-white focus:ring-2 focus:ring-[#be8a83]/20 focus:border-[#be8a83] outline-none transition-all resize-none"
              />
            </div>
          </div>
        )}

        {/* Step 4: Summary */}
        {step === 4 && (
          <div className="max-w-lg mx-auto space-y-4">
            <h4 className="font-heading text-lg font-bold text-center text-[#3b3036]">
              Revisar pedido
            </h4>
            <div className="rounded-2xl overflow-hidden border border-gray-100 shadow-xs">
              <div className="bg-gradient-to-r from-[#3b3036] to-[#261f23] text-white px-6 py-5 text-center">
                <CheckCircle size={40} className="mx-auto mb-2 text-[#e5a49c]" />
                <h5 className="font-bold text-lg text-white">Resumo da solicitação</h5>
              </div>
              <div className="divide-y divide-gray-50 px-6">
                {[
                  { label: 'Serviços', value: selectedServices.map((s) => s.name).join(', ') },
                  {
                    label: 'Profissional',
                    value: employees.find((e) => e.id === selectedEmployee)?.name,
                  },
                  ...(preferredDate
                    ? [
                        {
                          label: 'Dia preferido',
                          value: new Date(preferredDate + 'T12:00:00').toLocaleDateString('pt-BR'),
                        },
                      ]
                    : []),
                  ...(clientNotes.trim()
                    ? [{ label: 'Observações', value: clientNotes.trim() }]
                    : []),
                  { label: 'Referência de valor', value: priceLabel || 'Definido no atendimento' },
                ].map((row, i) => (
                  <div key={i} className="py-3.5 flex flex-col gap-0.5">
                    <span className="text-xs font-bold uppercase tracking-widest text-gray-400">
                      {row.label}
                    </span>
                    <span className="text-sm font-semibold text-[#3b3036] whitespace-pre-wrap break-words">
                      {row.value}
                    </span>
                  </div>
                ))}
              </div>
            </div>

            {!isAuthenticated && (
              <div className="p-4 bg-amber-50 border border-amber-200 rounded-xl text-amber-700 text-sm flex items-start gap-2.5">
                <AlertCircle size={18} className="shrink-0 mt-0.5" />
                <span>Você precisará entrar na sua conta para enviar a solicitação.</span>
              </div>
            )}
          </div>
        )}

        {/* Navigation */}
        <div className="flex justify-between mt-8 pt-6 border-t border-gray-100">
          <button
            onClick={handleBack}
            disabled={step === 1 || isLoading}
            className="flex items-center gap-2 px-5 py-2.5 border border-gray-200 text-sm font-semibold text-[#3b3036] hover:bg-gray-50 disabled:opacity-40 disabled:pointer-events-none rounded-xl transition-all"
          >
            <ArrowLeft size={18} /> Voltar
          </button>

          {step < 4 ? (
            <button
              onClick={handleNext}
              disabled={(step === 1 && selectedServiceIds.length === 0) || (step === 2 && !selectedEmployee)}
              className="flex items-center gap-2 px-6 py-2.5 bg-[#be8a83] text-white hover:bg-[#a6726b] font-semibold text-sm rounded-xl transition-all disabled:opacity-40 disabled:pointer-events-none"
            >
              Próximo <ArrowRight size={18} />
            </button>
          ) : (
            <button
              onClick={handleSubmit}
              disabled={isLoading}
              className="flex items-center gap-2 px-6 py-2.5 bg-[#be8a83] text-white hover:bg-[#a6726b] font-semibold text-sm rounded-xl transition-all disabled:opacity-40 disabled:pointer-events-none"
            >
              {isLoading ? 'Enviando...' : 'Enviar solicitação'} <CheckCircle size={18} />
            </button>
          )}
        </div>
      </div>
    </div>
  );
};
