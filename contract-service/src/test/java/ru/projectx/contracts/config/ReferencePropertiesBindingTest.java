package ru.projectx.contracts.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/**
 * Настройки клиента справочников читаются и проверяются на старте: без адреса, с адресом не по HTTP, с нулевым
 * таймаутом или отрицательным числом повторов сервис не поднимается, а отказ называет поле.
 */
class ReferencePropertiesBindingTest {

    private static final String PREFIX = "projectx.reference";

    private final ApplicationContextRunner contexts = new ApplicationContextRunner()
            .withUserConfiguration(Reference.class)
            .withPropertyValues(
                    PREFIX + ".url=http://reference.example:8080/internal/v1",
                    PREFIX + ".connect-timeout=2s",
                    PREFIX + ".read-timeout=7s",
                    PREFIX + ".retry.max-retries=3",
                    PREFIX + ".retry.delay=1s");

    @Test
    void completeConfiguration_binds() {
        contexts.run(context -> {
            ReferenceProperties reference = context.getBean(ReferenceProperties.class);

            assertThat(reference.url()).isEqualTo("http://reference.example:8080/internal/v1");
            assertThat(reference.connectTimeout()).isEqualTo(Duration.ofSeconds(2));
            assertThat(reference.readTimeout()).isEqualTo(Duration.ofSeconds(7));
            assertThat(reference.retry().maxRetries()).isEqualTo(3);
            assertThat(reference.retry().delay()).isEqualTo(Duration.ofSeconds(1));
        });
    }

    @Test
    void zeroRetriesAndZeroDelay_areAllowed() {
        contexts.withPropertyValues(PREFIX + ".retry.max-retries=0", PREFIX + ".retry.delay=0s")
                .run(context -> {
                    ReferenceProperties.Retry retry = context.getBean(ReferenceProperties.class).retry();

                    assertThat(retry.maxRetries()).isZero();
                    assertThat(retry.delay()).isZero();
                });
    }

    /** Пустой адрес — так выглядит забытая переменная окружения с пустым умолчанием плейсхолдера. */
    @ParameterizedTest
    @CsvSource({
        "url, '', url",
        "url, reference.example:8080/internal/v1, url",
        "url, ftp://reference.example/internal/v1, url",
        "connect-timeout, 0s, connectTimeout",
        "read-timeout, 0ms, readTimeout",
        "retry.max-retries, -1, maxRetries"})
    void invalidValue_failsStartupNamingTheField(String property, String value, String field) {
        contexts.withPropertyValues(PREFIX + "." + property + "=" + value).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).rootCause().hasMessageContaining(field);
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(ReferenceProperties.class)
    static class Reference {
    }
}
