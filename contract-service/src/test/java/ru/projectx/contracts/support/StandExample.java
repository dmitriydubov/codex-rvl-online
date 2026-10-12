package ru.projectx.contracts.support;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

public record StandExample(String name, JsonNode root) {

    private static final String LOCATION = "data-reference/examples/";
    private static final JsonMapper JSON = JsonMapper.shared();

    public static StandExample load(String name) {
        try {
            String text = new ClassPathResource(LOCATION + name).getContentAsString(StandardCharsets.UTF_8);
            return new StandExample(name, JSON.readTree(text));
        }
        catch (IOException unreadable) {
            throw new UncheckedIOException("не прочитать пример стенда " + name, unreadable);
        }
    }

    public int status() {
        return root.path("response").path("status").asInt();
    }

    public String body(Map<String, String> placeholders) {
        String body = JSON.writeValueAsString(root.path("response").path("body"));
        for (Map.Entry<String, String> placeholder : placeholders.entrySet()) {
            body = body.replace("{" + placeholder.getKey() + "}", placeholder.getValue());
        }
        return body;
    }
}
