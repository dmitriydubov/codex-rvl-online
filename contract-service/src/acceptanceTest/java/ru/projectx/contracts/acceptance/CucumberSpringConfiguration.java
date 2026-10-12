package ru.projectx.contracts.acceptance;

import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Один контекст Spring на все сценарии приёмки: настоящий PostgreSQL той же версии, что в контуре. Своя обвязка,
 * а не классы из src/test, потому что приёмка в контур не переносится.
 */
@CucumberContextConfiguration
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(CucumberSpringConfiguration.Containers.class)
public class CucumberSpringConfiguration {

    /** Контейнеры приёмки: адрес и учётные данные подставляет {@link ServiceConnection}. */
    @TestConfiguration(proxyBeanMethods = false)
    static class Containers {

        private static final String POSTGRES_IMAGE = "postgres:16.10-alpine";

        @Bean
        @ServiceConnection
        PostgreSQLContainer postgresContainer() {
            return new PostgreSQLContainer(POSTGRES_IMAGE);
        }
    }
}
