package ru.projectx.datareference.acceptance;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
public class DatabaseContents {

    private static final List<String> STARTUP_OWN = List.of("databasechangelog", "databasechangeloglock", "leader_lock");

    private static final String TABLES = """
            select table_name
              from information_schema.tables
             where table_schema = current_schema()
               and table_type = 'BASE TABLE'
             order by table_name
            """;
    private static final String FINGERPRINT = """
            select count(*) || ':' || coalesce(md5(string_agg(row_text, E'\\n' order by row_text)), '')
              from (select contents::text as row_text from "%s" contents) rows
            """;

    private final JdbcClient jdbc;

    public DatabaseContents(DataSource dataSource) {
        this.jdbc = JdbcClient.create(dataSource);
    }

    Map<String, String> fingerprints() {
        return jdbc.sql(TABLES).query(String.class).list().stream()
                .filter(table -> !STARTUP_OWN.contains(table))
                .collect(Collectors.toMap(Function.identity(), this::fingerprint));
    }

    private String fingerprint(String table) {
        return jdbc.sql(FINGERPRINT.formatted(table)).query(String.class).single();
    }
}
