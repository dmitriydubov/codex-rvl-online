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
 * Правила преобразования значений 1С. Чистая функция от JSON — ни контекста,
 * ни базы здесь не нужно, и это те тесты, которые ловят самые дорогие ошибки:
 * неверно понятое значение не роняет обработку, а тихо оседает в базе.
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
     * Смещение отбрасывается, а не переводится в UTC: это календарная дата договора.
     * Перевод сдвинул бы полночь в +03:00 на день назад — ровно та ошибка, которую
     * легко не заметить.
     */
    @Test
    void midnightWithOffset_keepsItsCalendarDate() {
        ObjectNode data = node("{\"beginDate\":\"2024-10-10T00:00:00+03:00\"}");

        assertThat(OneCValues.date(data, "beginDate")).contains(LocalDate.of(2024, 10, 10));
    }

    /**
     * Третий вид даты, ТЗ-2: момент <b>без</b> смещения. Такой не разбирается ни как
     * OffsetDateTime, ни как LocalDate — до появления этого потока он попадал
     * в «поле не является датой» и уводил всё сообщение в терминальный отказ.
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

    @Test
    void unparsableDate_isContractViolation() {
        assertThatThrownBy(() -> OneCValues.date(node("{\"beginDate\":\"10.10.2024\"}"), "beginDate"))
                .isInstanceOf(OneCFieldException.class);
    }

    /** Открытая дата в обязательном поле — это незаполненное обязательное поле. */
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

    /** Нулевой GUID в обязательном поле — «ссылки нет», то есть поле не заполнено. */
    @Test
    void requiredGuid_rejectsZeroGuid() {
        ObjectNode data = node("{\"id\":\"00000000-0000-0000-0000-000000000000\"}");

        assertThatThrownBy(() -> OneCValues.requiredUuid(data, "id"))
                .isInstanceOf(OneCFieldException.class)
                .hasMessage("нет обязательного GUID в поле id");
    }

    /** Текст исключения доезжает до лога: в нём имя поля, но не значение. */
    @Test
    void unparsableGuid_namesFieldButNotValue() {
        assertThatThrownBy(() -> OneCValues.uuid(node("{\"guid\":\"не-гуид-42\"}"), "guid"))
                .isInstanceOf(OneCFieldException.class)
                .hasMessage("поле guid не является GUID")
                .hasCauseInstanceOf(IllegalArgumentException.class);
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

    /** Значение — возможно, дата рождения из полиса — в текст исключения не попадает. */
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

    /** JSON {@code null} — то же, что отсутствующее значение, а не строка «null». */
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

    /** Число строкой — не число: контракт передаёт числа числами. */
    @Test
    void integerAsText_isEmpty() {
        assertThat(OneCValues.integer(node("{\"rowNumber\":\"7\"}"), "rowNumber")).isEmpty();
    }

    @Test
    void amountAsText_isEmpty() {
        assertThat(OneCValues.amount(node("{\"price\":\"4999.95\"}"), "price")).isEmpty();
    }

    /** Пустая ссылка — «ссылки нет», как и нулевой GUID, а не нарушение контракта. */
    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void blankGuid_meansNoReference(String blank) {
        assertThat(OneCValues.uuid(node("{\"parentId\":\"%s\"}".formatted(blank)), "parentId")).isEmpty();
    }

    /**
     * Заглушка и открытая дата узнаются по календарному дню, в каком бы из трёх видов дата
     * ни пришла. Сравнение текста с «3000-01-01» пропустило бы открытую дату моментом
     * (ТЗ-5, ТЗ-2), и договор действовал бы тысячу лет.
     */
    @ParameterizedTest
    @ValueSource(strings = {"3000-01-01T00:00:00+03:00", "3000-01-01T00:00:00",
            "0001-01-01T00:00:00+03:00", "0001-01-01T00:00:00"})
    void placeholderAndOpenDate_asMoment_areEmpty(String unset) {
        assertThat(OneCValues.date(node("{\"endDate\":\"%s\"}".formatted(unset)), "endDate")).isEmpty();
    }

    /** В обязательном поле заглушка и открытая дата — незаполненное поле, а не нечитаемая дата. */
    @ParameterizedTest
    @ValueSource(strings = {"0001-01-01", "3000-01-01T00:00:00+03:00"})
    void requiredDate_reportsPlaceholderAndOpenDateAsMissing(String unset) {
        ObjectNode data = node("{\"beginDate\":\"%s\"}".formatted(unset));

        assertThatThrownBy(() -> OneCValues.requiredDate(data, "beginDate"))
                .isInstanceOf(OneCFieldException.class)
                .hasMessage("нет обязательной даты в поле beginDate");
    }

    /**
     * Календарный день «как написан» — не «первые десять символов»: момент с невозможным
     * временем — не дата, и оседать в базе днём 2024-10-10 он не должен.
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

    /** Флаг читается по значению, а не по присутствию поля: deleteMark=false — запись не в архиве. */
    @Test
    void explicitlyFalseFlag_isFalse() {
        assertThat(OneCValues.flag(node("{\"deleteMark\":false}"), "deleteMark")).isFalse();
    }

    private static ObjectNode node(String json) {
        return (ObjectNode) JSON.readTree(json);
    }
}
