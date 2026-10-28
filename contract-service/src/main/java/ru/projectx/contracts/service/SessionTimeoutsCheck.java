package ru.projectx.contracts.service;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import ru.projectx.contracts.config.DatabaseTimeoutsProperties;
import ru.projectx.contracts.repository.database.SessionSettingsRepository;

public final class SessionTimeoutsCheck {

    static final String LOCK_TIMEOUT = "lock_timeout";
    static final String STATEMENT_TIMEOUT = "statement_timeout";
    static final String IDLE_IN_TRANSACTION_TIMEOUT = "idle_in_transaction_session_timeout";
    static final String DISABLED = "0";

    private static final Logger log = LoggerFactory.getLogger(SessionTimeoutsCheck.class);

    private final String pool;
    private final Map<String, String> configured;
    private final SessionSettingsRepository sessionSettings;
    private final List<String> parameters;

    private SessionTimeoutsCheck(String pool, Map<String, String> configured, SessionSettingsRepository sessionSettings) {
        this.pool = pool;
        this.configured = configured;
        this.sessionSettings = sessionSettings;
        this.parameters = List.copyOf(configured.keySet());
    }

    public static SessionTimeoutsCheck forApplicationPool(String pool, DatabaseTimeoutsProperties timeouts,
            SessionSettingsRepository sessionSettings) {
        return new SessionTimeoutsCheck(pool, byParameter(timeouts.lockTimeout(), timeouts), sessionSettings);
    }

    public static SessionTimeoutsCheck forPoolWithoutLockTimeout(String pool, DatabaseTimeoutsProperties timeouts,
            SessionSettingsRepository sessionSettings) {
        return new SessionTimeoutsCheck(pool, byParameter(DISABLED, timeouts), sessionSettings);
    }

    public void run() {
        read().ifPresent(this::check);
    }

    private Optional<Map<String, Duration>> read() {
        try {
            return Optional.of(sessionSettings.durations(parameters));
        }
        catch (DataAccessException failure) {
            log.warn("проверка таймаутов PostgreSQL: сеанс пула {} не прочитан: {}, SQLSTATE {}", pool,
                    failure.getClass().getSimpleName(), SqlStates.deepestOrNone(failure));
            return Optional.empty();
        }
    }

    private void check(Map<String, Duration> actual) {
        for (Map.Entry<String, String> setting : configured.entrySet()) {
            checkParameter(setting.getKey(), setting.getValue(), actual);
        }
    }

    private void checkParameter(String parameter, String configuredValue, Map<String, Duration> actual) {
        Optional<Duration> expected = PostgresDurations.parse(configuredValue);
        if (expected.isEmpty()) {
            log.warn("проверка таймаутов PostgreSQL: настройка {} пула {} не разобрана, параметр не проверен",
                    parameter, pool);
            return;
        }
        Optional<Duration> found = Optional.ofNullable(actual.get(parameter));
        if (found.filter(expected.get()::equals).isEmpty()) {
            log.warn("таймаут сеанса PostgreSQL расходится с настройкой: пул={}, параметр={}, ожидается={}, "
                    + "фактически={}", pool, parameter, expected.get(), found.map(Duration::toString).orElse("неизвестно"));
        }
    }

    private static Map<String, String> byParameter(String lockTimeout, DatabaseTimeoutsProperties timeouts) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put(LOCK_TIMEOUT, lockTimeout);
        values.put(STATEMENT_TIMEOUT, timeouts.statementTimeout());
        values.put(IDLE_IN_TRANSACTION_TIMEOUT, timeouts.idleInTransactionSessionTimeout());
        return values;
    }
}
