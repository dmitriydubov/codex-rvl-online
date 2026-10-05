package ru.projectx.datareference.service.onec;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;
import ru.projectx.datareference.exception.OneCFieldException;
import tools.jackson.databind.JsonNode;

/**
 * Правила, по которым значения из сообщений 1С:УСК становятся значениями справочников: GUID, даты,
 * строки, флаги, целые числа и деньги.
 *
 * <p>Правила собраны в одном месте, потому что нарушаются они одинаково во всех потоках и каждый
 * раз <b>тихо</b>: неверная дата или незамеченный нулевой GUID не роняют обработку, а оседают в
 * базе неправильным значением, которое обнаружится через месяц.
 *
 * <p>Три ловушки, ради которых этот класс и существует:
 * <ul>
 *   <li>нулевой GUID {@code 00000000-…} означает «ссылки нет», а не «ссылка на объект
 *       с таким идентификатором»;</li>
 *   <li>{@code 0001-01-01} — заглушка «дата не заполнена»;</li>
 *   <li>{@code 3000-01-01} — <b>открытая дата</b>. Сохранить её как есть значит получить
 *       договор, действующий тысячу лет, и фильтр «действующие на сегодня», который
 *       никогда ничего не отсекает.</li>
 * </ul>
 *
 * <p><b>В тексте исключения есть имя поля, но нет его значения.</b> Эти же правила разбирают
 * полисы, а там значением может оказаться дата рождения или почта. Текст исключения попадает в
 * журнал, и «не разобрана дата 1980-05-17» — это уже персональные данные в журнале. Само сообщение
 * при этом целиком лежит в таблице сырья, и разобрать случай можно там, не вынося содержимое наружу.
 */
public final class OneCValues {

    private static final LocalDate UNSET_DATE = LocalDate.of(1, 1, 1);
    private static final LocalDate OPEN_DATE = LocalDate.of(3000, 1, 1);
    private static final String NO_REQUIRED_GUID_MESSAGE = "нет обязательного GUID в поле ";
    private static final String NO_REQUIRED_DATE_MESSAGE = "нет обязательной даты в поле ";
    private static final String NO_REQUIRED_TEXT_MESSAGE = "нет обязательного поля ";

    /** GUID поля. Нулевой GUID и пустое поле значат «ссылки нет», и тогда результат пустой. */
    public static Optional<UUID> uuid(JsonNode data, String field) {
        return parsed(data, field, OneCRawParser::uuid, Predicate.not(OneCGuid::isEmpty));
    }

    /** Обязательный GUID, как правило {@code external_id} записи. Его отсутствие нарушает контракт. */
    public static UUID requiredUuid(JsonNode data, String field) {
        return uuid(data, field).orElseThrow(missing(NO_REQUIRED_GUID_MESSAGE, field));
    }

    /** Дата поля. Заглушка и открытая дата дают пустой результат. */
    public static Optional<LocalDate> date(JsonNode data, String field) {
        return parsed(data, field, OneCRawParser::date,
                value -> !UNSET_DATE.equals(value) && !OPEN_DATE.equals(value));
    }

    /**
     * Обязательная дата.
     *
     * <p>Заглушка {@code 0001-01-01} и открытая дата {@code 3000-01-01} и здесь остаются пустыми
     * значениями. Поле, которое контракт объявил обязательным, пришло незаполненным, а это
     * невалидное сообщение, а не «дата открыта».
     */
    public static LocalDate requiredDate(JsonNode data, String field) {
        return date(data, field).orElseThrow(missing(NO_REQUIRED_DATE_MESSAGE, field));
    }

    /**
     * Строка поля без пробелов по краям: коды 1С приходят дополненными пробелами до длины поля.
     * Пустая строка даёт пустой результат.
     *
     * <p>Объект, массив и отсутствующее поле тоже дают пустой результат. JSON {@code null} отдельно
     * не проверяется: {@link JsonNode#asString()} в Jackson 3 отдаёт для него пустую строку, это
     * сверено по версии 3.1.4. В Jackson 2 {@code asText()} давал {@code "null"}, поэтому при смене
     * версии это место нужно перепроверить.
     */
    public static Optional<String> text(JsonNode data, String field) {
        return Optional.of(data.path(field))
                .filter(JsonNode::isValueNode)
                .map(value -> value.asString().trim())
                .filter(raw -> !raw.isEmpty());
    }

    /** Обязательная строка. Её отсутствие означает невалидное сообщение, а не пустое значение. */
    public static String requiredText(JsonNode data, String field) {
        return text(data, field).orElseThrow(missing(NO_REQUIRED_TEXT_MESSAGE, field));
    }

    /**
     * Строка поля, которая обязана поместиться в {@code maxLength} символов после обрезки пробелов. Длиннее —
     * значит испорчено: реквизит такой длины не бывает, а база отвергла бы запись с текстом ошибки, в котором
     * видно значение. Отказ называет поле и предел, но не значение.
     */
    public static Optional<String> limitedText(JsonNode data, String field, int maxLength) {
        Optional<String> value = text(data, field);
        if (value.filter(text -> text.length() > maxLength).isPresent()) {
            throw new OneCFieldException("поле %s длиннее %d символов".formatted(field, maxLength));
        }
        return value;
    }

    /** Отсутствующий флаг считается {@code false}, потому что контракт не различает «нет» и «не задано». */
    public static boolean flag(JsonNode data, String field) {
        return data.path(field).asBoolean(false);
    }

    public static Optional<Integer> integer(JsonNode data, String field) {
        JsonNode value = data.path(field);
        return value.isNumber() ? Optional.of(value.asInt()) : Optional.empty();
    }

    /**
     * Денежное значение. Оно читается из текста числа, а не через {@code BigDecimal.valueOf(double)},
     * даже для целых цен. Двоичная дробь даёт копеечные расхождения, а цена путёвки попадает в расчёт
     * доплаты.
     */
    public static Optional<BigDecimal> amount(JsonNode data, String field) {
        JsonNode value = data.path(field);
        return value.isNumber() ? Optional.of(new BigDecimal(value.asString())) : Optional.empty();
    }

    /** Разбирает непустой текст поля. Значение, которое не прошло {@code meaningful}, даёт пустой результат. */
    private static <T> Optional<T> parsed(JsonNode data, String field,
                                          BiFunction<String, String, T> parser, Predicate<T> meaningful) {
        return text(data, field)
                .map(raw -> parser.apply(raw, field))
                .filter(meaningful);
    }

    /** Текст исключения собирается, только когда значения нет: в нём имя поля, но не значение. */
    private static Supplier<OneCFieldException> missing(String messagePrefix, String field) {
        return () -> new OneCFieldException(messagePrefix + field);
    }

    private OneCValues() {
    }
}
