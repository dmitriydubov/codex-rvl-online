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
 * Защищает разбор конверта сообщения 1С:УСК: из тела достаётся только {@code message-id}, а
 * сообщение без пригодного {@code message-id} отклоняется.
 *
 * <p>Разбор — чистая функция от байтов, поэтому проверяется без контекста Spring, базы и брокера.
 * {@code ObjectMapper} берётся у приложения, а не собирается заново: разбор обязан работать на той
 * настройке, которая стоит в проде, в частности на игнорировании незнакомых полей.
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

    /** Если metadata не объект, это конверт без metadata, а не metadata без message-id. */
    @Test
    void metadataThatIsNotObject_isRejectedAsMissingMetadata() {
        assertThatThrownBy(() -> parser.messageIdOf(bytes("""
                {"data":{},"metadata":"message-id"}""")))
                .isInstanceOf(OneCEnvelopeException.class)
                .hasMessage("в конверте нет объекта metadata");
    }

    /**
     * Нулевой GUID в message-id значит, что идентификатора нет. Принять такое сообщение нельзя: первое
     * легло бы в сырьё под нулевым ключом, а все следующие молча считались бы его дублями и терялись.
     */
    @Test
    void zeroMessageId_isRejectedAsMissing() {
        String body = """
                {"data":{},"metadata":{"message-id":"00000000-0000-0000-0000-000000000000"}}""";

        assertThatThrownBy(() -> parser.messageIdOf(bytes(body)))
                .isInstanceOf(OneCEnvelopeException.class)
                .hasMessage("в metadata нет обязательного message-id");
    }

    /** Пустой и пробельный message-id считается отсутствующим, а не «не GUID». */
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
     * message-id принимается только в канонической записи 8-4-4-4-12. Эти значения
     * {@code UUID.fromString} в JDK 21 молча читает как другой GUID, и сообщение легло бы в сырьё под
     * чужим ключом дедупликации. Отказ такой же, как у нечитаемого message-id: без значения и без
     * исключения-причины, текст которого процитировал бы значение.
     */
    @ParameterizedTest
    @ValueSource(strings = {"1-2-3-4-5", "0-0-0-0-0", "09f1c0f2e-0000-4000-8000-00000000000",
            "9f1c0f2e-0000-4000-8000-+0000000000a", "9f1c0f2e-0000-4000-8000-00000000000\uFF15"})
    void nonCanonicalMessageId_isRejected(String messageId) {
        String body = """
                {"data":{},"metadata":{"message-id":"%s"}}""".formatted(messageId);

        assertThatThrownBy(() -> parser.messageIdOf(bytes(body)))
                .isInstanceOf(OneCEnvelopeException.class)
                .hasMessage("metadata.message-id не является GUID")
                .hasNoCause();
    }

    /**
     * Конверт не обрезает message-id, в отличие от полей сообщения. С пробелом или переводом строки
     * по краям message-id записан не канонически и отклоняется.
     */
    @ParameterizedTest
    @ValueSource(strings = {" 9f1c0f2e-0000-4000-8000-00000000000a", "9f1c0f2e-0000-4000-8000-00000000000a\\n"})
    void messageIdWithSurroundingWhitespace_isRejected(String messageId) {
        String body = """
                {"data":{},"metadata":{"message-id":"%s"}}""".formatted(messageId);

        assertThatThrownBy(() -> parser.messageIdOf(bytes(body)))
                .isInstanceOf(OneCEnvelopeException.class)
                .hasMessage("metadata.message-id не является GUID");
    }

    /** 1С:УСК регистр не задаёт, поэтому message-id прописными цифрами — тот же GUID. */
    @Test
    void uppercaseMessageId_isTheSameGuid() {
        String body = """
                {"data":{},"metadata":{"message-id":"9F1C0F2E-0000-4000-8000-00000000000A"}}""";

        assertThat(parser.messageIdOf(bytes(body))).isEqualTo(MESSAGE_ID);
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

    /**
     * Нечитаемое тело может оказаться полисом. Jackson называет в своей ошибке токен, на котором
     * споткнулся ({@code Unrecognized token '…'}), поэтому её текст в отказ не идёт. Отказ — это
     * фиксированная фраза без содержимого тела.
     */
    @Test
    void unreadableBody_isRejectedWithoutQuotingIt() {
        String brokenPolicy = """
                {"data":{"surname":Testova},"metadata":{"message-id":"%s"}}""".formatted(MESSAGE_ID);

        assertThatThrownBy(() -> parser.messageIdOf(bytes(brokenPolicy)))
                .isInstanceOf(OneCEnvelopeException.class)
                .hasMessage("тело сообщения не является читаемым JSON");
    }

    /**
     * Пустое тело отклоняется как неопознаваемое сообщение, а не роняет разбор другим
     * исключением: приём перехватывает только {@link OneCEnvelopeException}.
     */
    @Test
    void emptyBody_isRejected() {
        assertThatThrownBy(() -> parser.messageIdOf(new byte[0]))
                .isInstanceOf(OneCEnvelopeException.class)
                .hasMessage("в конверте нет объекта metadata");
    }

    /**
     * message-id объектом или массивом текстом не является. Такой конверт отклоняется как
     * неопознаваемый, а не роняет разбор исключением Jackson о приведении типа.
     */
    @ParameterizedTest
    @ValueSource(strings = {"{}", "[]"})
    void messageIdThatIsNotText_isRejected(String messageId) {
        String body = """
                {"data":{},"metadata":{"message-id":%s}}""".formatted(messageId);

        assertThatThrownBy(() -> parser.messageIdOf(bytes(body)))
                .isInstanceOf(OneCEnvelopeException.class)
                .hasMessage("в metadata нет обязательного message-id");
    }

    private static byte[] bytes(String body) {
        return body.getBytes(StandardCharsets.UTF_8);
    }
}
