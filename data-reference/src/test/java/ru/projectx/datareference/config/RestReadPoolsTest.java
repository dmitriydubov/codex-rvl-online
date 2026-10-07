package ru.projectx.datareference.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariDataSource;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/**
 * Пул REST-чтений берёт у пула приложения адрес, учётные данные и параметры драйвера, а размер, ожидание
 * соединения и сетевой тайм-аут чтения — свои. Пул приложения при этом не меняется.
 */
class RestReadPoolsTest {

    private static final RestReadPoolProperties SETTINGS =
            new RestReadPoolProperties(5, Duration.ofMillis(1500), 7, 2);

    @Test
    void restReadPool_takesAddressAndCredentialsOfApplicationPool() {
        HikariDataSource restRead = RestReadPools.restReadPool(application(), SETTINGS);

        assertThat(restRead.getJdbcUrl()).isEqualTo("jdbc:postgresql://db:5432/data_reference");
        assertThat(restRead.getUsername()).isEqualTo("data_reference");
        assertThat(restRead.getPassword()).isEqualTo("secret");
        assertThat(restRead.getDriverClassName()).isEqualTo("org.postgresql.Driver");
    }

    @Test
    void restReadPool_hasOwnNameSizeAndConnectionWait() {
        HikariDataSource restRead = RestReadPools.restReadPool(application(), SETTINGS);

        assertThat(restRead.getPoolName()).isEqualTo("rest-read");
        assertThat(restRead.getMaximumPoolSize()).isEqualTo(5);
        assertThat(restRead.getConnectionTimeout()).isEqualTo(1500);
    }

    @Test
    void restReadPool_keepsDriverPropertiesButOwnSocketAndCancelTimeouts() {
        HikariDataSource application = application();

        HikariDataSource restRead = RestReadPools.restReadPool(application, SETTINGS);

        assertThat(restRead.getDataSourceProperties())
                .containsEntry("options", "-c lock_timeout=5s")
                .containsEntry("logServerErrorDetail", "false")
                .containsEntry("socketTimeout", "7")
                .containsEntry("cancelSignalTimeout", "2");
        assertThat(application.getDataSourceProperties())
                .containsEntry("socketTimeout", "60")
                .doesNotContainKey("cancelSignalTimeout");
    }

    @Test
    void restReadPool_checksConnectionsLikeApplicationPool() {
        HikariDataSource restRead = RestReadPools.restReadPool(application(), SETTINGS);

        assertThat(restRead.getValidationTimeout()).isEqualTo(1000);
        assertThat(restRead.getIdleTimeout()).isEqualTo(300_000);
        assertThat(restRead.getMaxLifetime()).isEqualTo(900_000);
        assertThat(restRead.getKeepaliveTime()).isEqualTo(60_000);
        assertThat(restRead.getConnectionInitSql()).isEqualTo("select 1");
        assertThat(restRead.getConnectionTestQuery()).isEqualTo("select 2");
        assertThat(restRead.getSchema()).isEqualTo("public");
        assertThat(restRead.getTransactionIsolation()).isEqualTo("TRANSACTION_READ_COMMITTED");
        assertThat(restRead.isAutoCommit()).isFalse();
    }

    /** Пул не открывается при сборке: соединения он откроет при первом REST-чтении, как и пул приложения. */
    @Test
    void restReadPool_isNotStartedYet() {
        HikariDataSource restRead = RestReadPools.restReadPool(application(), SETTINGS);

        assertThat(restRead.isRunning()).isFalse();
    }

    private static HikariDataSource application() {
        HikariDataSource application = new HikariDataSource();
        application.setPoolName("application");
        application.setJdbcUrl("jdbc:postgresql://db:5432/data_reference");
        application.setUsername("data_reference");
        application.setPassword("secret");
        application.setDriverClassName("org.postgresql.Driver");
        application.setMaximumPoolSize(10);
        application.setConnectionTimeout(2000);
        application.setValidationTimeout(1000);
        application.setIdleTimeout(300_000);
        application.setMaxLifetime(900_000);
        application.setKeepaliveTime(60_000);
        application.setConnectionInitSql("select 1");
        application.setConnectionTestQuery("select 2");
        application.setSchema("public");
        application.setTransactionIsolation("TRANSACTION_READ_COMMITTED");
        application.setAutoCommit(false);
        application.addDataSourceProperty("options", "-c lock_timeout=5s");
        application.addDataSourceProperty("logServerErrorDetail", "false");
        application.addDataSourceProperty("socketTimeout", "60");
        return application;
    }
}
