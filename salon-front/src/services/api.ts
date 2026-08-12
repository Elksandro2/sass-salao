import axios, { AxiosError } from 'axios';
import type { InternalAxiosRequestConfig } from 'axios';

interface CustomAxiosRequestConfig extends InternalAxiosRequestConfig {
  _isRefreshRequest?: boolean;
}

const api = axios.create({
  baseURL: import.meta.env.VITE_API_URL || 'http://localhost:8080/v1',
  headers: {
    'Content-Type': 'application/json',
  },
});

let isRefreshing = false;
type FailedPromise = {
  resolve: (value?: string | null) => void;
  reject: (reason?: Error | AxiosError) => void;
};

let failedQueue: FailedPromise[] = [];

const processQueue = (error: Error | AxiosError | null, token: string | null = null) => {
  failedQueue.forEach((prom) => {
    if (error) {
      prom.reject(error);
    } else {
      prom.resolve(token);
    }
  });
  failedQueue = [];
};

/**
 * Dispara um evento global 'auth:logout' que o AuthContext escuta para realizar
 * o logout e redirecionamento de forma React-friendly, sem hard reloads.
 * Apenas é disparado para requisições que não são de autenticação, evitando
 * conflitos com o próprio fluxo de login.
 */
const dispatchLogoutEvent = (config?: CustomAxiosRequestConfig) => {
  const url = config?.url || '';
  const isAuthEndpoint = url.includes('/auth/login') || url.includes('/auth/register');
  if (!isAuthEndpoint) {
    window.dispatchEvent(new CustomEvent('auth:logout'));
  }
};

api.interceptors.request.use(
  (config: CustomAxiosRequestConfig) => {
    // Pula injeção do token apenas para o próprio request de refresh e endpoints públicos de auth
    const isPublicAuthEndpoint =
      config.url?.includes('/auth/login') ||
      config.url?.includes('/auth/register') ||
      config.url?.includes('/auth/refresh');

    if (config._isRefreshRequest || isPublicAuthEndpoint) {
      return config;
    }

    const token = localStorage.getItem('@Salon:token');
    if (token && config.headers) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

api.interceptors.response.use(
  (response) => {
    return response;
  },
  async (error: AxiosError) => {
    const originalRequest = error.config as CustomAxiosRequestConfig & { _retry?: boolean };

    if (error.response?.status === 401 && !originalRequest._retry) {
      if (isRefreshing) {
        return new Promise(function (resolve, reject) {
          failedQueue.push({ resolve, reject });
        })
          .then((token) => {
            if (originalRequest.headers) {
              originalRequest.headers.Authorization = `Bearer ${token}`;
            }
            return api(originalRequest);
          })
          .catch((err) => {
            return Promise.reject(err);
          });
      }

      originalRequest._retry = true;
      isRefreshing = true;

      const refreshToken = localStorage.getItem('@Salon:refreshToken');

      if (!refreshToken) {
        isRefreshing = false;
        localStorage.removeItem('@Salon:token');
        localStorage.removeItem('@Salon:refreshToken');
        // React-friendly logout via Event Bus — sem hard reload
        dispatchLogoutEvent(originalRequest);
        return Promise.reject(error);
      }

      try {
        const { data } = await api.post(
          '/auth/refresh',
          {
            refreshToken,
          },
          {
            _isRefreshRequest: true,
          } as CustomAxiosRequestConfig
        );

        localStorage.setItem('@Salon:token', data.accessToken);
        localStorage.setItem('@Salon:refreshToken', data.refreshToken);

        if (originalRequest.headers) {
          originalRequest.headers.Authorization = `Bearer ${data.accessToken}`;
        }

        processQueue(null, data.accessToken);
        return api(originalRequest);
      } catch (err) {
        processQueue(err as Error | AxiosError, null);
        localStorage.removeItem('@Salon:token');
        localStorage.removeItem('@Salon:refreshToken');
        // React-friendly logout via Event Bus — sem hard reload
        dispatchLogoutEvent(originalRequest);
        return Promise.reject(err);
      } finally {
        isRefreshing = false;
      }
    }

    // 403 é "autenticado, mas sem permissão pra essa ação específica" (ex.: FUNCIONARIA
    // batendo num endpoint restrito a ADMIN) — não é sessão inválida. Derrubar a sessão aqui
    // deslogava qualquer usuário com permissões mais restritas a cada ação bloqueada, mesmo com
    // token válido. Só 401 (tratado acima) indica sessão realmente expirada/inválida.
    return Promise.reject(error);
  }
);

export default api;
