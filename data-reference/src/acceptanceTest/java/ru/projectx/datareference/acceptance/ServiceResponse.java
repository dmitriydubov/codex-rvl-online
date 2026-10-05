package ru.projectx.datareference.acceptance;

import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.client.ExchangeResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.MissingNode;

/**
 * Ответ Дата-референса, каким его видит сервис Договоров: статус, заголовки, тело JSON и размер тела
 * в байтах. Пустое тело (ответ на HEAD и OPTIONS) и тело не в JSON (страница ошибки самого сервера до
 * сервиса) читаются как {@code MissingNode}: у такого ответа нет полей, а его тип проверяет отдельный шаг.
 *
 * <p>Поле тела задаётся путём, как в сценариях: имена через точку, индекс массива
 * в квадратных скобках ({@code programs[0].program.id}). Путь переводится в JSON Pointer
 * ({@code /programs/0/program/id}), и отсутствующее поле даёт {@code MissingNode},
 * а не {@code null}: «поля нет» и «поле пусто» остаются разными исходами.
 */
record ServiceResponse(int status, HttpHeaders headers, JsonNode body, int bodyBytes) {

    static final String REQUEST_ID = "X-Request-Id";

    private static final JsonMapper JSON = JsonMapper.shared();
    private static final String JSON_SUBTYPE = "json";

    static ServiceResponse of(ExchangeResult result) {
        byte[] content = result.getResponseBodyContent();
        HttpHeaders headers = result.getResponseHeaders();
        JsonNode body = isJson(headers) ? JSON.readTree(content) : MissingNode.getInstance();
        return new ServiceResponse(result.getStatus().value(), headers, body, content.length);
    }

    /**
     * JSON — и {@code application/json}, и тип с суффиксом {@code +json}, как у ручки здоровья. Тело без типа —
     * пустое: его чтение как JSON и даёт {@code MissingNode}.
     */
    private static boolean isJson(HttpHeaders headers) {
        return Optional.ofNullable(headers.getContentType())
                .map(type -> JSON_SUBTYPE.equals(type.getSubtype()) || JSON_SUBTYPE.equals(type.getSubtypeSuffix()))
                .orElse(true);
    }

    JsonNode field(String path) {
        return body.at("/" + path.replace("]", "").replace('[', '.').replace('.', '/'));
    }

    /**
     * Все значения заголовка {@code X-Request-Id}: не только первое, чтобы второй заголовок
     * (присланный и выданный сервисом разом) не прятался за первым.
     */
    List<String> requestIds() {
        return headers.getOrEmpty(REQUEST_ID);
    }
}
