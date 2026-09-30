package ru.projectx.datareference.support;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * PostgreSQL для тестов. Настоящий, той же версии, что в контуре: часть поведения,
 * ради которого этот сервис существует, — уникальные ключи, {@code jsonb}, поведение
 * Liquibase — на встроенной базе либо работает иначе, либо не работает вовсе.
 *
 * <p>Обычно это контейнер Testcontainers: адрес и учётные данные подставляет в контекст
 * {@link ServiceConnection}. Если задан {@code TEST_POSTGRES_URL}, контейнер не поднимается,
 * а датасорс смотрит на внешнюю базу — для машин без Docker, см. {@link TestDatabase}.
 *
 * <p>Контейнер живёт как бин контекста, а не статическим полем теста: контексты Spring
 * кэшируются между тестовыми классами, и вместе с контекстом переиспользуется контейнер.
 * Схему в нём накатывает Liquibase на старте приложения — как и в проде.
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresTestcontainer {

    private static final String IMAGE = "postgres:16.10-alpine";
    /** Учётные данные внешней базы по умолчанию — те же, что у локального контура. */
    private static final String LOCAL_CREDENTIAL = "data_reference";

    @Bean
    @ServiceConnection
    @ConditionalOnExpression("'${" + TestDatabase.URL_PROPERTY + ":}'.isEmpty()")
    public PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(IMAGE);
    }

    @Bean
    @ConditionalOnExpression("!'${" + TestDatabase.URL_PROPERTY + ":}'.isEmpty()")
    public DynamicPropertyRegistrar externalPostgres(Environment environment) {
        String url = environment.getRequiredProperty(TestDatabase.URL_PROPERTY);
        String username = environment.getProperty(TestDatabase.USERNAME_PROPERTY, LOCAL_CREDENTIAL);
        String password = environment.getProperty(TestDatabase.PASSWORD_PROPERTY, LOCAL_CREDENTIAL);
        return registry -> {
            registry.add("spring.datasource.url", () -> url);
            registry.add("spring.datasource.username", () -> username);
            registry.add("spring.datasource.password", () -> password);
        };
    }
}
