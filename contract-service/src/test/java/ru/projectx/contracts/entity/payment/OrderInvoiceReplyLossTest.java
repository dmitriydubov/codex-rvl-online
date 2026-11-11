package ru.projectx.contracts.entity.payment;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.util.ReflectionTestUtils;

class OrderInvoiceReplyLossTest {

    private static final UUID ORDER = UUID.fromString("0196f0a2-0000-7000-8000-000000000011");
    private static final UUID CONTRACT = UUID.fromString("0196f0a2-0000-7000-8000-000000000001");
    private static final Instant OPENED = Instant.parse("2026-02-10T07:05:00Z");
    private static final Instant LATER = Instant.parse("2026-02-10T07:06:00Z");
    private static final Instant LATEST = Instant.parse("2026-02-10T07:30:00Z");
    private static final InvoiceLink LINK = new InvoiceLink("inv-0001", "https://pay.example.test/inv-0001");

    private static OrderInvoice invoiceIn(InvoiceState state) {
        OrderInvoice invoice = OrderInvoice.open(ORDER, CONTRACT, OPENED);
        ReflectionTestUtils.setField(invoice, "state", state);
        return invoice;
    }

    private static Object updatedAt(OrderInvoice invoice) {
        return ReflectionTestUtils.getField(invoice, "updatedAt");
    }

    private static Object reported(OrderInvoice invoice) {
        return ReflectionTestUtils.getField(invoice, "replyLostReported");
    }

    @Test
    void paymentAwaited_issuesTheInvoiceInCheckoutWithoutMarkingTheLostReply() {
        OrderInvoice invoice = invoiceIn(InvoiceState.CHECKOUT);

        assertThat(invoice.pass(InvoiceTransition.PAYMENT_AWAITED, LATER)).isTrue();

        assertThat(invoice.view().state()).isEqualTo(InvoiceState.ISSUED);
        assertThat(invoice.view().link()).isEmpty();
        assertThat(reported(invoice)).isEqualTo(false);
        assertThat(updatedAt(invoice)).isEqualTo(LATER);
    }

    @ParameterizedTest
    @EnumSource(mode = EnumSource.Mode.EXCLUDE, names = "CHECKOUT")
    void paymentAwaited_isRefusedOutsideCheckout(InvoiceState state) {
        OrderInvoice invoice = invoiceIn(state);

        assertThat(invoice.pass(InvoiceTransition.PAYMENT_AWAITED, LATER)).isFalse();

        assertThat(invoice.view().state()).isEqualTo(state);
        assertThat(updatedAt(invoice)).isEqualTo(OPENED);
    }

    @Test
    void lostReplyOfInvoiceThatTheEventIssued_isReportedExactlyOnce() {
        OrderInvoice invoice = invoiceIn(InvoiceState.CHECKOUT);
        invoice.pass(InvoiceTransition.PAYMENT_AWAITED, OPENED);

        assertThat(invoice.pass(InvoiceTransition.REPLY_LOSS_REPORTED, LATER)).isTrue();
        assertThat(invoice.pass(InvoiceTransition.REPLY_LOSS_REPORTED, LATEST)).isFalse();

        assertThat(invoice.view().state()).isEqualTo(InvoiceState.ISSUED);
        assertThat(reported(invoice)).isEqualTo(true);
        assertThat(updatedAt(invoice)).isEqualTo(LATER);
    }

    @Test
    void replyLost_marksTheReportSoThatItIsNotReportedAgain() {
        OrderInvoice invoice = invoiceIn(InvoiceState.CHECKOUT);

        assertThat(invoice.pass(InvoiceTransition.REPLY_LOST, LATER)).isTrue();
        assertThat(invoice.pass(InvoiceTransition.REPLY_LOSS_REPORTED, LATEST)).isFalse();

        assertThat(invoice.view().state()).isEqualTo(InvoiceState.ISSUED);
        assertThat(reported(invoice)).isEqualTo(true);
        assertThat(updatedAt(invoice)).isEqualTo(LATER);
    }

    @Test
    void lostReplyOfInvoiceThatHasItsLink_isNotReported() {
        OrderInvoice invoice = invoiceIn(InvoiceState.CHECKOUT);
        invoice.issue(LINK, OPENED);

        assertThat(invoice.pass(InvoiceTransition.REPLY_LOSS_REPORTED, LATER)).isFalse();

        assertThat(reported(invoice)).isEqualTo(false);
        assertThat(updatedAt(invoice)).isEqualTo(OPENED);
    }

    @ParameterizedTest
    @EnumSource(mode = EnumSource.Mode.EXCLUDE, names = "ISSUED")
    void lostReply_isReportedOnlyForIssuedInvoice(InvoiceState state) {
        OrderInvoice invoice = invoiceIn(state);

        assertThat(invoice.pass(InvoiceTransition.REPLY_LOSS_REPORTED, LATER)).isFalse();

        assertThat(invoice.view().state()).isEqualTo(state);
        assertThat(reported(invoice)).isEqualTo(false);
        assertThat(updatedAt(invoice)).isEqualTo(OPENED);
    }

    private static OrderInvoice failedWithoutLink() {
        OrderInvoice invoice = invoiceIn(InvoiceState.CHECKOUT);
        invoice.pass(InvoiceTransition.PAYMENT_AWAITED, OPENED);
        invoice.pass(InvoiceTransition.PAYMENT_FAILED, OPENED);
        return invoice;
    }

    @Test
    void lostReplyOfInvoiceWhosePaymentFailed_isReportedExactlyOnceAndTheInvoiceKeepsItsState() {
        OrderInvoice invoice = failedWithoutLink();

        assertThat(invoice.pass(InvoiceTransition.REPLY_LOSS_REPORTED_AFTER_FAILURE, LATER)).isTrue();
        assertThat(invoice.pass(InvoiceTransition.REPLY_LOSS_REPORTED_AFTER_FAILURE, LATEST)).isFalse();

        assertThat(invoice.view().state()).isEqualTo(InvoiceState.PAYMENT_ERROR);
        assertThat(invoice.view().link()).isEmpty();
        assertThat(reported(invoice)).isEqualTo(true);
        assertThat(updatedAt(invoice)).isEqualTo(LATER);
    }

    @Test
    void lostReplyReportedBeforeThePaymentFailed_isNotReportedAgainAfterIt() {
        OrderInvoice invoice = invoiceIn(InvoiceState.CHECKOUT);
        invoice.pass(InvoiceTransition.PAYMENT_AWAITED, OPENED);
        invoice.pass(InvoiceTransition.REPLY_LOSS_REPORTED, OPENED);
        invoice.pass(InvoiceTransition.PAYMENT_FAILED, OPENED);

        assertThat(invoice.pass(InvoiceTransition.REPLY_LOSS_REPORTED_AFTER_FAILURE, LATER)).isFalse();

        assertThat(invoice.view().state()).isEqualTo(InvoiceState.PAYMENT_ERROR);
        assertThat(updatedAt(invoice)).isEqualTo(OPENED);
    }

    @Test
    void lostReplyOfFailedPaymentWhoseInvoiceHasItsLink_isNotReported() {
        OrderInvoice invoice = invoiceIn(InvoiceState.CHECKOUT);
        invoice.issue(LINK, OPENED);
        invoice.pass(InvoiceTransition.PAYMENT_FAILED, OPENED);

        assertThat(invoice.pass(InvoiceTransition.REPLY_LOSS_REPORTED_AFTER_FAILURE, LATER)).isFalse();

        assertThat(reported(invoice)).isEqualTo(false);
        assertThat(updatedAt(invoice)).isEqualTo(OPENED);
    }

    @ParameterizedTest
    @EnumSource(mode = EnumSource.Mode.EXCLUDE, names = "PAYMENT_ERROR")
    void lostReplyAfterFailure_isReportedOnlyForInvoiceInPaymentError(InvoiceState state) {
        OrderInvoice invoice = invoiceIn(state);

        assertThat(invoice.pass(InvoiceTransition.REPLY_LOSS_REPORTED_AFTER_FAILURE, LATER)).isFalse();

        assertThat(invoice.view().state()).isEqualTo(state);
        assertThat(reported(invoice)).isEqualTo(false);
        assertThat(updatedAt(invoice)).isEqualTo(OPENED);
    }

    @Test
    void lateLinkAfterTheReportedLoss_isAcceptedAndClosesTheReportForGood() {
        OrderInvoice invoice = invoiceIn(InvoiceState.CHECKOUT);
        invoice.pass(InvoiceTransition.REPLY_LOST, OPENED);

        assertThat(invoice.issue(LINK, LATER)).isTrue();

        assertThat(invoice.view().link()).contains(LINK);
        assertThat(invoice.view().state()).isEqualTo(InvoiceState.ISSUED);
        assertThat(invoice.pass(InvoiceTransition.REPLY_LOSS_REPORTED, LATEST)).isFalse();
    }

    @Test
    void transitionsThatReportNothing_passForInvoiceWithLinkAndAfterTheReport() {
        OrderInvoice linked = invoiceIn(InvoiceState.CHECKOUT);
        linked.issue(LINK, OPENED);
        OrderInvoice reportedLost = invoiceIn(InvoiceState.CHECKOUT);
        reportedLost.pass(InvoiceTransition.REPLY_LOST, OPENED);

        assertThat(linked.pass(InvoiceTransition.PAYMENT_FAILED, LATER)).isTrue();
        assertThat(reportedLost.pass(InvoiceTransition.PAYMENT_OVERDUE, LATER)).isTrue();

        assertThat(linked.view().state()).isEqualTo(InvoiceState.PAYMENT_ERROR);
        assertThat(reportedLost.view().state()).isEqualTo(InvoiceState.OVERDUE);
    }

    @ParameterizedTest
    @EnumSource(names = {"REPLY_LOST", "REPLY_LOSS_REPORTED", "REPLY_LOSS_REPORTED_AFTER_FAILURE"})
    void transitionsOfTheLostReply_reportIt(InvoiceTransition transition) {
        assertThat(transition.reportsLostReply()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(mode = EnumSource.Mode.EXCLUDE,
            names = {"REPLY_LOST", "REPLY_LOSS_REPORTED", "REPLY_LOSS_REPORTED_AFTER_FAILURE"})
    void otherTransitions_reportNoLostReply(InvoiceTransition transition) {
        assertThat(transition.reportsLostReply()).isFalse();
    }

    @ParameterizedTest
    @EnumSource(mode = EnumSource.Mode.EXCLUDE, names = "DOCUMENT_RESENT")
    void onlyTheResentDocument_startsTheRemindersAnew(InvoiceTransition transition) {
        assertThat(transition.remindersAfter(2)).isEqualTo(2);
        assertThat(InvoiceTransition.DOCUMENT_RESENT.remindersAfter(2)).isZero();
    }

    @Test
    void newTransitions_nameTheirSourceAndTargetStates() {
        assertThat(InvoiceTransition.PAYMENT_AWAITED.from()).containsExactly(InvoiceState.CHECKOUT);
        assertThat(InvoiceTransition.PAYMENT_AWAITED.to()).isEqualTo(InvoiceState.ISSUED);
        assertThat(InvoiceTransition.REPLY_LOSS_REPORTED.from()).containsExactly(InvoiceState.ISSUED);
        assertThat(InvoiceTransition.REPLY_LOSS_REPORTED.to()).isEqualTo(InvoiceState.ISSUED);
        assertThat(InvoiceTransition.REPLY_LOSS_REPORTED_AFTER_FAILURE.from())
                .containsExactly(InvoiceState.PAYMENT_ERROR);
        assertThat(InvoiceTransition.REPLY_LOSS_REPORTED_AFTER_FAILURE.to()).isEqualTo(InvoiceState.PAYMENT_ERROR);
    }
}
