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
    private static final int MAX_QUERY_LENGTH = 100;
    private static final int MAX_CODES = 100;
    private static final int READ_TIMEOUT_SECONDS = 3;
    private static final int MAX_NIGHTS = 366;

    private TestApiProperties() {
    }

    public static ReferenceApiProperties defaults() {
        return withPriceMaxNights(MAX_NIGHTS);
    }

    /** Умолчания, кроме предела длины периода расчёта стоимости в ночах. */
    public static ReferenceApiProperties withPriceMaxNights(int maxNights) {
        return properties(search(MAX_QUERY_LENGTH), mkb10(MAX_QUERY_LENGTH, MAX_CODES), maxNights);
    }

    /**
     * Умолчания, кроме верхнего предела длины строки поиска полисов. В умолчаниях этот предел равен пределу
     * МКБ-10, и тест не отличил бы, какую из двух настроек читает поиск полисов; здесь их значения разные.
     */
    public static ReferenceApiProperties withPolicySearchMaxQueryLength(int maxQueryLength) {
        return withSearchLimits(search(maxQueryLength), mkb10(MAX_QUERY_LENGTH, MAX_CODES));
    }

    /**
     * Умолчания, кроме верхних пределов МКБ-10: длины строки поиска и числа кодов в перечне. В умолчаниях оба
     * равны максимуму выдачи МКБ-10 и пределу строки поиска полисов; здесь значения разные.
     */
    public static ReferenceApiProperties withMkb10InputLimits(int maxQueryLength, int maxCodes) {
        return withSearchLimits(search(MAX_QUERY_LENGTH), mkb10(maxQueryLength, maxCodes));
    }

    private static ReferenceApiProperties withSearchLimits(
            ReferenceApiProperties.Search search, ReferenceApiProperties.Mkb10 mkb10) {

        return properties(search, mkb10, MAX_NIGHTS);
    }

    private static ReferenceApiProperties properties(
            ReferenceApiProperties.Search search, ReferenceApiProperties.Mkb10 mkb10, int maxNights) {

        return new ReferenceApiProperties(ZONE, List.of(RVL_AND_SKL, RVL_IN_RUSSIA), search, mkb10,
                new ReferenceApiProperties.Price(maxNights), READ_TIMEOUT_SECONDS);
    }

    private static ReferenceApiProperties.Search search(int maxQueryLength) {
        return new ReferenceApiProperties.Search(
                SEARCH_DEFAULT_LIMIT, SEARCH_MAX_LIMIT, MIN_QUERY_LENGTH, maxQueryLength);
    }

    private static ReferenceApiProperties.Mkb10 mkb10(int maxQueryLength, int maxCodes) {
        return new ReferenceApiProperties.Mkb10(
                MKB10_DEFAULT_LIMIT, MKB10_MAX_LIMIT, MIN_QUERY_LENGTH, maxQueryLength, maxCodes);
    }
}
