package ru.projectx.contracts.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class DatabaseTimeoutsPropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(Timeouts.class);

    @Test
    void completeConfiguration_bindsTheThreeSessionTimeoutsByPostgresParameter() {
        runner.withPropertyValues(
                "projectx.database.session-timeouts.lock-timeout=5s",
                "projectx.database.session-timeouts.statement-timeout=30s",
                "projectx.database.session-timeouts.idle-in-transaction-session-timeout=1min")
                .run(context -> assertThat(context.getBean(DatabaseTimeoutsProperties.class).byParameter())
                        .containsExactly(
                                entry("lock_timeout", "5s"),
                                entry("statement_timeout", "30s"),
                                entry("idle_in_transaction_session_timeout", "1min")));
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
