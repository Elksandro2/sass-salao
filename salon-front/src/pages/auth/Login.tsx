import { useState, useEffect } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { useNavigate, useLocation, Link } from 'react-router-dom';
import api from '../../services/api';
import { useAuth } from '../../hooks/useAuth';
import { useFeatureFlag } from '../../hooks/useFeatureFlag';
import { getApiErrorMessage } from '../../utils/apiError';
import { getDefaultAdminPath } from '../../config/adminNav';
import { AlertCircle, ArrowLeft, CheckCircle2, Eye, EyeOff } from 'lucide-react';
import { loginFormSchema } from './login.schema';
import type { LoginFormValues } from './login.schema';

export const Login = () => {
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<LoginFormValues>({ resolver: zodResolver(loginFormSchema) });
  const [errorMsg, setErrorMsg] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [showPassword, setShowPassword] = useState(false);
  const { login } = useAuth();
  const { enabled: selfRegistrationEnabled } = useFeatureFlag('ENABLE_SELF_REGISTRATION');
  const navigate = useNavigate();
  const location = useLocation();
  const passwordResetSuccess = Boolean((location.state as { passwordResetSuccess?: boolean } | null)?.passwordResetSuccess);

  const images = [
    '/images/salon1.png',
    '/images/salon2.png',
    '/images/salon3.png',
    '/images/salon4.jpg',
  ];
  const [currentImageIndex, setCurrentImageIndex] = useState(0);

  useEffect(() => {
    const interval = setInterval(() => {
      setCurrentImageIndex((prev) => (prev + 1) % images.length);
    }, 5000);
    return () => clearInterval(interval);
  }, []);

  const onSubmit = async (data: LoginFormValues, e?: React.BaseSyntheticEvent) => {
    e?.preventDefault();
    setIsLoading(true);
    setErrorMsg('');
    try {
      const response = await api.post('/auth/login', data);
      const { accessToken, refreshToken } = response.data;

      // 1. Decodificar o token ANTES de chamar login() para calcular a rota destino.
      //    Isso elimina o flicker causado por redirecionamentos assíncronos no AuthContext.
      let destination = '/';
      try {
        const { jwtDecode } = await import('jwt-decode');
        const decoded = jwtDecode<{ role?: string }>(accessToken);
        const role = decoded.role?.toUpperCase() ?? '';
        if (role === 'SYSADMIN') {
          destination = '/sysadmin/rbac';
        } else if (role === 'ADMIN' || role === 'GERENTE_DE_ATENDIMENTO' || role === 'FUNCIONARIA') {
          // Cada cargo administrativo cai na primeira seção que pode acessar
          // (ex.: FUNCIONARIA vai para /admin/appointments, não para /admin/reports).
          destination = getDefaultAdminPath(role);
        } else {
          // Cliente: verificar se havia um agendamento pendente
          const pending = localStorage.getItem('pending_appointment');
          destination = pending ? '/appointment' : '/my-appointments';
        }
      } catch {
        // Fallback seguro se o token não puder ser decodificado
        destination = '/';
      }

      // 2. Atualizar o contexto com os tokens (aguarda o /auth/me)
      await login(accessToken, refreshToken);

      // 3. Navegar para a rota calculada — sem flicker, sem hard reload
      navigate(destination, { replace: true });
    } catch (err: any) {
      if (err.response?.status === 401 || err.response?.status === 403) {
        setErrorMsg('E-mail ou senha incorretos.');
      } else {
        const msg = getApiErrorMessage(err, 'Erro ao realizar login. Tente novamente.');
        setErrorMsg(msg);
      }
    } finally {
      setIsLoading(false);
    }
  };


  return (
    <div className="min-h-screen w-full flex flex-col md:flex-row bg-white overflow-hidden">
      {/* Left Side: Carousel Slideshow of the business */}
      <div className="hidden md:flex md:w-1/2 h-screen relative flex-col justify-between p-12 text-[#fcf9f9] md:animate-slide-image-to-left overflow-hidden">
        {/* Background Images Cross-Fade */}
        {images.map((img, index) => (
          <div
            key={img}
            className={`absolute inset-0 bg-center bg-cover transition-opacity duration-1000 ${
              index === currentImageIndex ? 'opacity-100' : 'opacity-0'
            }`}
            style={{ backgroundImage: `url(${img})` }}
          />
        ))}

        {/* Véu quente em tom rose/plum da marca — realça o texto sem apagar a foto do salão */}
        <div className="absolute inset-0 bg-gradient-to-t from-[#3b3036]/85 via-[#3b3036]/25 to-[#3b3036]/10 z-10" />
        <div className="absolute inset-0 bg-gradient-to-br from-[#e5a49c]/15 via-transparent to-transparent z-10" />

        {/* Logo/Brand Title */}
        <div className="z-20">
          <Link
            to="/"
            className="inline-block font-heading text-2xl font-bold tracking-wider text-[#fcf9f9] px-4 py-2 rounded-full bg-[#3b3036]/40 backdrop-blur-md border border-[#fcf9f9]/10 hover:text-[#e5a49c] hover:border-[#e5a49c]/30 transition-colors shadow-lg"
          >
            ESPAÇO CRISTIANE MOURA
          </Link>
        </div>

        {/* Brand Tagline */}
        <div className="z-20 space-y-4 p-6 -mx-2 rounded-3xl bg-[#3b3036]/45 backdrop-blur-md border border-[#fcf9f9]/5 shadow-xl">
          <h1 className="font-heading text-4xl lg:text-5xl font-light leading-tight !text-[#fcf9f9]">
            Sua beleza refletida nos mínimos detalhes.
          </h1>
          <p className="text-[#fcf9f9]/90 text-sm max-w-md font-sans tracking-wide">
            Agende serviços de alta qualidade com nossas profissionais especializadas em um ambiente
            elegante e acolhedor.
          </p>
          <div className="flex gap-1.5 pt-1">
            {images.map((img, index) => (
              <span
                key={img}
                className={`h-1.5 rounded-full transition-all duration-500 ${
                  index === currentImageIndex ? 'w-6 bg-[#e5a49c]' : 'w-1.5 bg-[#fcf9f9]/40'
                }`}
              />
            ))}
          </div>
        </div>

        {/* Footer/Copyright inside image */}
        <div className="z-20 text-xs text-[#fcf9f9]/80">
          © {new Date().getFullYear()} Espaço Cristiane Moura. Todos os direitos reservados.
        </div>
      </div>

      {/* Right Side: Form */}
      <div className="w-full md:w-1/2 min-h-screen bg-white flex flex-col justify-center px-6 py-12 sm:px-16 lg:px-24 relative overflow-y-auto md:animate-slide-form-to-right">
        {/* Back Button */}
        <Link
          to="/"
          className="absolute top-6 left-6 sm:left-12 flex items-center gap-2 text-sm text-[#7a7074] hover:text-[#3b3036] font-semibold transition-colors group"
        >
          <ArrowLeft
            size={16}
            className="transform group-hover:-translate-x-1 transition-transform"
          />
          Voltar para o início
        </Link>

        <div className="w-full max-w-md mx-auto space-y-8">
          <div>
            <h2 className="font-heading text-3xl font-bold text-[#3b3036] tracking-tight">
              Bem-vinda de volta
            </h2>
            <p className="text-sm text-[#7a7074] mt-2">
              Faça login para gerenciar seus agendamentos e acessar sua conta.
            </p>
          </div>

          {passwordResetSuccess && (
            <div className="p-4 bg-emerald-50 border border-emerald-100 rounded-xl text-emerald-700 text-sm flex items-start gap-2.5 animate-fadeIn">
              <CheckCircle2 size={18} className="shrink-0 mt-0.5" />
              <span>Senha redefinida com sucesso! Faça login com sua nova senha.</span>
            </div>
          )}

          {errorMsg && (
            <div className="p-4 bg-rose-50 border border-rose-100 rounded-xl text-rose-700 text-sm flex items-start gap-2.5 animate-fadeIn">
              <AlertCircle size={18} className="shrink-0 mt-0.5" />
              <span>{errorMsg}</span>
            </div>
          )}

          <form onSubmit={handleSubmit(onSubmit)} className="space-y-6">
            <div className="space-y-1.5">
              <label className="label-premium">E-mail *</label>
              <input
                type="email"
                placeholder="seuemail@exemplo.com"
                {...register('email')}
                className={`input-premium ${
                  errors.email ? 'border-rose-300 focus:border-rose-500' : ''
                }`}
              />
              {errors.email && (
                <span className="text-xs text-rose-500 font-semibold">{errors.email.message}</span>
              )}
            </div>

            <div className="space-y-1.5">
              <div className="flex items-center justify-between">
                <label className="label-premium">Senha *</label>
                <Link to="/forgot-password" className="text-xs text-[#be8a83] font-semibold hover:underline">
                  Esqueci minha senha
                </Link>
              </div>
              <div className="relative">
                <input
                  type={showPassword ? 'text' : 'password'}
                  placeholder="Sua senha"
                  {...register('password')}
                  className={`input-premium pr-10 ${
                    errors.password ? 'border-rose-300 focus:border-rose-500' : ''
                  }`}
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
                <span className="text-xs text-rose-500 font-semibold">
                  {errors.password.message}
                </span>
              )}
            </div>

            <button
              type="submit"
              disabled={isLoading}
              className="w-full py-3 bg-[#be8a83] hover:bg-[#a1706a] text-[#fcf9f9] font-semibold rounded-xl text-sm transition-all shadow-md shadow-[#be8a83]/10 disabled:opacity-50 disabled:pointer-events-none cursor-pointer flex items-center justify-center gap-2"
            >
              {isLoading ? 'Acessando...' : 'Entrar na minha conta'}
            </button>

            {selfRegistrationEnabled && (
              <div className="text-center pt-4 text-sm">
                <span className="text-[#7a7074]">Não tem uma conta? </span>
                <Link to="/register" className="text-[#be8a83] font-semibold hover:underline">
                  Cadastre-se
                </Link>
              </div>
            )}
          </form>
        </div>
      </div>
    </div>
  );
};
