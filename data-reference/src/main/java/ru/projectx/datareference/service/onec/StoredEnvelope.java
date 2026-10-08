package ru.projectx.datareference.service.onec;

import java.util.Optional;
import ru.projectx.datareference.entity.InboundMessage;
import ru.projectx.datareference.exception.OneCFieldException;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.ObjectReadContext;
import tools.jackson.core.TokenStreamFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Объект {@code data} из конверта сообщения 1С, сохранённого в строке сырья: по нему идёт разбор.
 * Предел длины числа здесь шире общего, потому что база возвращает короткое {@code 1e1000} тысячей с лишним цифр.
 * Настройки чтения, заданные самому общему мапперу, сюда не переходят; появятся такие настройки — перенесите их сюда.
 */
final class StoredEnvelope {

    private static final String DATA = "data";

    /**
     * Самое длинное число, которое хранит тип {@code numeric} PostgreSQL: 131072 цифры до запятой и 16383 после.
     * Длиннее в сырьё ничего не попадёт. Предел ограничивает время и память на чтение одного числа, а не сообщения.
     */
    static final int MAX_STORED_NUMBER_DIGITS = 131_072 + 16_383;

    private final ObjectMapper json;
    private final TokenStreamFactory storedJson;

    /** Общий маппер не меняется: его предел длины числа по-прежнему защищает запросы REST и приём из очереди. */
    StoredEnvelope(ObjectMapper json) {
        this.json = json;
        this.storedJson = json.tokenStreamFactory().rebuild()
                .streamReadConstraints(json.tokenStreamFactory().streamReadConstraints().rebuild()
                        .maxNumberLength(MAX_STORED_NUMBER_DIGITS)
                        .build())
                .build();
    }

    /**
     * Текст отказа не цитирует тело, потому что в теле полиса бывают персональные данные.
     *
     * @return пусто, если тело сообщения стёрто по сроку хранения
     * @throws OneCFieldException если тело не читается как JSON или в конверте нет объекта {@code data}
     */
    Optional<ObjectNode> dataOf(InboundMessage message) {
        return Optional.ofNullable(message.payload()).map(this::dataOf);
    }

    /** Пустое тело Jackson читает как отсутствие дерева, и такой конверт тоже без объекта {@code data}. */
    private ObjectNode dataOf(String payload) {
        return Optional.ofNullable(readTree(payload))
                .map(envelope -> envelope.get(DATA))
                .filter(JsonNode::isObject)
                .map(ObjectNode.class::cast)
                .orElseThrow(() -> new OneCFieldException("в конверте сообщения нет объекта data"));
    }

    private JsonNode readTree(String payload) {
        try (JsonParser parser = storedJson.createParser(ObjectReadContext.empty(), payload)) {
            return json.readTree(parser);
        }
        catch (JacksonException malformed) {
            // В таблицу попадает только то, что PostgreSQL принял как JSON, так что случай почти невозможный.
            // Но и тогда нужна внятная постоянная ошибка, а не обращение к пустой ссылке дальше по разбору.
            throw new OneCFieldException("тело сообщения не читается как JSON", malformed);
        }
    }
}
