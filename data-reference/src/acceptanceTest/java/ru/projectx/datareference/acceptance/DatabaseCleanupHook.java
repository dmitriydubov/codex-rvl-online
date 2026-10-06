package ru.projectx.datareference.acceptance;

import io.cucumber.java.Before;
import java.util.List;
import java.util.Objects;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Пустая база перед каждым сценарием: контекст Spring и контейнер PostgreSQL общие
 * на всю приёмку, и строки одного сценария иначе становились бы данными следующего.
 *
 * <p>Одним {@code TRUNCATE ... CASCADE} по всем таблицам схемы, кроме служебных таблиц
 * Liquibase: список спрашивается у базы, чтобы новая таблица не требовала правки здесь.
 * Свой хук, а не {@code DatabaseCleaner} из src/test: классы src/test приёмке не видны.
 *
 * <p>Вместе с базой очищаются кэши ответов справочных ручек: ответ, взятый в кэш прошлым сценарием, вернулся бы
 * вместо строк этого.
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
