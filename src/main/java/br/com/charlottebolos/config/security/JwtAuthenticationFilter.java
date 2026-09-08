package br.com.charlottebolos.config.security;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Le o token do cabecalho {@code Authorization} e popula o
 * {@code SecurityContext}.
 *
 * <p>O filtro nunca rejeita uma requisicao por conta propria: quando nao ha
 * token, ou o token e invalido, ele simplesmente segue a cadeia sem autenticar.
 * Quem responde e a autorizacao mais adiante — o entry point configurado no
 * {@code SecurityConfig} devolve 401. Concentrar a recusa num lugar so evita
 * duas fontes de resposta divergentes.
 *
 * <p><strong>Por que o try/catch importa.</strong> A leitura do token lanca
 * {@link JwtException} para token expirado, malformado ou com assinatura
 * invalida. Sem captura, a excecao sobe de dentro do filtro — e o
 * {@code GlobalExceptionHandler} nao a alcanca, porque {@code @RestControllerAdvice}
 * so cobre o que acontece dentro do {@code DispatcherServlet}, e o filtro roda
 * antes dele. O resultado seria HTTP 500 para um token expirado, deixando o
 * cliente sem saber que bastava renovar a sessao.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String header = request.getHeader(AUTHORIZATION_HEADER);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(BEARER_PREFIX.length());
        try {
            authenticate(token, request);
        } catch (JwtException | UsernameNotFoundException e) {
            // Token invalido ou usuario removido depois da emissao. Segue sem
            // autenticar; o entry point responde 401.
            SecurityContextHolder.clearContext();
            log.debug("Token rejeitado: {}", e.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    private void authenticate(String token, HttpServletRequest request) {
        String email = jwtService.extractUsername(token);
        if (email == null || SecurityContextHolder.getContext().getAuthentication() != null) {
            return;
        }

        UserDetails user = userDetailsService.loadUserByUsername(email);
        if (!jwtService.isTokenValid(token, user)) {
            return;
        }

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
