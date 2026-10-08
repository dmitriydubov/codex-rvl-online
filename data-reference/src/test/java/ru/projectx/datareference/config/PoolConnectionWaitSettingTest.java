package ru.projectx.datareference.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariDataSource;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Проверяет, как пул читает из настоящего {@code application.yml} ожидание соединения и проверку живости:
 * умолчания и переменную окружения, которой эксплуатация меняет ожидание. Базы не нужно: пул подключается
 * к ней только при первом запросе соединения.
 */
class PoolConnectionWaitSettingTest {

    private static final Duration DEFAULT_CONNECTION_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration VALIDATION_TIMEOUT = Duration.ofSeconds(1);
    /** Отличается от умолчания, чтобы было видно, откуда пул взял значение. */
    private static final Duration OVERRIDDEN_CONNECTION_TIMEOUT = Duration.ofMillis(3500);

    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withConfiguration(AutoConfigurations.of(DataSourceAutoConfiguration.class))
            .withPropertyValues("DB_PASSWORD=not-a-real-password");

    @Test
    void withoutEnvironment_poolWaitsTwoSecondsAndValidatesWithinOne() {
        context.run(started -> {
            HikariDataSource pool = pool(started);

            assertThat(Duration.ofMillis(pool.getConnectionTimeout())).isEqualTo(DEFAULT_CONNECTION_TIMEOUT);
            assertThat(Duration.ofMillis(pool.getValidationTimeout())).isEqualTo(VALIDATION_TIMEOUT);
        });
    }

    @Test
    void connectionTimeoutVariable_setsPoolWaitInMilliseconds() {
        context.withPropertyValues("DB_CONNECTION_TIMEOUT_MS=" + OVERRIDDEN_CONNECTION_TIMEOUT.toMillis())
                .run(started -> {
                    HikariDataSource pool = pool(started);

                    assertThat(Duration.ofMillis(pool.getConnectionTimeout())).isEqualTo(OVERRIDDEN_CONNECTION_TIMEOUT);
                    assertThat(Duration.ofMillis(pool.getValidationTimeout())).isEqualTo(VALIDATION_TIMEOUT);
                });
    }

    private static HikariDataSource pool(AssertableApplicationContext started) {
        assertThat(started).hasNotFailed();
        return started.getBean(HikariDataSource.class);
    }
}
