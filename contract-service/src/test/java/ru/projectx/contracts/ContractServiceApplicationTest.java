package ru.projectx.contracts;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import ru.projectx.contracts.support.FixedClockConfig;
import ru.projectx.contracts.support.IntegrationTest;

/**
 * Контекст поднимается, и Liquibase накатывает схему. Так ловятся поломки, которые иначе видны только
 * на стенде: несогласованные бины, невалидная конфигурация, неприменимый changeSet.
 */
@IntegrationTest
class ContractServiceApplicationTest {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private Clock clock;

    /** Журнал миграций появляется, только если Liquibase действительно прошёл по оглавлению на этой базе. */
    @Test
    void migrations_runOnStartup() {
        Integer journals = jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_name = 'databasechangelog'", Integer.class);

        assertThat(journals).isEqualTo(1);
    }

    /**
     * Порт 8081 открывает образ, и на него настроены мониторинг и пробы. В тестах на случайных портах умолчание
     * подменено, а MANAGEMENT_PORT там, где идут тесты, не задают.
     */
    @Test
    void managementPort_defaultsTo8081() {
        assertThat(context.getEnvironment().getProperty("management.server.port", Integer.class)).isEqualTo(8081);
    }

    @Test
    void clock_isTheStoppedTestClock() {
        assertThat(clock.instant()).isEqualTo(FixedClockConfig.NOW);
    }
}
