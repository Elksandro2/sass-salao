import { useAuth } from './useAuth';

export const usePermission = (method: string, endpoint: string) => {
  const { user } = useAuth();

  if (!user) return false;
  // Espelha o bypass do backend (VerifyUserPermissions) — SYSADMIN e ADMIN têm acesso a tudo,
  // inclusive endpoints sem nenhuma linha de permissão explícita (ver migrations V34/V36/V42).
  if (user.role === 'ADMIN' || user.role === 'SYSADMIN') return true;

  const requestAuthority = `${method.toUpperCase()}:${endpoint}`;

  return user.permissions.some((authority: string) => {
    if (authority === requestAuthority) return true;

    // Support for wildcard endpoints like GET:/v1/users/*
    const authParts = authority.split(':');
    if (authParts.length !== 2) return false;

    const [authMethod, authEndpoint] = authParts;

    if (authMethod !== '*' && authMethod !== method.toUpperCase()) return false;

    // Trailing /* é um prefixo aberto (ex.: /v1/users/* casa /v1/users/5/qualquer-coisa).
    if (authEndpoint.endsWith('/*')) {
      const baseEndpoint = authEndpoint.replace('/*', '');
      return endpoint.startsWith(baseEndpoint);
    }

    // Curinga no meio do caminho (ex.: /v1/clients/*/anamnesis) — cada "*" casa exatamente um
    // segmento, igual ao AntPathMatcher usado no backend (CustomPermissionEvaluator).
    if (authEndpoint.includes('*')) {
      const pattern =
        '^' +
        authEndpoint
          .split('/')
          .map((segment) => (segment === '*' ? '[^/]+' : segment.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')))
          .join('/') +
        '$';
      return new RegExp(pattern).test(endpoint);
    }

    return authEndpoint === endpoint;
  });
};
