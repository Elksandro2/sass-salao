import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import api from '../api';

const requestInterceptor = (api.interceptors.request as any).handlers[0];
const responseInterceptor = (api.interceptors.response as any).handlers[0];

describe('api interceptors', () => {
  const originalAdapter = api.defaults.adapter;

  beforeEach(() => {
    vi.spyOn(window, 'dispatchEvent');
    localStorage.clear();
  });

  afterEach(() => {
    api.defaults.adapter = originalAdapter;
    vi.restoreAllMocks();
  });

  describe('request interceptor', () => {
    it('should add Authorization header if token exists and not an auth/refresh request', () => {
      localStorage.setItem('@Salon:token', 'test-token');
      const config = { headers: {}, url: '/users' };
      const result = requestInterceptor.fulfilled(config);
      expect(result.headers.Authorization).toBe('Bearer test-token');
    });

    it('should not add Authorization header if request is for auth', () => {
      localStorage.setItem('@Salon:token', 'test-token');
      const config = { headers: {}, url: '/auth/login' };
      const result = requestInterceptor.fulfilled(config);
      expect(result.headers.Authorization).toBeUndefined();
    });

    it('should reject with same error on request error', async () => {
      const error = new Error('request error');
      await expect(requestInterceptor.rejected(error)).rejects.toThrow('request error');
    });
  });

  describe('response interceptor', () => {
    it('should reject with same error if status is not 401 or 403', async () => {
      const mockError = {
        response: { status: 500 },
        config: {},
      };

      await expect(responseInterceptor.rejected(mockError)).rejects.toEqual(mockError);
    });

    it('should NOT clear the session nor dispatch auth:logout on 403 (permission denied, not an invalid session)', async () => {
      localStorage.setItem('@Salon:token', 'test-token');
      const mockError = {
        response: { status: 403 },
        config: { url: '/appointments' },
      };

      await expect(responseInterceptor.rejected(mockError)).rejects.toEqual(mockError);
      // Um 403 significa "autenticado, mas sem permissão pra essa ação" — não invalida a sessão.
      // FUNCIONARIA (permissões restritas) batendo em endpoints sem permissão não pode ser
      // deslogada por isso, senão qualquer ação bloqueada a expulsa do sistema.
      expect(localStorage.getItem('@Salon:token')).toBe('test-token');
      expect(window.dispatchEvent).not.toHaveBeenCalledWith(
        expect.objectContaining({ type: 'auth:logout' })
      );
    });

    it('should clear localStorage and dispatch auth:logout Event Bus on 401 if no refresh token exists', async () => {
      localStorage.setItem('@Salon:token', 'test-token');
      const mockError = {
        response: { status: 401 },
        config: { _retry: false, url: '/users' },
      };

      await expect(responseInterceptor.rejected(mockError)).rejects.toEqual(mockError);
      expect(localStorage.getItem('@Salon:token')).toBeNull();
      // O Event Bus 'auth:logout' deve ser disparado em vez de hard reload
      expect(window.dispatchEvent).toHaveBeenCalledWith(
        expect.objectContaining({ type: 'auth:logout' })
      );
    });

    it('should request new token and retry request on 401 success', async () => {
      console.log('--- START TEST 401 success ---');
      localStorage.setItem('@Salon:refreshToken', 'valid-refresh-token');

      let requestCount = 0;
      const mockAdapter = vi.fn().mockImplementation(async (config) => {
        console.log('mockAdapter called with url:', config.url, 'requestCount:', requestCount);
        if (config.url?.includes('/auth/refresh')) {
          console.log('mockAdapter resolving /auth/refresh');
          return {
            data: {
              accessToken: 'new-access-token',
              refreshToken: 'new-refresh-token',
            },
            status: 200,
            statusText: 'OK',
            headers: {},
            config,
          };
        }

        if (config.url === '/users') {
          requestCount++;
          if (requestCount === 1) {
            console.log('mockAdapter throwing 401 for /users');
            throw {
              response: { status: 401 },
              config,
            };
          }
          console.log('mockAdapter resolving /users with 200');
          return {
            data: 'users-data',
            status: 200,
            statusText: 'OK',
            headers: {},
            config,
          };
        }

        return { data: {}, status: 200, headers: {}, config };
      });

      api.defaults.adapter = mockAdapter;

      console.log('Calling api.get(/users)');
      const response = await api.get('/users');
      console.log('api.get(/users) returned response:', response.data);

      expect(response.data).toBe('users-data');
      expect(localStorage.getItem('@Salon:token')).toBe('new-access-token');
      expect(localStorage.getItem('@Salon:refreshToken')).toBe('new-refresh-token');
      expect(mockAdapter).toHaveBeenCalledTimes(3); // /users (401) -> /auth/refresh -> /users (200)
    });

    it('should clear localStorage and dispatch auth:logout Event Bus if refresh fails', async () => {
      console.log('--- START TEST 401 failure ---');
      localStorage.setItem('@Salon:refreshToken', 'invalid-refresh-token');
      localStorage.setItem('@Salon:token', 'old-access-token');

      const mockAdapter = vi.fn().mockImplementation(async (config) => {
        console.log('mockAdapter called for failure test, url:', config.url);
        if (config.url?.includes('/auth/refresh')) {
          console.log('mockAdapter throwing 400 for /auth/refresh');
          throw {
            response: { status: 400 },
            config,
          };
        }

        if (config.url === '/users') {
          console.log('mockAdapter throwing 401 for /users');
          throw {
            response: { status: 401 },
            config,
          };
        }

        return { data: {}, status: 200, headers: {}, config };
      });

      api.defaults.adapter = mockAdapter;

      console.log('Calling api.get(/users) for failure test');
      await expect(api.get('/users')).rejects.toBeDefined();
      console.log('api.get(/users) rejected as expected');
      expect(localStorage.getItem('@Salon:token')).toBeNull();
      expect(localStorage.getItem('@Salon:refreshToken')).toBeNull();
      // O Event Bus 'auth:logout' deve ser disparado (em vez de hard reload)
      expect(window.dispatchEvent).toHaveBeenCalledWith(
        expect.objectContaining({ type: 'auth:logout' })
      );
    });

    it('should queue multiple requests and resolve them when token refresh succeeds', async () => {
      localStorage.setItem('@Salon:refreshToken', 'valid-refresh-token');

      const callCounts: Record<string, number> = { '/first': 0, '/second': 0 };
      let refreshStartedResolve: () => void = () => {};
      const refreshStartedPromise = new Promise<void>((resolve) => {
        refreshStartedResolve = resolve;
      });

      const mockAdapter = vi.fn().mockImplementation(async (config) => {
        if (config.url?.includes('/auth/refresh')) {
          refreshStartedResolve();
          await new Promise((resolve) => setTimeout(resolve, 50));
          return {
            data: {
              accessToken: 'new-access-token',
              refreshToken: 'new-refresh-token',
            },
            status: 200,
            statusText: 'OK',
            headers: {},
            config,
          };
        }

        if (config.url === '/first' || config.url === '/second') {
          const url = config.url;
          callCounts[url]++;
          if (callCounts[url] === 1) {
            throw {
              response: { status: 401 },
              config,
            };
          }
          return {
            data: `${url}-data`,
            status: 200,
            statusText: 'OK',
            headers: {},
            config,
          };
        }

        return { data: {}, status: 200, headers: {}, config };
      });

      api.defaults.adapter = mockAdapter;

      const firstPromise = api.get('/first');

      // Esperar deterministicamente o início do refresh (/auth/refresh)
      await refreshStartedPromise;

      const secondPromise = api.get('/second');

      const [res1, res2] = await Promise.all([firstPromise, secondPromise]);

      expect(res1.data).toBe('/first-data');
      expect(res2.data).toBe('/second-data');
      expect(localStorage.getItem('@Salon:token')).toBe('new-access-token');
    });

    it('should queue multiple requests and reject them when token refresh fails', async () => {
      localStorage.setItem('@Salon:refreshToken', 'invalid-refresh-token');

      let refreshStartedResolve: () => void = () => {};
      const refreshStartedPromise = new Promise<void>((resolve) => {
        refreshStartedResolve = resolve;
      });

      const mockAdapter = vi.fn().mockImplementation(async (config) => {
        if (config.url?.includes('/auth/refresh')) {
          refreshStartedResolve();
          await new Promise((resolve) => setTimeout(resolve, 50));
          throw {
            response: { status: 400 },
            config,
          };
        }

        if (config.url === '/first' || config.url === '/second') {
          throw {
            response: { status: 401 },
            config,
          };
        }

        return { data: {}, status: 200, headers: {}, config };
      });

      api.defaults.adapter = mockAdapter;

      const firstPromise = api.get('/first');

      await refreshStartedPromise;
      const secondPromise = api.get('/second');

      await expect(firstPromise).rejects.toBeDefined();
      await expect(secondPromise).rejects.toBeDefined();
    });
  });
});
