import { describe, it, expect, beforeAll } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { AdminLayout } from '../AdminLayout';
import { AuthContext } from '../../context/AuthContext';
import { ThemeProvider } from '../../context/ThemeContext';
import type { UserContextData } from '../../types/auth';

// ThemeProvider consulta window.matchMedia (preferência de tema do SO) — não implementado no jsdom.
beforeAll(() => {
  Object.defineProperty(window, 'matchMedia', {
    writable: true,
    value: (query: string) => ({
      matches: false,
      media: query,
      onchange: null,
      addListener: () => {},
      removeListener: () => {},
      addEventListener: () => {},
      removeEventListener: () => {},
      dispatchEvent: () => false,
    }),
  });
});

const renderAdminLayout = (user: UserContextData | null, initialRoute = '/admin/appointments') =>
  render(
    <ThemeProvider>
      <AuthContext.Provider
        value={{
          user,
          isAuthenticated: !!user,
          login: async () => {},
          logout: () => {},
          updateUserCpf: () => {},
          isLoading: false,
        }}
      >
        <MemoryRouter initialEntries={[initialRoute]}>
          <Routes>
            <Route element={<AdminLayout />}>
              <Route path="/admin/reports" element={<div>Reports Page</div>} />
              <Route path="/admin/appointments" element={<div>Appointments Page</div>} />
            </Route>
          </Routes>
        </MemoryRouter>
      </AuthContext.Provider>
    </ThemeProvider>
  );

describe('AdminLayout', () => {
  it('does not render admin content for an unauthenticated visitor', () => {
    renderAdminLayout(null);
    expect(screen.queryByText('Appointments Page')).not.toBeInTheDocument();
  });

  it('blocks a CLIENTE from the admin layout', () => {
    renderAdminLayout({ email: 'cliente@x.com', role: 'CLIENTE', userId: 1, permissions: [] });
    expect(screen.queryByText('Appointments Page')).not.toBeInTheDocument();
  });

  it('allows FUNCIONARIA into the admin layout and only shows the Agendamentos nav item', () => {
    renderAdminLayout({ email: 'func@x.com', role: 'FUNCIONARIA', userId: 2, permissions: [] });

    expect(screen.getByText('Appointments Page')).toBeInTheDocument();
    expect(screen.getByText('Agendamentos')).toBeInTheDocument();
    expect(screen.queryByText('Relatórios')).not.toBeInTheDocument();
    expect(screen.queryByText('Fluxo de Caixa')).not.toBeInTheDocument();
    expect(screen.queryByText('Produtos')).not.toBeInTheDocument();
  });

  it('shows the "Colaboradora" role label in the header for a FUNCIONARIA user', () => {
    renderAdminLayout({ email: 'func@x.com', role: 'FUNCIONARIA', userId: 2, permissions: [] });
    expect(screen.getByText('Colaboradora')).toBeInTheDocument();
  });

  it('shows the full menu for ADMIN, including Produtos and Fluxo de Caixa', () => {
    renderAdminLayout(
      { email: 'admin@x.com', role: 'ADMIN', userId: 3, permissions: [] },
      '/admin/reports'
    );

    expect(screen.getByText('Reports Page')).toBeInTheDocument();
    expect(screen.getByText('Relatórios')).toBeInTheDocument();
    expect(screen.getByText('Fluxo de Caixa')).toBeInTheDocument();
    expect(screen.getByText('Produtos')).toBeInTheDocument();
  });

  it('hides Produtos from GERENTE_DE_ATENDIMENTO while keeping other management items', () => {
    renderAdminLayout(
      { email: 'gerente@x.com', role: 'GERENTE_DE_ATENDIMENTO', userId: 4, permissions: [] },
      '/admin/reports'
    );

    expect(screen.getByText('Reports Page')).toBeInTheDocument();
    expect(screen.queryByText('Produtos')).not.toBeInTheDocument();
    expect(screen.getByText('Fluxo de Caixa')).toBeInTheDocument();
  });
});
