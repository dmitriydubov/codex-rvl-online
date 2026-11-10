package ru.projectx.contracts.service.payment.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import ru.projectx.contracts.entity.contract.Contract;
import ru.projectx.contracts.entity.payment.OrderInvoice;
import ru.projectx.contracts.entity.payment.PaymentOrder;
import ru.projectx.contracts.exception.ContractViolationException;
import ru.projectx.contracts.service.payment.IgnoredEvent;
import ru.projectx.contracts.service.payment.Invoices;
import ru.projectx.contracts.service.payment.LockedInvoice;
import ru.projectx.contracts.service.payment.LockedOrder;
import ru.projectx.contracts.service.payment.invoice.PaymentOutcomes;
import ru.projectx.contracts.support.PaymentOrders;

class PaymentReactionsTest {

    private static final Instant NOW = Instant.parse("2026-02-11T10:00:05Z");
    private static final Instant UPDATED = Instant.parse("2026-02-11T10:00:00.250Z");

    private final PaymentOutcomes outcomes = mock();
    private final PaidReaction paid = new PaidReaction(outcomes);
    private final PaymentErrorReaction paymentError = new PaymentErrorReaction(outcomes);
    private final PaymentOverdueReaction overdue = new PaymentOverdueReaction(outcomes);
    private final Contract contract = Invoices.invoicedContract();
    private final PaymentOrder order = PaymentOrders.created();
    private final OrderInvoice invoice = Invoices.issued();
    private final LockedInvoice locked = new LockedInvoice(contract, order, invoice);
    private final EventTarget invoiced = new EventTarget(new LockedOrder(contract, order), Optional.of(invoice));
    private final EventTarget withoutInvoice = new EventTarget(new LockedOrder(contract, order), Optional.empty());

    private static OrderEvent event(String type, Optional<Instant> updated) {
        return new OrderEvent(Optional.of(type), Optional.of(PaymentOrders.ORDER_ID),
                new EventDocument.Ours(PaymentOrders.ORDER, Optional.of(new BigDecimal("34000.00"))), updated);
    }

    @Test
    void reactions_nameTheEventTypesOfOrdering() {
        assertThat(paid.eventType()).isEqualTo("paid");
        assertThat(paymentError.eventType()).isEqualTo("paymentError");
        assertThat(overdue.eventType()).isEqualTo("paymentOverdue");
    }

    @Test
    void paid_appliesThePaymentAtTheMomentOfTheEvent() {
        given(outcomes.paid(locked, UPDATED, NOW)).willReturn(true);

        assertThat(paid.react(invoiced, event("paid", Optional.of(UPDATED)), NOW)).isEmpty();

        verify(outcomes).paid(locked, UPDATED, NOW);
    }

    @Test
    void paid_thatThePaymentOutcomeRefuses_isOutOfOrder() {
        given(outcomes.paid(locked, UPDATED, NOW)).willReturn(false);

        assertThat(paid.react(invoiced, event("paid", Optional.of(UPDATED)), NOW)).contains(
                new Ignored(IgnoredEvent.OUT_OF_ORDER, "событие paid не по порядку: заказ ждёт оплаты"));
    }

    @Test
    void paid_forOrderWithoutInvoice_isOutOfOrder() {
        assertThat(paid.react(withoutInvoice, event("paid", Optional.of(UPDATED)), NOW))
                .contains(new Ignored(IgnoredEvent.OUT_OF_ORDER, "событие paid не по порядку: заказ создан"));

        verifyNoInteractions(outcomes);
    }

    @Test
    void paid_withoutTheMomentOfTheChange_violatesTheContractAndAppliesNothing() {
        assertThatThrownBy(() -> paid.react(invoiced, event("paid", Optional.empty()), NOW))
                .isInstanceOf(ContractViolationException.class)
                .hasMessage("недопустимое значение поля updated");

        verifyNoInteractions(outcomes);
    }

    @Test
    void paymentError_marksTheFailedPayment() {
        given(outcomes.paymentFailed(locked, NOW)).willReturn(true);

        assertThat(paymentError.react(invoiced, event("paymentError", Optional.of(UPDATED)), NOW)).isEmpty();

        verify(outcomes).paymentFailed(locked, NOW);
    }

    @Test
    void paymentError_thatThePaymentOutcomeRefuses_isOutOfOrder() {
        given(outcomes.paymentFailed(locked, NOW)).willReturn(false);

        assertThat(paymentError.react(invoiced, event("paymentError", Optional.empty()), NOW)).contains(
                new Ignored(IgnoredEvent.OUT_OF_ORDER, "событие paymentError не по порядку: заказ ждёт оплаты"));
    }

    @Test
    void paymentError_forOrderWithoutInvoice_isOutOfOrder() {
        assertThat(paymentError.react(withoutInvoice, event("paymentError", Optional.empty()), NOW)).contains(
                new Ignored(IgnoredEvent.OUT_OF_ORDER, "событие paymentError не по порядку: заказ создан"));

        verifyNoInteractions(outcomes);
    }

    @Test
    void paymentOverdue_appliesTheOverdueAtTheMomentOfTheEvent() {
        given(outcomes.overdue(locked, UPDATED, NOW)).willReturn(true);

        assertThat(overdue.react(invoiced, event("paymentOverdue", Optional.of(UPDATED)), NOW)).isEmpty();

        verify(outcomes).overdue(locked, UPDATED, NOW);
    }

    @Test
    void paymentOverdue_withoutTheMomentOfTheChange_appliesTheOverdueAtTheMomentOfProcessing() {
        given(outcomes.overdue(locked, NOW, NOW)).willReturn(true);

        assertThat(overdue.react(invoiced, event("paymentOverdue", Optional.empty()), NOW)).isEmpty();

        verify(outcomes).overdue(locked, NOW, NOW);
    }

    @Test
    void paymentOverdue_thatThePaymentOutcomeRefuses_isOutOfOrder() {
        given(outcomes.overdue(locked, UPDATED, NOW)).willReturn(false);

        assertThat(overdue.react(invoiced, event("paymentOverdue", Optional.of(UPDATED)), NOW)).contains(
                new Ignored(IgnoredEvent.OUT_OF_ORDER, "событие paymentOverdue не по порядку: заказ ждёт оплаты"));
    }

    @Test
    void paymentOverdue_forOrderWithoutInvoice_isOutOfOrder() {
        assertThat(overdue.react(withoutInvoice, event("paymentOverdue", Optional.of(UPDATED)), NOW)).contains(
                new Ignored(IgnoredEvent.OUT_OF_ORDER, "событие paymentOverdue не по порядку: заказ создан"));

        verifyNoInteractions(outcomes);
    }
}
