package ru.projectx.contracts.service;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import ru.projectx.contracts.config.DatabaseTimeoutsProperties;
import ru.projectx.contracts.repository.SessionSettingsRepository;

@Component
public class DatabaseTimeoutsCheck implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DatabaseTimeoutsCheck.class);

    private final Map<String, String> configured;
    private final SessionSettingsRepository sessionSettings;
    private final List<String> parameters;

    public DatabaseTimeoutsCheck(DatabaseTimeoutsProperties timeouts, SessionSettingsRepository sessionSettings) {
        this.configured = byParameter(timeouts);
        this.sessionSettings = sessionSettings;
        this.parameters = List.copyOf(configured.keySet());
    }

    @Override
    public void run(@Nullable ApplicationArguments arguments) {
        read().ifPresent(this::check);
    }

    private Optional<Map<String, Duration>> read() {
        try {
            return Optional.of(sessionSettings.durations(parameters));
        }
        catch (DataAccessException failure) {
            log.warn("проверка таймаутов PostgreSQL: сеанс пула не прочитан: {}, SQLSTATE {}",
                    failure.getClass().getSimpleName(), SqlStates.deepestOrNone(failure));
            return Optional.empty();
        }
    }

    private void check(Map<String, Duration> actual) {
        for (Map.Entry<String, String> setting : configured.entrySet()) {
            checkParameter(setting.getKey(), setting.getValue(), actual);
        }
    }

    private static void checkParameter(String parameter, String configuredValue, Map<String, Duration> actual) {
        Optional<Duration> expected = PostgresDurations.parse(configuredValue);
        if (expected.isEmpty()) {
            log.warn("проверка таймаутов PostgreSQL: настройка {} не разобрана, параметр не проверен", parameter);
            return;
        }
        Optional<Duration> found = Optional.ofNullable(actual.get(parameter));
        if (found.filter(expected.get()::equals).isEmpty()) {
            log.warn("таймаут сеанса PostgreSQL расходится с настройкой: параметр={}, ожидается={}, фактически={}",
                    parameter, expected.get(), found.map(Duration::toString).orElse("неизвестно"));
        }
    }

    private static Map<String, String> byParameter(DatabaseTimeoutsProperties timeouts) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("lock_timeout", timeouts.lockTimeout());
        values.put("statement_timeout", timeouts.statementTimeout());
        values.put("idle_in_transaction_session_timeout", timeouts.idleInTransactionSessionTimeout());
        return values;
    }
}
