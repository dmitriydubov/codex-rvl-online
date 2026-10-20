package ru.projectx.datareference.config.cache;

import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariDataSource;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ListenerConnectionSettingsTest {

    @Test
    void listenerConnectsLikeApplicationPool_underItsOwnName() {
        try (HikariDataSource application = pool(2000)) {
            application.addDataSourceProperty("socketTimeout", "60");
            application.addDataSourceProperty("logServerErrorDetail", "false");

            Properties settings = ListenerConnectionSettings.like(application);

            assertThat(settings)
                    .containsEntry("user", "data_reference")
                    .containsEntry("password", "секрет")
                    .containsEntry("socketTimeout", "60")
                    .containsEntry("logServerErrorDetail", "false")
                    .containsEntry("ApplicationName", "data-reference-cache-notifications");
        }
    }

    @ParameterizedTest
    @CsvSource({"250, 1", "1000, 1", "2000, 2", "2500, 2", "30000, 30"})
    void connectAndLoginWait_isPoolConnectionWaitInWholeSeconds_atLeastOne(long poolWaitMillis, String seconds) {
        try (HikariDataSource application = pool(poolWaitMillis)) {
            Properties settings = ListenerConnectionSettings.like(application);

            assertThat(settings)
                    .containsEntry("connectTimeout", seconds)
                    .containsEntry("loginTimeout", seconds);
        }
    }

    private static HikariDataSource pool(long connectionTimeoutMillis) {
        HikariDataSource application = new HikariDataSource();
        application.setJdbcUrl("jdbc:postgresql://localhost:5432/data_reference");
        application.setUsername("data_reference");
        application.setPassword("секрет");
        application.setConnectionTimeout(connectionTimeoutMillis);
        return application;
    }
}
