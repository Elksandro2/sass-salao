import { useState } from 'react';
import { Outlet, Navigate, Link, useLocation } from 'react-router-dom';
import { useAuth } from '../hooks/useAuth';
import { useFeatureFlag } from '../hooks/useFeatureFlag';
import { useTheme } from '../context/ThemeContext';
import { getVisibleAdminNavItems } from '../config/adminNav';
import { Menu, X, LogOut, ChevronLeft, ChevronRight, Sun, Moon } from 'lucide-react';

export const AdminLayout = () => {
  const { user, isAuthenticated, isLoading, logout } = useAuth();
  const [showSidebar, setShowSidebar] = useState(false);
  const [isCollapsed, setIsCollapsed] = useState(() => {
    return localStorage.getItem('salon_sidebar_collapsed') === 'true';
  });
  const location = useLocation();
  const { theme, toggleTheme } = useTheme();
  const { enabled: aiRecommendationsEnabled } = useFeatureFlag('ENABLE_AI_RECOMMENDATIONS');
  const { enabled: emailNotificationsEnabled } = useFeatureFlag('EMAIL_NOTIFICATIONS');

  if (isLoading)
    return (
      <div className="flex justify-center items-center h-screen bg-[#fcf9f9]">
        <div className="animate-spin rounded-full h-10 w-10 border-t-2 border-b-2 border-[#be8a83]"></div>
      </div>
    );

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  const allowedAdminLayoutRoles = ['ADMIN', 'GERENTE_DE_ATENDIMENTO', 'FUNCIONARIA', 'SYSADMIN'];
  if (!user?.role || !allowedAdminLayoutRoles.includes(user.role)) {
    return <Navigate to="/" replace />;
  }

  const toggleSidebar = () => setShowSidebar(!showSidebar);
  const closeSidebar = () => setShowSidebar(false);

  const toggleCollapse = () => {
    setIsCollapsed((prev) => {
      const next = !prev;
      localStorage.setItem('salon_sidebar_collapsed', String(next));
      return next;
    });
  };

  // "Recomendações de IA" e "Central de E-mails" só aparecem com a feature flag ligada — o
  // envio de e-mail (continuidade de negócio) está desligado nesta versão, sem sumir da
  // Central de Feature Flags do Sysadmin caso precise ser reativado depois.
  const menuItems = getVisibleAdminNavItems(user?.role).filter((item) => {
    if (item.to === '/admin/recommendations') return aiRecommendationsEnabled;
    if (item.to === '/admin/email-outbox') return emailNotificationsEnabled;
    return true;
  });

  const userName = user?.email ? user.email.split('@')[0] : 'Admin';

  return (
    <div className="min-h-screen bg-[#fcf9f9] flex flex-col md:flex-row">
      {/* Mobile Top Navbar */}
      <div className="md:hidden bg-white/90 backdrop-blur-md text-[#3b3036] flex justify-between items-center p-4 border-b border-[#eae1e1]/50 shadow-xs z-40 sticky top-0">
        <h5 className="m-0 font-heading font-semibold text-lg tracking-wide text-[#be8a83]">
          Admin Salão
        </h5>
        <div className="flex items-center gap-2">
          <button
            onClick={toggleTheme}
            className="p-1.5 text-[#3b3036] hover:text-[#be8a83] focus:outline-none transition-colors cursor-pointer"
            aria-label="Alternar tema"
          >
            {theme === 'light' ? <Moon size={20} /> : <Sun size={20} />}
          </button>
          <button
            onClick={toggleSidebar}
            className="p-1.5 text-[#3b3036] hover:text-[#be8a83] focus:outline-none transition-colors"
            aria-label="Toggle navigation"
          >
            {showSidebar ? <X size={24} /> : <Menu size={24} />}
          </button>
        </div>
      </div>

      {/* Overlay when sidebar is open on mobile */}
      {showSidebar && (
        <div
          className="fixed inset-0 bg-black/30 backdrop-blur-xs z-40 md:hidden transition-opacity"
          onClick={closeSidebar}
        />
      )}

      {/* Sidebar */}
      <aside
        className={`bg-white/80 backdrop-blur-md border-r border-[#eae1e1]/50 text-[#3b3036] flex flex-col fixed md:sticky top-0 h-screen z-50 transition-all duration-300 md:translate-x-0 ${
          showSidebar ? 'translate-x-0' : '-translate-x-full'
        } ${isCollapsed ? 'md:w-20' : 'md:w-[260px]'}`}
      >
        <div
          className={`flex items-center px-4 py-5 border-b border-[#eae1e1]/50 h-[73px] bg-white/50 gap-2 ${isCollapsed ? 'justify-center' : 'justify-between'}`}
        >
          <div className="flex items-center gap-2">
            <button
              onClick={toggleCollapse}
              className="hidden md:flex p-1.5 hover:bg-[#be8a83]/10 text-[#3b3036] rounded-full transition-all duration-200 cursor-pointer"
              aria-label="Toggle sidebar"
            >
              {isCollapsed ? <ChevronRight size={18} /> : <ChevronLeft size={18} />}
            </button>
            {!isCollapsed && (
              <h4 className="font-heading font-bold text-lg tracking-wide text-[#3b3036] transition-all duration-300 whitespace-nowrap">
                Admin <span className="text-[#be8a83]">Salão</span>
              </h4>
            )}
          </div>
          <button
            className="md:hidden text-[#3b3036]/60 hover:text-[#3b3036] focus:outline-none"
            onClick={closeSidebar}
            aria-label="Close sidebar"
          >
            <X size={20} />
          </button>
        </div>

        <nav className="flex-1 px-3 py-6 space-y-1 overflow-y-auto">
          {menuItems.map((item) => {
            const isActive = location.pathname === item.to;
            const Icon = item.icon;
            return (
              <Link
                key={item.to}
                to={item.to}
                onClick={closeSidebar}
                className={`flex items-center rounded-full transition-all duration-200 group text-sm font-medium relative ${
                  isCollapsed ? 'justify-center p-3 mx-1' : 'gap-3 px-5 py-3 mx-2'
                } ${
                  isActive
                    ? 'bg-[#be8a83]/12 text-[#be8a83] font-semibold'
                    : 'text-[#3b3036]/75 hover:text-[#be8a83] hover:bg-[#be8a83]/5'
                }`}
              >
                <Icon
                  size={18}
                  className={
                    isActive
                      ? 'text-[#be8a83]'
                      : 'text-[#3b3036]/50 group-hover:text-[#be8a83] transition-colors'
                  }
                />
                {!isCollapsed && <span>{item.label}</span>}

                {isCollapsed && (
                  <div className="absolute left-full ml-4 px-3 py-1.5 bg-[#3b3036] text-white text-xs font-semibold rounded-lg shadow-lg border border-[#eae1e1]/20 opacity-0 invisible group-hover:opacity-100 group-hover:visible transition-all duration-200 z-50 whitespace-nowrap">
                    {item.label}
                  </div>
                )}
              </Link>
            );
          })}

          <button
            onClick={() => {
              logout();
              closeSidebar();
            }}
            className={`w-[calc(100%-16px)] flex items-center rounded-full transition-all duration-200 text-sm font-bold mt-8 text-left relative ${
              isCollapsed ? 'justify-center p-3 mx-1' : 'gap-3 px-5 py-3 mx-2'
            } text-[#3b3036]/75 hover:text-white bg-[#3b3036]/5 hover:bg-[#be8a83] border border-[#3b3036]/10 hover:border-[#be8a83] shadow-xs`}
          >
            <LogOut
              size={18}
              className="text-[#3b3036]/60 group-hover:text-white transition-colors"
            />
            {!isCollapsed && <span>Sair</span>}

            {isCollapsed && (
              <div className="absolute left-full ml-4 px-3 py-1.5 bg-[#3b3036] text-white text-xs font-semibold rounded-lg shadow-lg border border-[#eae1e1]/20 opacity-0 invisible group-hover:opacity-100 group-hover:visible transition-all duration-200 z-50 whitespace-nowrap">
                Sair
              </div>
            )}
          </button>
        </nav>

        {!isCollapsed && (
          <div className="shrink-0 px-3 py-3 border-t border-[#eae1e1]/50 text-center">
            <span className="text-[10px] text-[#3b3036]/40">
              Sistema desenvolvido por <span className="text-[#be8a83]">Elksandro</span>
            </span>
          </div>
        )}
      </aside>

      {/* Main Content Area with Desktop Header */}
      <div className="flex-1 flex flex-col min-w-0 overflow-hidden">
        {/* Desktop Header */}
        <header className="hidden md:flex justify-between items-center px-8 py-4 bg-white/70 backdrop-blur-md border-b border-[#eae1e1]/50 z-30 h-[73px]">
          <div className="flex items-center gap-4">
            <span className="text-xs font-semibold bg-[#be8a83]/10 text-[#be8a83] px-2.5 py-1 rounded-full uppercase tracking-wider">
              {user?.role === 'SYSADMIN'
                ? 'Sysadmin'
                : user?.role === 'ADMIN'
                  ? 'Administrador'
                  : user?.role === 'FUNCIONARIA'
                    ? 'Colaboradora'
                    : 'Gerente'}
            </span>
          </div>
          <div className="flex items-center gap-4">
            <button
              onClick={toggleTheme}
              className="p-2 text-[#3b3036]/70 hover:text-[#be8a83] hover:bg-[#be8a83]/5 rounded-xl transition-all duration-200 cursor-pointer flex items-center justify-center"
              aria-label="Alternar tema"
            >
              {theme === 'light' ? <Moon size={18} /> : <Sun size={18} />}
            </button>
            <div className="text-right">
              <div className="text-sm font-semibold text-[#2a2528] capitalize">{userName}</div>
              <div className="text-xs text-[#7a7074]">{user?.email}</div>
            </div>
            <div className="h-10 w-10 rounded-full bg-gradient-to-tr from-[#be8a83] to-[#e5a49c] flex items-center justify-center text-white font-bold shadow-sm uppercase">
              {userName.charAt(0)}
            </div>
          </div>
        </header>

        <main className="flex-1 p-6 md:p-8 overflow-y-auto max-w-full">
          <Outlet />
        </main>
      </div>
    </div>
  );
};
