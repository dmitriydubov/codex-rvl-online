package ru.projectx.datareference.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ImportPropertiesTest {

    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(ImportPropertiesBinding.class);

    @Test
    void initialDelay_defaultsToThirtySeconds() {
        context.run(started -> assertThat(started.getBean(ImportProperties.class).initialDelay())
                .isEqualTo(Duration.ofSeconds(30)));
    }

    @Test
    void schedules_haveTheirDefaults() {
        context.run(started -> {
            ImportProperties properties = started.getBean(ImportProperties.class);
            assertThat(properties.pollInterval()).isEqualTo(Duration.ofSeconds(10));
            assertThat(properties.retentionPollInterval()).isEqualTo(Duration.ofHours(1));
            assertThat(properties.stuckTransferPollInterval()).isEqualTo(Duration.ofMinutes(15));
            assertThat(properties.stuckTransferReportLimit()).isEqualTo(20);
            assertThat(properties.retentionBatchSize()).isEqualTo(500);
        });
    }

    @Test
    void schedules_areBoundFromTheirProperties() {
        context.withPropertyValues("projectx.import.poll-interval=11s", "projectx.import.retention-poll-interval=2h",
                        "projectx.import.stuck-transfer-poll-interval=16m",
                        "projectx.import.stuck-transfer-report-limit=5")
                .run(started -> {
                    ImportProperties properties = started.getBean(ImportProperties.class);
                    assertThat(properties.pollInterval()).isEqualTo(Duration.ofSeconds(11));
                    assertThat(properties.retentionPollInterval()).isEqualTo(Duration.ofHours(2));
                    assertThat(properties.stuckTransferPollInterval()).isEqualTo(Duration.ofMinutes(16));
                    assertThat(properties.stuckTransferReportLimit()).isEqualTo(5);
                });
    }

    @ParameterizedTest
    @CsvSource({"poll-interval,pollInterval", "retention-poll-interval,retentionPollInterval",
            "stuck-transfer-poll-interval,stuckTransferPollInterval"})
    void zeroInterval_failsStartup(String property, String component) {
        context.withPropertyValues("projectx.import." + property + "=0s")
                .run(started -> assertThat(started).getFailure()
                        .hasRootCauseInstanceOf(BindValidationException.class)
                        .rootCause().hasMessageContaining(component));
    }

    @Test
    void zeroStuckTransferThreshold_failsStartup() {
        context.withPropertyValues("projectx.import.stuck-transfer-threshold=0s")
                .run(started -> assertThat(started).getFailure()
                        .hasRootCauseInstanceOf(BindValidationException.class)
                        .rootCause().hasMessageContaining("stuckTransferThreshold"));
    }

    @Test
    void payloadTtlBelowOneDay_failsStartup() {
        context.withPropertyValues("projectx.import.payload-ttl=23h")
                .run(started -> assertThat(started).getFailure()
                        .hasRootCauseInstanceOf(BindValidationException.class)
                        .rootCause().hasMessageContaining("payloadTtl"));
    }

    @Test
    void payloadTtlOfOneDay_isAccepted() {
        context.withPropertyValues("projectx.import.payload-ttl=1d", "projectx.import.parent-wait-timeout=12h")
                .run(started -> assertThat(started.getBean(ImportProperties.class).payloadTtl())
                        .isEqualTo(Duration.ofDays(1)));
    }

    @Test
    void zeroReportLimit_failsStartup() {
        context.withPropertyValues("projectx.import.stuck-transfer-report-limit=0")
                .run(started -> assertThat(started).getFailure()
                        .hasRootCauseInstanceOf(BindValidationException.class)
                        .rootCause().hasMessageContaining("stuckTransferReportLimit"));
    }

    @Test
    void emptyInitialDelay_fallsBackToDefault() {
        context.withPropertyValues("projectx.import.initial-delay=")
                .run(started -> assertThat(started.getBean(ImportProperties.class).initialDelay())
                        .isEqualTo(Duration.ofSeconds(30)));
    }

    @Test
    void initialDelay_isBoundFromProperty() {
        context.withPropertyValues("projectx.import.initial-delay=2m")
                .run(started -> assertThat(started.getBean(ImportProperties.class).initialDelay())
                        .isEqualTo(Duration.ofMinutes(2)));
    }

    @Test
    void zeroInitialDelay_isAccepted() {
        context.withPropertyValues("projectx.import.initial-delay=0s")
                .run(started -> assertThat(started.getBean(ImportProperties.class).initialDelay())
                        .isZero());
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1s", "-1ns"})
    void negativeInitialDelay_failsStartup(String initialDelay) {
        context.withPropertyValues("projectx.import.initial-delay=" + initialDelay)
                .run(started -> assertThat(started).getFailure()
                        .hasRootCauseInstanceOf(BindValidationException.class)
                        .rootCause().hasMessageContaining("initialDelay"));
    }

    @Test
    void unreadableInitialDelay_failsStartup() {
        context.withPropertyValues("projectx.import.initial-delay=soon")
                .run(started -> assertThat(started).getFailure()
                        .cause().hasMessageContaining("projectx.import.initial-delay"));
    }

    @Test
    void parentWait_defaultsToADayAndTwentyParents() {
        context.run(started -> {
            ImportProperties properties = started.getBean(ImportProperties.class);
            assertThat(properties.parentWaitTimeout()).isEqualTo(Duration.ofHours(24));
            assertThat(properties.parentWaitReportLimit()).isEqualTo(20);
        });
    }

    @Test
    void parentWait_isBoundFromItsProperties() {
        context.withPropertyValues("projectx.import.parent-wait-timeout=36h",
                        "projectx.import.parent-wait-report-limit=5")
                .run(started -> {
                    ImportProperties properties = started.getBean(ImportProperties.class);
                    assertThat(properties.parentWaitTimeout()).isEqualTo(Duration.ofHours(36));
                    assertThat(properties.parentWaitReportLimit()).isEqualTo(5);
                });
    }

    @ParameterizedTest
    @CsvSource({"parent-wait-timeout=0s,parentWaitTimeout", "parent-wait-report-limit=0,parentWaitReportLimit"})
    void zeroParentWaitSetting_failsStartup(String setting, String component) {
        context.withPropertyValues("projectx.import." + setting)
                .run(started -> assertThat(started).getFailure()
                        .hasRootCauseInstanceOf(BindValidationException.class)
                        .rootCause().hasMessageContaining(component));
    }

    @ParameterizedTest
    @CsvSource({"30d,30d,false", "31d,30d,false", "2d,1d,false", "29d,30d,true", "PT719H59M59S,30d,true"})
    void parentWaitAgainstPayloadTtl_decidesTheStartup(String timeout, String payloadTtl, boolean starts) {
        context.withPropertyValues("projectx.import.parent-wait-timeout=" + timeout,
                        "projectx.import.payload-ttl=" + payloadTtl)
                .run(started -> {
                    if (starts) {
                        assertThat(started).hasNotFailed();
                        return;
                    }
                    assertThat(started).getFailure()
                            .hasRootCauseInstanceOf(BindValidationException.class)
                            .rootCause()
                            .hasMessageContaining("parent-wait-timeout")
                            .hasMessageContaining("payload-ttl");
                });
    }

    @Test
    void policyPersonalDataGrace_defaultsToThirtyDays() {
        context.run(started -> assertThat(started.getBean(ImportProperties.class).policyPersonalDataGrace())
                .isEqualTo(Duration.ofDays(30)));
    }

    @Test
    void policyPersonalDataGrace_isBoundFromItsProperty() {
        context.withPropertyValues("projectx.import.policy-personal-data-grace=1d")
                .run(started -> assertThat(started.getBean(ImportProperties.class).policyPersonalDataGrace())
                        .isEqualTo(Duration.ofDays(1)));
    }

    @Test
    void policyPersonalDataGraceBelowOneDay_failsStartup() {
        context.withPropertyValues("projectx.import.policy-personal-data-grace=PT23H59M59S")
                .run(started -> assertThat(started).getFailure()
                        .hasRootCauseInstanceOf(BindValidationException.class)
                        .rootCause().hasMessageContaining("policyPersonalDataGrace"));
    }

    @EnableConfigurationProperties(ImportProperties.class)
    static class ImportPropertiesBinding {
    }
}
