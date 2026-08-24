import { describe, it, expect, vi } from 'vitest';
import { usePermission } from '../usePermission';
import { useAuth } from '../useAuth';

vi.mock('../useAuth', () => ({
  useAuth: vi.fn(),
}));

describe('usePermission', () => {
  it('should return false if user is null', () => {
    vi.mocked(useAuth).mockReturnValue({
      user: null,
      isAuthenticated: false,
      login: vi.fn(),
      logout: vi.fn(),
      updateUserCpf: vi.fn(),
      isLoading: false,
    });

    const result = usePermission('GET', '/v1/users');
    expect(result).toBe(false);
  });

  it('should return true if user role is ADMIN', () => {
    vi.mocked(useAuth).mockReturnValue({
      user: {
        email: 'admin@salao.com',
        role: 'ADMIN',
        userId: 1,
        permissions: [],
      },
      isAuthenticated: true,
      login: vi.fn(),
      logout: vi.fn(),
      updateUserCpf: vi.fn(),
      isLoading: false,
    });

    const result = usePermission('DELETE', '/v1/users/123');
    expect(result).toBe(true);
  });

  it('should return true if user role is SYSADMIN', () => {
    vi.mocked(useAuth).mockReturnValue({
      user: {
        email: 'sysadmin@salao.com',
        role: 'SYSADMIN',
        userId: 1,
        permissions: [],
      },
      isAuthenticated: true,
      login: vi.fn(),
      logout: vi.fn(),
      updateUserCpf: vi.fn(),
      isLoading: false,
    });

    const result = usePermission('POST', '/v1/salon/profile');
    expect(result).toBe(true);
  });

  it('should return true if requestAuthority matches exactly', () => {
    vi.mocked(useAuth).mockReturnValue({
      user: {
        email: 'user@salao.com',
        role: 'CLIENTE',
        userId: 2,
        permissions: ['GET:/v1/users'],
      },
      isAuthenticated: true,
      login: vi.fn(),
      logout: vi.fn(),
      updateUserCpf: vi.fn(),
      isLoading: false,
    });

    const result = usePermission('GET', '/v1/users');
    expect(result).toBe(true);
  });

  it('should return false if requestAuthority does not match', () => {
    vi.mocked(useAuth).mockReturnValue({
      user: {
        email: 'user@salao.com',
        role: 'CLIENTE',
        userId: 2,
        permissions: ['GET:/v1/users'],
      },
      isAuthenticated: true,
      login: vi.fn(),
      logout: vi.fn(),
      updateUserCpf: vi.fn(),
      isLoading: false,
    });

    const result = usePermission('POST', '/v1/users');
    expect(result).toBe(false);
  });

  it('should support wildcard methods', () => {
    vi.mocked(useAuth).mockReturnValue({
      user: {
        email: 'user@salao.com',
        role: 'GERENTE_DE_ATENDIMENTO',
        userId: 3,
        permissions: ['*:/v1/users'],
      },
      isAuthenticated: true,
      login: vi.fn(),
      logout: vi.fn(),
      updateUserCpf: vi.fn(),
      isLoading: false,
    });

    expect(usePermission('GET', '/v1/users')).toBe(true);
    expect(usePermission('POST', '/v1/users')).toBe(true);
  });

  it('should support wildcard endpoints with /*', () => {
    vi.mocked(useAuth).mockReturnValue({
      user: {
        email: 'user@salao.com',
        role: 'CLIENTE',
        userId: 2,
        permissions: ['GET:/v1/users/*'],
      },
      isAuthenticated: true,
      login: vi.fn(),
      logout: vi.fn(),
      updateUserCpf: vi.fn(),
      isLoading: false,
    });

    expect(usePermission('GET', '/v1/users/123')).toBe(true);
    expect(usePermission('GET', '/v1/users/abc/details')).toBe(true);
    expect(usePermission('GET', '/v1/other')).toBe(false);
  });

  it('should return false for malformed authority strings', () => {
    vi.mocked(useAuth).mockReturnValue({
      user: {
        email: 'user@salao.com',
        role: 'CLIENTE',
        userId: 2,
        permissions: ['GET'],
      },
      isAuthenticated: true,
      login: vi.fn(),
      logout: vi.fn(),
      updateUserCpf: vi.fn(),
      isLoading: false,
    });

    const result = usePermission('GET', '/v1/users');
    expect(result).toBe(false);
  });
});
