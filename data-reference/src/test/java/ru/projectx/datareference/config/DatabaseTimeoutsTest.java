package ru.projectx.datareference.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.NestedExceptionUtils;
import ru.projectx.datareference.repository.InboundMessageRepository;
import ru.projectx.datareference.repository.SessionSettingsRepository;
import ru.projectx.datareference.support.IntegrationTest;

/**
 * Таймауты PostgreSQL: пулу приложения — да, миграциям — нет.
 *
 * <p>Значения — умолчания {@code application.yml}; переменные окружения из раздела
 * datasource в тестах не заданы. База и Liquibase подключаются свойствами, как в контуре
 * (см. {@code PostgresTestcontainer}), поэтому проверяется та же конфигурация, что уйдёт в прод.
 */
@IntegrationTest
class DatabaseTimeoutsTest {

    private static final Duration LOCK_TIMEOUT = Duration.ofSeconds(5);
    /** Срок, после которого запрос считается зависшим: lock_timeout с запасом на ответ сервера. */
    private static final Duration HANG_DEADLINE = LOCK_TIMEOUT.multipliedBy(3);
    /** SQLSTATE lock_not_available — так PostgreSQL отменяет оператор по lock_timeout. */
    private static final String LOCK_NOT_AVAILABLE = "55P03";
    private static final String DISABLED = "0";

    @Autowired
    private DataSource dataSource;

    @Autowired
    private SpringLiquibase liquibase;

    @Autowired
    private InboundMessageRepository inboundMessageRepository;

    @Autowired
    private SessionSettingsRepository sessionSettings;

    @Autowired
    private DatabaseSessionTimeouts configured;

    @Test
    void applicationConnection_carriesSessionAndSocketTimeouts() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            assertThat(setting(connection, "lock_timeout")).isEqualTo("5s");
            assertThat(setting(connection, "statement_timeout")).isEqualTo("30s");
            assertThat(setting(connection, "idle_in_transaction_session_timeout")).isEqualTo("1min");
            assertThat(connection.getNetworkTimeout()).isEqualTo((int) Duration.ofSeconds(60).toMillis());
        }
    }

    /**
     * Проверка таймаутов при старте читает то же, что {@code SHOW}, — в миллисекундах из {@code pg_settings};
     * у пула это значения настройки, у миграций — нули.
     */
    @Test
    void sessionSettings_readPoolValuesAndZeroForMigrations() {
        List<String> parameters = List.copyOf(configured.byParameter().keySet());

        assertThat(sessionSettings.durations(dataSource, parameters)).containsExactlyInAnyOrderEntriesOf(Map.of(
                "lock_timeout", Duration.ofSeconds(5),
                "statement_timeout", Duration.ofSeconds(30),
                "idle_in_transaction_session_timeout", Duration.ofMinutes(1)));
        assertThat(sessionSettings.durations(liquibase.getDataSource(), parameters)).containsExactlyInAnyOrderEntriesOf(Map.of(
                "lock_timeout", Duration.ZERO,
                "statement_timeout", Duration.ZERO,
                "idle_in_transaction_session_timeout", Duration.ZERO));
    }

    /** Долгая миграция на живой базе не должна обрываться ни по одному из таймаутов пула. */
    @Test
    void migrationConnection_hasNoTimeouts() throws SQLException {
        assertThat(liquibase.getDataSource()).isNotSameAs(dataSource);
        try (Connection connection = liquibase.getDataSource().getConnection()) {
            assertThat(setting(connection, "lock_timeout")).isEqualTo(DISABLED);
            assertThat(setting(connection, "statement_timeout")).isEqualTo(DISABLED);
            assertThat(setting(connection, "idle_in_transaction_session_timeout")).isEqualTo(DISABLED);
            assertThat(connection.getNetworkTimeout()).isZero();
        }
    }

    /**
     * Чужая транзакция держит таблицу сырья — как ручной SQL на живой базе. Запрос приложения
     * отказывает по lock_timeout, а не висит, занимая единственный поток планировщиков.
     */
    @Test
    void tableLockedByAnotherSession_queryFailsAfterLockTimeout() throws SQLException {
        try (Connection anotherSession = dataSource.getConnection()) {
            anotherSession.setAutoCommit(false);
            try {
                lockInboundMessages(anotherSession);
                long started = System.nanoTime();

                Throwable failure = catchThrowable(
                        () -> assertTimeoutPreemptively(HANG_DEADLINE, () -> inboundMessageRepository.count()));

                Duration waited = Duration.ofNanos(System.nanoTime() - started);
                assertThat(NestedExceptionUtils.getMostSpecificCause(failure))
                        .isInstanceOfSatisfying(SQLException.class,
                                cause -> assertThat(cause.getSQLState()).isEqualTo(LOCK_NOT_AVAILABLE));
                assertThat(waited).isBetween(LOCK_TIMEOUT, HANG_DEADLINE);
            }
            finally {
                anotherSession.rollback();
            }
        }
    }

    private static void lockInboundMessages(Connection session) throws SQLException {
        try (Statement statement = session.createStatement()) {
            statement.execute("LOCK TABLE inbound_message IN ACCESS EXCLUSIVE MODE");
        }
    }

    private static String setting(Connection connection, String name) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SHOW " + name)) {
            result.next();
            return result.getString(1);
        }
    }
}
