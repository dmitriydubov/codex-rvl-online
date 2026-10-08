package ru.projectx.datareference.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariDataSource;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/**
 * Пул REST-чтений берёт у пула приложения адрес, учётные данные и общие свойства {@code spring.datasource.hikari.*},
 * а имя, размер и тайм-ауты у него свои. Пул приложения при этом не меняется.
 */
class RestReadPoolsTest {

    private static final RestReadPoolProperties SETTINGS =
            new RestReadPoolProperties(5, Duration.ofMillis(1500), 7, 2);

    @Test
    void restReadPool_takesAddressAndCredentialsOfApplicationPool() {
        HikariDataSource restRead = RestReadPools.restReadPool(application(), applicationPoolProperties(), SETTINGS);

        assertThat(restRead.getJdbcUrl()).isEqualTo("jdbc:postgresql://db:5432/data_reference");
        assertThat(restRead.getUsername()).isEqualTo("data_reference");
        assertThat(restRead.getPassword()).isEqualTo("secret");
        assertThat(restRead.getDriverClassName()).isEqualTo("org.postgresql.Driver");
    }

    /** Драйвер найдётся по адресу, поэтому без явного драйвера у пула приложения его нет и у пула REST-чтений. */
    @Test
    void restReadPool_withoutDriverOfApplicationPool_leavesDriverUnset() {
        HikariDataSource application = new HikariDataSource();
        application.setJdbcUrl("jdbc:postgresql://db:5432/data_reference");

        HikariDataSource restRead = RestReadPools.restReadPool(application, new MockEnvironment(), SETTINGS);

        assertThat(restRead.getDriverClassName()).isNull();
    }

    @Test
    void restReadPool_hasOwnNameSizeAndConnectionWait() {
        HikariDataSource restRead = RestReadPools.restReadPool(application(), applicationPoolProperties(), SETTINGS);

        assertThat(restRead.getPoolName()).isEqualTo("rest-read");
        assertThat(restRead.getMaximumPoolSize()).isEqualTo(5);
        assertThat(restRead.getConnectionTimeout()).isEqualTo(1500);
    }

    @Test
    void restReadPool_keepsDriverPropertiesButOwnSocketAndCancelTimeouts() {
        HikariDataSource application = application();

        HikariDataSource restRead = RestReadPools.restReadPool(application, applicationPoolProperties(), SETTINGS);

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
    void restReadPool_takesOtherSettingsOfApplicationPool() {
        HikariDataSource restRead = RestReadPools.restReadPool(application(), applicationPoolProperties(), SETTINGS);

        assertThat(restRead.getValidationTimeout()).isEqualTo(1000);
        assertThat(restRead.getLeakDetectionThreshold()).isEqualTo(30_000);
        assertThat(restRead.getSchema()).isEqualTo("public");
    }

    /** Соединения пул откроет при первом REST-чтении, как и пул приложения. */
    @Test
    void restReadPool_isNotStartedYet() {
        HikariDataSource restRead = RestReadPools.restReadPool(application(), applicationPoolProperties(), SETTINGS);

        assertThat(restRead.isRunning()).isFalse();
    }

    private static HikariDataSource application() {
        HikariDataSource application = new HikariDataSource();
        application.setPoolName("application");
        application.setJdbcUrl("jdbc:postgresql://db:5432/data_reference");
        application.setUsername("data_reference");
        application.setPassword("secret");
        application.setDriverClassName("org.postgresql.Driver");
        application.addDataSourceProperty("socketTimeout", "60");
        return application;
    }

    /** Свойства пула приложения, как в {@code application.yml}, с собственными именем, размером и ожиданием. */
    private static MockEnvironment applicationPoolProperties() {
        return new MockEnvironment()
                .withProperty("spring.datasource.hikari.pool-name", "application")
                .withProperty("spring.datasource.hikari.maximum-pool-size", "10")
                .withProperty("spring.datasource.hikari.connection-timeout", "2000")
                .withProperty("spring.datasource.hikari.validation-timeout", "1000")
                .withProperty("spring.datasource.hikari.leak-detection-threshold", "30000")
                .withProperty("spring.datasource.hikari.schema", "public")
                .withProperty("spring.datasource.hikari.data-source-properties.options", "-c lock_timeout=5s")
                .withProperty("spring.datasource.hikari.data-source-properties.logServerErrorDetail", "false")
                .withProperty("spring.datasource.hikari.data-source-properties.socketTimeout", "60");
    }
}
