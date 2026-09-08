package br.com.charlottebolos;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Login de ponta a ponta, contra o PostgreSQL do Testcontainers.
 *
 * <p>Primeiro teste do projeto que exercita uma rota HTTP de verdade. Ate aqui
 * tudo estava verificado por compilacao e por contexto subindo — CORS, cadeia de
 * filtros e envelope de resposta nunca tinham atendido uma requisicao.
 *
 * <p>Roda no perfil {@code dev}, onde o segredo do JWT e a credencial do
 * administrador tem valores fixos. O administrador vem da migracao {@code V2},
 * nao de dados montados pelo teste: o que se verifica aqui e o caminho real de
 * producao, so que com credencial descartavel.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class AuthLoginTest {

    private static final String LOGIN_URL = "/api/auth/login";
    private static final String ADMIN_EMAIL = "admin@charlottebolos.com.br";
    private static final String ADMIN_PASSWORD = "admin123";

    @Autowired
    private MockMvc mockMvc;

    private static String loginBody(String email, String password) {
        return """
                {"email": "%s", "password": "%s"}
                """.formatted(email, password);
    }

    @Test
    void loginComCredenciaisValidasDevolveToken() throws Exception {
        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(ADMIN_EMAIL, ADMIN_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.email").value(ADMIN_EMAIL))
                .andExpect(jsonPath("$.data.role").value("ADMIN"))
                .andExpect(jsonPath("$.data.fullName").value("Admin Charlotte"));
    }

    /**
     * A mensagem precisa ser generica: dizer que o e-mail existe mas a senha
     * esta errada transformaria o login num verificador de contas cadastradas.
     */
    @Test
    void senhaIncorretaDevolve401ComMensagemGenerica() throws Exception {
        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(ADMIN_EMAIL, "senha-errada")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Credenciais invalidas"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    /**
     * E-mail inexistente tem de responder exatamente como senha errada — mesmo
     * status e mesma mensagem —, senao a diferenca revela quais contas existem.
     */
    @Test
    void emailInexistenteRespondeIgualASenhaErrada() throws Exception {
        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("ninguem@charlottebolos.com.br", ADMIN_PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Credenciais invalidas"));
    }

    /**
     * Verifica o {@code GlobalExceptionHandler}: a falha de {@code @Valid} vira
     * 400 com o mapa de campos, nao a pagina de erro padrao do Spring.
     */
    @Test
    void corpoInvalidoDevolve400ComOsCamposQueFalharam() throws Exception {
        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("nao-e-email", "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data.email").isNotEmpty())
                .andExpect(jsonPath("$.data.password").isNotEmpty());
    }

    /**
     * O ponto corrigido em relacao ao projeto de origem: sem token a resposta e
     * 401, e nao o 403 que o {@code Http403ForbiddenEntryPoint} devolveria por
     * padrao. O cliente precisa distinguir "faca login" de "sem permissao".
     */
    @Test
    void rotaProtegidaSemTokenDevolve401() throws Exception {
        mockMvc.perform(get("/api/qualquer-coisa"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Token malformado nao pode virar 500. No projeto de origem a excecao subia
     * de dentro do filtro, fora do alcance do {@code @RestControllerAdvice}.
     */
    @Test
    void tokenMalformadoDevolve401ENao500() throws Exception {
        mockMvc.perform(get("/api/qualquer-coisa")
                        .header("Authorization", "Bearer isto-nao-e-um-jwt"))
                .andExpect(status().isUnauthorized());
    }
}
