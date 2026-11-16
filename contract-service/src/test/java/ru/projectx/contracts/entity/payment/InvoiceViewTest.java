package ru.projectx.contracts.entity.payment;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import ru.projectx.contracts.entity.issuance.CancelReason;
import ru.projectx.contracts.entity.issuance.Cancellation;

class InvoiceViewTest {

    private static final UUID ID = UUID.fromString("0196f0a2-0000-7000-8000-000000000011");

    private static InvoiceView view(InvoiceState state, Optional<Cancellation> deferred) {
        return new InvoiceView(ID, ID, state, 0, Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), deferred);
    }

    @Test
    void issuedInvoice_awaitsPayment() {
        assertThat(view(InvoiceState.ISSUED, Optional.empty()).awaitsPayment()).isTrue();
    }

    @Test
    void paidInvoice_doesNotAwaitPayment() {
        assertThat(view(InvoiceState.PAID, Optional.empty()).awaitsPayment()).isFalse();
    }

    @Test
    void invoiceWithDeferredCancellation_saysSo() {
        Cancellation cancellation =
                new Cancellation(CancelReason.REQUEST_CANCELLED, null, Instant.parse("2026-02-12T11:30:00Z"));

        assertThat(view(InvoiceState.ISSUED, Optional.of(cancellation)).cancellationDeferred()).isTrue();
    }

    @Test
    void invoiceWithoutDeferredCancellation_saysSo() {
        assertThat(view(InvoiceState.ISSUED, Optional.empty()).cancellationDeferred()).isFalse();
    }

    private static InvoiceView linked(InvoiceState state) {
        return new InvoiceView(ID, ID, state, 0, Optional.empty(),
                Optional.of(new InvoiceLink("inv-0001", "https://pay.example.test/inv-0001")), Optional.empty(),
                Optional.empty(), Optional.empty());
    }

    @ParameterizedTest
    @EnumSource(names = {"CHECKOUT", "ISSUED", "PAYMENT_ERROR"})
    void invoiceThatAwaitsPaymentWithoutLink_lacksThePaymentLink(InvoiceState state) {
        assertThat(view(state, Optional.empty()).lacksPaymentLink()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(names = {"ISSUED", "PAYMENT_ERROR"})
    void invoiceThatHasItsLink_doesNotLackIt(InvoiceState state) {
        assertThat(linked(state).lacksPaymentLink()).isFalse();
    }

    @ParameterizedTest
    @EnumSource(mode = EnumSource.Mode.EXCLUDE, names = {"CHECKOUT", "ISSUED", "PAYMENT_ERROR"})
    void invoiceThatDoesNotAwaitPayment_lacksNoPaymentLink(InvoiceState state) {
        assertThat(view(state, Optional.empty()).lacksPaymentLink()).isFalse();
    }

    private static InvoiceView startedAt(Instant started) {
        return new InvoiceView(ID, ID, InvoiceState.CHECKOUT, 0,
                Optional.of(new InvoiceTerm(started, started.plusSeconds(259_200))), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty());
    }

    @Test
    void checkoutStartedBeforeOrExactlyAtTheMoment_wasStartedByIt() {
        Instant moment = Instant.parse("2026-02-10T07:20:00Z");

        assertThat(startedAt(moment.minusMillis(1)).checkoutStartedBy(moment)).isTrue();
        assertThat(startedAt(moment).checkoutStartedBy(moment)).isTrue();
    }

    @Test
    void checkoutStartedAfterTheMoment_wasNotStartedByIt() {
        Instant moment = Instant.parse("2026-02-10T07:20:00Z");

        assertThat(startedAt(moment.plusMillis(1)).checkoutStartedBy(moment)).isFalse();
    }

    @Test
    void invoiceWhoseCheckoutNeverStarted_wasNotStartedByAnyMoment() {
        assertThat(view(InvoiceState.READY, Optional.empty()).checkoutStartedBy(Instant.MAX)).isFalse();
    }
}
