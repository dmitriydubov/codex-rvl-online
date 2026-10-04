package ru.projectx.datareference.service.integration;

import java.util.UUID;
import org.springframework.stereotype.Component;
import ru.projectx.datareference.exception.OneCEnvelopeException;
import ru.projectx.datareference.service.onec.OneCGuid;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Достаёт из конверта сообщения 1С:УСК его идентификатор {@code message-id}, который нужен приёму
 * из очереди.
 *
 * <p>Конверт устроен так: {@code { "data": {…}, "metadata": { "message-id": …,
 * "topic": …, "producer": …, "creation-timestamp": … } } }. Идентификатор {@code message-id}
 * служит ключом дедупликации и ключом строки сырья, поэтому нулевой GUID в нём означает, что
 * идентификатора нет. Всё остальное разбирается позже, из сохранённого тела.
 *
 * <p>Остальные поля {@code metadata} не читаются намеренно. Поле {@code topic} у части потоков
 * пустое, потому что сообщение направляет очередь, а не это поле. Поле {@code creation-timestamp}
 * в примерах контракта приходит то строкой даты, то большим числом неизвестной единицы
 * измерения. Числовой вид истолковать нельзя, иначе в базу попадёт неверная дата.
 */
@Component
public class OneCEnvelopeParser {

    private static final String METADATA = "metadata";
    private static final String MESSAGE_ID = "message-id";
    private static final String NO_MESSAGE_ID = "в metadata нет обязательного message-id";

    /** Нулевой GUID в {@code message-id} значит, что идентификатора у сообщения нет. */
    private static final UUID ZERO_GUID = new UUID(0L, 0L);

    private final ObjectMapper json;

    public OneCEnvelopeParser(ObjectMapper json) {
        this.json = json;
    }

    /**
     * Достаёт {@code message-id} из тела сообщения.
     *
     * @throws OneCEnvelopeException если тело не читается как JSON, в нём нет {@code message-id},
     *         он нулевой или записан не как GUID в канонической форме. Такое сообщение нельзя ни
     *         дедуплицировать, ни сохранить.
     */
    public UUID messageIdOf(byte[] body) {
        JsonNode envelope = readTree(body);
        JsonNode metadata = metadataOf(envelope);
        return requireNotZero(parse(messageIdText(metadata)));
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
            throw new OneCEnvelopeException(NO_MESSAGE_ID);
        }
        return raw;
    }

    /**
     * Нулевой {@code message-id} отклоняется так же, как отсутствующий. Иначе первое такое сообщение
     * легло бы в сырьё под нулевым ключом, а все следующие молча считались бы его дублями и терялись
     * без следа в журнале.
     */
    private static UUID requireNotZero(UUID messageId) {
        if (ZERO_GUID.equals(messageId)) {
            throw new OneCEnvelopeException(NO_MESSAGE_ID);
        }
        return messageId;
    }

    /**
     * Принимает только каноническую запись GUID ({@link OneCGuid}). Нестрогий разбор прочитал бы
     * искажённый {@code message-id} как чужой GUID, и сообщение легло бы под чужим ключом
     * дедупликации. Значение в текст исключения не попадает: этим же путём приходят полисы, а текст
     * исключения попадает в журнал.
     */
    private static UUID parse(String raw) {
        return OneCGuid.parse(raw)
                .orElseThrow(() -> new OneCEnvelopeException("metadata.message-id не является GUID"));
    }
}
