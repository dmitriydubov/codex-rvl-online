package ru.projectx.datareference.support;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * PostgreSQL для тестов. Настоящий, той же версии, что в контуре: часть поведения,
 * ради которого этот сервис существует, — уникальные ключи, {@code jsonb}, поведение
 * Liquibase — на встроенной базе либо работает иначе, либо не работает вовсе.
 *
 * <p>Обычно это контейнер Testcontainers. Если задан {@code TEST_POSTGRES_URL}, контейнер
 * не поднимается, а датасорс смотрит на внешнюю базу — для машин без Docker, см. {@link TestDatabase}.
 *
 * <p>В обоих случаях адрес и учётные данные приходят в контекст свойствами
 * {@code spring.datasource.*} — так же, как в контуре. {@code @ServiceConnection} здесь
 * намеренно нет: он отдаёт Liquibase собственные данные подключения к контейнеру, и тесты
 * не видели бы, с какими настройками соединения миграции идут на самом деле. А они
 * отличаются от пула приложения: см. {@code spring.liquibase} в {@code application.yml}.
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
    @ConditionalOnExpression("'${" + TestDatabase.URL_PROPERTY + ":}'.isEmpty()")
    public PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(IMAGE);
    }

    /** Контейнер к этому моменту запущен: Boot стартует его при создании бина. */
    @Bean
    @ConditionalOnExpression("'${" + TestDatabase.URL_PROPERTY + ":}'.isEmpty()")
    public DynamicPropertyRegistrar containerPostgres(PostgreSQLContainer container) {
        return registry -> {
            registry.add("spring.datasource.url", container::getJdbcUrl);
            registry.add("spring.datasource.username", container::getUsername);
            registry.add("spring.datasource.password", container::getPassword);
        };
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
