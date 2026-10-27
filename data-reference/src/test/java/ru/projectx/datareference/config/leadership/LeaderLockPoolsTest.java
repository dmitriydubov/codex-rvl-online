package ru.projectx.datareference.config.leadership;

import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import ru.projectx.datareference.config.DatabaseSessionTimeouts;

class LeaderLockPoolsTest {

    private static final LeaderLockPoolProperties ONE_CONNECTION = new LeaderLockPoolProperties(1);
    private static final DatabaseSessionTimeouts SESSION_TIMEOUTS = new DatabaseSessionTimeouts("5s", "30s", "1min");

    @Test
    void leaderLockPool_takesAddressAndCredentialsOfApplicationPool() {
        HikariDataSource leaderLock = leaderLockPool(ONE_CONNECTION);

        assertThat(leaderLock.getJdbcUrl()).isEqualTo("jdbc:postgresql://db:5432/data_reference");
        assertThat(leaderLock.getUsername()).isEqualTo("data_reference");
        assertThat(leaderLock.getPassword()).isEqualTo("secret");
        assertThat(leaderLock.getDriverClassName()).isEqualTo("org.postgresql.Driver");
    }

    @Test
    void leaderLockPool_hasOwnNameAndSize() {
        HikariDataSource leaderLock = leaderLockPool(new LeaderLockPoolProperties(2));

        assertThat(leaderLock.getPoolName()).isEqualTo("leader-lock");
        assertThat(leaderLock.getMaximumPoolSize()).isEqualTo(2);
    }

    @Test
    void leaderLockPool_keepsEveryConnectionOpen_evenWhenApplicationPoolLetsIdleOnesGo() {
        HikariDataSource leaderLock = LeaderLockPools.leaderLockPool(application(),
                applicationPoolProperties().withProperty("spring.datasource.hikari.minimum-idle", "0"),
                new LeaderLockPoolProperties(2), SESSION_TIMEOUTS);

        assertThat(leaderLock.getMinimumIdle()).isEqualTo(2);
    }

    @Test
    void leaderLockPool_waitsForLocksWithoutLimit_andKeepsOtherSessionTimeouts() {
        HikariDataSource leaderLock = leaderLockPool(ONE_CONNECTION);

        assertThat(leaderLock.getDataSourceProperties()).containsEntry("options",
                "-c lock_timeout=0 -c statement_timeout=30s -c idle_in_transaction_session_timeout=1min");
    }

    @Test
    void leaderLockPool_namesItsSessionsForTheDatabase() {
        HikariDataSource leaderLock = leaderLockPool(ONE_CONNECTION);

        assertThat(leaderLock.getDataSourceProperties()).containsEntry("ApplicationName", "data-reference-leader-lock");
    }

    @Test
    void leaderLockPool_keepsDriverPropertiesAndWaitSettingsOfApplicationPool() {
        HikariDataSource application = application();

        HikariDataSource leaderLock = LeaderLockPools.leaderLockPool(
                application, applicationPoolProperties(), ONE_CONNECTION, SESSION_TIMEOUTS);

        assertThat(leaderLock.getDataSourceProperties())
                .containsEntry("logServerErrorDetail", "false")
                .containsEntry("socketTimeout", "60");
        assertThat(leaderLock.getConnectionTimeout()).isEqualTo(2000);
        assertThat(leaderLock.getValidationTimeout()).isEqualTo(1000);
        assertThat(application.getDataSourceProperties())
                .containsEntry("options", "-c lock_timeout=5s")
                .doesNotContainKey("ApplicationName");
    }

    @Test
    void leaderLockPool_isNotStartedYet() {
        assertThat(leaderLockPool(ONE_CONNECTION).isRunning()).isFalse();
    }

    private static HikariDataSource leaderLockPool(LeaderLockPoolProperties settings) {
        return LeaderLockPools.leaderLockPool(application(), applicationPoolProperties(), settings, SESSION_TIMEOUTS);
    }

    private static HikariDataSource application() {
        HikariDataSource application = new HikariDataSource();
        application.setPoolName("application");
        application.setJdbcUrl("jdbc:postgresql://db:5432/data_reference");
        application.setUsername("data_reference");
        application.setPassword("secret");
        application.setDriverClassName("org.postgresql.Driver");
        application.addDataSourceProperty("options", "-c lock_timeout=5s");
        application.addDataSourceProperty("socketTimeout", "60");
        return application;
    }

    private static MockEnvironment applicationPoolProperties() {
        return new MockEnvironment()
                .withProperty("spring.datasource.hikari.pool-name", "application")
                .withProperty("spring.datasource.hikari.maximum-pool-size", "10")
                .withProperty("spring.datasource.hikari.connection-timeout", "2000")
                .withProperty("spring.datasource.hikari.validation-timeout", "1000")
                .withProperty("spring.datasource.hikari.data-source-properties.logServerErrorDetail", "false")
                .withProperty("spring.datasource.hikari.data-source-properties.socketTimeout", "60");
    }
}
