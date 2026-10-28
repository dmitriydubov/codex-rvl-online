package ru.projectx.contracts.service.leadership;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import ru.projectx.contracts.config.DatabaseTimeoutsProperties;
import ru.projectx.contracts.repository.database.SessionSettingsRepository;
import ru.projectx.contracts.service.database.SessionTimeoutsCheck;

class LeaderLockSessionCheckTest {

    private static final DatabaseTimeoutsProperties TIMEOUTS = new DatabaseTimeoutsProperties("5s", "30s", "1min");
    private static final List<String> PARAMETERS =
            List.of("lock_timeout", "statement_timeout", "idle_in_transaction_session_timeout");

    private final SessionSettingsRepository leaderLockSession = mock();
    private final LeaderLockSessionCheck check = new LeaderLockSessionCheck(TIMEOUTS, leaderLockSession);
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
    void leaderLockSessionWithoutLockTimeoutAndWithTheOtherTimeouts_writesNoWarning() {
        given(leaderLockSession.durations(PARAMETERS)).willReturn(Map.of(
                "lock_timeout", Duration.ZERO,
                "statement_timeout", Duration.ofSeconds(30),
                "idle_in_transaction_session_timeout", Duration.ofMinutes(1)));

        check.run(null);

        assertThat(journal.list).isEmpty();
    }

    @Test
    void leaderLockSessionWithALockTimeout_warnsNamingThePool() {
        given(leaderLockSession.durations(PARAMETERS)).willReturn(Map.of(
                "lock_timeout", Duration.ofSeconds(5),
                "statement_timeout", Duration.ofSeconds(30),
                "idle_in_transaction_session_timeout", Duration.ofMinutes(1)));

        check.run(null);

        assertThat(journal.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(event.getFormattedMessage()).isEqualTo("таймаут сеанса PostgreSQL расходится с настройкой: "
                    + "пул=leader-lock, параметр=lock_timeout, ожидается=PT0S, фактически=PT5S");
        });
    }
}
