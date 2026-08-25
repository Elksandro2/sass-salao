package com.cristiane.salon.security;

import com.cristiane.salon.exception.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;

/**
 * Sem isso, a ausência de um {@link AuthenticationEntryPoint} explícito faz o Spring Security
 * cair no padrão {@code Http403ForbiddenEntryPoint} — toda requisição sem autenticação válida
 * (token expirado, assinatura inválida, usuário do token não existe mais) vira 403, idêntico
 * ao 403 de "autenticado mas sem permissão". O frontend só tenta renovar o token (refresh) em
 * 401 — com tudo virando 403, a renovação nunca dispara e a sessão trava com "Forbidden" em
 * tudo até a pessoa deslogar e logar de novo manualmente (mesmo o access token durando só 15min,
 * isso acontecia toda vez que ele expirava durante o uso).
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException
    ) throws IOException {
        ErrorResponse error = new ErrorResponse(
                Instant.now(),
                HttpStatus.UNAUTHORIZED.value(),
                "Não Autorizado",
                "Sessão inválida ou expirada. Faça login novamente.",
                request.getRequestURI()
        );

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(error));
    }
}
