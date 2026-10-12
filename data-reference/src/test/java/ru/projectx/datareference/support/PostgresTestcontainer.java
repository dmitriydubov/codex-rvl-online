package ru.projectx.datareference.support;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.postgresql.PostgreSQLContainer;

@TestConfiguration(proxyBeanMethods = false)
public class PostgresTestcontainer {

    private static final String IMAGE = "postgres:16.10-alpine";
    private static final String LOCAL_CREDENTIAL = "data_reference";

    @Bean
    @ConditionalOnExpression("'${" + TestDatabase.URL_PROPERTY + ":}'.isEmpty()")
    public PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(IMAGE);
    }

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
