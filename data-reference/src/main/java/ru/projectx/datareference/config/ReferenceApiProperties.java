package ru.projectx.datareference.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.time.ZoneId;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Настройки REST-чтения справочников: календарный день контура, виды обращения РВЛ
 * и пределы поиска.
 *
 * <p><b>Зона</b> — то, что в ТЗ на API названо «текущая дата в Europe/Moscow»: сервис
 * живёт в UTC, а «действующий на сегодня» договор считается по московскому дню, иначе
 * три часа в сутки он отставал бы на день. День вычисляется один раз на запрос
 * и передаётся в SQL параметром, не {@code CURRENT_DATE}.
 *
 * <p><b>Виды обращения РВЛ</b> — наименования из {@code appeal_types}, по которым R-01
 * и R-03 отбирают строки перечня программы и услуги договора: позиции размещения,
 * а не процедуры и анализы. Список в конфигурации, а не в коде, по ответу В-9: отбор
 * по наименованию хрупок, переименование в 1С молча обнуляет выдачу, и уточнить
 * список должно быть возможно без выпуска новой версии. Умолчания в коде нет: это
 * значения справочника контура. Придут GUID от 1С ({@code OQ-48}) — их место здесь же,
 * сравнение по строке уйдёт.
 *
 * <p><b>Лимит времени чтения</b> — сколько секунд REST-чтение может ждать базу, прежде чем
 * ответить 503 (Ш41). Применяет его аннотация транзакции чтения, которая читает то же
 * свойство; здесь он проверяется на старте ({@code @Min}) и сверяется с {@code lock_timeout}
 * пула — лимит должен быть меньше.
 *
 * @param zone           календарный день для правила «действует на сегодня»
 * @param rvlAppealTypes наименования видов обращения РВЛ; сравниваются без учёта
 *                       регистра и краевых пробелов, см. {@code RvlAppealTypes}
 * @param search         пределы поиска полисов (R-04)
 * @param mkb10          пределы поиска по МКБ-10 (R-07)
 * @param readTimeoutSeconds лимит времени REST-чтения, целые секунды
 */
@Validated
@ConfigurationProperties("projectx.api")
public record ReferenceApiProperties(
        @DefaultValue("Europe/Moscow") ZoneId zone,
        @NotEmpty List<@NotBlank String> rvlAppealTypes,
        @Valid @DefaultValue Search search,
        @Valid @DefaultValue Mkb10 mkb10,
        @Min(1) int readTimeoutSeconds) {

    /**
     * Пределы подсказки для поля «найти сотрудника»: короче {@code minQueryLength} —
     * не ищем (400), больше {@code maxLimit} — не отдаём (400).
     */
    public record Search(
            @Min(1) @DefaultValue("10") int defaultLimit,
            @Min(1) @DefaultValue("50") int maxLimit,
            @Min(1) @DefaultValue("2") int minQueryLength) {
    }

    /** Те же пределы для поиска по МКБ-10. */
    public record Mkb10(
            @Min(1) @DefaultValue("20") int defaultLimit,
            @Min(1) @DefaultValue("100") int maxLimit,
            @Min(1) @DefaultValue("2") int minQueryLength) {
    }
}
