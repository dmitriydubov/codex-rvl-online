package ru.projectx.contracts.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class LeadershipPropertiesTest {

    private static final String GROUP = "projectx.leadership.group=contract-service";
    private static final String TTL = "projectx.leadership.lock-ttl=30s";
    private static final String HEARTBEAT = "projectx.leadership.heartbeat=1s";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(Leadership.class);

    @Test
    void completeConfiguration_bindsGroupTtlAndHeartbeat() {
        runner.withPropertyValues(GROUP, TTL, HEARTBEAT).run(context -> {
            LeadershipProperties properties = context.getBean(LeadershipProperties.class);
            assertThat(properties.group()).isEqualTo("contract-service");
            assertThat(properties.lockTtl()).isEqualTo(Duration.ofSeconds(30));
            assertThat(properties.heartbeat()).isEqualTo(Duration.ofSeconds(1));
        });
    }

    @Test
    void blankGroup_failsValidation() {
        runner.withPropertyValues("projectx.leadership.group= ", TTL, HEARTBEAT)
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void groupLongerThanTheLockColumn_failsValidation() {
        runner.withPropertyValues("projectx.leadership.group=" + "g".repeat(101), TTL, HEARTBEAT)
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void ttlBelowASecond_failsValidation() {
        runner.withPropertyValues(GROUP, "projectx.leadership.lock-ttl=999ms", HEARTBEAT)
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void heartbeatNotShorterThanTtl_failsValidation() {
        runner.withPropertyValues(GROUP, TTL, "projectx.leadership.heartbeat=30s")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void missingHeartbeat_failsValidation() {
        runner.withPropertyValues(GROUP, TTL).run(context -> assertThat(context).hasFailed());
    }

    @ParameterizedTest
    @CsvSource({
            "PT1S, PT1S, false",
            "PT1.001S, PT1S, true",
            "PT30S, PT1S, true",
            "PT1S, PT1.001S, false",
            "PT1S, PT30S, false"})
    void ttlCheck_holdsOnlyWhenTtlStrictlyExceedsHeartbeat(Duration lockTtl, Duration heartbeat, boolean longer) {
        assertThat(new LeadershipProperties("contract-service", lockTtl, heartbeat).isLockTtlLongerThanHeartbeat())
                .isEqualTo(longer);
    }

    @Test
    void ttlCheck_isFalseWhileAValueIsMissing() {
        assertThat(new LeadershipProperties("contract-service", null, Duration.ofSeconds(1))
                .isLockTtlLongerThanHeartbeat()).isFalse();
        assertThat(new LeadershipProperties("contract-service", Duration.ofSeconds(30), null)
                .isLockTtlLongerThanHeartbeat()).isFalse();
    }

    @EnableConfigurationProperties(LeadershipProperties.class)
    static class Leadership {
    }
}
