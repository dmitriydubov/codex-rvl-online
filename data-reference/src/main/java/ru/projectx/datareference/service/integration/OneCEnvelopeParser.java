package ru.projectx.datareference.service.integration;

import java.util.UUID;
import org.springframework.stereotype.Component;
import ru.projectx.datareference.exception.OneCEnvelopeException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Разбор конверта сообщения 1С — ровно настолько, насколько нужно приёму.
 *
 * <p>Конверт устроен так: {@code { "data": {…}, "metadata": { "message-id": …,
 * "topic": …, "producer": …, "creation-timestamp": … } } }. Приёму отсюда нужно
 * <b>одно</b> — {@code message-id}: он и ключ дедупликации, и ключ строки сырья.
 * Всё остальное разбирается позже, из сохранённого тела.
 *
 * <p>Остальные поля {@code metadata} не читаются намеренно. {@code topic} у части потоков
 * пуст — маршрутизация идёт по очереди; {@code creation-timestamp} в разных примерах
 * контракта то ISO-строка, то большое число неизвестной единицы измерения, и числовой вид
 * интерпретировать нельзя, иначе в базу попадёт неверная дата.
 */
@Component
public class OneCEnvelopeParser {

    private static final String METADATA = "metadata";
    private static final String MESSAGE_ID = "message-id";

    private final ObjectMapper json;

    public OneCEnvelopeParser(ObjectMapper json) {
        this.json = json;
    }

    /**
     * @throws OneCEnvelopeException если тело нечитаемо или {@code message-id} нет либо
     *         он не GUID — такое сообщение невозможно ни дедуплицировать, ни сохранить
     */
    public UUID messageIdOf(byte[] body) {
        JsonNode envelope = readTree(body);
        JsonNode metadata = metadataOf(envelope);
        return parse(messageIdText(metadata));
    }

    private JsonNode readTree(byte[] body) {
        try {
            return json.readTree(body);
        }
        catch (JacksonException malformed) {
            throw new OneCEnvelopeException("тело сообщения не является читаемым JSON", malformed);
        }
    }

    private static JsonNode metadataOf(JsonNode envelope) {
        JsonNode metadata = envelope.path(METADATA);
        if (!metadata.isObject()) {
            throw new OneCEnvelopeException("в конверте нет объекта metadata");
        }
        return metadata;
    }

    private static String messageIdText(JsonNode metadata) {
        String raw = metadata.path(MESSAGE_ID).asString(null);
        if (raw == null || raw.isBlank()) {
            throw new OneCEnvelopeException("в metadata нет обязательного message-id");
        }
        return raw;
    }

    /**
     * Значение в текст исключения не идёт: этим же путём проходят полисы, а текст
     * исключения доезжает до лога.
     */
    private static UUID parse(String raw) {
        try {
            return UUID.fromString(raw);
        }
        catch (IllegalArgumentException notAGuid) {
            throw new OneCEnvelopeException("metadata.message-id не является GUID", notAGuid);
        }
    }
}
