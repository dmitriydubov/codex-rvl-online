package ru.projectx.contracts.support;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * PostgreSQL той же версии, что в контуре: уникальные ключи, перечисления и Liquibase на встроенной базе ведут себя
 * иначе. Адрес контейнера или внешней базы ({@link TestDatabase}) приходит свойствами {@code spring.datasource.*}, без
 * {@code @ServiceConnection}, чтобы миграции шли со своими настройками соединения, как в контуре.
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresTestcontainer {

    private static final String IMAGE = "postgres:16.10-alpine";
    /** Учётные данные внешней базы по умолчанию — те же, что у локального контура. */
    private static final String LOCAL_CREDENTIAL = "contract_service";

    // Контейнер — бин контекста, а не статическое поле теста: живёт вместе с закэшированным контекстом Spring.
    // Статическое поле остановилось бы после своего класса, а контекст продолжал бы ходить по старому адресу.
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
