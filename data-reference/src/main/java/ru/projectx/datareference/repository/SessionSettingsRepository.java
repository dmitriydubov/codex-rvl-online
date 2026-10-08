package ru.projectx.datareference.repository;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Действующие на соединении из {@link DataSource} параметры времени сеанса PostgreSQL, такие как {@code lock_timeout} и
 * {@code statement_timeout}. Только чтение; параметр с единицей не {@code ms} в ответ не попадает.
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
