package ru.projectx.datareference.support;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;
import javax.sql.DataSource;
import org.springframework.cache.CacheManager;

public class DatabaseCleaner {

    private static final String DOMAIN_TABLES = """
            select string_agg(quote_ident(tablename), ', ')
              from pg_tables
             where schemaname = 'public'
               and tablename not like 'databasechangelog%'
            """;

    private final DataSource dataSource;
    private final CacheManager cacheManager;

    public DatabaseCleaner(DataSource dataSource, CacheManager cacheManager) {
        this.dataSource = dataSource;
        this.cacheManager = cacheManager;
    }

    public void clear() {
        clearTables();
        cacheManager.getCacheNames().forEach(name -> Objects.requireNonNull(cacheManager.getCache(name)).invalidate());
    }

    private void clearTables() {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {

            String tables = domainTables(statement);
            if (tables != null) {
                statement.execute("truncate table " + tables + " cascade");
            }
        }
        catch (SQLException databaseUnavailable) {
            throw new IllegalStateException("не удалось очистить базу перед тестом", databaseUnavailable);
        }
    }

    private static String domainTables(Statement statement) throws SQLException {
        try (ResultSet tables = statement.executeQuery(DOMAIN_TABLES)) {
            return tables.next() ? tables.getString(1) : null;
        }
    }
}
