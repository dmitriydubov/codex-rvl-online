package ru.projectx.contracts.entity.payment;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class InvoiceStateTest {

    @ParameterizedTest
    @EnumSource(names = {"AWAITING_DOCUMENT", "DOCUMENT_UNCONFIRMED"})
    void invoiceWhoseDocumentIsNotConfirmed_awaitsConfirmation(InvoiceState state) {
        assertThat(state.awaitsConfirmation()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(mode = EnumSource.Mode.EXCLUDE, names = {"AWAITING_DOCUMENT", "DOCUMENT_UNCONFIRMED"})
    void invoiceWithConfirmedDocument_doesNotAwaitConfirmation(InvoiceState state) {
        assertThat(state.awaitsConfirmation()).isFalse();
    }

    @ParameterizedTest
    @EnumSource(names = {"READY", "CHECKOUT"})
    void readyInvoiceAndInvoiceInCheckout_awaitCheckout(InvoiceState state) {
        assertThat(state.awaitsCheckout()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(mode = EnumSource.Mode.EXCLUDE, names = {"READY", "CHECKOUT"})
    void anyOtherInvoice_doesNotAwaitCheckout(InvoiceState state) {
        assertThat(state.awaitsCheckout()).isFalse();
    }

    @ParameterizedTest
    @EnumSource(names = {"CHECKOUT", "ISSUED", "PAYMENT_ERROR"})
    void startedInvoiceWithoutOutcome_awaitsPayment(InvoiceState state) {
        assertThat(state.awaitsPayment()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(mode = EnumSource.Mode.EXCLUDE, names = {"CHECKOUT", "ISSUED", "PAYMENT_ERROR"})
    void invoiceNotStartedRejectedOrWithOutcome_doesNotAwaitPayment(InvoiceState state) {
        assertThat(state.awaitsPayment()).isFalse();
    }

    @ParameterizedTest
    @EnumSource(names = {"CHECKOUT", "ISSUED", "PAYMENT_ERROR", "PAID", "OVERDUE"})
    void startedInvoiceThatWasNotRejected_isInvoiced(InvoiceState state) {
        assertThat(state.invoiced()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(names = {"AWAITING_DOCUMENT", "DOCUMENT_UNCONFIRMED", "READY", "REJECTED"})
    void invoiceNotStartedOrRejected_isNotInvoiced(InvoiceState state) {
        assertThat(state.invoiced()).isFalse();
    }

    @ParameterizedTest
    @EnumSource(mode = EnumSource.Mode.EXCLUDE, names = {"REJECTED", "PAID", "OVERDUE"})
    void invoiceOnItsWayToPayment_awaitsItsOutcome(InvoiceState state) {
        assertThat(state.awaitsOutcome()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(names = {"REJECTED", "PAID", "OVERDUE"})
    void rejectedPaidAndOverdueInvoices_awaitNoOutcome(InvoiceState state) {
        assertThat(state.awaitsOutcome()).isFalse();
    }
}
