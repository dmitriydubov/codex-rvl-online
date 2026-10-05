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

/**
 * Настройки REST-чтения справочников: календарный день контура, виды обращения восстановительного лечения
 * и пределы поиска.
 *
 * <p><b>Зона</b> задаёт, по какому дню считается «действует на сегодня». Сервис живёт в UTC, а договор
 * считается действующим по московскому дню, иначе три часа в сутки он отставал бы на день. День вычисляется
 * один раз на запрос и передаётся в SQL параметром, а не берётся из {@code CURRENT_DATE}.
 *
 * <p><b>Виды обращения</b> — наименования из справочника видов обращения, по которым из перечня программы
 * и из услуг договора отбираются позиции размещения, а не процедуры и анализы. Список лежит в конфигурации,
 * а не в коде: отбор по наименованию хрупок, переименование в 1С молча обнуляет выдачу, и поправить список
 * должно быть можно без выпуска новой версии. Умолчания в коде нет, потому что это значения справочника
 * контура. Когда 1С пришлёт идентификаторы видов обращения, их место здесь же.
 *
 * <p><b>Предел длины периода</b> в расчёте стоимости размещения — наибольшее число ночей между заездом и
 * выездом. Путёвка в санаторий длится недели, а не годы; предел с запасом в високосный год отсекает
 * опечатки в датах и запросы на века вперёд ещё до чтения базы.
 *
 * <p><b>Лимит времени чтения</b> — сколько секунд REST-чтение может ждать базу, прежде чем ответить 503.
 * Применяет его аннотация транзакции чтения, которая читает то же свойство. Здесь он проверяется на старте
 * и сверяется с ожиданием блокировки в пуле соединений: лимит должен быть меньше.
 *
 * @param zone               календарный день для правила «действует на сегодня»
 * @param rvlAppealTypes     наименования видов обращения восстановительного лечения; сравниваются без учёта
 *                           регистра и пробелов по краям, см. {@code RvlAppealTypes}
 * @param search             пределы поиска полисов договора
 * @param mkb10              пределы поиска по МКБ-10 и перечня кодов
 * @param price              предел длины периода в расчёте стоимости размещения
 * @param readTimeoutSeconds лимит времени REST-чтения, целые секунды
 */
@Validated
@ConfigurationProperties("projectx.api")
public record ReferenceApiProperties(
        @DefaultValue("Europe/Moscow") ZoneId zone,
        @NotEmpty List<@NotBlank String> rvlAppealTypes,
        @Valid @DefaultValue Search search,
        @Valid @DefaultValue Mkb10 mkb10,
        @Valid @DefaultValue Price price,
        @Min(1) int readTimeoutSeconds) {

    /**
     * Пределы подсказки для поля «найти сотрудника». Строка короче {@code minQueryLength} или длиннее
     * {@code maxQueryLength} символов и предел выдачи больше {@code maxLimit} отклоняются с ответом 400. Длина
     * сверху считается по строке, как она пришла в запросе, вместе с пробелами по краям: этот предел
     * защищает сервис от объёма, а не проверяет смысл.
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
