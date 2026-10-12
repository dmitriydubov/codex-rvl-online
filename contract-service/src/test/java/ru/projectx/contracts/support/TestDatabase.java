package ru.projectx.contracts.support;

/**
 * Откуда тестам взять PostgreSQL: контейнер Testcontainers или отдельная внешняя база из {@code TEST_POSTGRES_URL}
 * (свойство {@value #URL_PROPERTY}). Без Docker и адреса тесты с базой падают, а не пропускаются, потому что
 * пропущенный тест выглядит в отчёте как проверенный.
 */
public final class TestDatabase {

    public static final String URL_PROPERTY = "test.postgres.url";
    public static final String USERNAME_PROPERTY = "test.postgres.username";
    public static final String PASSWORD_PROPERTY = "test.postgres.password";

    private TestDatabase() {
    }
}
