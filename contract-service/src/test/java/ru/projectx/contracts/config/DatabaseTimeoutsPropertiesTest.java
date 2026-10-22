package ru.projectx.contracts.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class DatabaseTimeoutsPropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(Timeouts.class);

    @Test
    void completeConfiguration_bindsTheThreeSessionTimeouts() {
        runner.withPropertyValues(
                "projectx.database.session-timeouts.lock-timeout=5s",
                "projectx.database.session-timeouts.statement-timeout=30s",
                "projectx.database.session-timeouts.idle-in-transaction-session-timeout=1min")
                .run(context -> assertThat(context.getBean(DatabaseTimeoutsProperties.class))
                        .isEqualTo(new DatabaseTimeoutsProperties("5s", "30s", "1min")));
    }

    @Test
    void blankTimeout_failsValidation() {
        runner.withPropertyValues(
                "projectx.database.session-timeouts.lock-timeout= ",
                "projectx.database.session-timeouts.statement-timeout=30s",
                "projectx.database.session-timeouts.idle-in-transaction-session-timeout=1min")
                .run(context -> assertThat(context).hasFailed());
    }

    @EnableConfigurationProperties(DatabaseTimeoutsProperties.class)
    static class Timeouts {
    }
}
