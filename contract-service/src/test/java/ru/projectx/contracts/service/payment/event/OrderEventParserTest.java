package ru.projectx.contracts.service.payment.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ru.projectx.contracts.exception.ContractViolationException;
import ru.projectx.contracts.support.OrderingTestProperties;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

class OrderEventParserTest {

    private static final UUID DOCUMENT = UUID.fromString("0196f0a2-0000-7000-8000-000000000011");
    private static final String EVENT = """
            {"eventId": "d1a0c001-5e2f-4a3b-8c4d-000000000021", "eventType": %s, "orderId": %s,
             "orderStatus": "PAID", "updated": %s,
             "orderDetails": [{"product": "rvl", "documentId": "0196f0a2-0000-7000-8000-000000000011",
                               "documentPremium": 34000.00}]}
            """;

    private final OrderEventParser parser =
            new OrderEventParser(JsonMapper.builder().build(), OrderingTestProperties.attempts(3));

    private OrderEvent parsed(String type, String orderId, String updated) {
        return parser.parse(EVENT.formatted(type, orderId, updated));
    }

    @Test
    void event_isReadWithItsTypeOrderDocumentAndMomentOfTheChange() {
        OrderEvent event = parsed("\"paid\"", "\"order-1\"", "\"2026-02-10T10:05:03.250000+03:00\"");

        assertThat(event).isEqualTo(new OrderEvent(Optional.of("paid"), Optional.of("order-1"),
                new EventDocument.Ours(DOCUMENT, Optional.of(new BigDecimal("34000.00"))),
                Optional.of(Instant.parse("2026-02-10T07:05:03.250Z"))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "\"\"", "\"  \"", "17", "{\"name\": \"paid\"}"})
    void typeThatIsNotAFilledString_isUnknown(String type) {
        OrderEvent event = parsed(type, "\"order-1\"", "\"2026-02-10T10:05:03+03:00\"");

        assertThat(event.type()).isEmpty();
        assertThat(event.orderId()).contains("order-1");
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "\"\"", "\" \"", "17"})
    void orderIdThatIsNotAFilledString_isUnknown(String orderId) {
        OrderEvent event = parsed("\"paid\"", orderId, "\"2026-02-10T10:05:03+03:00\"");

        assertThat(event.orderId()).isEmpty();
        assertThat(event.type()).contains("paid");
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "\"\"", "\"вчера\"", "\"2026-02-10T10:05:03\"", "1770707103"})
    void momentThatIsNotADateWithOffset_isUnknown(String updated) {
        assertThat(parsed("\"paid\"", "\"order-1\"", updated).updated()).isEmpty();
    }

    @Test
    void eventWithoutFields_isReadAsUnknownTypeOrderAndForeignDocument() {
        OrderEvent event = parser.parse("{}");

        assertThat(event).isEqualTo(new OrderEvent(Optional.empty(), Optional.empty(),
                new EventDocument.Foreign("в событии нет документа продукта Договоров"), Optional.empty()));
    }

    @Test
    void documentOfAnotherProduct_isNotOurs() {
        OrderEvent event = parser.parse("""
                {"eventType": "paid", "orderId": "order-1",
                 "orderDetails": [{"product": "dmsfl", "documentId": "0196f0a2-0000-7000-8000-000000000011"}]}
                """);

        assertThat(event.document()).isEqualTo(new EventDocument.Foreign("в событии нет документа продукта Договоров"));
    }

    @Test
    void premium_isReadExactlyAsWrittenWithoutBinaryRounding() {
        OrderEvent event = parser.parse("""
                {"orderDetails": [{"product": "rvl", "documentId": "0196f0a2-0000-7000-8000-000000000011",
                                   "documentPremium": 33999.10}]}
                """);

        assertThat(event.document())
                .isEqualTo(new EventDocument.Ours(DOCUMENT, Optional.of(new BigDecimal("33999.10"))));
    }

    @Test
    void bodyThatIsNotJson_isAContractViolationWithoutTheBodyInTheText() {
        assertThatThrownBy(() -> parser.parse("eventType=paid; insured@example.test"))
                .isInstanceOf(ContractViolationException.class)
                .hasMessage("тело не читается как JSON")
                .hasCauseInstanceOf(JacksonException.class);
    }
}
