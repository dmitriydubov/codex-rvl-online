package ru.projectx.datareference.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.time.ZoneId;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/** Настройки REST-чтения справочников; значение, не прошедшее проверку, не пускает сервис в работу. */
@Validated
@ConfigurationProperties("projectx.api")
public record ReferenceApiProperties(
        // Сервис живёт в UTC, а договор действует по московскому дню, иначе три часа в сутки ответ отставал бы на день.
        // День вычисляется раз на запрос и передаётся в SQL параметром, а не берётся из CURRENT_DATE.
        @DefaultValue("Europe/Moscow") ZoneId zone,
        // Виды обращения восстановительного лечения, по которым отбираются позиции размещения, без учёта регистра и
        // крайних пробелов. Список лежит в настройках, чтобы править его без выпуска. Умолчания нет: это значения
        // справочника конкретного контура.
        @NotEmpty List<@NotBlank String> rvlAppealTypes,
        @Valid @DefaultValue Search search,
        @Valid @DefaultValue Mkb10 mkb10,
        @Valid @DefaultValue Price price,
        // Сколько секунд REST-чтение ждёт базу до ответа 503. Лимит должен быть меньше lock_timeout: если это не так,
        // сервис на старте пишет WARN, но запускается.
        @Min(1) int readTimeoutSeconds) {

    /**
     * Пределы подсказки для поля «найти сотрудника»; строка вне пределов длины и выдача больше {@code maxLimit}
     * получают 400. Длина считается вместе с пробелами по краям: предел защищает сервис от объёма, а не проверяет
     * смысл.
     */
    public record Search(
            @Min(1) @DefaultValue("10") int defaultLimit,
            @Min(1) @DefaultValue("50") int maxLimit,
            @Min(1) @DefaultValue("2") int minQueryLength,
            @Min(1) @DefaultValue("100") int maxQueryLength) implements QueryLengthRange {
    }

    /**
     * Те же пределы для подсказки по МКБ-10 и предел числа кодов в перечне, который раскрывается разом.
     * Коды считаются, как пришли в запросе, вместе с повторами и пустыми.
     */
    public record Mkb10(
            @Min(1) @DefaultValue("20") int defaultLimit,
            @Min(1) @DefaultValue("100") int maxLimit,
            @Min(1) @DefaultValue("2") int minQueryLength,
            @Min(1) @DefaultValue("100") int maxQueryLength,
            @Min(1) @DefaultValue("100") int maxCodes) implements QueryLengthRange {
    }

    /**
     * Наибольшая длина периода в расчёте стоимости, в ночах: выезд не дальше {@code maxNights} суток после заезда.
     * Ночи считаются так же, как в ответе расчёта, день выезда не оплачивается.
     */
    public record Price(@Min(1) @DefaultValue("366") int maxNights) {
    }

    /**
     * Нижний и верхний пределы длины строки поиска. Верхний не может быть ниже нижнего: иначе не прошла бы ни
     * одна строка, и такая настройка не пускает сервис в работу. Проверка общая для обоих поисков и описана здесь
     * один раз: валидатор берёт ограничения и с интерфейса, который реализует запись настроек.
     */
    public interface QueryLengthRange {

        int minQueryLength();

        int maxQueryLength();

        @AssertTrue(message = "max-query-length не меньше min-query-length")
        default boolean isQueryLengthRangeConsistent() {
            return maxQueryLength() >= minQueryLength();
        }
    }
}
