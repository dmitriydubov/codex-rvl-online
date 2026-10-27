package ru.projectx.datareference.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DatabaseSessionTimeoutsStartupOptionsTest {

    private static final DatabaseSessionTimeouts CONFIGURED = new DatabaseSessionTimeouts("5s", "30s", "1min");

    @Test
    void startupOptions_listEveryParameterInOrder() {
        assertThat(CONFIGURED.startupOptions())
                .isEqualTo("-c lock_timeout=5s -c statement_timeout=30s -c idle_in_transaction_session_timeout=1min");
    }

    @Test
    void withoutLockWait_disablesOnlyTheLockTimeout() {
        DatabaseSessionTimeouts withoutLockWait = CONFIGURED.withoutLockWait();

        assertThat(withoutLockWait.lockTimeout()).isEqualTo("0");
        assertThat(withoutLockWait.statementTimeout()).isEqualTo("30s");
        assertThat(withoutLockWait.idleInTransactionSessionTimeout()).isEqualTo("1min");
        assertThat(withoutLockWait.byParameter()).containsEntry("lock_timeout", "0");
    }

    @Test
    void withoutLockWait_leavesTheConfiguredTimeoutsUntouched() {
        CONFIGURED.withoutLockWait();

        assertThat(CONFIGURED.lockTimeout()).isEqualTo("5s");
    }
}
