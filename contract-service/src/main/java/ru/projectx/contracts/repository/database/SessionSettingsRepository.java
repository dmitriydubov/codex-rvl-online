package ru.projectx.contracts.repository.database;

import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class SessionSettingsRepository {

    private static final String MILLISECOND_SETTINGS = """
            select name, setting
              from pg_settings
             where unit = 'ms'
               and name in (:names)
            """;

    private final JdbcClient jdbc;

    public SessionSettingsRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Map<String, Duration> durations(Collection<String> parameters) {
        return jdbc.sql(MILLISECOND_SETTINGS)
                .param("names", parameters)
                .query((row, number) -> Map.entry(row.getString("name"), Duration.ofMillis(row.getLong("setting"))))
                .list()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
