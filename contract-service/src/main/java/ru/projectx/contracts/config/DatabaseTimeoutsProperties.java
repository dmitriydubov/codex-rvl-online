package ru.projectx.contracts.config;

import jakarta.validation.constraints.NotBlank;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("projectx.database.session-timeouts")
public record DatabaseTimeoutsProperties(
        @NotBlank String lockTimeout,
        @NotBlank String statementTimeout,
        @NotBlank String idleInTransactionSessionTimeout) {

    public Map<String, String> byParameter() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("lock_timeout", lockTimeout);
        values.put("statement_timeout", statementTimeout);
        values.put("idle_in_transaction_session_timeout", idleInTransactionSessionTimeout);
        return values;
    }
}
