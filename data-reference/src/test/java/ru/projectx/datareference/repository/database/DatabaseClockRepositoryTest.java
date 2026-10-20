package ru.projectx.datareference.repository.database;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Objects;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ru.projectx.datareference.support.FixedClockConfig;
import ru.projectx.datareference.support.IntegrationTest;

@IntegrationTest
class DatabaseClockRepositoryTest {

    private static final Duration PAUSE_IN_DATABASE = Duration.ofMillis(200);

    @Autowired
    private DatabaseClockRepository databaseClock;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private PlatformTransactionManager transactions;

    @Test
    void currentMoment_comesFromDatabaseClock_notFromInstanceClock() {
        Instant before = databaseMoment("select clock_timestamp()");

        Instant moment = databaseClock.currentMoment();

        assertThat(moment).isBetween(before, databaseMoment("select clock_timestamp()"));
        assertThat(moment).isNotEqualTo(FixedClockConfig.NOW);
    }

    @Test
    void currentMoment_isActualTimeOfTheCall_notStartOfTheTransaction() {
        Instant[] moments = Objects.requireNonNull(new TransactionTemplate(transactions).execute(status -> {
            Instant start = databaseMoment("select now()");
            JdbcClient.create(dataSource).sql("select pg_sleep(:seconds)")
                    .param("seconds", PAUSE_IN_DATABASE.toMillis() / 1000.0)
                    .query(Object.class).list();
            return new Instant[] {start, databaseClock.currentMoment(), databaseMoment("select now()")};
        }));

        assertThat(moments[2]).as("начало транзакции после паузы — та же транзакция").isEqualTo(moments[0]);
        assertThat(Duration.between(moments[0], moments[1])).isGreaterThanOrEqualTo(PAUSE_IN_DATABASE);
    }

    private Instant databaseMoment(String query) {
        return JdbcClient.create(dataSource).sql(query).query(OffsetDateTime.class).single().toInstant();
    }
}
