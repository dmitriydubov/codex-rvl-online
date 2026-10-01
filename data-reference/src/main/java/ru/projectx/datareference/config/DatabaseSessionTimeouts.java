package ru.projectx.datareference.config;

import jakarta.validation.constraints.NotBlank;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Таймауты сеанса PostgreSQL, которые пул приложения передаёт в стартовом {@code options}
 * (Ш4): {@code application.yml} подставляет эти же значения в
 * {@code spring.datasource.hikari.data-source-properties.options}, так что настройка одна.
 * Значения — в записи PostgreSQL, с единицей ({@code 5s}, {@code 1min}).
 *
 * <p>Отдельной записью они нужны проверке при старте (Ш41): пулер с
 * {@code ignore_startup_parameters} молча выбрасывает {@code options}, и сервис работал бы
 * без ограничений, а журнал был бы чист.
 */
@Validated
@ConfigurationProperties("projectx.database.session-timeouts")
public record DatabaseSessionTimeouts(
        @NotBlank String lockTimeout,
        @NotBlank String statementTimeout,
        @NotBlank String idleInTransactionSessionTimeout) {

    /** Значения по именам параметров PostgreSQL, в порядке их объявления. */
    public Map<String, String> byParameter() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("lock_timeout", lockTimeout);
        values.put("statement_timeout", statementTimeout);
        values.put("idle_in_transaction_session_timeout", idleInTransactionSessionTimeout);
        return values;
    }
}
