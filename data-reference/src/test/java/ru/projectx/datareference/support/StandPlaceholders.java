package ru.projectx.datareference.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

public final class StandPlaceholders {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([a-z_]+)}");
    private static final Pattern UUID_FORMAT =
            Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private static final JsonMapper JSON = JsonMapper.shared();

    private final Map<String, String> bound = new HashMap<>();

    public static Set<String> namesIn(String text) {
        return PLACEHOLDER.matcher(text).results()
                .map(match -> match.group(1))
                .collect(Collectors.toSet());
    }

    public void bind(JsonNode expected, JsonNode actual) {
        if (expected.isObject()) {
            expected.properties().forEach(field -> bind(field.getValue(), actual.path(field.getKey())));
        }
        else if (expected.isArray()) {
            for (int index = 0; index < expected.size(); index++) {
                bind(expected.get(index), actual.path(index));
            }
        }
        else if (expected.isString()) {
            bindValue(expected.asString(), actual.isString() ? actual.asString() : actual.toString());
        }
    }

    public void bindValue(String expected, String actual) {
        Matcher whole = PLACEHOLDER.matcher(expected);
        if (!whole.matches() || bound.containsKey(whole.group(1))) {
            return;
        }
        String name = whole.group(1);
        assertThat(actual).as("{%s} генерирует сервис, ожидается UUID", name).matches(UUID_FORMAT);
        bound.put(name, actual);
    }

    public String resolve(String template) {
        return PLACEHOLDER.matcher(template).replaceAll(match -> Matcher.quoteReplacement(valueOf(match.group(1))));
    }

    public JsonNode resolve(JsonNode template) {
        return JSON.readTree(resolve(JSON.writeValueAsString(template)));
    }

    private String valueOf(String name) {
        String value = bound.get(name);
        if (value == null) {
            fail("{%s} ещё не встречался в ответах: пример ссылается на него раньше, чем его выдал сервис", name);
        }
        return value;
    }
}
