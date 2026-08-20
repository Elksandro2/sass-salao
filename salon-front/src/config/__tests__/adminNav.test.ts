import { describe, it, expect } from 'vitest';
import {
  ADMIN_NAV_ITEMS,
  canAccessAdminNavItem,
  getVisibleAdminNavItems,
  getDefaultAdminPath,
} from '../adminNav';

describe('adminNav', () => {
  it('SYSADMIN can access every nav item regardless of allowedRoles', () => {
    ADMIN_NAV_ITEMS.forEach((item) => {
      expect(canAccessAdminNavItem(item, 'SYSADMIN')).toBe(true);
    });
  });

  it('FUNCIONARIA only sees the Agendamentos and Meu Perfil items', () => {
    const visible = getVisibleAdminNavItems('FUNCIONARIA');
    expect(visible.map((i) => i.to)).toEqual(['/admin/appointments', '/admin/profile']);
  });

  it('ADMIN sees every configured nav item', () => {
    expect(getVisibleAdminNavItems('ADMIN')).toHaveLength(ADMIN_NAV_ITEMS.length);
  });

  it('GERENTE_DE_ATENDIMENTO sees everything except Produtos and Perfil do Salão', () => {
    const visible = getVisibleAdminNavItems('GERENTE_DE_ATENDIMENTO');
    expect(visible.map((i) => i.to)).not.toContain('/admin/products');
    expect(visible.map((i) => i.to)).not.toContain('/admin/salon-profile');
    expect(visible).toHaveLength(ADMIN_NAV_ITEMS.length - 2);
  });

  it('returns no items for a role without admin access', () => {
    expect(getVisibleAdminNavItems('CLIENTE')).toHaveLength(0);
    expect(getVisibleAdminNavItems(null)).toHaveLength(0);
  });

  it('defaults FUNCIONARIA to /admin/appointments instead of the unreachable /admin/reports', () => {
    expect(getDefaultAdminPath('FUNCIONARIA')).toBe('/admin/appointments');
  });

  it('defaults ADMIN, GERENTE_DE_ATENDIMENTO and SYSADMIN to /admin/reports', () => {
    expect(getDefaultAdminPath('ADMIN')).toBe('/admin/reports');
    expect(getDefaultAdminPath('GERENTE_DE_ATENDIMENTO')).toBe('/admin/reports');
    expect(getDefaultAdminPath('SYSADMIN')).toBe('/admin/reports');
  });

  it('falls back to "/" for roles with no admin nav access at all', () => {
    expect(getDefaultAdminPath('CLIENTE')).toBe('/');
    expect(getDefaultAdminPath(null)).toBe('/');
    expect(getDefaultAdminPath(undefined)).toBe('/');
  });
});
