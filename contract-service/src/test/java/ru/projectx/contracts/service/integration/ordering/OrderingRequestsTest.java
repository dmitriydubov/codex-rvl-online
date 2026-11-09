package ru.projectx.contracts.service.integration.ordering;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import ru.projectx.contracts.entity.payment.OrderDocument;
import ru.projectx.contracts.service.payment.NewOrder;
import tools.jackson.databind.json.JsonMapper;

class OrderingRequestsTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final UUID CLIENT = UUID.fromString("ae8312c3-350b-4d0b-806c-5e2460e74dbc");
    private static final UUID DOCUMENT = UUID.fromString("0196f0a2-0000-7000-8000-000000000011");

    private final OrderingRequests requests =
            new OrderingRequests(JSON, OrderingSettings.at("http://ordering.test", "http://keycloak.test/token"));

    @Test
    void create_carriesOneContractDocumentAndTheChannelOnly() {
        NewOrder order = new NewOrder(CLIENT, DOCUMENT, new OrderDocument("DKM-2026-000001", new BigDecimal("1234.5"),
                Instant.parse("2026-02-10T07:04:30.250Z"), Instant.parse("2026-04-09T20:59:59Z")));

        assertThat(JSON.writeValueAsString(requests.create(order))).isEqualTo("""
                {"clientId":"ae8312c3-350b-4d0b-806c-5e2460e74dbc","orderDetails":[{"product":"rvl",\
                "documentType":"contract","documentId":"0196f0a2-0000-7000-8000-000000000011",\
                "documentNumber":"DKM-2026-000001","documentPremium":1234.50,\
                "documentDate":"2026-02-10T10:04:30+03:00","ttl":"2026-04-09T23:59:59+03:00"}],\
                "eventDetails":{"channel":"rvl-lk"}}""");
    }

    @Test
    void find_selectsTheDocumentOfOurProduct() {
        assertThat(JSON.writeValueAsString(requests.find(DOCUMENT.toString()))).isEqualTo(
                "{\"orderDetails\":{\"product\":\"rvl\",\"documentId\":\"0196f0a2-0000-7000-8000-000000000011\"}}");
    }

    @Test
    void cancel_carriesTheChannelAndTheReasonFromSettings() {
        assertThat(JSON.writeValueAsString(requests.cancel()))
                .isEqualTo("{\"channel\":\"rvl-lk\",\"cancelReason\":\"notActual\"}");
    }

    @Test
    void product_isTheConfiguredOne() {
        assertThat(requests.product()).isEqualTo("rvl");
    }
}
