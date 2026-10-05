package ru.projectx.datareference.support;

/**
 * Откуда тестам взять PostgreSQL.
 *
 * <p>Два источника. Основной — контейнер Testcontainers, для него нужен Docker.
 * Запасной — внешняя база, адрес которой задан переменной окружения
 * {@code TEST_POSTGRES_URL}: он для машин, где Docker недоступен, а PostgreSQL есть. Spring
 * читает её как свойство {@value #URL_PROPERTY} (переменные окружения связываются со свойствами
 * по ослабленным правилам имён), см. {@link PostgresTestcontainer}.
 * Внешняя база должна быть <b>отдельной</b>: тесты очищают в ней все таблицы
 * перед каждым запуском, и указать сюда рабочую базу значит потерять её содержимое.
 *
 * <p>Нет ни Docker, ни {@code TEST_POSTGRES_URL} — тесты с базой <b>падают</b>, а не
 * пропускаются: контекст не поднимается, и Testcontainers называет причину
 * («Could not find a valid Docker environment»). Пропущенный тест выглядит в отчёте как
 * проверенный, поэтому тесты с базой не пропускаются никогда.
 */
public final class TestDatabase {

    public static final String URL_PROPERTY = "test.postgres.url";
    public static final String USERNAME_PROPERTY = "test.postgres.username";
    public static final String PASSWORD_PROPERTY = "test.postgres.password";

    private TestDatabase() {
    }
}
