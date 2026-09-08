package br.com.charlottebolos.config.security;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Configuracao de seguranca da API.
 *
 * <p>Autenticacao por JWT: {@code POST /api/auth/login} confere a senha e emite
 * o token; o {@link JwtAuthenticationFilter} o le em cada requisicao seguinte e
 * popula o {@code SecurityContext}. Os usuarios vem do banco, pelo
 * {@code UserDetailsServiceImpl}.
 *
 * <p><strong>Stateless.</strong> {@link SessionCreationPolicy#STATELESS} impede
 * o Spring de criar {@code HttpSession}: cada requisicao carrega sua propria
 * credencial. E o que um cliente de token espera, e o que permite escalar a API
 * horizontalmente sem sessao compartilhada.
 *
 * <p><strong>CSRF desligado.</strong> A protecao contra CSRF existe para
 * credencial que o navegador anexa sozinho — cookie de sessao. Um token enviado
 * explicitamente no cabecalho {@code Authorization} nao e anexado
 * automaticamente, entao o ataque nao se aplica. Se um dia o refresh token
 * passar a viajar em cookie, esta linha precisa ser revista.
 *
 * <p><strong>Onde cada recusa e decidida.</strong> Vale conhecer a divisao,
 * porque as tres acontecem em camadas diferentes:
 *
 * <ul>
 *   <li>Token ausente ou invalido — o entry point desta classe, 401.</li>
 *   <li>Senha errada ou usuario inativo — {@code GlobalExceptionHandler}, 401 e
 *       403, ja dentro do {@code DispatcherServlet}.</li>
 *   <li>Autenticado sem permissao — {@code @PreAuthorize}, tambem via
 *       {@code GlobalExceptionHandler}, 403.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    /**
     * Prefixo reservado para login e refresh de token. Liberado desde ja: sem
     * isso, o futuro endpoint de autenticacao exigiria estar autenticado para
     * autenticar-se.
     */
    private static final String AUTH_PATH = "/api/auth/**";

    /**
     * Le o token e popula o {@code SecurityContext}. Injetado por
     * {@code @RequiredArgsConstructor} — a anotacao, antes inerte, passa a ter
     * funcao com este campo.
     */
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    /**
     * Origens autorizadas a fazer requisicao cross-origin, lidas de
     * {@code app.cors.allowed-origins} como lista separada por virgula.
     *
     * <p>Deliberadamente sem valor default. Em {@code prod} a propriedade vem da
     * variavel {@code CORS_ALLOWED_ORIGINS} e, se ela faltar, o {@code @Value}
     * interrompe o start com "Could not resolve placeholder". E uma falha mais
     * limpa que a do datasource descrita em {@code application-prod.yml}, que so
     * se manifesta na primeira conexao — aqui a aplicacao nem sobe.
     */
    @Value("${app.cors.allowed-origins}")
    private List<String> corsAllowedOrigins;

    /**
     * Cadeia de filtros da API.
     *
     * <p>A politica e restritiva por padrao — {@code anyRequest().authenticated()}
     * — na mesma linha do {@code application.yml}, onde esquecer de declarar o
     * ambiente cai no perfil {@code prod}. Liberar rota passa a ser um ato
     * explicito, nunca o efeito de um esquecimento.
     *
     * @param http builder fornecido pelo Spring Security
     * @return a cadeia configurada
     * @throws Exception propagada pelo builder do {@link HttpSecurity}
     */
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(AUTH_PATH).permitAll()
                        .anyRequest().authenticated())
                // 401, nao o 403 que o Http403ForbiddenEntryPoint devolveria por
                // padrao. A distincao importa para o cliente: 401 significa
                // "autentique-se ou renove o token"; 403, "voce esta autenticado
                // mas nao pode fazer isso".
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    /**
     * Algoritmo de hash das senhas.
     *
     * <p>BCrypt tem custo de calculo deliberadamente alto, o que encarece um
     * ataque de forca bruta sobre a base vazada, e embute o salt no proprio hash
     * — nao ha coluna de salt a manter.
     *
     * @return o encoder usado tanto para gravar quanto para conferir senha
     */
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Expoe o {@link AuthenticationManager} como bean.
     *
     * <p>Consumido pelo {@code AuthServiceImpl} no login. O Spring nao publica
     * esse bean sozinho, e e ele que delega ao {@code DaoAuthenticationProvider}
     * — quem carrega o usuario pelo {@code UserDetailsService} e compara o hash
     * pelo {@link PasswordEncoder}.
     *
     * @param configuration configuracao montada pelo Spring Security
     * @return o gerenciador de autenticacao do contexto
     * @throws Exception propagada por {@link AuthenticationConfiguration}
     */
    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration configuration)
            throws Exception {
        return configuration.getAuthenticationManager();
    }

    /**
     * Politica de CORS aplicada a toda a API.
     *
     * <p>So surte efeito porque {@code securityFilterChain} chama
     * {@code cors(...)}: declarado sozinho, o bean e ignorado pelo Spring
     * Security. Sem essa ligacao o preflight {@code OPTIONS} do navegador cairia
     * em {@code anyRequest().authenticated()} e voltaria 401 — o sintoma
     * classico de CORS "configurado" que nao funciona.
     *
     * <p>{@code allowCredentials(true)} obriga a declarar as origens uma a uma:
     * a especificacao proibe o coringa {@code *} junto de credenciais, e o
     * Spring rejeita a combinacao. Se um dia for preciso liberar por padrao, o
     * caminho e {@code setAllowedOriginPatterns}, nao
     * {@code setAllowedOrigins}.
     *
     * @return configuracao de CORS registrada para todas as rotas
     */
    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOrigins(corsAllowedOrigins);
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("*"));
        cfg.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource src = new UrlBasedCorsConfigurationSource();
        src.registerCorsConfiguration("/**", cfg);
        return src;
    }

}
