package br.com.charlottebolos;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifica o seed do administrador inicial, criado pela migracao {@code V2}.
 *
 * <p>A migracao roda em todos os ambientes; o que muda por perfil e a origem da
 * credencial, via placeholders do Flyway. Este teste ativa {@code dev}, onde os
 * valores sao fixos, e por isso e tambem o unico teste que carrega o
 * {@code application-dev.yml} — que de outro modo nunca seria exercitado por
 * nada automatizado, ja que os demais rodam no perfil {@code prod}, o padrao.
 *
 * <p>Nao precisa injetar {@code app.cors.allowed-origins} nem os placeholders,
 * como faz o {@link CharlotteBolosApplicationTests}: no perfil {@code dev}
 * todos tem valor proprio.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("dev")
class AdminSeedTest {

    private static final String ADMIN_EMAIL = "admin@charlottebolos.com.br";
    private static final String ADMIN_PASSWORD = "admin123";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void seedInsereExatamenteUmAdmin() {
        Integer total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE email = ?", Integer.class, ADMIN_EMAIL);

        assertThat(total).isEqualTo(1);
    }

    @Test
    void adminTemPapelAtivoEPessoaVinculada() {
        Map<String, Object> row = jdbcTemplate.queryForMap("""
                SELECT u.role, u.active, p.name, p.surname
                  FROM users u
                  JOIN people p ON p.id = u.person_id
                 WHERE u.email = ?
                """, ADMIN_EMAIL);

        assertThat(row.get("role")).isEqualTo("ADMIN");
        assertThat(row.get("active")).isEqualTo(true);
        assertThat(row.get("name")).isEqualTo("Admin");
        assertThat(row.get("surname")).isEqualTo("Charlotte");
    }

    /**
     * O ponto que realmente importa: o hash gravado no SQL tem de casar com a
     * senha documentada. Um hash colado errado passaria despercebido em todos
     * os outros testes e so apareceria na primeira tentativa de login.
     */
    @Test
    void hashGravadoConfereComASenhaDocumentada() {
        String hash = jdbcTemplate.queryForObject(
                "SELECT password FROM users WHERE email = ?", String.class, ADMIN_EMAIL);

        assertThat(passwordEncoder.matches(ADMIN_PASSWORD, hash)).isTrue();
    }

    /**
     * A auditoria preenche as datas via {@code AuditingEntityListener}, mas o
     * seed entra por SQL puro, sem passar pelo JPA. As colunas sao NOT NULL,
     * entao o SQL precisa preenche-las por conta propria.
     */
    @Test
    void seedPreencheAsColunasDeAuditoria() {
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT created_at, created_by, updated_at FROM users WHERE email = ?",
                ADMIN_EMAIL);

        assertThat(row.get("created_at")).isNotNull();
        assertThat(row.get("updated_at")).isNotNull();
        assertThat(row.get("created_by")).isEqualTo("system");
    }
}
