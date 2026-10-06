package ru.projectx.datareference.config;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Маппер для тестов, который читает сообщения 1С так же, как сервис: умолчания Jackson и правила
 * {@link JacksonConfig#oneCMessageReading()}.
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
