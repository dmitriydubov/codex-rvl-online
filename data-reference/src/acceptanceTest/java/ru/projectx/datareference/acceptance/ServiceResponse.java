package ru.projectx.datareference.acceptance;

import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.client.ExchangeResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Ответ Дата-референса, каким его видит сервис Договоров: статус, заголовки и тело JSON.
 *
 * <p>Поле тела задаётся путём, как в сценариях: имена через точку, индекс массива
 * в квадратных скобках ({@code programs[0].program.id}). Путь переводится в JSON Pointer
 * ({@code /programs/0/program/id}), и отсутствующее поле даёт {@code MissingNode},
 * а не {@code null}: «поля нет» и «поле пусто» остаются разными исходами.
 */
record ServiceResponse(int status, HttpHeaders headers, JsonNode body) {

    static final String REQUEST_ID = "X-Request-Id";

    private static final JsonMapper JSON = JsonMapper.shared();

    static ServiceResponse of(ExchangeResult result) {
        return new ServiceResponse(result.getStatus().value(), result.getResponseHeaders(),
                JSON.readTree(result.getResponseBodyContent()));
    }

    JsonNode field(String path) {
        return body.at("/" + path.replace("]", "").replace('[', '.').replace('.', '/'));
    }

    Optional<String> requestId() {
        return Optional.ofNullable(headers.getFirst(REQUEST_ID));
    }
}
