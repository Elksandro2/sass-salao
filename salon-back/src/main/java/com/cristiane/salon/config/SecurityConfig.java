package com.cristiane.salon.config;

import com.cristiane.salon.mcp.security.McpAuthenticationFilter;
import com.cristiane.salon.security.AuditRequestFilter;
import com.cristiane.salon.security.JwtAuthenticationEntryPoint;
import com.cristiane.salon.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final McpAuthenticationFilter mcpAuthFilter;
    private final AuditRequestFilter auditRequestFilter;
    private final AuthenticationProvider authenticationProvider;

    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtFilterRegistration(JwtAuthenticationFilter filter) {
        FilterRegistrationBean<JwtAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<McpAuthenticationFilter> mcpFilterRegistration(McpAuthenticationFilter filter) {
        FilterRegistrationBean<McpAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<AuditRequestFilter> auditFilterRegistration(AuditRequestFilter filter) {
        FilterRegistrationBean<AuditRequestFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> {}) // will be configured in CorsConfig
            .authorizeHttpRequests(auth -> auth
                // Public routes
                .requestMatchers("/ping").permitAll()
                .requestMatchers("/v1/auth/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/v1/services").permitAll()
                .requestMatchers(HttpMethod.GET, "/v1/products").permitAll()
                .requestMatchers(HttpMethod.GET, "/v1/employees/booking").permitAll()
                .requestMatchers(HttpMethod.GET, "/v1/feature-flags").permitAll()
                .requestMatchers(HttpMethod.GET, "/v1/salon/profile").permitAll()
                .requestMatchers(HttpMethod.POST, "/v1/webhooks/mercadopago").permitAll()
                // O Mercado Pago redireciona o NAVEGADOR da funcionária pra cá depois que ela
                // autoriza (fluxo OAuth de split) — não é uma chamada de API do frontend, então
                // não carrega o JWT da sessão (não é um axios com interceptor, é navegação de
                // topo). Quem autentica essa operação é o "state" de uso único, validado dentro
                // do EmployeeMercadoPagoConnectionService — não o Spring Security aqui.
                .requestMatchers(HttpMethod.GET, "/v1/employees/mercadopago/callback").permitAll()

                // Swagger & API Docs
                .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**", "/swagger-resources/**", "/webjars/**").permitAll()
                
                // Static resources & Frontend SPA routing
                .requestMatchers(HttpMethod.GET, "/", "/index.html", "/static/**", "/assets/**", "/*.png", "/*.ico", "/*.json", "/*.txt").permitAll()
                
                // API routes require authentication (except the permitted ones above)
                .requestMatchers("/v1/**").authenticated()

                // Servidor MCP: autenticado via McpAuthenticationFilter (Bearer token próprio,
                // gerado/revogado pela Central de IA) — nunca aberto por padrão como o resto
                // do que não é /v1/**.
                .requestMatchers("/sse", "/mcp/message").authenticated()

                // Any other request (mostly frontend React routes) is permitted so the SPA config can forward to index.html
                .anyRequest().permitAll()
            )
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(jwtAuthenticationEntryPoint))
            .authenticationProvider(authenticationProvider)
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(mcpAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(auditRequestFilter, JwtAuthenticationFilter.class);

        return http.build();
    }
}
