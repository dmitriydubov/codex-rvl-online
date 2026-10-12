package ru.projectx.datareference.acceptance;

import io.cucumber.java.Before;
import java.util.List;
import java.util.Objects;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.simple.JdbcClient;

public class DatabaseCleanupHook {

    private static final String DOMAIN_TABLES = """
            select quote_ident(tablename)
              from pg_tables
             where schemaname = 'public'
               and tablename not like 'databasechangelog%'
            """;

    private final JdbcClient jdbc;
    private final CacheManager cacheManager;

    public DatabaseCleanupHook(JdbcClient jdbc, CacheManager cacheManager) {
        this.jdbc = jdbc;
        this.cacheManager = cacheManager;
    }

    @Before
    public void emptyDatabase() {
        List<String> tables = jdbc.sql(DOMAIN_TABLES).query(String.class).list();
        jdbc.sql("truncate table " + String.join(", ", tables) + " cascade").update();
        cacheManager.getCacheNames().forEach(name -> Objects.requireNonNull(cacheManager.getCache(name)).invalidate());
    }
}
