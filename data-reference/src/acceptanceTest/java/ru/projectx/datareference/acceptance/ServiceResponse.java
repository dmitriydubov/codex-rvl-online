package ru.projectx.datareference.acceptance;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.client.ExchangeResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.MissingNode;

/**
 * Ответ Дата-референса, каким его видит сервис Договоров. Пустое тело и тело не в JSON читаются как
 * {@code MissingNode}, а тип такого ответа проверяет отдельный шаг. Отсутствующее поле тоже даёт {@code MissingNode},
 * а не {@code null}, чтобы «поля нет» и «поле пусто» оставались разными исходами.
 */
record ServiceResponse(int status, HttpHeaders headers, JsonNode body, int bodyBytes) {

    static final String REQUEST_ID = "X-Request-Id";

    private static final JsonMapper JSON = JsonMapper.shared();
    private static final String JSON_SUBTYPE = "json";
    /** Путь к полю: имена через точку, у имени — необязательные индексы массива в квадратных скобках. */
    private static final Pattern FIELD_PATH =
            Pattern.compile("[A-Za-z_][A-Za-z0-9_]*(\\[\\d+])*(\\.[A-Za-z_][A-Za-z0-9_]*(\\[\\d+])*)*");

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

    /**
     * Узел по пути из сценария. Путь вне грамматики (незакрытая скобка, пустое имя, индекс не числом) —
     * ошибка самого шага: иначе искажённый путь вроде {@code services[0.layer_number} молча свёлся бы к тому же
     * JSON Pointer, что и верный, и проверка прошла бы по полю, которого сценарий не называл.
     */
    JsonNode field(String path) {
        if (!FIELD_PATH.matcher(path).matches()) {
            throw new IllegalArgumentException("путь к полю ответа не по грамматике «имя[индекс].имя»: " + path);
        }
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
