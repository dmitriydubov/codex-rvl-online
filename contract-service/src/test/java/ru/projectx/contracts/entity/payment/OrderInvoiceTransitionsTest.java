package ru.projectx.contracts.entity.payment;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.util.ReflectionTestUtils;
import ru.projectx.contracts.entity.issuance.CancelReason;
import ru.projectx.contracts.entity.issuance.Cancellation;

class OrderInvoiceTransitionsTest {

    private static final UUID ORDER = UUID.fromString("0196f0a2-0000-7000-8000-000000000011");
    private static final UUID CONTRACT = UUID.fromString("0196f0a2-0000-7000-8000-000000000001");
    private static final Instant OPENED = Instant.parse("2026-02-10T07:05:00Z");
    private static final Instant LATER = Instant.parse("2026-02-10T07:06:00Z");
    private static final Cancellation REFUSED =
            new Cancellation(CancelReason.INSURED_REFUSED, "стенд", Instant.parse("2026-02-12T11:30:00Z"));
    private static final Cancellation OTHER =
            new Cancellation(CancelReason.OTHER, null, Instant.parse("2026-02-12T12:00:00Z"));

    private static OrderInvoice invoiceIn(InvoiceState state) {
        OrderInvoice invoice = OrderInvoice.open(ORDER, CONTRACT, OPENED);
        ReflectionTestUtils.setField(invoice, "state", state);
        return invoice;
    }

    private static Object updatedAt(OrderInvoice invoice) {
        return ReflectionTestUtils.getField(invoice, "updatedAt");
    }

    private static void assertPassed(InvoiceTransition transition, InvoiceState from, InvoiceState to) {
        OrderInvoice invoice = invoiceIn(from);

        assertThat(invoice.pass(transition, LATER)).isTrue();

        assertThat(invoice.view().state()).isEqualTo(to);
        assertThat(updatedAt(invoice)).isEqualTo(LATER);
    }

    private static void assertRefused(InvoiceTransition transition, InvoiceState from) {
        OrderInvoice invoice = invoiceIn(from);

        assertThat(invoice.pass(transition, LATER)).isFalse();

        assertThat(invoice.view().state()).isEqualTo(from);
        assertThat(updatedAt(invoice)).isEqualTo(OPENED);
    }

    @ParameterizedTest
    @EnumSource(names = {"AWAITING_DOCUMENT", "DOCUMENT_UNCONFIRMED"})
    void documentConfirmed_makesTheInvoiceAwaitingConfirmationReady(InvoiceState state) {
        assertPassed(InvoiceTransition.DOCUMENT_CONFIRMED, state, InvoiceState.READY);
    }

    @ParameterizedTest
    @EnumSource(mode = EnumSource.Mode.EXCLUDE, names = {"AWAITING_DOCUMENT", "DOCUMENT_UNCONFIRMED"})
    void documentConfirmed_isRefusedOnceTheDocumentWasConfirmed(InvoiceState state) {
        assertRefused(InvoiceTransition.DOCUMENT_CONFIRMED, state);
    }

    @Test
    void remindersExhausted_marksTheAwaitedDocumentUnconfirmed() {
        assertPassed(InvoiceTransition.REMINDERS_EXHAUSTED, InvoiceState.AWAITING_DOCUMENT,
                InvoiceState.DOCUMENT_UNCONFIRMED);
    }

    @ParameterizedTest
    @EnumSource(mode = EnumSource.Mode.EXCLUDE, names = "AWAITING_DOCUMENT")
    void remindersExhausted_isRefusedInAnyOtherState(InvoiceState state) {
        assertRefused(InvoiceTransition.REMINDERS_EXHAUSTED, state);
    }

    @Test
    void replyLost_issuesTheInvoiceInCheckout() {
        assertPassed(InvoiceTransition.REPLY_LOST, InvoiceState.CHECKOUT, InvoiceState.ISSUED);
    }

    @ParameterizedTest
    @EnumSource(mode = EnumSource.Mode.EXCLUDE, names = "CHECKOUT")
    void replyLost_isRefusedOutsideCheckout(InvoiceState state) {
        assertRefused(InvoiceTransition.REPLY_LOST, state);
    }

    @Test
    void paymentFailed_marksTheIssuedInvoice() {
        assertPassed(InvoiceTransition.PAYMENT_FAILED, InvoiceState.ISSUED, InvoiceState.PAYMENT_ERROR);
    }

    @ParameterizedTest
    @EnumSource(mode = EnumSource.Mode.EXCLUDE, names = "ISSUED")
    void paymentFailed_isRefusedForInvoiceThatIsNotIssued(InvoiceState state) {
        assertRefused(InvoiceTransition.PAYMENT_FAILED, state);
    }

    @ParameterizedTest
    @EnumSource(names = {"CHECKOUT", "ISSUED", "PAYMENT_ERROR"})
    void paymentOverdue_closesTheInvoiceAwaitingPayment(InvoiceState state) {
        assertPassed(InvoiceTransition.PAYMENT_OVERDUE, state, InvoiceState.OVERDUE);
    }

    @ParameterizedTest
    @EnumSource(mode = EnumSource.Mode.EXCLUDE, names = {"CHECKOUT", "ISSUED", "PAYMENT_ERROR"})
    void paymentOverdue_isRefusedForInvoiceThatDoesNotAwaitPayment(InvoiceState state) {
        assertRefused(InvoiceTransition.PAYMENT_OVERDUE, state);
    }

    @Test
    void deferCancellation_keepsTheFirstCancellation() {
        OrderInvoice invoice = invoiceIn(InvoiceState.ISSUED);

        invoice.deferCancellation(REFUSED, LATER);
        invoice.deferCancellation(OTHER, LATER.plusSeconds(60));

        assertThat(invoice.view().deferredCancellation()).contains(REFUSED);
        assertThat(invoice.view().cancellationDeferred()).isTrue();
        assertThat(updatedAt(invoice)).isEqualTo(LATER);
    }

    @Test
    void liftDeferredCancellation_returnsItOnceAndForgetsIt() {
        OrderInvoice invoice = invoiceIn(InvoiceState.ISSUED);
        invoice.deferCancellation(REFUSED, OPENED);

        assertThat(invoice.liftDeferredCancellation(LATER)).contains(REFUSED);

        assertThat(invoice.view().deferredCancellation()).isEmpty();
        assertThat(updatedAt(invoice)).isEqualTo(LATER);
        assertThat(invoice.liftDeferredCancellation(LATER)).isEmpty();
    }

    @Test
    void liftDeferredCancellation_withoutOne_changesNothing() {
        OrderInvoice invoice = invoiceIn(InvoiceState.ISSUED);

        assertThat(invoice.liftDeferredCancellation(LATER)).isEqualTo(Optional.empty());

        assertThat(updatedAt(invoice)).isEqualTo(OPENED);
    }

    @Test
    void transitions_nameTheirSourceAndTargetStates() {
        assertThat(InvoiceTransition.DOCUMENT_CONFIRMED.from())
                .containsExactlyInAnyOrder(InvoiceState.AWAITING_DOCUMENT, InvoiceState.DOCUMENT_UNCONFIRMED);
        assertThat(InvoiceTransition.DOCUMENT_CONFIRMED.to()).isEqualTo(InvoiceState.READY);
        assertThat(InvoiceTransition.REMINDERS_EXHAUSTED.to()).isEqualTo(InvoiceState.DOCUMENT_UNCONFIRMED);
        assertThat(InvoiceTransition.REPLY_LOST.from()).containsExactly(InvoiceState.CHECKOUT);
        assertThat(InvoiceTransition.REPLY_LOST.to()).isEqualTo(InvoiceState.ISSUED);
        assertThat(InvoiceTransition.PAYMENT_FAILED.to()).isEqualTo(InvoiceState.PAYMENT_ERROR);
        assertThat(InvoiceTransition.PAYMENT_OVERDUE.to()).isEqualTo(InvoiceState.OVERDUE);
    }
}
