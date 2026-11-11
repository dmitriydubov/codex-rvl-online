package ru.projectx.contracts.service.payment.invoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumSet;
import org.junit.jupiter.api.Test;
import org.springframework.integration.leader.Context;
import ru.projectx.contracts.config.payment.PaymentOrderPassesProperties;
import ru.projectx.contracts.entity.payment.InvoiceState;
import ru.projectx.contracts.entity.payment.PaymentOrderState;
import ru.projectx.contracts.repository.payment.OrderInvoiceRepository;
import ru.projectx.contracts.service.leadership.BackgroundLeadership;
import ru.projectx.contracts.support.TestLeadership;

class InvoiceSummariesTest {

    private static final Instant NOW = Instant.parse("2026-02-10T09:05:00Z");
    private static final String WITHOUT_INVOICE = "contracts.km.contracts.without.invoice.overdue";
    private static final String WITHOUT_OUTCOME = "contracts.km.invoices.without.outcome.overdue";
    private static final PaymentOrderPassesProperties PASSES = new PaymentOrderPassesProperties(Duration.ofSeconds(10),
            Duration.ofMinutes(1), 7, Duration.ofMinutes(1), Duration.ofHours(5),
            new PaymentOrderPassesProperties.Invoices(Duration.ofSeconds(10), Duration.ofHours(72), Duration.ofHours(4),
                    Duration.ofMinutes(30)),
            new PaymentOrderPassesProperties.Reconciliation(Duration.ofMinutes(1), Duration.ofMinutes(15),
                    Duration.ofHours(2), 2, Duration.ofMinutes(1)));

    private final OrderInvoiceRepository invoices = mock();
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final BackgroundLeadership leadership = TestLeadership.leader();
    private final InvoiceSummaries summaries =
            new InvoiceSummaries(invoices, leadership, PASSES, Clock.fixed(NOW, ZoneOffset.UTC), registry);

    private double gauge(String name) {
        return registry.get(name).gauge().value();
    }

    private void overdue(long withoutInvoice, long withoutOutcome) {
        given(invoices.countCreatedBefore(PaymentOrderState.CREATED,
                EnumSet.of(InvoiceState.AWAITING_DOCUMENT, InvoiceState.DOCUMENT_UNCONFIRMED, InvoiceState.READY),
                Instant.parse("2026-02-10T08:35:00Z"))).willReturn(withoutInvoice);
        given(invoices.countDueBefore(PaymentOrderState.CREATED,
                EnumSet.of(InvoiceState.ISSUED, InvoiceState.PAYMENT_ERROR), Instant.parse("2026-02-10T07:05:00Z")))
                .willReturn(withoutOutcome);
    }

    @Test
    void beforeTheFirstRefresh_bothSummariesAreZero() {
        assertThat(gauge(WITHOUT_INVOICE)).isZero();
        assertThat(gauge(WITHOUT_OUTCOME)).isZero();
    }

    @Test
    void leader_showsContractsWithoutInvoiceLongerThanTheThreshold() {
        overdue(3, 0);

        summaries.refresh();

        assertThat(gauge(WITHOUT_INVOICE)).isEqualTo(3);
        assertThat(gauge(WITHOUT_OUTCOME)).isZero();
    }

    @Test
    void leader_showsInvoicesWithoutOutcomeAfterThePaymentDeadlineAndTheGrace() {
        overdue(0, 2);

        summaries.refresh();

        assertThat(gauge(WITHOUT_OUTCOME)).isEqualTo(2);
        assertThat(gauge(WITHOUT_INVOICE)).isZero();
    }

    @Test
    void instanceThatLostTheRole_resetsBothSummaries() {
        given(invoices.countCreatedBefore(any(), any(), any())).willReturn(3L);
        given(invoices.countDueBefore(any(), any(), any())).willReturn(2L);
        summaries.refresh();
        leadership.onRevoked(mock(Context.class));

        summaries.refresh();

        assertThat(gauge(WITHOUT_INVOICE)).isZero();
        assertThat(gauge(WITHOUT_OUTCOME)).isZero();
    }

    @Test
    void summaries_carryDescriptionsForTheOperator() {
        assertThat(registry.get(WITHOUT_INVOICE).gauge().getId().getDescription())
                .isEqualTo("договоры КМ с заказом на оплату, счёт по которым не выставлен дольше порога");
        assertThat(registry.get(WITHOUT_OUTCOME).gauge().getId().getDescription())
                .isEqualTo("счета договоров КМ, по которым после срока оплаты нет ни оплаты, ни просрочки");
    }
}
