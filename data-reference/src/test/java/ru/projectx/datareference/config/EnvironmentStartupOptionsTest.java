package ru.projectx.datareference.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.context.TestPropertySource;
import ru.projectx.datareference.config.leadership.LeaderLockPoolConfig;
import ru.projectx.datareference.config.restread.ApplicationPool;
import ru.projectx.datareference.config.restread.RestReadPoolConfig;
import ru.projectx.datareference.support.IntegrationTest;

@IntegrationTest
@TestPropertySource(properties = "spring.datasource.hikari.data-source-properties.options=-c lock_timeout=9s")
class EnvironmentStartupOptionsTest {

    @Autowired
    private ApplicationPool applicationPool;

    @Autowired
    @Qualifier(RestReadPoolConfig.REST_READ_POOL)
    private DataSource restReadPool;

    @Autowired
    @Qualifier(LeaderLockPoolConfig.LEADER_LOCK_POOL)
    private DataSource leaderLockPool;

    @Test
    void startupOptionsFromEnvironment_giveWayToSessionTimeoutsOfTheService() throws SQLException {
        assertThat(lockTimeout(applicationPool.pool())).isEqualTo("5s");
        assertThat(lockTimeout(restReadPool)).isEqualTo("5s");
        assertThat(lockTimeout(leaderLockPool)).isEqualTo("0");
    }

    private static String lockTimeout(DataSource pool) throws SQLException {
        try (Connection connection = pool.getConnection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SHOW lock_timeout")) {
            result.next();
            return result.getString(1);
        }
    }
}
