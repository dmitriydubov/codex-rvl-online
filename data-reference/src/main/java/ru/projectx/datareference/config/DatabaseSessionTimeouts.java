package ru.projectx.datareference.config;

import jakarta.validation.constraints.NotBlank;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("projectx.database.session-timeouts")
public record DatabaseSessionTimeouts(
        @NotBlank String lockTimeout,
        @NotBlank String statementTimeout,
        @NotBlank String idleInTransactionSessionTimeout) {

    private static final String NO_LOCK_WAIT = "0";
    private static final String STARTUP_OPTION = "-c %s=%s";
    private static final String OPTION_SEPARATOR = " ";

    public Map<String, String> byParameter() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("lock_timeout", lockTimeout);
        values.put("statement_timeout", statementTimeout);
        values.put("idle_in_transaction_session_timeout", idleInTransactionSessionTimeout);
        return values;
    }

    public DatabaseSessionTimeouts withoutLockWait() {
        return new DatabaseSessionTimeouts(NO_LOCK_WAIT, statementTimeout, idleInTransactionSessionTimeout);
    }

    public String startupOptions() {
        return byParameter().entrySet().stream()
                .map(setting -> STARTUP_OPTION.formatted(setting.getKey(), setting.getValue()))
                .collect(Collectors.joining(OPTION_SEPARATOR));
    }
}
