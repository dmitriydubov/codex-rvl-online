package ru.projectx.datareference.service.onec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ru.projectx.datareference.exception.OneCFieldException;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Защищает правила преобразования значений из сообщений 1С:УСК: GUID, даты, строки, флаги, числа
 * и деньги.
 *
 * <p>Неверно понятое значение не роняет обработку, а тихо оседает в базе, поэтому эти тесты ловят
 * самые дорогие ошибки. Правила — чистые функции от JSON, и ни контекста Spring, ни базы здесь не
 * нужно.
 */
class OneCValuesTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test
    void zeroGuid_meansNoReference() {
        ObjectNode data = node("{\"parentId\":\"00000000-0000-0000-0000-000000000000\"}");

        assertThat(OneCValues.uuid(data, "parentId")).isEmpty();
    }

    @Test
    void realGuid_isRead() {
        ObjectNode data = node("{\"id\":\"f3876c53-c7d8-4620-b364-9538164ba5d4\"}");

        assertThat(OneCValues.uuid(data, "id"))
                .contains(UUID.fromString("f3876c53-c7d8-4620-b364-9538164ba5d4"));
    }

    @Test
    void missingRequiredGuid_isContractViolation() {
        assertThatThrownBy(() -> OneCValues.requiredUuid(node("{}"), "id"))
                .isInstanceOf(OneCFieldException.class)
                .hasMessageContaining("id");
    }

    @Test
    void unparsableGuid_isContractViolation() {
        assertThatThrownBy(() -> OneCValues.uuid(node("{\"id\":\"не-гуид\"}"), "id"))
                .isInstanceOf(OneCFieldException.class);
    }

    /** Заглушка «дата не заполнена». */
    @Test
    void placeholderDate_isEmpty() {
        assertThat(OneCValues.date(node("{\"beginDate\":\"0001-01-01\"}"), "beginDate")).isEmpty();
    }

    /**
     * Открытая дата источника. Сохранить её как есть значит получить договор,
     * действующий тысячу лет, и фильтр «действующие на сегодня», который никогда
     * ничего не отсекает.
     */
    @Test
    void openDate_isEmpty() {
        assertThat(OneCValues.date(node("{\"endDate\":\"3000-01-01\"}"), "endDate")).isEmpty();
    }

    /**
     * Смещение отбрасывается, а не переводится в UTC, потому что это календарная дата договора.
     * Перевод сдвинул бы полночь со смещением +03:00 на предыдущий день, а такую ошибку легко не
     * заметить.
     */
    @Test
    void midnightWithOffset_keepsItsCalendarDate() {
        ObjectNode data = node("{\"beginDate\":\"2024-10-10T00:00:00+03:00\"}");

        assertThat(OneCValues.date(data, "beginDate")).contains(LocalDate.of(2024, 10, 10));
    }

    /**
     * Третий вид даты — момент <b>без</b> смещения, как в договорах страхования. Он не разбирается
     * ни как {@code OffsetDateTime}, ни как {@code LocalDate}. Без отдельной ветки разбора такая дата
     * давала бы «поле не является датой», и всё сообщение уходило бы в отказ без повторов.
     */
    @Test
    void dateTimeWithoutOffset_isRead() {
        assertThat(OneCValues.date(node("{\"dateBeginISO\":\"2026-05-07T00:00:00\"}"), "dateBeginISO"))
                .contains(LocalDate.of(2026, 5, 7));
    }

    /** Конец дня остаётся тем же календарным днём, а не переезжает на следующий. */
    @Test
    void endOfDayWithoutOffset_keepsItsCalendarDate() {
        assertThat(OneCValues.date(node("{\"dateEndISO\":\"2027-05-06T23:59:59\"}"), "dateEndISO"))
                .contains(LocalDate.of(2027, 5, 6));
    }

    @Test
    void plainDate_isRead() {
        assertThat(OneCValues.date(node("{\"beginDate\":\"2024-10-10\"}"), "beginDate"))
                .contains(LocalDate.of(2024, 10, 10));
    }

    /**
     * Нечитаемая дата — нарушение контракта с именем поля. Причина в цепочке называет тип исключения JDK, но не
     * его текст: исключение разбора даты повторяет исходное значение, а им может оказаться дата рождения из
     * полиса, которую вывел бы любой стек в журнале.
     */
    @Test
    void unparsableDate_isContractViolationNamingFieldWithCauseTypeButNotValue() {
        assertThatThrownBy(() -> OneCValues.date(node("{\"beginDate\":\"10.10.2024\"}"), "beginDate"))
                .isInstanceOf(OneCFieldException.class)
                .hasMessage("поле beginDate не является датой")
                .cause()
                .hasMessage("java.time.format.DateTimeParseException")
                .hasNoCause();
    }

    /** Предел длины считается после обрезки пробелов: код 1С, дополненный пробелами, ровно в предел принимается. */
    @Test
    void limitedText_withinLimitAfterTrimming_isRead() {
        assertThat(OneCValues.limitedText(node("{\"ИНН\":\" 770000000012 \"}"), "ИНН", 12))
                .contains("770000000012");
    }

    /** Значение длиннее предела — нарушение контракта: в отказе поле и предел, значения нет. */
    @Test
    void limitedText_overLimit_isContractViolationNamingFieldAndLimit() {
        assertThatThrownBy(() -> OneCValues.limitedText(node("{\"ИНН\":\"7700000000123\"}"), "ИНН", 12))
                .isInstanceOf(OneCFieldException.class)
                .hasMessage("поле ИНН длиннее 12 символов");
    }

    @Test
    void limitedText_missing_isEmpty() {
        assertThat(OneCValues.limitedText(node("{}"), "КПП", 9)).isEmpty();
    }

    /** Открытая дата в обязательном поле означает, что поле не заполнено. */
    @Test
    void requiredDate_rejectsOpenDate() {
        assertThatThrownBy(() -> OneCValues.requiredDate(node("{\"beginDate\":\"3000-01-01\"}"), "beginDate"))
                .isInstanceOf(OneCFieldException.class);
    }

    /** Коды в 1С приходят дополненными пробелами до длины поля. */
    @Test
    void text_isTrimmed() {
        assertThat(OneCValues.text(node("{\"code\":\"A00   \"}"), "code")).contains("A00");
    }

    @Test
    void blankText_isEmpty() {
        assertThat(OneCValues.text(node("{\"code\":\"   \"}"), "code")).isEmpty();
    }

    @Test
    void missingFlag_isFalse() {
        assertThat(OneCValues.flag(node("{}"), "deleteMark")).isFalse();
    }

    /**
     * Деньги читаются из текстового представления числа. Через двоичную дробь
     * {@code double} цена 4999.95 превращается в 4999.9499999999998181, и расхождение
     * доезжает до расчёта доплаты.
     */
    @Test
    void amount_keepsExactDecimal() {
        assertThat(OneCValues.amount(node("{\"price\":4999.95}"), "price"))
                .contains(new BigDecimal("4999.95"));
    }

    @Test
    void requiredGuid_isRead() {
        ObjectNode data = node("{\"id\":\"f3876c53-c7d8-4620-b364-9538164ba5d4\"}");

        assertThat(OneCValues.requiredUuid(data, "id"))
                .isEqualTo(UUID.fromString("f3876c53-c7d8-4620-b364-9538164ba5d4"));
    }

    /** Нулевой GUID в обязательном поле значит «ссылки нет», то есть поле не заполнено. */
    @Test
    void requiredGuid_rejectsZeroGuid() {
        ObjectNode data = node("{\"id\":\"00000000-0000-0000-0000-000000000000\"}");

        assertThatThrownBy(() -> OneCValues.requiredUuid(data, "id"))
                .isInstanceOf(OneCFieldException.class)
                .hasMessage("нет обязательного GUID в поле id");
    }

    /**
     * Текст исключения попадает в журнал, поэтому в нём есть имя поля, но нет значения. У отказа нет
     * исключения-причины: разбор не вызывает нестрогий {@code UUID.fromString}, а его ошибка
     * процитировала бы значение («Invalid UUID string: …»).
     */
    @Test
    void unparsableGuid_namesFieldButNotValue() {
        assertThatThrownBy(() -> OneCValues.uuid(node("{\"guid\":\"не-гуид-42\"}"), "guid"))
                .isInstanceOf(OneCFieldException.class)
                .hasMessage("поле guid не является GUID")
                .hasNoCause();
    }

    /**
     * GUID поля принимается только в канонической записи 8-4-4-4-12. Эти значения
     * {@code UUID.fromString} в JDK 21 молча читает как чужой GUID 00000001-0002-0003-0004-000000000005,
     * и сообщение 1С затёрло бы чужую запись. Отказ называет поле, но не значение.
     */
    @ParameterizedTest
    @ValueSource(strings = {"1-2-3-4-5", "000000001-002-0003-0004-000000000005",
            "00000001-0002-0003-0004-+00000000005", "00000001-0002-0003-0004-00000000000\uFF15"})
    void nonCanonicalGuid_isContractViolationNamingFieldButNotValue(String nonCanonical) {
        ObjectNode data = node("{\"contractId\":\"%s\"}".formatted(nonCanonical));

        assertThatThrownBy(() -> OneCValues.requiredUuid(data, "contractId"))
                .isInstanceOf(OneCFieldException.class)
                .hasMessage("поле contractId не является GUID")
                .hasNoCause();
    }

    /**
     * {@code 0-0-0-0-0} — искажённое значение, а не нулевой GUID «ссылки нет». Нестрогий разбор
     * прочитал бы его как нулевой GUID, и договор молча сохранился бы без ссылки.
     */
    @Test
    void nonCanonicalZeroGuid_isContractViolationRatherThanNoReference() {
        assertThatThrownBy(() -> OneCValues.uuid(node("{\"guid\":\"0-0-0-0-0\"}"), "guid"))
                .isInstanceOf(OneCFieldException.class)
                .hasMessage("поле guid не является GUID");
    }

    /** 1С:УСК регистр не задаёт, поэтому GUID прописными цифрами — тот же GUID, и хранится он строчными. */
    @Test
    void uppercaseGuid_isTheSameGuid() {
        ObjectNode data = node("{\"id\":\"F3876C53-C7D8-4620-B364-9538164BA5D4\"}");

        assertThat(OneCValues.requiredUuid(data, "id"))
                .isEqualTo(UUID.fromString("f3876c53-c7d8-4620-b364-9538164ba5d4"));
    }

    /** Пробелы по краям GUID отбрасываются до разбора, как у любой строки 1С. */
    @Test
    void guidWithSurroundingSpaces_isRead() {
        ObjectNode data = node("{\"id\":\"  f3876c53-c7d8-4620-b364-9538164ba5d4 \"}");

        assertThat(OneCValues.uuid(data, "id")).contains(UUID.fromString("f3876c53-c7d8-4620-b364-9538164ba5d4"));
    }

    @Test
    void requiredDate_isRead() {
        assertThat(OneCValues.requiredDate(node("{\"beginDate\":\"2024-10-10\"}"), "beginDate"))
                .isEqualTo(LocalDate.of(2024, 10, 10));
    }

    @Test
    void missingRequiredDate_namesField() {
        assertThatThrownBy(() -> OneCValues.requiredDate(node("{}"), "beginDate"))
                .isInstanceOf(OneCFieldException.class)
                .hasMessage("нет обязательной даты в поле beginDate");
    }

    /** Значение в текст исключения не попадает, потому что им может оказаться дата рождения из полиса. */
    @Test
    void unparsableDate_namesFieldButNotValue() {
        assertThatThrownBy(() -> OneCValues.date(node("{\"birthDate\":\"17.05.1980\"}"), "birthDate"))
                .isInstanceOf(OneCFieldException.class)
                .hasMessage("поле birthDate не является датой");
    }

    @Test
    void missingText_isEmpty() {
        assertThat(OneCValues.text(node("{}"), "code")).isEmpty();
    }

    /** JSON {@code null} означает отсутствующее значение, а не строку «null». */
    @Test
    void nullText_isEmpty() {
        assertThat(OneCValues.text(node("{\"code\":null}"), "code")).isEmpty();
    }

    @Test
    void objectInsteadOfText_isEmpty() {
        assertThat(OneCValues.text(node("{\"code\":{\"value\":\"A00\"}}"), "code")).isEmpty();
    }

    @Test
    void numberAsText_isItsTextForm() {
        assertThat(OneCValues.text(node("{\"code\":123}"), "code")).contains("123");
    }

    @Test
    void requiredText_isRead() {
        assertThat(OneCValues.requiredText(node("{\"name\":\" Терапия \"}"), "name")).isEqualTo("Терапия");
    }

    @Test
    void missingRequiredText_namesField() {
        assertThatThrownBy(() -> OneCValues.requiredText(node("{\"name\":\"  \"}"), "name"))
                .isInstanceOf(OneCFieldException.class)
                .hasMessage("нет обязательного поля name");
    }

    @Test
    void presentFlag_isRead() {
        assertThat(OneCValues.flag(node("{\"deleteMark\":true}"), "deleteMark")).isTrue();
    }

    @Test
    void integer_isRead() {
        assertThat(OneCValues.integer(node("{\"rowNumber\":7}"), "rowNumber")).contains(7);
    }

    /** Число, записанное строкой, числом не считается: контракт передаёт числа числами. */
    @Test
    void integerAsText_isEmpty() {
        assertThat(OneCValues.integer(node("{\"rowNumber\":\"7\"}"), "rowNumber")).isEmpty();
    }

    @Test
    void amountAsText_isEmpty() {
        assertThat(OneCValues.amount(node("{\"price\":\"4999.95\"}"), "price")).isEmpty();
    }

    /** Пустая ссылка значит «ссылки нет», как и нулевой GUID, и нарушением контракта не считается. */
    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void blankGuid_meansNoReference(String blank) {
        assertThat(OneCValues.uuid(node("{\"parentId\":\"%s\"}".formatted(blank)), "parentId")).isEmpty();
    }

    /**
     * Заглушка и открытая дата узнаются по календарному дню, в каком бы из трёх видов дата ни пришла.
     * Сравнение текста с «3000-01-01» пропустило бы открытую дату, записанную моментом, как в договорах
     * ЛПУ и договорах страхования, и договор действовал бы тысячу лет.
     */
    @ParameterizedTest
    @ValueSource(strings = {"3000-01-01T00:00:00+03:00", "3000-01-01T00:00:00",
            "0001-01-01T00:00:00+03:00", "0001-01-01T00:00:00"})
    void placeholderAndOpenDate_asMoment_areEmpty(String unset) {
        assertThat(OneCValues.date(node("{\"endDate\":\"%s\"}".formatted(unset)), "endDate")).isEmpty();
    }

    /** В обязательном поле заглушка и открытая дата означают незаполненное поле, а не нечитаемую дату. */
    @ParameterizedTest
    @ValueSource(strings = {"0001-01-01", "3000-01-01T00:00:00+03:00"})
    void requiredDate_reportsPlaceholderAndOpenDateAsMissing(String unset) {
        ObjectNode data = node("{\"beginDate\":\"%s\"}".formatted(unset));

        assertThatThrownBy(() -> OneCValues.requiredDate(data, "beginDate"))
                .isInstanceOf(OneCFieldException.class)
                .hasMessage("нет обязательной даты в поле beginDate");
    }

    /**
     * Календарный день берётся так, как написан, но это не «первые десять символов». Момент с
     * невозможным временем датой не является, и днём 2024-10-10 в базу он попасть не должен.
     */
    @Test
    void momentWithImpossibleTime_isContractViolation() {
        assertThatThrownBy(() -> OneCValues.date(node("{\"beginDate\":\"2024-10-10T25:00:00\"}"), "beginDate"))
                .isInstanceOf(OneCFieldException.class)
                .hasMessage("поле beginDate не является датой");
    }

    @Test
    void arrayInsteadOfText_isEmpty() {
        assertThat(OneCValues.text(node("{\"code\":[\"A00\"]}"), "code")).isEmpty();
    }

    /** Флаг читается по значению, а не по присутствию поля, поэтому deleteMark=false значит «не в архиве». */
    @Test
    void explicitlyFalseFlag_isFalse() {
        assertThat(OneCValues.flag(node("{\"deleteMark\":false}"), "deleteMark")).isFalse();
    }

    private static ObjectNode node(String json) {
        return (ObjectNode) JSON.readTree(json);
    }
}
