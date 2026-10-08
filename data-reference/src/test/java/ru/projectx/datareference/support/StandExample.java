package ru.projectx.datareference.support;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.http.HttpMethod;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Пример запроса и ответа стенда из {@code src/test/resources/stand/examples}; те же файлы читает разработчик сервиса
 * Договоров. Примеры идут по порядку имён файлов, и порядок значим: пример, выдающий {@code id}, стоит раньше того,
 * что им пользуется ({@link StandPlaceholders}).
 */
public record StandExample(String name, String text, JsonNode root) {

    private static final String LOCATION = "classpath:stand/examples/*.json";
    private static final JsonMapper JSON = JsonMapper.shared();

    public static List<StandExample> loadAll() {
        try {
            Resource[] files = new PathMatchingResourcePatternResolver().getResources(LOCATION);
            return Arrays.stream(files)
                    .map(StandExample::load)
                    .sorted(Comparator.comparing(StandExample::name))
                    .toList();
        }
        catch (IOException unreadable) {
            throw new UncheckedIOException("не прочитать примеры стенда " + LOCATION, unreadable);
        }
    }

    private static StandExample load(Resource file) {
        try {
            String text = file.getContentAsString(StandardCharsets.UTF_8);
            return new StandExample(file.getFilename(), text, JSON.readTree(text));
        }
        catch (IOException unreadable) {
            throw new UncheckedIOException("не прочитать пример стенда " + file.getFilename(), unreadable);
        }
    }

    public HttpMethod method() {
        return HttpMethod.valueOf(root.path("request").path("method").asString());
    }

    public String path() {
        return root.path("request").path("path").asString();
    }

    public Map<String, String> requestHeaders() {
        return headers(root.path("request").path("headers"));
    }

    public int status() {
        return root.path("response").path("status").asInt();
    }

    public Map<String, String> responseHeaders() {
        return headers(root.path("response").path("headers"));
    }

    public JsonNode body() {
        return root.path("response").path("body");
    }

    /**
     * Таблица, которую на время запроса другой сеанс держит под ACCESS EXCLUSIVE ({@code condition.locked_table}):
     * так пример показывает 503 при базе, не ответившей вовремя.
     */
    public Optional<String> lockedTable() {
        JsonNode table = root.path("condition").path("locked_table");
        return table.isMissingNode() ? Optional.empty() : Optional.of(table.asString());
    }

    /** Плейсхолдеры, которые пример описал в разделе {@code placeholders}. */
    public Set<String> describedPlaceholders() {
        return Set.copyOf(root.path("placeholders").propertyNames());
    }

    private static Map<String, String> headers(JsonNode headers) {
        return headers.properties().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, header -> header.getValue().asString()));
    }
}
