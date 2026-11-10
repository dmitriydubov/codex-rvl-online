package ru.projectx.contracts.service.integration.ordering;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ru.projectx.contracts.entity.payment.InvoiceLink;
import ru.projectx.contracts.entity.payment.OrderDocument;
import ru.projectx.contracts.entity.payment.PayerEmail;
import ru.projectx.contracts.exception.OrderingOperation;
import ru.projectx.contracts.exception.OrderingOrderFailureException;
import ru.projectx.contracts.exception.OrderingRejectedException;
import ru.projectx.contracts.exception.OrderingUnavailableException;
import ru.projectx.contracts.service.integration.ordering.OrderingTestServer.Received;
import ru.projectx.contracts.service.payment.CheckoutRequest;
import ru.projectx.contracts.service.payment.NewOrder;
import ru.projectx.contracts.service.payment.OrderingMetrics;
import tools.jackson.databind.json.JsonMapper;

class OrderingHttpCheckoutTest {

    private static final UUID CORRELATION = UUID.fromString("0196f0a2-0000-7000-8000-0000000000aa");
    private static final String ORDER_ID = "0196f0a2-5b1c-7d3e-9f40-1a2b3c4d5e01";
    private static final String INVOICE_ID = "7c1e5a90-3b42-4d6f-8a17-9e0f2b6c4d31";
    private static final String PAYMENT_URL = "https://pay.example.test/pay/7c1e5a90";
    private static final CheckoutRequest CHECKOUT = new CheckoutRequest(ORDER_ID,
            new NewOrder(UUID.fromString("ae8312c3-350b-4d0b-806c-5e2460e74dbc"),
                    UUID.fromString("0196f0a2-0000-7000-8000-000000000011"),
                    new OrderDocument("DKM-2026-000001", new BigDecimal("34000.00"),
                            Instant.parse("2026-02-10T07:04:58Z"), Instant.parse("2026-04-09T20:59:59Z"))),
            Instant.parse("2026-02-13T07:05:00Z"), new PayerEmail("insured@example.test"));

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private OrderingTestServer server;
    private OrderingHttpClient client;

    @BeforeEach
    void startServer() throws IOException {
        server = new OrderingTestServer();
        client = OrderingHttpClients.create(OrderingSettings.at(server.orderingUrl(), server.tokenUri()),
                JsonMapper.builder().build(), new OrderingMetrics(registry),
                new MovableClock(Instant.parse("2026-02-10T07:05:00Z")));
        server.token(OrderingTestServer.accessToken("tok-1", 600));
    }

    @AfterEach
    void stopServer() {
        server.close();
    }

    @Test
    void checkout_postsTheOrderWithItsPaymentToTheOrderAndReturnsTheInvoiceWithItsLink() {
        server.ordering(OrderingTestServer.json(200, invoice(INVOICE_ID, PAYMENT_URL)));

        InvoiceLink link = client.session(CORRELATION).checkout(CHECKOUT);

        assertThat(link).isEqualTo(new InvoiceLink(INVOICE_ID, PAYMENT_URL));
        Received checkout = server.orderingRequests().getFirst();
        assertThat(checkout.method()).isEqualTo("POST");
        assertThat(checkout.path()).isEqualTo("/ordering/orders/" + ORDER_ID + "/checkout");
        assertThat(checkout.header("Authorization")).isEqualTo("Bearer tok-1");
        assertThat(checkout.header("correlationId")).isEqualTo(CORRELATION.toString());
        assertThat(checkout.body()).contains("\"documentNumber\":\"DKM-2026-000001\"",
                "\"payment\":{\"paymentType\":\"internetAcquiring\",\"paymentTtl\":\"2026-02-13T10:05:00+03:00\","
                        + "\"email\":\"insured@example.test\"}");
        assertThat(server.orderingRequests()).hasSize(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{\"status\": \"success\", \"invoiceId\": \"7c1e5a90-3b42-4d6f-8a17-9e0f2b6c4d31\"}",
        "{\"status\": \"success\", \"paymentUrl\": \"https://pay.example.test/pay/7c1e5a90\"}",
        "{\"status\": \"success\", \"invoiceId\": \"7c1e5a90\", \"paymentUrl\": \"pay.example.test/pay/7c1e5a90\"}",
        "{\"status\": \"success\", \"invoiceId\": \"\", \"paymentUrl\": \"https://pay.example.test/pay/7c1e5a90\"}"})
    void successWithoutInvoiceOrUsableLink_isAFailureOfThisOrderCountedForCheckoutWithoutTheLinkInItsText(String body) {
        server.ordering(OrderingTestServer.json(200, body));

        assertThatThrownBy(() -> client.session(CORRELATION).checkout(CHECKOUT))
                .isInstanceOfSatisfying(OrderingOrderFailureException.class, failure -> {
                    assertThat(failure.operation()).isEqualTo(OrderingOperation.CHECKOUT);
                    assertThat(failure.serviceWide()).isFalse();
                })
                .message().doesNotContain("pay.example.test");
        assertThat(failures()).isEqualTo(1);
    }

    @Test
    void checkoutRefusedOnItsMerits_isARejectionWithTheCodeCountedForCheckout() {
        server.ordering(OrderingTestServer.json(200,
                "{\"status\": \"error\", \"errors\": [{\"code\": \"CheckoutValidatorService.valid\"}]}"));

        assertThatThrownBy(() -> client.session(CORRELATION).checkout(CHECKOUT))
                .isInstanceOfSatisfying(OrderingRejectedException.class, rejected -> {
                    assertThat(rejected.operation()).isEqualTo(OrderingOperation.CHECKOUT);
                    assertThat(rejected.code()).isEqualTo("CheckoutValidatorService.valid");
                });
        assertThat(failures()).isEqualTo(1);
    }

    @Test
    void unavailableOrderingAtCheckout_isATemporaryFailureOfTheWholeService() {
        server.ordering(OrderingTestServer.json(503, ""));

        assertThatThrownBy(() -> client.session(CORRELATION).checkout(CHECKOUT))
                .isInstanceOfSatisfying(OrderingUnavailableException.class, unavailable -> {
                    assertThat(unavailable.operation()).isEqualTo(OrderingOperation.CHECKOUT);
                    assertThat(unavailable.serviceWide()).isTrue();
                });
        assertThat(failures()).isEqualTo(1);
    }

    private static String invoice(String invoiceId, String paymentUrl) {
        return "{\"status\": \"success\", \"invoiceId\": \"" + invoiceId + "\", \"paymentUrl\": \"" + paymentUrl + "\"}";
    }

    private double failures() {
        return registry.get("contracts.ordering.failures").tag("operation", "checkout").counter().count();
    }
}
