package ru.projectx.contracts.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import ru.projectx.contracts.dto.DocumentState;
import ru.projectx.contracts.dto.PackageItem;
import ru.projectx.contracts.dto.PackageItem.PackagePayment;
import ru.projectx.contracts.dto.PackageItem.PaymentState;
import ru.projectx.contracts.dto.PackageResponse;
import ru.projectx.contracts.entity.issuance.DocumentKind;
import ru.projectx.contracts.service.packages.RequestPackageReads;
import ru.projectx.contracts.service.storage.AttachmentMetrics;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

class RequestPackagePaymentJsonTest {

    private static final UUID REQUEST = UUID.fromString("61a7982c-a0b4-4bbf-9527-60b40b752803");
    private static final UUID SPOUSE = UUID.fromString("9275471c-d2fe-484f-97a6-d6d7b1267b5e");
    private static final UUID CONTRACT = UUID.fromString("3f77b1c8-2a55-4c19-9e30-6b8d2f0a71c4");
    private static final String PATH = "/internal/v1/requests/{request}/package";
    private static final BigDecimal PREMIUM = new BigDecimal("34000.00");

    private final RequestPackageReads packages = mock(RequestPackageReads.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new RequestPackageController(packages))
            .setControllerAdvice(new RequestFormatAdvice(new AttachmentMetrics(new SimpleMeterRegistry())),
                    new DomainErrorAdvice(), new UnexpectedErrorAdvice())
            .setMessageConverters(new JacksonJsonHttpMessageConverter(JsonMapper.builder()
                    .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build()))
            .addFilters(new RequestIdFilter())
            .build();

    @Test
    void pendingPaymentOfAnInvoicedContract_carriesTheLinkAndTheDeadlineInSnakeCase() throws Exception {
        answered(DocumentState.INVOICE_ISSUED, new PackagePayment(PaymentState.PENDING, PREMIUM,
                "https://pay.example.test/pay/7c1e5a90", Instant.parse("2026-02-13T07:05:00Z")));

        mvc.perform(get(PATH, REQUEST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].state").value("INVOICE_ISSUED"))
                .andExpect(jsonPath("$.items[0].payment.status").value("PENDING"))
                .andExpect(jsonPath("$.items[0].payment.amount").value(34000.00))
                .andExpect(jsonPath("$.items[0].payment.payment_url").value("https://pay.example.test/pay/7c1e5a90"))
                .andExpect(jsonPath("$.items[0].payment.expires_at").value("2026-02-13T07:05:00Z"));
    }

    @Test
    void pendingPaymentWithoutLink_carriesTheDeadlineAndNoLinkField() throws Exception {
        answered(DocumentState.INVOICE_ISSUED,
                new PackagePayment(PaymentState.PENDING, PREMIUM, null, Instant.parse("2026-02-13T07:05:00Z")));

        mvc.perform(get(PATH, REQUEST))
                .andExpect(jsonPath("$.items[0].payment.status").value("PENDING"))
                .andExpect(jsonPath("$.items[0].payment.payment_url").doesNotExist())
                .andExpect(jsonPath("$.items[0].payment.expires_at").value("2026-02-13T07:05:00Z"));
    }

    @Test
    void paidPayment_carriesOnlyTheStatusAndTheAmount() throws Exception {
        answered(DocumentState.PAID, new PackagePayment(PaymentState.PAID, PREMIUM, null, null));

        mvc.perform(get(PATH, REQUEST))
                .andExpect(jsonPath("$.items[0].payment.status").value("PAID"))
                .andExpect(jsonPath("$.items[0].payment.amount").value(34000.00))
                .andExpect(jsonPath("$.items[0].payment.payment_url").doesNotExist())
                .andExpect(jsonPath("$.items[0].payment.expires_at").doesNotExist());
    }

    private void answered(DocumentState state, PackagePayment payment) {
        when(packages.packageOf(REQUEST)).thenReturn(new PackageResponse(REQUEST, "IN_PROGRESS", List.of(
                new PackageItem(SPOUSE, DocumentKind.KM_CONTRACT, CONTRACT, "DKM-2026-000001", 1, state, PREMIUM, null,
                        null, payment, List.of(), null, null))));
    }
}
