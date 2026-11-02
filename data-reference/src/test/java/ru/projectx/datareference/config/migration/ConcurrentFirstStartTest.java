package ru.projectx.datareference.config.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import ru.projectx.datareference.support.IntegrationTest;
import ru.projectx.datareference.support.ServiceInstances;

@IntegrationTest
class ConcurrentFirstStartTest {

    private static final int TWO_INSTANCES = 2;
    private static final int INSTANCES_DURING_THREE_REPLICA_ROLLOUT = 4;
    private static final int RACE_ATTEMPTS = 5;
    private static final Duration START_TIMEOUT = Duration.ofMinutes(3);
    private static final String DATABASE_IN_URL = "^(jdbc:postgresql://[^/]+/)[^?]*";
    private static final String APPLIED_CHANGE_SETS = "select count(*) from databasechangelog";
    private static final String CHANGE_SETS_APPLIED_MORE_THAN_ONCE = """
            select id || ' ' || author || ' ' || filename
              from databasechangelog
             group by id, author, filename
            having count(*) > 1
            """;
    private static final String LOCK_HELD = "select locked from databasechangeloglock where id = 1";
    private static final String LOCK_LEFT_BY_DEAD_INSTANCE = """
            create table databasechangeloglock (
              id integer primary key, locked boolean not null, lockgranted timestamp, lockedby varchar(255));
            insert into databasechangeloglock values (1, true, now(), 'погибший экземпляр');
            """;

    @Autowired
    private Environment environment;

    @Autowired
    private DataSource dataSource;

    private final List<ConfigurableApplicationContext> instances = new ArrayList<>();
    private String emptyDatabase;

    @BeforeEach
    void createEmptyDatabase() {
        emptyDatabase = "first_start_" + UUID.randomUUID().toString().replace("-", "");
        JdbcClient.create(dataSource).sql("create database " + emptyDatabase).update();
    }

    @AfterEach
    void stopInstancesAndDropDatabase() {
        instances.forEach(ConfigurableApplicationContext::close);
        JdbcClient.create(dataSource).sql("drop database if exists " + emptyDatabase + " with (force)").update();
    }

    @RepeatedTest(RACE_ATTEMPTS)
    void twoInstancesStartingTogetherOnEmptyDatabase_bothStartAtFirstAttempt_andApplyEveryChangeSetOnce()
            throws Exception {
        instances.addAll(startTogether(TWO_INSTANCES, emptyDatabaseUrl()));

        assertEveryInstanceStartedAndMigrationsAppliedOnce(TWO_INSTANCES);
    }

    @Test
    void fourInstancesStartingTogetherOnEmptyDatabase_allStartAtFirstAttempt_andApplyEveryChangeSetOnce()
            throws Exception {
        instances.addAll(startTogether(INSTANCES_DURING_THREE_REPLICA_ROLLOUT, emptyDatabaseUrl()));

        assertEveryInstanceStartedAndMigrationsAppliedOnce(INSTANCES_DURING_THREE_REPLICA_ROLLOUT);
    }

    @Test
    void lockLeftByDeadInstance_isReleasedByStartingInstances_andBothStartAtFirstAttempt() throws Exception {
        JdbcClient.create(emptyDatabaseConnections()).sql(LOCK_LEFT_BY_DEAD_INSTANCE).update();

        instances.addAll(startTogether(TWO_INSTANCES, emptyDatabaseUrl()));

        assertEveryInstanceStartedAndMigrationsAppliedOnce(TWO_INSTANCES);
    }

    private void assertEveryInstanceStartedAndMigrationsAppliedOnce(int started) {
        JdbcClient emptyDatabaseJdbc = JdbcClient.create(emptyDatabaseConnections());
        assertThat(instances).hasSize(started).allMatch(ConfigurableApplicationContext::isActive);
        assertThat(emptyDatabaseJdbc.sql(CHANGE_SETS_APPLIED_MORE_THAN_ONCE).query(String.class).list())
                .as("changeSet'ы, записанные дважды").isEmpty();
        assertThat(emptyDatabaseJdbc.sql(APPLIED_CHANGE_SETS).query(Long.class).single())
                .as("применены все changeSet'ы журнала")
                .isEqualTo(JdbcClient.create(dataSource).sql(APPLIED_CHANGE_SETS).query(Long.class).single());
        assertThat(emptyDatabaseJdbc.sql(LOCK_HELD).query(Boolean.class).single()).as("замок миграций занят").isFalse();
    }

    private List<ConfigurableApplicationContext> startTogether(int count, String databaseUrl) throws Exception {
        CyclicBarrier sameMoment = new CyclicBarrier(count);
        ExecutorService starters = Executors.newFixedThreadPool(count);
        try {
            List<CompletableFuture<ConfigurableApplicationContext>> starting = new ArrayList<>();
            for (int instance = 0; instance < count; instance++) {
                starting.add(CompletableFuture.supplyAsync(() -> startAfter(sameMoment, databaseUrl), starters));
            }
            List<ConfigurableApplicationContext> started = new ArrayList<>();
            for (CompletableFuture<ConfigurableApplicationContext> instance : starting) {
                started.add(instance.get(START_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS));
            }
            return started;
        } finally {
            starters.shutdownNow();
        }
    }

    private ConfigurableApplicationContext startAfter(CyclicBarrier sameMoment, String databaseUrl) {
        try {
            sameMoment.await(START_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("старт экземпляра прерван", interrupted);
        } catch (BrokenBarrierException | TimeoutException notTogether) {
            throw new IllegalStateException("экземпляры не дошли до общего старта", notTogether);
        }
        return ServiceInstances.startWithoutBrokerOnSharedLogging(environment, databaseUrl);
    }

    private String emptyDatabaseUrl() {
        return environment.getRequiredProperty("spring.datasource.url").replaceFirst(DATABASE_IN_URL, "$1" + emptyDatabase);
    }

    private DataSource emptyDatabaseConnections() {
        return new DriverManagerDataSource(emptyDatabaseUrl(),
                environment.getRequiredProperty("spring.datasource.username"),
                environment.getRequiredProperty("spring.datasource.password"));
    }
}
