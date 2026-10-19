package ru.projectx.datareference.service.onec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ru.projectx.datareference.config.OneCJson;
import ru.projectx.datareference.entity.inbound.InboundMessage;
import ru.projectx.datareference.entity.inbound.InboundReceipt;
import ru.projectx.datareference.entity.inbound.OneCStream;
import ru.projectx.datareference.exception.OneCFieldException;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.exc.StreamConstraintsException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class StoredEnvelopeTest {

    private static final String LONGEST_STORED_NUMBER = "9".repeat(131_072) + "." + "9".repeat(16_383);
    private static final UUID MESSAGE_ID = UUID.fromString("7d2b1f0e-0000-4000-8000-0000000000cc");

    private final StoredEnvelope envelope = new StoredEnvelope(JsonMapper.builder().build());
    private final StoredEnvelope serviceEnvelope = new StoredEnvelope(OneCJson.mapper());

    @Test
    void dataOfEnvelope_isRead() {
        assertThat(envelope.dataOf(message("{\"data\":{\"code\":\"A00\"},\"metadata\":{}}")))
                .hasValueSatisfying(data -> assertThat(data.path("code").asString()).isEqualTo("A00"));
    }

    @Test
    void erasedPayload_isNoData() {
        assertThat(envelope.dataOf(message(null))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"metadata\":{}}", "{\"data\":[1]}", "{\"data\":\"A00\"}", "[1]"})
    void envelopeWithoutDataObject_isContractViolation(String payload) {
        assertThatThrownBy(() -> envelope.dataOf(message(payload)))
                .isInstanceOf(OneCFieldException.class)
                .hasMessage("в конверте сообщения нет объекта data");
    }

    @Test
    void emptyBody_isEnvelopeWithoutDataObject() {
        assertThatThrownBy(() -> envelope.dataOf(message("")))
                .isInstanceOf(OneCFieldException.class)
                .hasMessage("в конверте сообщения нет объекта data");
    }

    @Test
    void bodyThatIsNotJson_isContractViolation_withoutQuotingTheBody() {
        assertThatThrownBy(() -> envelope.dataOf(message("{не json")))
                .isInstanceOf(OneCFieldException.class)
                .hasMessage("тело сообщения не читается как JSON")
                .cause()
                .hasMessageStartingWith("tools.jackson.")
                .hasMessageNotContaining("не json");
    }

    @Test
    void numberAsLongAsDatabaseStores_isRead() {
        assertThat(serviceEnvelope.dataOf(message(envelopeOf("{\"price\":" + LONGEST_STORED_NUMBER + "}"))))
                .hasValueSatisfying(data -> assertThat(data.path("price").decimalValue().precision())
                        .isEqualTo(147_455));
    }

    @Test
    void integerAtNumberLengthLimit_isRead() {
        assertThat(serviceEnvelope.dataOf(message(envelopeOf("{\"price\":" + "9".repeat(147_455) + "}"))))
                .hasValueSatisfying(data -> assertThat(data.path("price").decimalValue().precision())
                        .isEqualTo(147_455));
    }

    @Test
    void integerOneDigitBeyondNumberLengthLimit_isNotJson() {
        assertThatThrownBy(() -> serviceEnvelope.dataOf(message(envelopeOf("{\"price\":" + "9".repeat(147_456) + "}"))))
                .isInstanceOf(OneCFieldException.class)
                .hasMessage("тело сообщения не читается как JSON")
                .cause()
                .hasMessageStartingWith(StreamConstraintsException.class.getName());
    }

    @Test
    void sharedMapper_keepsItsNumberLengthLimit() {
        ObjectMapper shared = OneCJson.mapper();
        StoredEnvelope reading = new StoredEnvelope(shared);

        assertThat(reading.dataOf(message(envelopeOf("{\"price\":" + "1".repeat(1001) + "}")))).isPresent();
        assertThat(shared.tokenStreamFactory().streamReadConstraints().getMaxNumberLength())
                .isEqualTo(StreamReadConstraints.DEFAULT_MAX_NUM_LEN);
        assertThatThrownBy(() -> shared.readTree("{\"price\":" + "1".repeat(1001) + "}"))
                .isInstanceOf(StreamConstraintsException.class);
    }

    @Test
    void messageFullOfLongestNumbers_isReadFast() {
        String row = "{\"price\":%1$s,\"priceWithDiscount\":%1$s,\"unknown\":%1$s}".formatted(LONGEST_STORED_NUMBER);
        String rows = IntStream.range(0, 100).mapToObj(index -> row).collect(Collectors.joining(","));
        InboundMessage message = message(envelopeOf("{\"recordSet\":[" + rows + "]}"));

        assertTimeoutPreemptively(Duration.ofSeconds(20), () -> assertThat(serviceEnvelope.dataOf(message))
                .hasValueSatisfying(data -> assertThat(data.path("recordSet").size()).isEqualTo(100)));
    }

    private static String envelopeOf(String data) {
        return "{\"data\":" + data + ",\"metadata\":{}}";
    }

    private static InboundMessage message(String payload) {
        return InboundMessage.receive(
                new InboundReceipt(MESSAGE_ID, OneCStream.PRICE_LISTS, "очередь", Instant.EPOCH), payload);
    }
}
