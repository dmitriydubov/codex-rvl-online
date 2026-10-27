package ru.projectx.datareference.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;

class SessionStartupOptionsTest {

    private static final DatabaseSessionTimeouts CONFIGURED = new DatabaseSessionTimeouts("5s", "30s", "1min");

    @Test
    void set_opensSessionsOfPoolWithTheseTimeouts() {
        HikariDataSource pool = new HikariDataSource();

        SessionStartupOptions.set(pool, CONFIGURED);

        assertThat(pool.getDataSourceProperties()).containsEntry("options",
                "-c lock_timeout=5s -c statement_timeout=30s -c idle_in_transaction_session_timeout=1min");
    }

    @Test
    void set_replacesStartupOptionsThePoolHad() {
        HikariDataSource pool = new HikariDataSource();
        pool.addDataSourceProperty("options", "-c lock_timeout=9s");

        SessionStartupOptions.set(pool, CONFIGURED.withoutLockWait());

        assertThat(pool.getDataSourceProperties()).containsEntry("options",
                "-c lock_timeout=0 -c statement_timeout=30s -c idle_in_transaction_session_timeout=1min");
    }

    @Test
    void copy_givesTargetTheStartupOptionsOfSource() {
        HikariDataSource source = new HikariDataSource();
        source.addDataSourceProperty("options", "-c lock_timeout=5s");
        HikariDataSource target = new HikariDataSource();
        target.addDataSourceProperty("options", "-c lock_timeout=9s");

        SessionStartupOptions.copy(source, target);

        assertThat(target.getDataSourceProperties()).containsEntry("options", "-c lock_timeout=5s");
    }

    @Test
    void copy_fromSourceWithoutStartupOptions_leavesTargetAsIs() {
        HikariDataSource target = new HikariDataSource();
        target.addDataSourceProperty("options", "-c lock_timeout=9s");

        SessionStartupOptions.copy(new HikariDataSource(), target);

        assertThat(target.getDataSourceProperties()).containsEntry("options", "-c lock_timeout=9s");
    }
}
