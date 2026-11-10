package ru.projectx.contracts.service.integration.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ru.projectx.contracts.config.payment.OrderingProperties;
import ru.projectx.contracts.entity.inbox.InboxReceipt;
import ru.projectx.contracts.exception.InboxEnvelopeException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

class OrderEventEnvelopesTest {

    private static final String EVENT_ID = "d1a0c001-5e2f-4a3b-8c4d-000000000001";
    private static final String DOCUMENT_ID = "0196f0a2-0000-7000-8000-000000000011";
    private static final String OTHER_DOCUMENT_ID = "0196f0a2-0000-7000-8000-000000000022";
    private static final String TOPIC = "ordering.order.events";
    private static final String EVENT_TYPE = "PaymentOrderEvent";

    private final OrderEventEnvelopes envelopes = new OrderEventEnvelopes(JsonMapper.builder().build(),
            new OrderingProperties("http://ordering.test", Duration.ofSeconds(1), Duration.ofSeconds(1), "rvl", "rvl-lk",
                    "notActual", new OrderingProperties.Retry(3, Duration.ZERO),
                    new OrderingProperties.Document(ZoneId.of("Europe/Moscow"), Duration.ofSeconds(1)),
                    new OrderingProperties.Token("http://keycloak.test/token", "contracts-stand", "secret",
                            Duration.ofSeconds(1), Duration.ofSeconds(1))));

    @Test
    void eventWithGuidIdentifierAndOurDocument_isStoredUnderTheEventIdAndTheDocumentId() {
        TaskEnvelope envelope = envelopes.parse(event(EVENT_ID, document("rvl", DOCUMENT_ID)));

        assertThat(envelope).isEqualTo(new TaskEnvelope.Storable(new InboxReceipt(UUID.fromString(EVENT_ID),
                UUID.fromString(DOCUMENT_ID), TOPIC, EVENT_TYPE, 0)));
    }

    @Test
    void guidIdentifierInUpperCase_isTheSameMessageId() {
        TaskEnvelope envelope = envelopes.parse(event(EVENT_ID.toUpperCase(), document("rvl", DOCUMENT_ID)));

        assertThat(envelope.receipt().messageId()).isEqualTo(UUID.fromString(EVENT_ID));
    }

    @ParameterizedTest
    @ValueSource(strings = {"evt-2026-000017", "d1a0c001-5e2f-4a3b-8c4d-00000000000", "идентификатор события"})
    void identifierThatIsNotAGuid_becomesTheNameBasedGuidOfItsUtf8Bytes(String eventId) {
        TaskEnvelope envelope = envelopes.parse(event(eventId, document("rvl", DOCUMENT_ID)));

        assertThat(envelope.receipt().messageId())
                .isEqualTo(UUID.nameUUIDFromBytes(eventId.getBytes(StandardCharsets.UTF_8)));
        assertThat(envelope.receipt().correlationId()).isEqualTo(UUID.fromString(DOCUMENT_ID));
    }

    @Test
    void sameIdentifierThatIsNotAGuid_givesTheSameMessageIdEveryTime() {
        byte[] body = event("evt-2026-000017", document("rvl", DOCUMENT_ID));

        assertThat(envelopes.parse(body).receipt().messageId()).isEqualTo(envelopes.parse(body).receipt().messageId())
                .isNotEqualTo(envelopes.parse(event("evt-2026-000018", document("rvl", DOCUMENT_ID))).receipt()
                        .messageId());
    }

    @Test
    void documentOfOurProductAmongOthers_givesTheCorrelation() {
        TaskEnvelope envelope = envelopes.parse(event(EVENT_ID,
                document("osago", OTHER_DOCUMENT_ID) + "," + document("rvl", DOCUMENT_ID)));

        assertThat(envelope.receipt().correlationId()).isEqualTo(UUID.fromString(DOCUMENT_ID));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{\"eventId\":\"d1a0c001-5e2f-4a3b-8c4d-000000000001\"}",
        "{\"eventId\":\"d1a0c001-5e2f-4a3b-8c4d-000000000001\",\"orderDetails\":[]}",
        "{\"eventId\":\"d1a0c001-5e2f-4a3b-8c4d-000000000001\",\"orderDetails\":"
                + "[{\"product\":\"osago\",\"documentId\":\"0196f0a2-0000-7000-8000-000000000022\"}]}",
        "{\"eventId\":\"d1a0c001-5e2f-4a3b-8c4d-000000000001\",\"orderDetails\":"
                + "[{\"product\":\"rvl\",\"documentId\":\"DKM-2026-000001\"}]}",
        "{\"eventId\":\"d1a0c001-5e2f-4a3b-8c4d-000000000001\",\"orderDetails\":[{\"product\":\"rvl\"}]}"})
    void eventWithoutOurDocumentInGuidForm_isCorrelatedByItsOwnMessageId(String body) {
        TaskEnvelope envelope = envelopes.parse(body.getBytes(StandardCharsets.UTF_8));

        assertThat(envelope).isEqualTo(new TaskEnvelope.Storable(new InboxReceipt(UUID.fromString(EVENT_ID),
                UUID.fromString(EVENT_ID), TOPIC, EVENT_TYPE, 0)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"eventId\":null}", "{\"eventId\":\"\"}", "{\"eventId\":\"   \"}",
        "{\"eventId\":42}", "{\"eventId\":{\"id\":\"d1a0c001-5e2f-4a3b-8c4d-000000000001\"}}", "[]", "\"событие\"",
        ""})
    void eventWithoutUsableIdentifier_isLostAsAnUnusableEnvelope(String body) {
        assertThatThrownBy(() -> envelopes.parse(body.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOfSatisfying(InboxEnvelopeException.class,
                        lost -> assertThat(lost.lossReason()).isEqualTo("unusable_envelope"))
                .hasMessage("в событии нет eventId");
    }

    @ParameterizedTest
    @ValueSource(strings = {"<не JSON>", "{\"eventId\":\"d1a0c001-5e2f-4a3b-8c4d-000000000001\"", "{\"eventId\":}"})
    void bodyThatIsNotJson_isLostAsUnreadableWithoutItsText(String body) {
        assertThatThrownBy(() -> envelopes.parse(body.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOfSatisfying(InboxEnvelopeException.class,
                        lost -> assertThat(lost.lossReason()).isEqualTo("unreadable_body"))
                .hasMessage("тело не читается как JSON")
                .hasCauseInstanceOf(JacksonException.class);
    }

    @Test
    void eventWithTextTheDatabaseCannotStore_isUnstorableUnderItsReceipt() {
        byte[] body = ("{\"eventId\":\"" + EVENT_ID + "\",\"comment\":\"оплата\\u0000\",\"orderDetails\":["
                + document("rvl", DOCUMENT_ID) + "]}").getBytes(StandardCharsets.UTF_8);

        assertThat(envelopes.parse(body)).isEqualTo(new TaskEnvelope.Unstorable(new InboxReceipt(
                UUID.fromString(EVENT_ID), UUID.fromString(DOCUMENT_ID), TOPIC, EVENT_TYPE, 0)));
    }

    private static byte[] event(String eventId, String documents) {
        return ("{\"eventId\":\"" + eventId + "\",\"eventType\":\"paid\",\"orderId\":"
                + "\"0196f0a2-5b1c-7d3e-9f40-1a2b3c4d5e01\",\"orderDetails\":[" + documents + "]}")
                .getBytes(StandardCharsets.UTF_8);
    }

    private static String document(String product, String documentId) {
        return "{\"product\":\"" + product + "\",\"documentId\":\"" + documentId + "\",\"documentPremium\":34000.00}";
    }
}
