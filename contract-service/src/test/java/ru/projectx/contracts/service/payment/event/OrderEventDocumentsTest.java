package ru.projectx.contracts.service.payment.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class OrderEventDocumentsTest {

    private static final UUID DOCUMENT = UUID.fromString("0196f0a2-0000-7000-8000-000000000011");
    private static final String NO_DOCUMENT = "в событии нет документа продукта Договоров";
    private static final String NOT_GUID = "идентификатор документа в событии не в формате GUID";

    private final JsonMapper json = JsonMapper.builder().build();

    private JsonNode event(String details) {
        return json.reader(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                .readTree("{\"orderDetails\": " + details + "}");
    }

    @Test
    void documentOfOurProduct_isOursWithItsIdentifierAndPremium() {
        JsonNode event = event("""
                [{"product": "rvl", "documentId": "0196f0a2-0000-7000-8000-000000000011", "documentPremium": 34000.00}]
                """);

        assertThat(OrderEventDocuments.of(event, "rvl"))
                .isEqualTo(new EventDocument.Ours(DOCUMENT, Optional.of(new BigDecimal("34000.00"))));
        assertThat(OrderEventDocuments.ourDocumentId(event, "rvl")).contains(DOCUMENT);
    }

    @Test
    void documentOfOurProduct_isFoundAmongDocumentsOfOtherProducts() {
        JsonNode event = event("""
                [{"product": "dmsfl", "documentId": "0196f0a2-0000-7000-8000-0000000000ff", "documentPremium": 1},
                 {"product": "rvl", "documentId": "0196f0a2-0000-7000-8000-000000000011", "documentPremium": 34000}]
                """);

        assertThat(OrderEventDocuments.of(event, "rvl"))
                .isEqualTo(new EventDocument.Ours(DOCUMENT, Optional.of(new BigDecimal("34000"))));
        assertThat(OrderEventDocuments.ourDocumentId(event, "rvl")).contains(DOCUMENT);
    }

    @Test
    void premiumThatIsNotANumber_isUnknown() {
        JsonNode event = event("""
                [{"product": "rvl", "documentId": "0196f0a2-0000-7000-8000-000000000011", "documentPremium": "34000.00"}]
                """);

        assertThat(OrderEventDocuments.of(event, "rvl")).isEqualTo(new EventDocument.Ours(DOCUMENT, Optional.empty()));
    }

    @Test
    void missingPremium_isUnknown() {
        JsonNode event = event("[{\"product\": \"rvl\", \"documentId\": \"0196f0a2-0000-7000-8000-000000000011\"}]");

        assertThat(OrderEventDocuments.of(event, "rvl")).isEqualTo(new EventDocument.Ours(DOCUMENT, Optional.empty()));
    }

    @Test
    void eventWithOnlyDocumentsOfOtherProducts_isForeign() {
        JsonNode event = event("[{\"product\": \"dmsfl\", \"documentId\": \"0196f0a2-0000-7000-8000-000000000011\"}]");

        assertThat(OrderEventDocuments.of(event, "rvl")).isEqualTo(new EventDocument.Foreign(NO_DOCUMENT));
        assertThat(OrderEventDocuments.ourDocumentId(event, "rvl")).isEmpty();
    }

    @Test
    void eventWithoutDocuments_isForeign() {
        JsonNode event = json.readTree("{\"eventType\": \"paid\"}");

        assertThat(OrderEventDocuments.of(event, "rvl")).isEqualTo(new EventDocument.Foreign(NO_DOCUMENT));
        assertThat(OrderEventDocuments.ourDocumentId(event, "rvl")).isEmpty();
    }

    @Test
    void documentWithoutProduct_isNotOurs() {
        JsonNode event = event("[{\"documentId\": \"0196f0a2-0000-7000-8000-000000000011\"}]");

        assertThat(OrderEventDocuments.of(event, "rvl")).isEqualTo(new EventDocument.Foreign(NO_DOCUMENT));
    }

    @Test
    void ourDocumentWhoseIdentifierIsNotAGuid_isForeign() {
        JsonNode event = event("[{\"product\": \"rvl\", \"documentId\": \"11\", \"documentPremium\": 34000.00}]");

        assertThat(OrderEventDocuments.of(event, "rvl")).isEqualTo(new EventDocument.Foreign(NOT_GUID));
        assertThat(OrderEventDocuments.ourDocumentId(event, "rvl")).isEmpty();
    }

    @Test
    void ourDocumentWithoutIdentifier_isForeign() {
        JsonNode event = event("[{\"product\": \"rvl\", \"documentPremium\": 34000.00}]");

        assertThat(OrderEventDocuments.of(event, "rvl")).isEqualTo(new EventDocument.Foreign(NOT_GUID));
        assertThat(OrderEventDocuments.ourDocumentId(event, "rvl")).isEmpty();
    }
}
