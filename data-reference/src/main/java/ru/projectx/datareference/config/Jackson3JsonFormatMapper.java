package ru.projectx.datareference.config;

import java.lang.reflect.Type;
import org.hibernate.type.format.AbstractJsonFormatMapper;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Мост между колонками {@code jsonb} и Jackson 3: Hibernate 7 ищет Jackson 2 или JSONB и без моста не отображает
 * JSON-типы вовсе. Подключается свойством {@code hibernate.type.json_format_mapper}, бин Spring для этого не нужен.
 * Единственное JSON-поле сейчас строковое и сюда не доходит; методы рабочие, чтобы не упало первое поле-объект.
 */
public class Jackson3JsonFormatMapper extends AbstractJsonFormatMapper {

    private final ObjectMapper mapper = JsonMapper.builder().build();

    @Override
    protected <T> T fromString(CharSequence charSequence, Type type) {
        return mapper.readValue(charSequence.toString(), mapper.constructType(type));
    }

    @Override
    protected <T> String toString(T value, Type type) {
        return mapper.writeValueAsString(value);
    }
}
