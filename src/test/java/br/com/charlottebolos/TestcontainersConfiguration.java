package br.com.charlottebolos;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Sobe um PostgreSQL real para os testes de integracao.
 *
 * <p>A imagem e a mesma do docker-compose.yml de propósito: o schema vem inteiro
 * das migracoes do Flyway e o Hibernate roda com ddl-auto=validate, entao testar
 * contra outro banco validaria um schema diferente do de producao.
 *
 * <p>{@code @ServiceConnection} injeta host, porta e credenciais do container no
 * contexto com precedencia sobre {@code spring.datasource.*} — por isso os
 * placeholders sem default do perfil prod nao atrapalham os testes.
 */
@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>("postgres:16");
    }
}
