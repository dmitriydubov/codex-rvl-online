package ru.projectx.datareference.config;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

public final class OneCJson {

    private OneCJson() {
    }

    public static ObjectMapper mapper() {
        JsonMapper.Builder builder = JsonMapper.builder();
        new JacksonConfig().oneCMessageReading().customize(builder);
        return builder.build();
    }
}
