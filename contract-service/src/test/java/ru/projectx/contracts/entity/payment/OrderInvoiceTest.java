package ru.projectx.contracts.entity.payment;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.util.ReflectionTestUtils;

class OrderInvoiceTest {

    private static final UUID ORDER = UUID.fromString("0196f0a2-0000-7000-8000-000000000011");
    private static final UUID CONTRACT = UUID.fromString("0196f0a2-0000-7000-8000-000000000001");
    private static final Instant OPENED = Instant.parse("2026-02-10T07:05:00Z");
    private static final Instant LATER = Instant.parse("2026-02-10T07:06:00Z");
    private static final Instant DEADLINE = Instant.parse("2026-02-13T07:06:00Z");
    private static final Instant PAID_AT = Instant.parse("2026-02-11T10:00:00.123456Z");
    private static final InvoiceLink LINK = new InvoiceLink("inv-0001", "https://pay.example.test/inv-0001");

    private static OrderInvoice invoiceIn(InvoiceState state) {
        OrderInvoice invoice = OrderInvoice.open(ORDER, CONTRACT, OPENED);
        ReflectionTestUtils.setField(invoice, "state", state);
        return invoice;
    }

    private static Object updatedAt(OrderInvoice invoice) {
        return ReflectionTestUtils.getField(invoice, "updatedAt");
    }

    @Test
    void openedInvoice_awaitsTheDocumentOfItsOrderAndContractWithNothingElseKnown() {
        OrderInvoice invoice = OrderInvoice.open(ORDER, CONTRACT, OPENED);

        assertThat(invoice.view()).isEqualTo(new InvoiceView(ORDER, CONTRACT, InvoiceState.AWAITING_DOCUMENT, 0,
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()));
        assertThat(updatedAt(invoice)).isEqualTo(OPENED);
        assertThat(ReflectionTestUtils.getField(invoice, "createdAt")).isEqualTo(OPENED);
    }

    @Test
    void repeatFix_countsEveryRepeatedConfirmation() {
        OrderInvoice invoice = OrderInvoice.open(ORDER, CONTRACT, OPENED);

        invoice.repeatFix(OPENED);
        invoice.repeatFix(LATER);

        assertThat(invoice.view().fixRepeats()).isEqualTo(2);
        assertThat(invoice.view().state()).isEqualTo(InvoiceState.AWAITING_DOCUMENT);
        assertThat(updatedAt(invoice)).isEqualTo(LATER);
    }

    @Test
    void touch_movesOnlyTheMomentOfTheLastChange() {
        OrderInvoice invoice = invoiceIn(InvoiceState.ISSUED);

        invoice.touch(LATER);

        assertThat(updatedAt(invoice)).isEqualTo(LATER);
        assertThat(invoice.view().state()).isEqualTo(InvoiceState.ISSUED);
    }

    @ParameterizedTest
    @EnumSource(names = {"READY", "CHECKOUT"})
    void start_ofInvoiceAwaitingCheckout_marksTheCheckoutMomentAndThePaymentDeadline(InvoiceState state) {
        OrderInvoice invoice = invoiceIn(state);

        assertThat(invoice.start(DEADLINE, LATER)).isTrue();

        assertThat(invoice.view().state()).isEqualTo(InvoiceState.CHECKOUT);
        assertThat(invoice.view().term()).contains(new InvoiceTerm(LATER, DEADLINE));
        assertThat(updatedAt(invoice)).isEqualTo(LATER);
    }

    @ParameterizedTest
    @EnumSource(mode = EnumSource.Mode.EXCLUDE, names = {"READY", "CHECKOUT"})
    void start_ofAnyOtherInvoice_changesNothing(InvoiceState state) {
        OrderInvoice invoice = invoiceIn(state);

        assertThat(invoice.start(DEADLINE, LATER)).isFalse();

        assertThat(invoice.view().state()).isEqualTo(state);
        assertThat(invoice.view().term()).isEmpty();
        assertThat(updatedAt(invoice)).isEqualTo(OPENED);
    }

    @Test
    void issue_ofInvoiceInCheckout_keepsTheInvoiceNumberAndThePaymentLink() {
        OrderInvoice invoice = invoiceIn(InvoiceState.CHECKOUT);

        assertThat(invoice.issue(LINK, LATER)).isTrue();

        assertThat(invoice.view().state()).isEqualTo(InvoiceState.ISSUED);
        assertThat(invoice.view().link()).contains(LINK);
        assertThat(updatedAt(invoice)).isEqualTo(LATER);
    }

    @ParameterizedTest
    @EnumSource(names = {"ISSUED", "PAYMENT_ERROR"})
    void issue_ofInvoiceLeftWithoutLinkByTheLostReply_takesTheLateLinkAndKeepsTheState(InvoiceState state) {
        OrderInvoice invoice = invoiceIn(state);

        assertThat(invoice.issue(LINK, LATER)).isTrue();

        assertThat(invoice.view().state()).isEqualTo(state);
        assertThat(invoice.view().link()).contains(LINK);
        assertThat(updatedAt(invoice)).isEqualTo(LATER);
    }

    @ParameterizedTest
    @EnumSource(names = {"ISSUED", "PAYMENT_ERROR"})
    void issue_ofInvoiceThatAlreadyHasItsLink_keepsTheFirstLink(InvoiceState state) {
        OrderInvoice invoice = invoiceIn(InvoiceState.CHECKOUT);
        invoice.issue(LINK, OPENED);
        ReflectionTestUtils.setField(invoice, "state", state);

        assertThat(invoice.issue(new InvoiceLink("inv-0002", "https://pay.example.test/inv-0002"), LATER)).isFalse();

        assertThat(invoice.view().state()).isEqualTo(state);
        assertThat(invoice.view().link()).contains(LINK);
        assertThat(updatedAt(invoice)).isEqualTo(OPENED);
    }

    @ParameterizedTest
    @EnumSource(mode = EnumSource.Mode.EXCLUDE, names = {"CHECKOUT", "ISSUED", "PAYMENT_ERROR"})
    void issue_ofInvoiceThatWasNotStartedOrAlreadyHasItsOutcome_changesNothing(InvoiceState state) {
        OrderInvoice invoice = invoiceIn(state);

        assertThat(invoice.issue(LINK, LATER)).isFalse();

        assertThat(invoice.view().state()).isEqualTo(state);
        assertThat(invoice.view().link()).isEmpty();
        assertThat(updatedAt(invoice)).isEqualTo(OPENED);
    }

    @Test
    void reject_ofInvoiceInCheckout_keepsTheRejectionCode() {
        OrderInvoice invoice = invoiceIn(InvoiceState.CHECKOUT);

        assertThat(invoice.reject("CheckoutValidatorService.valid", LATER)).isTrue();

        assertThat(invoice.view().state()).isEqualTo(InvoiceState.REJECTED);
        assertThat(invoice.view().rejectionCode()).contains("CheckoutValidatorService.valid");
        assertThat(updatedAt(invoice)).isEqualTo(LATER);
    }

    @ParameterizedTest
    @EnumSource(mode = EnumSource.Mode.EXCLUDE, names = "CHECKOUT")
    void reject_ofInvoiceNotInCheckout_changesNothing(InvoiceState state) {
        OrderInvoice invoice = invoiceIn(state);

        assertThat(invoice.reject("code", LATER)).isFalse();

        assertThat(invoice.view().state()).isEqualTo(state);
        assertThat(invoice.view().rejectionCode()).isEmpty();
        assertThat(updatedAt(invoice)).isEqualTo(OPENED);
    }

    @ParameterizedTest
    @EnumSource(names = {"CHECKOUT", "ISSUED", "PAYMENT_ERROR", "OVERDUE"})
    void pay_ofInvoiceThatCouldBePaid_keepsTheMomentOfPayment(InvoiceState state) {
        OrderInvoice invoice = invoiceIn(state);

        assertThat(invoice.pay(PAID_AT, LATER)).isTrue();

        assertThat(invoice.view().state()).isEqualTo(InvoiceState.PAID);
        assertThat(invoice.view().paidAt()).contains(PAID_AT);
        assertThat(updatedAt(invoice)).isEqualTo(LATER);
    }

    @ParameterizedTest
    @EnumSource(names = {"AWAITING_DOCUMENT", "DOCUMENT_UNCONFIRMED", "READY", "REJECTED", "PAID"})
    void pay_ofInvoiceThatWasNotStartedOrIsAlreadyPaid_changesNothing(InvoiceState state) {
        OrderInvoice invoice = invoiceIn(state);

        assertThat(invoice.pay(PAID_AT, LATER)).isFalse();

        assertThat(invoice.view().state()).isEqualTo(state);
        assertThat(invoice.view().paidAt()).isEmpty();
        assertThat(updatedAt(invoice)).isEqualTo(OPENED);
    }

    @Test
    void lateReplyAfterTheLostOne_givesTheIssuedInvoiceItsLinkAndKeepsThePaymentTerm() {
        OrderInvoice invoice = invoiceIn(InvoiceState.READY);
        invoice.start(DEADLINE, OPENED);
        invoice.pass(InvoiceTransition.REPLY_LOST, OPENED);

        assertThat(invoice.issue(LINK, LATER)).isTrue();

        assertThat(invoice.view().state()).isEqualTo(InvoiceState.ISSUED);
        assertThat(invoice.view().link()).contains(LINK);
        assertThat(invoice.view().term()).contains(new InvoiceTerm(OPENED, DEADLINE));
    }

    @Test
    void replyLost_leavesTheIssuedInvoiceWithoutLink() {
        OrderInvoice invoice = invoiceIn(InvoiceState.READY);
        invoice.start(DEADLINE, OPENED);

        assertThat(invoice.pass(InvoiceTransition.REPLY_LOST, LATER)).isTrue();

        assertThat(invoice.view().state()).isEqualTo(InvoiceState.ISSUED);
        assertThat(invoice.view().link()).isEmpty();
        assertThat(invoice.view().term()).contains(new InvoiceTerm(OPENED, DEADLINE));
    }
}
