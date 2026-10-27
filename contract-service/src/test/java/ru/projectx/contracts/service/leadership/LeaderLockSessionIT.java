package ru.projectx.contracts.service.leadership;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import ru.projectx.contracts.support.IntegrationTest;

@IntegrationTest
class LeaderLockSessionIT {

    @Autowired
    private LeaderLockConnections leaderLock;

    @Autowired
    private DataSource application;

    @Test
    void leaderLockSession_hasNoLockTimeoutAndItsOwnApplicationNameButKeepsTheOtherTimeouts() {
        JdbcClient leaderLockSession = JdbcClient.create(leaderLock.dataSource());
        JdbcClient applicationSession = JdbcClient.create(application);

        assertThat(setting(leaderLockSession, "lock_timeout")).isEqualTo("0");
        assertThat(setting(applicationSession, "lock_timeout")).isNotEqualTo("0");
        assertThat(setting(leaderLockSession, "statement_timeout"))
                .isEqualTo(setting(applicationSession, "statement_timeout"));
        assertThat(setting(leaderLockSession, "idle_in_transaction_session_timeout"))
                .isEqualTo(setting(applicationSession, "idle_in_transaction_session_timeout"));
        assertThat(setting(leaderLockSession, "application_name")).isEqualTo("contract-service-leader-lock");
        assertThat(setting(applicationSession, "application_name")).isNotEqualTo("contract-service-leader-lock");
    }

    private static String setting(JdbcClient session, String parameter) {
        return session.sql("select current_setting(:parameter)").param("parameter", parameter).query(String.class).single();
    }
}
