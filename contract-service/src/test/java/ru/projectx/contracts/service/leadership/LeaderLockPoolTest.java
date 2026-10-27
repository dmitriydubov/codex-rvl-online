package ru.projectx.contracts.service.leadership;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.metrics.micrometer.MicrometerMetricsTrackerFactory;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class LeaderLockPoolTest {

    private static final String URL = "jdbc:postgresql://db.test:5432/contract_service?logServerErrorDetail=false";
    private static final String APPLICATION_OPTIONS =
            "-c lock_timeout=5s -c statement_timeout=30s -c idle_in_transaction_session_timeout=1min";

    private final HikariDataSource application = applicationPool();
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    @AfterEach
    void closeApplicationPool() {
        application.close();
    }

    @Test
    void poolBesideTheApplicationPool_copiesItsSettingsAndDisablesOnlyTheLockTimeout() {
        application.addDataSourceProperty("options", APPLICATION_OPTIONS);

        try (LeaderLockPool pool = LeaderLockPool.besideApplicationPool(new DelegatingDataSource(application), 2, registry)) {
            HikariDataSource connections = (HikariDataSource) pool.dataSource();

            assertThat(connections.getPoolName()).isEqualTo("leader-lock");
            assertThat(connections.getJdbcUrl()).isEqualTo(URL);
            assertThat(connections.getUsername()).isEqualTo("contract_service");
            assertThat(connections.getPassword()).isEqualTo("secret-for-test");
            assertThat(connections.getDriverClassName()).isEqualTo("org.postgresql.Driver");
            assertThat(connections.getSchema()).isEqualTo("contracts");
            assertThat(connections.getConnectionInitSql()).isEqualTo("select 1");
            assertThat(connections.getTransactionIsolation()).isEqualTo("TRANSACTION_READ_COMMITTED");
            assertThat(connections.getConnectionTimeout()).isEqualTo(2500);
            assertThat(connections.getValidationTimeout()).isEqualTo(900);
            assertThat(connections.getIdleTimeout()).isEqualTo(300_000);
            assertThat(connections.getMaxLifetime()).isEqualTo(1_234_567);
            assertThat(connections.getKeepaliveTime()).isEqualTo(45_000);
            assertThat(connections.isReadOnly()).isFalse();
            assertThat(connections.getMaximumPoolSize()).isEqualTo(2);
            assertThat(connections.getMinimumIdle()).isEqualTo(2);
            assertThat(connections.getDataSourceProperties())
                    .containsEntry("options",
                            "-c lock_timeout=0 -c statement_timeout=30s -c idle_in_transaction_session_timeout=1min")
                    .containsEntry("socketTimeout", "60")
                    .containsEntry("logServerErrorDetail", "false");
            assertThat(connections.getMetricsTrackerFactory()).isInstanceOf(MicrometerMetricsTrackerFactory.class);
        }
    }

    @Test
    void applicationPoolSettings_stayUntouched() {
        application.addDataSourceProperty("options", APPLICATION_OPTIONS);

        try (LeaderLockPool pool = LeaderLockPool.besideApplicationPool(application, 1, registry)) {
            assertThat(pool.dataSource()).isNotSameAs(application);
            assertThat(((HikariDataSource) pool.dataSource()).getDataSourceProperties())
                    .isNotSameAs(application.getDataSourceProperties());
        }

        assertThat(application.getPoolName()).isEqualTo("application");
        assertThat(application.getDataSourceProperties().getProperty("options")).isEqualTo(APPLICATION_OPTIONS);
        assertThat(application.getMaximumPoolSize()).isEqualTo(10);
        assertThat(application.getMetricsTrackerFactory()).isNull();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "-c lock_timeout=5s -c statement_timeout=30s | -c lock_timeout=0 -c statement_timeout=30s",
            "-c statement_timeout=30s -c   lock_timeout=5000 | -c statement_timeout=30s -c lock_timeout=0",
            "-c statement_timeout=30s | -c statement_timeout=30s -c lock_timeout=0",
            "'' | -c lock_timeout=0"})
    void sessionOptions_differFromTheApplicationPoolOnlyByADisabledLockTimeout(String application, String leaderLock) {
        assertThat(LeaderLockPool.lockTimeoutDisabled(application)).isEqualTo(leaderLock);
    }

    @Test
    void applicationPoolWithoutSessionOptions_getsOnlyTheDisabledLockTimeout() {
        try (LeaderLockPool pool = LeaderLockPool.besideApplicationPool(application, 1, registry)) {
            assertThat(((HikariDataSource) pool.dataSource()).getDataSourceProperties()).containsOnly(
                    Map.entry("options", "-c lock_timeout=0"),
                    Map.entry("socketTimeout", "60"),
                    Map.entry("logServerErrorDetail", "false"));
        }
    }

    @Test
    void close_closesOnlyTheLeaderLockPool() {
        LeaderLockPool pool = LeaderLockPool.besideApplicationPool(application, 1, registry);

        pool.close();

        assertThat(((HikariDataSource) pool.dataSource()).isClosed()).isTrue();
        assertThat(application.isClosed()).isFalse();
    }

    @Test
    void applicationPoolThatIsNotHikari_isRefused() {
        DriverManagerDataSource plain = new DriverManagerDataSource(URL, "contract_service", "secret-for-test");

        assertThatThrownBy(() -> LeaderLockPool.besideApplicationPool(plain, 1, registry))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(DriverManagerDataSource.class.getName());
    }

    private static HikariDataSource applicationPool() {
        HikariDataSource pool = new HikariDataSource();
        pool.setPoolName("application");
        pool.setJdbcUrl(URL);
        pool.setUsername("contract_service");
        pool.setPassword("secret-for-test");
        pool.setDriverClassName("org.postgresql.Driver");
        pool.setSchema("contracts");
        pool.setConnectionInitSql("select 1");
        pool.setTransactionIsolation("TRANSACTION_READ_COMMITTED");
        pool.setConnectionTimeout(2500);
        pool.setValidationTimeout(900);
        pool.setIdleTimeout(300_000);
        pool.setMaxLifetime(1_234_567);
        pool.setKeepaliveTime(45_000);
        pool.setMaximumPoolSize(10);
        pool.addDataSourceProperty("socketTimeout", "60");
        pool.addDataSourceProperty("logServerErrorDetail", "false");
        return pool;
    }
}
