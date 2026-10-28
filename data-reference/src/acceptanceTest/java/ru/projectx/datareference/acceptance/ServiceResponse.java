package ru.projectx.datareference.acceptance;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.client.ExchangeResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.MissingNode;

record ServiceResponse(int status, HttpHeaders headers, JsonNode body, int bodyBytes, Duration elapsed) {

    static final String REQUEST_ID = "X-Request-Id";

    private static final JsonMapper JSON = JsonMapper.shared();
    private static final String JSON_SUBTYPE = "json";
    private static final Pattern FIELD_PATH =
            Pattern.compile("[A-Za-z_][A-Za-z0-9_]*(\\[\\d+])*(\\.[A-Za-z_][A-Za-z0-9_]*(\\[\\d+])*)*");

    static ServiceResponse of(ExchangeResult result, Duration elapsed) {
        byte[] content = result.getResponseBodyContent();
        HttpHeaders headers = result.getResponseHeaders();
        JsonNode body = isJson(headers) ? JSON.readTree(content) : MissingNode.getInstance();
        return new ServiceResponse(result.getStatus().value(), headers, body, content.length, elapsed);
    }

    private static boolean isJson(HttpHeaders headers) {
        return Optional.ofNullable(headers.getContentType())
                .map(type -> JSON_SUBTYPE.equals(type.getSubtype()) || JSON_SUBTYPE.equals(type.getSubtypeSuffix()))
                .orElse(true);
    }

    JsonNode field(String path) {
        if (!FIELD_PATH.matcher(path).matches()) {
            throw new IllegalArgumentException("путь к полю ответа не по грамматике «имя[индекс].имя»: " + path);
        }
        return body.at("/" + path.replace("]", "").replace('[', '.').replace('.', '/'));
    }

    List<String> requestIds() {
        return headers.getOrEmpty(REQUEST_ID);
    }
}
