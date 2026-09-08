package br.com.charlottebolos;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Verifica que o contexto da aplicacao sobe inteiro.
 *
 * <p>Os testes rodam no perfil {@code prod}, que e o padrao. Os placeholders sem
 * default do datasource nao atrapalham porque o {@code @ServiceConnection} de
 * {@link TestcontainersConfiguration} tem precedencia sobre eles — mas
 * {@code app.cors.allowed-origins} nao tem equivalente: e lido por
 * {@code @Value}, que aborta o start quando o placeholder nao resolve. Por isso
 * o valor e fornecido aqui.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {
        "app.cors.allowed-origins=http://localhost:4200",
        // A migracao V2 exige estes placeholders, que em prod vem de variavel de
        // ambiente. O hash e o de 'admin123', o mesmo usado em dev.
        "spring.flyway.placeholders.admin_email=admin@charlottebolos.com.br",
        "spring.flyway.placeholders.admin_password_hash=$2a$10$bNBTq0b1r7OiSD7kEoOpCOl1QXkMpB45X7mhot79p/QuUTFyTJ1sy",
        // Em prod vem de JWT_SECRET. Precisa de 32+ caracteres, senao o
        // JwtService aborta o start de proposito.
        "app.jwt.secret=segredo-de-teste-com-mais-de-32-caracteres"
})
class CharlotteBolosApplicationTests {

    @Test
    void contextLoads() {
    }

}
