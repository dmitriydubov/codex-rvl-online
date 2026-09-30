package ru.projectx.datareference.config;

import java.lang.reflect.Type;
import org.hibernate.type.format.AbstractJsonFormatMapper;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Мост между колонками {@code jsonb} и Jackson 3.
 *
 * <p>Существует из-за расхождения версий: Hibernate 7 ищет на пути классов Jackson 2
 * ({@code com.fasterxml.jackson}) либо реализацию JSONB, а Spring Boot 4 приносит
 * Jackson 3 ({@code tools.jackson}). Ни того, ни другого Hibernate не находит и
 * отказывается отображать JSON-типы вовсе — «Could not find a FormatMapper for the JSON
 * format». Альтернатива этому классу — притащить в сборку вторую версию Jackson
 * ради одной проверки на старте.
 *
 * <p>Подключается свойством {@code hibernate.type.json_format_mapper} в
 * {@code application.yml}: Hibernate создаёт мост сам, бин Spring для этого не нужен.
 *
 * <p>Сегодня работает только путь «строка как есть»: единственное JSON-поле сервиса —
 * сырое тело сообщения 1С, и {@link AbstractJsonFormatMapper} для {@code String}
 * отдаёт значение без преобразования, не доходя до методов ниже. Они реализованы
 * по-настоящему, а не заглушкой, чтобы первое же поле-объект с {@code jsonb}
 * заработало, а не упало в рантайме.
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
