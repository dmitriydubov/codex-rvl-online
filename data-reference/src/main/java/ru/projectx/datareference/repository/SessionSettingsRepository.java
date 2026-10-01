package ru.projectx.datareference.repository;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Действующие параметры времени сеанса PostgreSQL на соединении из данного {@link DataSource}:
 * {@code pg_settings.setting} в миллисекундах (у {@code lock_timeout}, {@code statement_timeout}
 * и {@code idle_in_transaction_session_timeout} единица — {@code ms}). Только чтение: база
 * и роли не меняются. Параметр без единицы {@code ms} в ответ не попадает.
 */
@Repository
public class SessionSettingsRepository {

    private static final String MILLISECOND_SETTINGS = """
            select name, setting
              from pg_settings
             where unit = 'ms'
               and name in (:names)
            """;

    public Map<String, Duration> durations(DataSource source, List<String> parameters) {
        return JdbcClient.create(source).sql(MILLISECOND_SETTINGS)
                .param("names", parameters)
                .query((row, number) -> Map.entry(row.getString("name"), Duration.ofMillis(row.getLong("setting"))))
                .list()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
