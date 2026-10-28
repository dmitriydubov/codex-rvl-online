package ru.projectx.contracts.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import ru.projectx.contracts.config.DatabaseTimeoutsProperties;
import ru.projectx.contracts.repository.database.SessionSettingsRepository;

class DatabaseTimeoutsCheckTest {

    private static final List<String> PARAMETERS =
            List.of("lock_timeout", "statement_timeout", "idle_in_transaction_session_timeout");
    private static final Map<String, Duration> CONFIGURED = Map.of(
            "lock_timeout", Duration.ofSeconds(5),
            "statement_timeout", Duration.ofSeconds(30),
            "idle_in_transaction_session_timeout", Duration.ofMinutes(1));
    private static final Map<String, Duration> DISABLED = Map.of(
            "lock_timeout", Duration.ZERO,
            "statement_timeout", Duration.ZERO,
            "idle_in_transaction_session_timeout", Duration.ZERO);

    private final SessionSettingsRepository sessionSettings = mock();
    private final ListAppender<ILoggingEvent> journal = new ListAppender<>();
    private final Logger log = (Logger) LoggerFactory.getLogger(SessionTimeoutsCheck.class);

    @BeforeEach
    void startJournal() {
        journal.start();
        log.addAppender(journal);
    }

    @AfterEach
    void stopJournal() {
        log.detachAppender(journal);
    }

    @Test
    void matchingTimeouts_writeNoWarning() {
        given(sessionSettings.durations(PARAMETERS)).willReturn(CONFIGURED);

        check("5s", "30s", "1min").run(null);

        assertThat(journal.list).isEmpty();
    }

    @Test
    void poolWithoutTimeouts_warnsPerParameterWithBothValues() {
        given(sessionSettings.durations(PARAMETERS)).willReturn(DISABLED);

        check("5s", "30s", "1min").run(null);

        assertThat(journal.list).allMatch(event -> event.getLevel() == Level.WARN)
                .extracting(ILoggingEvent::getFormattedMessage).containsExactly(
                        "таймаут сеанса PostgreSQL расходится с настройкой: пул=application, параметр=lock_timeout, "
                                + "ожидается=PT5S, фактически=PT0S",
                        "таймаут сеанса PostgreSQL расходится с настройкой: пул=application, параметр=statement_timeout, "
                                + "ожидается=PT30S, фактически=PT0S",
                        "таймаут сеанса PostgreSQL расходится с настройкой: пул=application, "
                                + "параметр=idle_in_transaction_session_timeout, ожидается=PT1M, фактически=PT0S");
    }

    @Test
    void parameterMissingOnServer_warnsAsUnknown() {
        given(sessionSettings.durations(PARAMETERS)).willReturn(Map.of(
                "lock_timeout", Duration.ofSeconds(5), "statement_timeout", Duration.ofSeconds(30)));

        check("5s", "30s", "1min").run(null);

        assertThat(journal.list).extracting(ILoggingEvent::getFormattedMessage).containsExactly(
                "таймаут сеанса PostgreSQL расходится с настройкой: пул=application, "
                        + "параметр=idle_in_transaction_session_timeout, ожидается=PT1M, фактически=неизвестно");
    }

    @Test
    void unparsedSetting_warnsThatTheParameterIsNotChecked() {
        given(sessionSettings.durations(PARAMETERS)).willReturn(CONFIGURED);

        check("1.5s", "30s", "1min").run(null);

        assertThat(journal.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(event.getFormattedMessage())
                    .isEqualTo("проверка таймаутов PostgreSQL: настройка lock_timeout пула application не разобрана, "
                            + "параметр не проверен");
        });
    }

    @Test
    void readFailure_warnsWithTypeAndSqlStateAndDoesNotStopTheStart() {
        given(sessionSettings.durations(PARAMETERS)).willThrow(
                new DataAccessResourceFailureException("Иванов 1980-05-17", new SQLException("нет", "08006")));

        assertThatCode(() -> check("5s", "30s", "1min").run(null)).doesNotThrowAnyException();

        assertThat(journal.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(event.getFormattedMessage()).isEqualTo("проверка таймаутов PostgreSQL: сеанс пула application не прочитан: "
                    + "DataAccessResourceFailureException, SQLSTATE 08006");
            assertThat(event.getThrowableProxy()).isNull();
        });
    }

    private DatabaseTimeoutsCheck check(String lockTimeout, String statementTimeout, String idleTimeout) {
        return new DatabaseTimeoutsCheck(new DatabaseTimeoutsProperties(lockTimeout, statementTimeout, idleTimeout),
                sessionSettings);
    }
}
