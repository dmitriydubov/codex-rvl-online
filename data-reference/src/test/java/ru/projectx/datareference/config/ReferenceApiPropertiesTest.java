package ru.projectx.datareference.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Защищает пределы строки поиска, перечня кодов МКБ-10 и периода расчёта в настройках REST-чтения.
 * Неверный предел обязан уронить запуск, а не отказывать потом каждому запросу.
 */
class ReferenceApiPropertiesTest {

    private static final int DEFAULT_INPUT_LIMIT = 100;
    private static final int DEFAULT_MAX_NIGHTS = 366;

    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(ReferenceApiPropertiesBinding.class)
            .withPropertyValues("projectx.api.rvl-appeal-types=РВЛ и СКЛ", "projectx.api.read-timeout-seconds=3");

    @Test
    void inputLimits_defaultToHundred() {
        context.run(started -> {
            ReferenceApiProperties api = started.getBean(ReferenceApiProperties.class);
            assertThat(api.search().maxQueryLength()).isEqualTo(DEFAULT_INPUT_LIMIT);
            assertThat(api.mkb10().maxQueryLength()).isEqualTo(DEFAULT_INPUT_LIMIT);
            assertThat(api.mkb10().maxCodes()).isEqualTo(DEFAULT_INPUT_LIMIT);
        });
    }

    /** По умолчанию — год с високосным запасом. */
    @Test
    void stayLimit_defaultsTo366Nights() {
        context.run(started -> assertThat(started.getBean(ReferenceApiProperties.class).price().maxNights())
                .isEqualTo(DEFAULT_MAX_NIGHTS));
    }

    /** Опечатка в имени свойства молча оставила бы умолчание. */
    @Test
    void stayLimit_isBoundFromItsProperty() {
        context.withPropertyValues("projectx.api.price.max-nights=21")
                .run(started -> assertThat(started.getBean(ReferenceApiProperties.class).price().maxNights())
                        .isEqualTo(21));
    }

    /** С нулевым пределом сервис отказывал бы в расчёте любого периода, поэтому такое значение роняет запуск. */
    @Test
    void zeroStayLimit_failsStartup() {
        context.withPropertyValues("projectx.api.price.max-nights=0")
                .run(started -> assertThat(started).getFailure()
                        .hasRootCauseInstanceOf(BindValidationException.class)
                        .rootCause().hasMessageContaining("price.maxNights"));
    }

    @Test
    void oneNightStayLimit_isAccepted() {
        context.withPropertyValues("projectx.api.price.max-nights=1")
                .run(started -> assertThat(started).hasNotFailed());
    }

    /** Опечатка в имени свойства молча оставила бы умолчание. */
    @Test
    void inputLimits_areBoundFromTheirProperties() {
        context.withPropertyValues("projectx.api.search.max-query-length=60",
                        "projectx.api.mkb10.max-query-length=70", "projectx.api.mkb10.max-codes=80")
                .run(started -> {
                    ReferenceApiProperties api = started.getBean(ReferenceApiProperties.class);
                    assertThat(api.search().maxQueryLength()).isEqualTo(60);
                    assertThat(api.mkb10().maxQueryLength()).isEqualTo(70);
                    assertThat(api.mkb10().maxCodes()).isEqualTo(80);
                });
    }

    /**
     * С нулевым пределом сервис отклонял бы любую строку поиска или любой перечень кодов,
     * поэтому такое значение не пускает сервис в работу.
     */
    @ParameterizedTest
    @CsvSource({"search,max-query-length,maxQueryLength", "mkb10,max-query-length,maxQueryLength",
            "mkb10,max-codes,maxCodes"})
    void zeroInputLimit_failsStartup(String section, String property, String component) {
        context.withPropertyValues("projectx.api." + section + "." + property + "=0")
                .run(started -> assertThat(started).getFailure()
                        .hasRootCauseInstanceOf(BindValidationException.class)
                        .rootCause().hasMessageContaining(section + "." + component));
    }

    /** Верхний предел длины ниже нижнего не пропустил бы ни одной строки поиска, поэтому роняет запуск. */
    @ParameterizedTest
    @ValueSource(strings = {"search", "mkb10"})
    void maxQueryLengthBelowMinimum_failsStartup(String section) {
        context.withPropertyValues(queryLengthRange(section, 3, 2))
                .run(started -> assertThat(started).getFailure()
                        .hasRootCauseInstanceOf(BindValidationException.class)
                        .rootCause().hasMessageContaining(section + ".queryLengthRangeConsistent"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"search", "mkb10"})
    void maxQueryLengthEqualToMinimum_isAccepted(String section) {
        context.withPropertyValues(queryLengthRange(section, 3, 3))
                .run(started -> assertThat(started).hasNotFailed());
    }

    private static String[] queryLengthRange(String section, int minLength, int maxLength) {
        String prefix = "projectx.api." + section + ".";
        return new String[] {prefix + "min-query-length=" + minLength, prefix + "max-query-length=" + maxLength};
    }

    @EnableConfigurationProperties(ReferenceApiProperties.class)
    static class ReferenceApiPropertiesBinding {
    }
}
