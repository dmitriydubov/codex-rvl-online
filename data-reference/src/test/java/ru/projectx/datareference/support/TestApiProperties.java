package ru.projectx.datareference.support;

import java.time.ZoneId;
import java.util.List;
import ru.projectx.datareference.config.ReferenceApiProperties;

/**
 * Настройки REST-чтения для unit-тестов сервисов — те же значения, что умолчания
 * {@code application.yml} и записи {@link ReferenceApiProperties}: пределы, по которым
 * тест проверяет отказ, видны в одном месте.
 */
public final class TestApiProperties {

    public static final ZoneId ZONE = ZoneId.of("Europe/Moscow");
    public static final String RVL_AND_SKL = "РВЛ и СКЛ";
    public static final String RVL_IN_RUSSIA = "РВЛ в России";

    private static final int SEARCH_DEFAULT_LIMIT = 10;
    private static final int SEARCH_MAX_LIMIT = 50;
    private static final int MKB10_DEFAULT_LIMIT = 20;
    private static final int MKB10_MAX_LIMIT = 100;
    private static final int MIN_QUERY_LENGTH = 2;
    private static final int READ_TIMEOUT_SECONDS = 3;

    private TestApiProperties() {
    }

    public static ReferenceApiProperties defaults() {
        return new ReferenceApiProperties(
                ZONE,
                List.of(RVL_AND_SKL, RVL_IN_RUSSIA),
                new ReferenceApiProperties.Search(SEARCH_DEFAULT_LIMIT, SEARCH_MAX_LIMIT, MIN_QUERY_LENGTH),
                new ReferenceApiProperties.Mkb10(MKB10_DEFAULT_LIMIT, MKB10_MAX_LIMIT, MIN_QUERY_LENGTH),
                READ_TIMEOUT_SECONDS);
    }
}
