package ru.projectx.datareference.mapper;

import java.util.Optional;
import java.util.regex.Pattern;
import ru.projectx.datareference.exception.OneCFieldException;
import tools.jackson.databind.JsonNode;

/**
 * Реквизиты контрагента 1С:УСК, общие для санатория и страхователя, и правила, по которым они принимаются.
 *
 * <p>ИНН — не длиннее 12 символов и только цифры 0–9 по правилу 1С:УСК. КПП — девять символов по формату ФНС: цифры
 * 0–9, а в пятой и шестой позициях ещё и заглавные латинские буквы A–Z. Пробелы по краям отбрасываются до проверки.
 * Длина проверяется раньше состава, поэтому у слишком длинного значения причина отказа — длина.
 *
 * <p>Испорченный реквизит отклоняет всё сообщение: сохранённый, он ушёл бы соседним сервисам как настоящий. Отказ
 * называет поле и правило, но не значение. Пустой реквизит принимается пустым.
 */
enum ContragentRequisite {

    INN("ИНН", 12, "[0-9]+", "содержит не только цифры"),
    KPP("КПП", 9, "[0-9]{4}[0-9A-Z]{2}[0-9]{3}", "не соответствует формату");

    private final String field;
    private final int maxLength;
    private final Pattern format;
    private final String violation;

    ContragentRequisite(String field, int maxLength, String format, String violation) {
        this.field = field;
        this.maxLength = maxLength;
        this.format = Pattern.compile(format);
        this.violation = "поле %s %s".formatted(field, violation);
    }

    /** Значение реквизита из сообщения контрагента или пустой результат, если реквизит не заполнен. */
    Optional<String> read(JsonNode data) {
        Optional<String> value = OneCValues.limitedText(data, field, maxLength);
        if (value.filter(text -> !format.matcher(text).matches()).isPresent()) {
            throw new OneCFieldException(violation);
        }
        return value;
    }
}
