package ru.projectx.datareference.service.onec;

import java.util.Optional;
import ru.projectx.datareference.entity.InboundMessage;
import ru.projectx.datareference.exception.OneCFieldException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Поле {@code data} конверта {@code {data, metadata}}, сохранённого в строке сырья: по нему идёт разбор, а
 * {@code metadata} нужна была только приёму.
 *
 * <p>Стёртое по сроку хранения тело даёт пустой результат, и что это значит, решает вызывающий. Текст отказа не
 * цитирует тело: в теле полиса бывают персональные данные.
 */
final class StoredEnvelope {

    private static final String DATA = "data";

    private final ObjectMapper json;

    StoredEnvelope(ObjectMapper json) {
        this.json = json;
    }

    /**
     * @return пусто, если тело сообщения стёрто по сроку хранения
     * @throws OneCFieldException если тело не читается как JSON или в конверте нет объекта {@code data}
     */
    Optional<ObjectNode> dataOf(InboundMessage message) {
        return Optional.ofNullable(message.payload()).map(this::dataOf);
    }

    private ObjectNode dataOf(String payload) {
        JsonNode data = readTree(payload).get(DATA);
        if (data == null || !data.isObject()) {
            throw new OneCFieldException("в конверте сообщения нет объекта data");
        }
        return (ObjectNode) data;
    }

    private JsonNode readTree(String payload) {
        try {
            return json.readTree(payload);
        }
        catch (JacksonException malformed) {
            // В таблицу попадает только то, что PostgreSQL принял как JSON, так что случай почти невозможный.
            // Но и тогда нужна внятная постоянная ошибка, а не обращение к пустой ссылке дальше по разбору.
            throw new OneCFieldException("тело сообщения не читается как JSON", malformed);
        }
    }
}
