package ru.projectx.contracts.entity.payment;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

class InvoiceTransitionTest {

    private static InvoiceProgress plain(InvoiceState state) {
        return new InvoiceProgress(state, 2, false, false);
    }

    @ParameterizedTest
    @CsvSource({
        "DOCUMENT_CONFIRMED, AWAITING_DOCUMENT, READY",
        "DOCUMENT_CONFIRMED, DOCUMENT_UNCONFIRMED, READY",
        "REMINDERS_EXHAUSTED, AWAITING_DOCUMENT, DOCUMENT_UNCONFIRMED",
        "DOCUMENT_RESENT, AWAITING_DOCUMENT, AWAITING_DOCUMENT",
        "DOCUMENT_RESENT, DOCUMENT_UNCONFIRMED, AWAITING_DOCUMENT",
        "PAYMENT_AWAITED, CHECKOUT, ISSUED",
        "REPLY_LOST, CHECKOUT, ISSUED",
        "REPLY_LOSS_REPORTED, ISSUED, ISSUED",
        "REPLY_LOSS_REPORTED_AFTER_FAILURE, PAYMENT_ERROR, PAYMENT_ERROR",
        "PAYMENT_FAILED, ISSUED, PAYMENT_ERROR",
        "PAYMENT_OVERDUE, CHECKOUT, OVERDUE",
        "PAYMENT_OVERDUE, ISSUED, OVERDUE",
        "PAYMENT_OVERDUE, PAYMENT_ERROR, OVERDUE"
    })
    void transition_leadsFromEachOfItsStatesToItsTarget(InvoiceTransition transition, InvoiceState from,
            InvoiceState to) {
        assertThat(transition.after(plain(from))).map(InvoiceProgress::state).contains(to);
    }

    @ParameterizedTest
    @EnumSource(value = InvoiceTransition.class, names = "DOCUMENT_RESENT")
    void resentDocument_startsTheConfirmationRepeatsAnew(InvoiceTransition transition) {
        assertThat(transition.after(plain(InvoiceState.DOCUMENT_UNCONFIRMED)))
                .contains(new InvoiceProgress(InvoiceState.AWAITING_DOCUMENT, 0, false, false));
    }

    @ParameterizedTest
    @CsvSource({"DOCUMENT_CONFIRMED, AWAITING_DOCUMENT, READY", "REMINDERS_EXHAUSTED, AWAITING_DOCUMENT, DOCUMENT_UNCONFIRMED",
        "PAYMENT_AWAITED, CHECKOUT, ISSUED", "PAYMENT_FAILED, ISSUED, PAYMENT_ERROR", "PAYMENT_OVERDUE, PAYMENT_ERROR, OVERDUE"})
    void transitionThatReportsNothing_keepsTheRepeatsTheReportAndTheLinkEvenAfterTheReport(
            InvoiceTransition transition, InvoiceState from, InvoiceState to) {
        assertThat(transition.after(new InvoiceProgress(from, 2, true, true)))
                .contains(new InvoiceProgress(to, 2, true, true));
    }

    @ParameterizedTest
    @CsvSource({"REPLY_LOST, CHECKOUT, ISSUED", "REPLY_LOSS_REPORTED, ISSUED, ISSUED",
        "REPLY_LOSS_REPORTED_AFTER_FAILURE, PAYMENT_ERROR, PAYMENT_ERROR"})
    void lostReply_isMarkedReportedWithTheRepeatsKept(InvoiceTransition transition, InvoiceState from,
            InvoiceState to) {
        assertThat(transition.after(plain(from))).contains(new InvoiceProgress(to, 2, true, false));
    }

    @ParameterizedTest
    @CsvSource({"REPLY_LOST, CHECKOUT", "REPLY_LOSS_REPORTED, ISSUED", "REPLY_LOSS_REPORTED_AFTER_FAILURE, PAYMENT_ERROR"})
    void lostReply_isRefusedOnceReported(InvoiceTransition transition, InvoiceState from) {
        assertThat(transition.after(new InvoiceProgress(from, 2, true, false))).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"REPLY_LOST, CHECKOUT", "REPLY_LOSS_REPORTED, ISSUED", "REPLY_LOSS_REPORTED_AFTER_FAILURE, PAYMENT_ERROR"})
    void lostReply_isRefusedForTheInvoiceWithItsLink(InvoiceTransition transition, InvoiceState from) {
        assertThat(transition.after(new InvoiceProgress(from, 2, false, true))).isEmpty();
    }
}
