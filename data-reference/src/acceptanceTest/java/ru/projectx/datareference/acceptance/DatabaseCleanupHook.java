package ru.projectx.datareference.acceptance;

import io.cucumber.java.Before;
import java.util.List;
import java.util.Objects;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Пустая база и кэши ответов перед каждым сценарием: контекст и PostgreSQL общие на всю приёмку, и данные одного
 * сценария иначе стали бы данными следующего. Таблицы спрашиваются у базы, чтобы новая таблица не требовала правки
 * здесь; свой хук, а не очистка из src/test, потому что классы src/test приёмке не видны.
 */
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
