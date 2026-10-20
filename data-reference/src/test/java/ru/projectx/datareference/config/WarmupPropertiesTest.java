package ru.projectx.datareference.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class WarmupPropertiesTest {

    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(WarmupPropertiesBinding.class);

    @Test
    void defaults_warmForThirtySecondsOverAHundredRecordsOfEachReference() {
        context.run(started -> {
            WarmupProperties properties = started.getBean(WarmupProperties.class);
            assertThat(properties.timeout()).isEqualTo(Duration.ofSeconds(30));
            assertThat(properties.recordsPerReference()).isEqualTo(100);
        });
    }

    @Test
    void timeoutShorterThanASecond_failsStartup() {
        context.withPropertyValues("projectx.warmup.timeout=999ms")
                .run(started -> assertThat(started).getFailure()
                        .hasRootCauseInstanceOf(BindValidationException.class)
                        .rootCause().hasMessageContaining("timeout"));
    }

    @Test
    void oneSecondTimeout_isAccepted() {
        context.withPropertyValues("projectx.warmup.timeout=1s")
                .run(started -> assertThat(started.getBean(WarmupProperties.class).timeout())
                        .isEqualTo(Duration.ofSeconds(1)));
    }

    @Test
    void zeroRecordsPerReference_failsStartup() {
        context.withPropertyValues("projectx.warmup.records-per-reference=0")
                .run(started -> assertThat(started).getFailure()
                        .hasRootCauseInstanceOf(BindValidationException.class)
                        .rootCause().hasMessageContaining("recordsPerReference"));
    }

    @Test
    void oneRecordPerReference_isAccepted() {
        context.withPropertyValues("projectx.warmup.records-per-reference=1")
                .run(started -> assertThat(started.getBean(WarmupProperties.class).recordsPerReference())
                        .isEqualTo(1));
    }

    @EnableConfigurationProperties(WarmupProperties.class)
    static class WarmupPropertiesBinding {
    }
}
