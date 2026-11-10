package ru.projectx.contracts.service.payment.invoice;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import ru.projectx.contracts.entity.payment.InvoiceState;
import ru.projectx.contracts.entity.payment.OrderInvoice;
import ru.projectx.contracts.service.payment.Invoices;
import ru.projectx.contracts.support.OrderingTestProperties;

class CheckoutMarksTest {

    private static final Duration THRESHOLD = OrderingTestProperties.passes().reconciliation().threshold();

    private final CheckoutMarks marks = new CheckoutMarks(OrderingTestProperties.passes());

    @Test
    void markAsOldAsTheThreshold_isAbandoned() {
        OrderInvoice invoice = Invoices.inCheckout();
        Instant started = invoice.view().term().orElseThrow().startedAt();

        assertThat(marks.abandoned(invoice.view(), started.plus(THRESHOLD))).isTrue();
    }

    @Test
    void markYoungerThanTheThreshold_isStillInFlight() {
        OrderInvoice invoice = Invoices.inCheckout();
        Instant started = invoice.view().term().orElseThrow().startedAt();

        assertThat(marks.abandoned(invoice.view(), started.plus(THRESHOLD).minusMillis(1))).isFalse();
    }

    @ParameterizedTest
    @EnumSource(mode = EnumSource.Mode.EXCLUDE, names = "CHECKOUT")
    void invoiceOutsideCheckout_isNeverAbandonedHoweverOldItsMarkIs(InvoiceState state) {
        OrderInvoice invoice = Invoices.in(state);

        assertThat(marks.abandoned(invoice.view(), Instant.parse("2030-01-01T00:00:00Z"))).isFalse();
    }
}
