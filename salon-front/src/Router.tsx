import { useState, useEffect } from 'react';
import { Routes, Route, Navigate } from 'react-router-dom';

import { DefaultLayout } from './layouts/DefaultLayout';
import { AdminLayout } from './layouts/AdminLayout';
import { CustomerLayout } from './layouts/CustomerLayout';
import { SysadminLayout } from './layouts/SysadminLayout';
import { FeatureFlags } from './pages/sysadmin/FeatureFlags';
import { Rbac } from './pages/sysadmin/Rbac';
import { AiConfig } from './pages/sysadmin/AiConfig';
import { ProtectedRoute } from './components/ProtectedRoute';
import { Login } from './pages/auth/Login';
import { Register } from './pages/auth/Register';
import { ForgotPassword } from './pages/auth/ForgotPassword';
import { ResetPassword } from './pages/auth/ResetPassword';
import { AdminServices } from './pages/admin/services/AdminServices';
import { Products } from './pages/admin/products/Products';
import { Team } from './pages/admin/team/Team';
import { Clients } from './pages/admin/clients/Clients';
import { PublicServices } from './pages/services/PublicServices';
import { PublicAppointment } from './pages/appointments/PublicAppointment';
import { MyAppointments } from './pages/appointments/MyAppointments';
import { AdminAppointments } from './pages/admin/appointments/AdminAppointments';
import { CashFlow } from './pages/admin/cashflow/CashFlow';
import { FixedExpenses } from './pages/admin/fixed-expenses/FixedExpenses';
import { GeneralNotes } from './pages/admin/general-notes/GeneralNotes';
import { Recommendations } from './pages/admin/recommendations/Recommendations';
import { EmailOutbox } from './pages/admin/email-outbox/EmailOutbox';
import { SalonProfile } from './pages/admin/salon-profile/SalonProfile';
import { Reports } from './pages/admin/reports/Reports';
import { AuditLog } from './pages/admin/audit/AuditLog';
import { NotFound } from './pages/error/NotFound';
import { Profile } from './pages/profile/Profile';
import { featureFlagsService } from './services/featureFlags';
import { useAuth } from './hooks/useAuth';
import { usePushNotification } from './hooks/usePushNotification';
import { getDefaultAdminPath } from './config/adminNav';

// Redireciona "/admin" para a primeira seção que o cargo do usuário logado pode acessar
// (ex.: FUNCIONARIA cai em /admin/appointments, não em /admin/reports, que ela não vê).
const AdminIndexRedirect = () => {
  const { user } = useAuth();
  return <Navigate to={getDefaultAdminPath(user?.role)} replace />;
};

export const Router = () => {
  const [isPortalEnabled, setIsPortalEnabled] = useState<boolean | null>(null);
  const { isAuthenticated } = useAuth();
  usePushNotification(isAuthenticated);

  useEffect(() => {
    const checkFlags = async () => {
      try {
        const flags = await featureFlagsService.getPublicFlags();
        const portalFlag = flags.find((f) => f.name === 'ENABLE_CUSTOMER_PORTAL');
        // Por segurança, se a flag não existir ainda, assumimos desativada (conforme valor padrão da migration)
        setIsPortalEnabled(portalFlag ? portalFlag.enabled : false);
      } catch (error) {
        console.error('Erro ao carregar feature flags:', error);
        // Em caso de falha de conexão com a API, assume-se ativa para não bloquear se houver falha de rede temporária
        setIsPortalEnabled(true);
      }
    };
    checkFlags();
  }, []);

  if (isPortalEnabled === null) {
    return (
      <div className="flex justify-center items-center h-screen bg-[#fcf9f9] dark:bg-[#0b0f17]">
        <div className="animate-spin rounded-full h-10 w-10 border-t-2 border-b-2 border-[#be8a83]"></div>
      </div>
    );
  }

  return (
    <Routes>
      {/* Redirecionamentos para rotas administrativas intuitivas */}
      <Route path="/admin" element={<AdminIndexRedirect />} />
      <Route path="/sysadmin" element={<Navigate to="/sysadmin/feature-flags" replace />} />

      {/* Nesta versão o portal do cliente é pouco usado — a raiz vai direto pro login.
          As rotas do portal continuam existindo (link direto, feature flag) pra não perder
          a funcionalidade, só não são mais a porta de entrada padrão. */}
      <Route path="/" element={<Navigate to="/login" replace />} />

      {/* Portal do Cliente - Condicional à Feature Flag */}
      {!isPortalEnabled ? (
        <>
          <Route path="/services" element={<Navigate to="/login" replace />} />
          <Route path="/appointment" element={<Navigate to="/login" replace />} />
          <Route path="/my-appointments" element={<Navigate to="/login" replace />} />
          <Route path="/profile" element={<Navigate to="/login" replace />} />
        </>
      ) : (
        <>
          <Route element={<DefaultLayout />}>
            <Route path="/services" element={<PublicServices />} />
            <Route path="/appointment" element={<PublicAppointment />} />
          </Route>

          <Route element={<CustomerLayout />}>
            <Route
              path="/my-appointments"
              element={
                <ProtectedRoute>
                  <MyAppointments />
                </ProtectedRoute>
              }
            />
            <Route
              path="/profile"
              element={
                <ProtectedRoute>
                  <Profile />
                </ProtectedRoute>
              }
            />
          </Route>
        </>
      )}

      <Route path="/login" element={<Login />} />
      <Route path="/register" element={<Register />} />
      <Route path="/forgot-password" element={<ForgotPassword />} />
      <Route path="/reset-password" element={<ResetPassword />} />

      <Route element={<AdminLayout />}>
        <Route path="/admin/dashboard" element={<AdminIndexRedirect />} />
        <Route
          path="/admin/clients"
          element={
            <ProtectedRoute allowedRoles={['ADMIN', 'GERENTE_DE_ATENDIMENTO']}>
              <Clients />
            </ProtectedRoute>
          }
        />
        <Route
          path="/admin/team"
          element={
            <ProtectedRoute allowedRoles={['ADMIN', 'GERENTE_DE_ATENDIMENTO']}>
              <Team />
            </ProtectedRoute>
          }
        />
        {/* Rotas antigas (bookmarks, links salvos) redirecionam pra aba única nova */}
        <Route path="/admin/users" element={<Navigate to="/admin/team" replace />} />
        <Route path="/admin/employees" element={<Navigate to="/admin/team" replace />} />
        <Route path="/admin/staff" element={<Navigate to="/admin/team" replace />} />
        <Route
          path="/admin/services"
          element={
            <ProtectedRoute allowedRoles={['ADMIN', 'GERENTE_DE_ATENDIMENTO']}>
              <AdminServices />
            </ProtectedRoute>
          }
        />
        <Route
          path="/admin/products"
          element={
            <ProtectedRoute allowedRoles={['ADMIN']}>
              <Products />
            </ProtectedRoute>
          }
        />
        <Route
          path="/admin/appointments"
          element={
            <ProtectedRoute allowedRoles={['ADMIN', 'GERENTE_DE_ATENDIMENTO', 'FUNCIONARIA']}>
              <AdminAppointments />
            </ProtectedRoute>
          }
        />
        <Route
          path="/admin/cashflow"
          element={
            <ProtectedRoute allowedRoles={['ADMIN', 'GERENTE_DE_ATENDIMENTO']}>
              <CashFlow />
            </ProtectedRoute>
          }
        />
        <Route
          path="/admin/fixed-expenses"
          element={
            <ProtectedRoute allowedRoles={['ADMIN', 'GERENTE_DE_ATENDIMENTO']}>
              <FixedExpenses />
            </ProtectedRoute>
          }
        />
        <Route
          path="/admin/general-notes"
          element={
            <ProtectedRoute allowedRoles={['ADMIN', 'GERENTE_DE_ATENDIMENTO']}>
              <GeneralNotes />
            </ProtectedRoute>
          }
        />
        <Route
          path="/admin/reports"
          element={
            <ProtectedRoute allowedRoles={['ADMIN', 'GERENTE_DE_ATENDIMENTO']}>
              <Reports />
            </ProtectedRoute>
          }
        />
        <Route
          path="/admin/recommendations"
          element={
            <ProtectedRoute allowedRoles={['ADMIN', 'GERENTE_DE_ATENDIMENTO']}>
              <Recommendations />
            </ProtectedRoute>
          }
        />
        <Route
          path="/admin/email-outbox"
          element={
            <ProtectedRoute allowedRoles={['ADMIN', 'GERENTE_DE_ATENDIMENTO']}>
              <EmailOutbox />
            </ProtectedRoute>
          }
        />
        <Route
          path="/admin/salon-profile"
          element={
            <ProtectedRoute allowedRoles={['ADMIN']}>
              <SalonProfile />
            </ProtectedRoute>
          }
        />
      </Route>

      <Route element={<SysadminLayout />}>
        <Route
          path="/sysadmin/feature-flags"
          element={
            <ProtectedRoute allowedRoles={['SYSADMIN']}>
              <FeatureFlags />
            </ProtectedRoute>
          }
        />
        <Route
          path="/sysadmin/audit"
          element={
            <ProtectedRoute allowedRoles={['SYSADMIN']}>
              <AuditLog />
            </ProtectedRoute>
          }
        />
        <Route
          path="/sysadmin/rbac"
          element={
            <ProtectedRoute allowedRoles={['SYSADMIN']}>
              <Rbac />
            </ProtectedRoute>
          }
        />
        <Route
          path="/sysadmin/ai-config"
          element={
            <ProtectedRoute allowedRoles={['SYSADMIN']}>
              <AiConfig />
            </ProtectedRoute>
          }
        />
      </Route>

      {/* Catch-all para 404 */}
      <Route path="*" element={<NotFound />} />
    </Routes>
  );
};
