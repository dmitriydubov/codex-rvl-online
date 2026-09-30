package ru.projectx.datareference.support;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;

/**
 * Пустая база перед каждым тестом.
 *
 * <p>Контекст Spring между тестовыми классами переиспользуется, а вместе с ним и база:
 * данные, оставленные соседним классом, — это не «чужой мусор», а чужие строки, которые
 * ломают счётчики и мешают удалению по внешним ключам.
 *
 * <p>Одним {@code TRUNCATE ... CASCADE}, а не вызовами {@code deleteAll()} по репозиториям:
 * порядок удаления по внешним ключам тогда пришлось бы поддерживать вручную в каждом
 * тесте, и он расходился бы с моделью при первой же новой связи.
 *
 * <p>Список таблиц спрашивается у самой базы, а не пишется здесь: иначе он устареет ровно
 * тогда, когда появится следующая таблица, и тест начнёт падать по причине, не связанной
 * с тем, что он проверяет. Служебные таблицы Liquibase не трогаются — схема накатана
 * один раз на весь прогон.

 */
public class DatabaseCleaner {

    private static final String DOMAIN_TABLES = """
            select string_agg(quote_ident(tablename), ', ')
              from pg_tables
             where schemaname = 'public'
               and tablename not like 'databasechangelog%'
            """;

    private final DataSource dataSource;

    public DatabaseCleaner(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void clear() {
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
