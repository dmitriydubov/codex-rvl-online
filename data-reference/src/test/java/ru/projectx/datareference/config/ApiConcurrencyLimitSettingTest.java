package ru.projectx.datareference.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ApiConcurrencyLimitSettingTest {

    private static final int DEFAULT_LIMIT = 100;
    private static final int OVERRIDDEN_LIMIT = 7;

    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(ReferenceApiPropertiesBinding.class);

    @Test
    void withoutEnvironment_aHundredRequestsAreHandledAtOnce() {
        context.run(started -> {
            assertThat(started).hasNotFailed();
            assertThat(started.getBean(ReferenceApiProperties.class).maxConcurrentRequests()).isEqualTo(DEFAULT_LIMIT);
        });
    }

    @Test
    void maxConcurrentRequestsVariable_setsTheLimit() {
        context.withPropertyValues("API_MAX_CONCURRENT_REQUESTS=" + OVERRIDDEN_LIMIT)
                .run(started -> assertThat(started.getBean(ReferenceApiProperties.class).maxConcurrentRequests())
                        .isEqualTo(OVERRIDDEN_LIMIT));
    }

    @EnableConfigurationProperties(ReferenceApiProperties.class)
    static class ReferenceApiPropertiesBinding {
    }
}
