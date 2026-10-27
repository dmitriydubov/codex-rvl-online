package ru.projectx.contracts.service.leadership;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.metrics.micrometer.MicrometerMetricsTrackerFactory;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import ru.projectx.contracts.config.DatabaseTimeoutsProperties;

class LeaderLockPoolTest {

    private static final DatabaseTimeoutsProperties TIMEOUTS = new DatabaseTimeoutsProperties("5s", "30s", "1min");
    private static final String URL = "jdbc:postgresql://db.test:5432/contract_service?logServerErrorDetail=false";

    private final HikariDataSource application = applicationPool();
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    @AfterEach
    void closePools() {
        application.close();
    }

    @Test
    void poolBesideTheApplicationPool_sharesAddressCredentialsAndTimeoutsButDropsLockTimeout() {
        try (LeaderLockPool pool = LeaderLockPool.besideApplicationPool(
                new DelegatingDataSource(application), TIMEOUTS, 2, registry)) {
            HikariDataSource connections = (HikariDataSource) pool.dataSource();

            assertThat(connections.getPoolName()).isEqualTo("leader-lock");
            assertThat(connections.getJdbcUrl()).isEqualTo(URL);
            assertThat(connections.getUsername()).isEqualTo("contract_service");
            assertThat(connections.getPassword()).isEqualTo("secret-for-test");
            assertThat(connections.getDriverClassName()).isEqualTo("org.postgresql.Driver");
            assertThat(connections.getConnectionTimeout()).isEqualTo(2500);
            assertThat(connections.getValidationTimeout()).isEqualTo(900);
            assertThat(connections.getMaximumPoolSize()).isEqualTo(2);
            assertThat(connections.getMinimumIdle()).isEqualTo(2);
            assertThat(connections.getDataSourceProperties())
                    .containsEntry("options", "-c statement_timeout=30s -c idle_in_transaction_session_timeout=1min")
                    .containsEntry("socketTimeout", "60")
                    .containsEntry("logServerErrorDetail", "false");
            assertThat(connections.getMetricsTrackerFactory()).isInstanceOf(MicrometerMetricsTrackerFactory.class);
        }
    }

    @Test
    void applicationPoolSettings_stayUntouched() {
        try (LeaderLockPool pool = LeaderLockPool.besideApplicationPool(application, TIMEOUTS, 1, registry)) {
            assertThat(pool.dataSource()).isNotSameAs(application);
        }

        assertThat(application.getDataSourceProperties().getProperty("options")).contains("lock_timeout=5s");
        assertThat(application.getMaximumPoolSize()).isEqualTo(10);
    }

    @Test
    void close_closesOnlyTheLeaderLockPool() {
        LeaderLockPool pool = LeaderLockPool.besideApplicationPool(application, TIMEOUTS, 1, registry);

        pool.close();

        assertThat(((HikariDataSource) pool.dataSource()).isClosed()).isTrue();
        assertThat(application.isClosed()).isFalse();
    }

    @Test
    void applicationPoolThatIsNotHikari_isRefused() {
        DriverManagerDataSource plain = new DriverManagerDataSource(URL, "contract_service", "secret-for-test");

        assertThatThrownBy(() -> LeaderLockPool.besideApplicationPool(plain, TIMEOUTS, 1, registry))
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
        pool.setConnectionTimeout(2500);
        pool.setValidationTimeout(900);
        pool.setMaximumPoolSize(10);
        pool.addDataSourceProperty("options",
                "-c lock_timeout=5s -c statement_timeout=30s -c idle_in_transaction_session_timeout=1min");
        pool.addDataSourceProperty("socketTimeout", "60");
        pool.addDataSourceProperty("logServerErrorDetail", "false");
        return pool;
    }
}
