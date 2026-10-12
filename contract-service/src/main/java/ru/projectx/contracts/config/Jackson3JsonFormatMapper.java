package ru.projectx.contracts.config;

import java.lang.reflect.Type;
import org.hibernate.type.format.AbstractJsonFormatMapper;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

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
