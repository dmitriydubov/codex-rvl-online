package ru.projectx.datareference.mapper;

import java.util.Optional;
import java.util.regex.Pattern;
import ru.projectx.datareference.exception.OneCFieldException;
import tools.jackson.databind.JsonNode;

/**
 * ИНН и КПП контрагента 1С:УСК, общие для санатория и страхователя. КПП по формату ФНС допускает латинские буквы
 * в пятой и шестой позициях. Испорченный реквизит отклоняет всё сообщение: сохранённый, он ушёл бы соседям как настоящий.
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

    Optional<String> read(JsonNode data) {
        Optional<String> value = OneCValues.limitedText(data, field, maxLength);
        if (value.filter(text -> !format.matcher(text).matches()).isPresent()) {
            throw new OneCFieldException(violation);
        }
        return value;
    }
}
