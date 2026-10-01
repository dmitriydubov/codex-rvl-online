package ru.projectx.datareference.service.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ru.projectx.datareference.config.JacksonConfig;
import ru.projectx.datareference.exception.OneCEnvelopeException;

/**
 * Разбор конверта — чистая функция от байтов, и проверяется без контекста Spring:
 * ни базы, ни брокера здесь не нужно.
 *
 * <p>{@code ObjectMapper} берётся у приложения, а не собирается заново: разбор обязан
 * работать на той настройке, которая стоит в проде — в частности, на игнорировании
 * незнакомых полей.
 */
class OneCEnvelopeParserTest {

    private static final UUID MESSAGE_ID = UUID.fromString("9f1c0f2e-0000-4000-8000-00000000000a");

    private final OneCEnvelopeParser parser = new OneCEnvelopeParser(new JacksonConfig().objectMapper());

    @Test
    void messageId_isTakenFromMetadata() {
        String body = """
                {"data":{"guid":"1a2b3c4d-0000-4000-8000-000000000001"},\
                "metadata":{"message-id":"%s","topic":"","producer":"1c-usk"}}"""
                .formatted(MESSAGE_ID);

        assertThat(parser.messageIdOf(bytes(body))).isEqualTo(MESSAGE_ID);
    }

    /**
     * {@code creation-timestamp} в примерах контракта то ISO-строка, то большое число
     * неизвестной единицы измерения. Разбор конверта на этом спотыкаться не должен:
     * поле не читается вовсе.
     */
    @ParameterizedTest
    @ValueSource(strings = {"\"2024-10-10T00:00:00+03:00\"", "1728507600000"})
    void creationTimestampOfAnyShape_doesNotBreakParsing(String timestamp) {
        String body = """
                {"data":{},"metadata":{"message-id":"%s","creation-timestamp":%s}}"""
                .formatted(MESSAGE_ID, timestamp);

        assertThat(parser.messageIdOf(bytes(body))).isEqualTo(MESSAGE_ID);
    }

    @Test
    void bodyThatIsNotJson_isRejected() {
        assertThatThrownBy(() -> parser.messageIdOf(bytes("не json вовсе")))
                .isInstanceOf(OneCEnvelopeException.class);
    }

    @Test
    void envelopeWithoutMetadata_isRejected() {
        assertThatThrownBy(() -> parser.messageIdOf(bytes("""
                {"data":{"guid":"1a2b3c4d-0000-4000-8000-000000000001"}}""")))
                .isInstanceOf(OneCEnvelopeException.class)
                .hasMessageContaining("metadata");
    }

    @Test
    void metadataWithoutMessageId_isRejected() {
        assertThatThrownBy(() -> parser.messageIdOf(bytes("""
                {"data":{},"metadata":{"producer":"1c-usk"}}""")))
                .isInstanceOf(OneCEnvelopeException.class)
                .hasMessageContaining("message-id");
    }

    /** metadata не объект — это конверт без metadata, а не metadata без message-id. */
    @Test
    void metadataThatIsNotObject_isRejectedAsMissingMetadata() {
        assertThatThrownBy(() -> parser.messageIdOf(bytes("""
                {"data":{},"metadata":"message-id"}""")))
                .isInstanceOf(OneCEnvelopeException.class)
                .hasMessage("в конверте нет объекта metadata");
    }

    /** Пустой и пробельный message-id — отсутствующий, а не «не GUID». */
    @ParameterizedTest
    @ValueSource(strings = {"\"\"", "\"   \"", "null"})
    void blankMessageId_isRejectedAsMissing(String messageId) {
        String body = """
                {"data":{},"metadata":{"message-id":%s}}""".formatted(messageId);

        assertThatThrownBy(() -> parser.messageIdOf(bytes(body)))
                .isInstanceOf(OneCEnvelopeException.class)
                .hasMessage("в metadata нет обязательного message-id");
    }

    @Test
    void messageIdThatIsNotGuid_isRejected() {
        assertThatThrownBy(() -> parser.messageIdOf(bytes("""
                {"data":{},"metadata":{"message-id":"не-guid"}}""")))
                .isInstanceOf(OneCEnvelopeException.class)
                .hasMessageContaining("GUID");
    }

    /**
     * Текст исключения доезжает до лога, а этим же путём идут полисы. Поэтому в ошибке
     * называется поле, но не его значение и тем более не тело сообщения.
     */
    @Test
    void rejection_doesNotQuoteMessageContent() {
        String policyLikeBody = """
                {"data":{"surname":"Иванов","birthDate":"1980-05-17"},\
                "metadata":{"message-id":"9f1c0f2e-НЕ-GUID"}}""";

        assertThatThrownBy(() -> parser.messageIdOf(bytes(policyLikeBody)))
                .isInstanceOf(OneCEnvelopeException.class)
                .extracting(Throwable::getMessage)
                .asString()
                .doesNotContain("Иванов", "1980-05-17", "9f1c0f2e-НЕ-GUID");
    }

    /**
     * Сообщения приходят из чужой системы, и на нечитаемом теле разбор обязан
     * завершиться, а не задуматься: потребитель очереди однопоточный, и застрявший
     * разбор останавливает весь поток.
     */
    @Test
    @Timeout(5)
    void truncatedBody_isRejectedRatherThanAwaited() {
        assertThatThrownBy(() -> parser.messageIdOf(bytes("""
                {"data":{"guid":"1a2b3c4d-0000-4000-8000-0000""")))
                .isInstanceOf(OneCEnvelopeException.class);
    }

    private static byte[] bytes(String body) {
        return body.getBytes(StandardCharsets.UTF_8);
    }
}
