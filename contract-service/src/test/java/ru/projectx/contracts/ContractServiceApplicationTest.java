package ru.projectx.contracts;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.registry.HealthContributorRegistry;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import ru.projectx.contracts.support.FixedClockConfig;
import ru.projectx.contracts.support.IntegrationTest;

/**
 * Контекст поднимается без брокеров, и Liquibase накатывает схему. Так ловятся поломки, которые иначе видны только
 * на стенде: несогласованные бины, невалидная конфигурация, неприменимый changeSet, подключение, которое роняет старт.
 */
@IntegrationTest
class ContractServiceApplicationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private Clock clock;

    @Autowired
    private ApplicationContext context;

    @Autowired
    private HealthContributorRegistry healthContributors;

    /** Недоступный брокер не мешает старту: подключения и шаблоны есть, но соединяться на старте им не нужно. */
    @Test
    void brokerBeans_existWithoutReachableBroker() {
        Map<String, ConnectionFactory> connectionFactories = context.getBeansOfType(ConnectionFactory.class);
        Map<String, RabbitTemplate> templates = context.getBeansOfType(RabbitTemplate.class);

        assertThat(connectionFactories).containsOnlyKeys("internalBrokerConnectionFactory",
                "onecBrokerConnectionFactory");
        assertThat(templates).containsOnlyKeys("internalBrokerTemplate", "onecBrokerTemplate");
    }

    /** Без брокера компоненты здоровья подключений выключены свойством, иначе каждый health ждал бы таймаут. */
    @Test
    void brokerHealth_isSwitchedOffInThisContext() {
        assertThat(healthContributors.getContributor("internalBroker")).isNull();
        assertThat(healthContributors.getContributor("onecBroker")).isNull();
        assertThat(healthContributors.getContributor("rabbit")).isNull();
    }

    /** Журнал миграций появляется, только если Liquibase действительно прошёл по оглавлению на этой базе. */
    @Test
    void migrations_runOnStartup() {
        Integer journals = jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_name = 'databasechangelog'", Integer.class);

        assertThat(journals).isEqualTo(1);
    }

    @Test
    void clock_isTheStoppedTestClock() {
        assertThat(clock.instant()).isEqualTo(FixedClockConfig.NOW);
    }
}
