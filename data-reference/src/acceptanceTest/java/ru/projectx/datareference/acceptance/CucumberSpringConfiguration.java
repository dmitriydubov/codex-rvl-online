package ru.projectx.datareference.acceptance;

import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Один контекст Spring на все сценарии приёмки (cucumber-spring). Step-определения лежат
 * в этом же пакете ({@code acceptance.glue} в gauntlet/config/data-reference/gauntlet.properties).
 *
 * <p>Приёмочные тесты живут только в GitHub и облаке Claude Code: в рабочий контур
 * каталог src/acceptanceTest не переносится, и ни продакшен-код, ни unit-тесты от него
 * не зависят. Поэтому здесь своя обвязка, а не классы из src/test.
 *
 * <p>Контекст — как у {@code @IntegrationTest}: настоящий PostgreSQL той же версии, что
 * в тестах и в контуре, AMQP выключен, шедулер разбора усыплён, чтобы его тики не меняли
 * статусы посреди сценария. Сценариям, которым нужны брокер 1С:УСК, остановленные часы
 * или тик шедулера из шага, эту обвязку расширяет первая же такая задача, вместе со
 * спецификацией.
 *
 * <p>Контекст и контейнер общие на все сценарии, поэтому данные между ними не
 * изолированы: первая задача со сценариями, которые пишут в базу, обязана добавить
 * очистку таблиц в хуке {@code @Before} (DatabaseCleaner из src/test приёмке не виден).
 */
@CucumberContextConfiguration
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "projectx.import.poll-interval=1h",
        "spring.autoconfigure.exclude=org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration"})
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
