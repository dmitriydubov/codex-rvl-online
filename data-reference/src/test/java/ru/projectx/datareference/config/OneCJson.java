package ru.projectx.datareference.config;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Маппер для тестов, который читает JSON-дерево сообщений 1С так же, как сервис: умолчания Jackson и правила
 * {@link JacksonConfig#oneCMessageReading()}. Настроек Spring Boot из {@code spring.jackson.*}, например имён полей
 * в snake_case, в нём нет, поэтому для чтения в записи и классы он не годится.
 */
public final class OneCJson {

    private OneCJson() {
    }

    public static ObjectMapper mapper() {
        JsonMapper.Builder builder = JsonMapper.builder();
        new JacksonConfig().oneCMessageReading().customize(builder);
        return builder.build();
    }
}
