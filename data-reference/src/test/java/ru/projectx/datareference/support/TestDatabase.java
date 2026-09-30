package ru.projectx.datareference.support;

import java.util.Optional;

/**
 * Откуда тестам взять PostgreSQL.
 *
 * <p>Два источника. Основной — контейнер Testcontainers, для него нужен Docker.
 * Запасной — внешняя база, адрес которой задан переменной окружения
 * {@code TEST_POSTGRES_URL}: он для машин, где Docker недоступен, а PostgreSQL есть.
 * Внешняя база должна быть <b>отдельной</b>: тесты очищают в ней все таблицы
 * перед каждым запуском, и указать сюда рабочую базу значит потерять её содержимое.
 *
 * <p>Проверка кэшируется: обращение к Docker без демона стоит несколько секунд ожидания,
 * умножать их на число тестовых классов незачем — ответ за время прогона не меняется.
 */
public final class TestDatabase {

    public static final String URL_PROPERTY = "test.postgres.url";
    public static final String USERNAME_PROPERTY = "test.postgres.username";
    public static final String PASSWORD_PROPERTY = "test.postgres.password";

    private static final boolean AVAILABLE = externalUrl().isPresent() || DockerAvailability.isDockerAvailable();

    public static boolean isAvailable() {
        return AVAILABLE;
    }

    /** Адрес внешней базы из окружения; пусто — тесты поднимают контейнер. */
    public static Optional<String> externalUrl() {
        return Optional.ofNullable(System.getenv("TEST_POSTGRES_URL")).filter(url -> !url.isBlank());
    }

    private TestDatabase() {
    }
}
