package ru.projectx.contracts.service.payment.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import ru.projectx.contracts.entity.contract.Contract;
import ru.projectx.contracts.entity.payment.InvoiceState;
import ru.projectx.contracts.entity.payment.OrderInvoice;
import ru.projectx.contracts.entity.payment.PaymentOrder;
import ru.projectx.contracts.service.payment.IgnoredEvent;
import ru.projectx.contracts.service.payment.Invoices;
import ru.projectx.contracts.service.payment.LockedOrder;
import ru.projectx.contracts.service.payment.invoice.InvoiceRecording;
import ru.projectx.contracts.support.PaymentOrders;

class DocumentReactionsTest {

    private static final Instant NOW = Instant.parse("2026-02-10T07:30:00Z");
    private static final OrderEvent EVENT = new OrderEvent(Optional.of("fixed"), Optional.of(PaymentOrders.ORDER_ID),
            new EventDocument.Ours(PaymentOrders.ORDER, Optional.of(new BigDecimal("34000.00"))), Optional.empty());

    private final InvoiceRecording recording = mock();
    private final FixedReaction fixed = new FixedReaction();
    private final PendingPaymentReaction pending = new PendingPaymentReaction(recording);
    private final Contract contract = PaymentOrders.acceptedContract();

    private EventTarget target(PaymentOrder order, OrderInvoice invoice) {
        return new EventTarget(new LockedOrder(contract, order), Optional.of(invoice));
    }

    private EventTarget withoutInvoice(PaymentOrder order) {
        return new EventTarget(new LockedOrder(contract, order), Optional.empty());
    }

    @Test
    void reactions_nameTheEventTypesOfOrdering() {
        assertThat(fixed.eventType()).isEqualTo("fixed");
        assertThat(pending.eventType()).isEqualTo("pendingPayment");
    }

    @ParameterizedTest
    @EnumSource(names = {"AWAITING_DOCUMENT", "DOCUMENT_UNCONFIRMED"})
    void fixed_makesTheInvoiceAwaitingTheDocumentReady(InvoiceState state) {
        OrderInvoice invoice = Invoices.in(state);

        assertThat(fixed.react(target(PaymentOrders.created(), invoice), EVENT, NOW)).isEmpty();

        assertThat(invoice.view().state()).isEqualTo(InvoiceState.READY);
        assertThat(Invoices.updatedAt(invoice)).isEqualTo(NOW);
    }

    @Test
    void fixed_repeatedForReadyInvoice_isOutOfOrder() {
        OrderInvoice invoice = Invoices.ready();

        assertThat(fixed.react(target(PaymentOrders.created(), invoice), EVENT, NOW)).contains(
                new Ignored(IgnoredEvent.OUT_OF_ORDER, "событие fixed не по порядку: заказ готов к оплате"));

        assertThat(invoice.view().state()).isEqualTo(InvoiceState.READY);
    }

    @Test
    void fixed_forOrderThatLeftTheWork_isOutOfOrderAndLeavesItsInvoiceAlone() {
        OrderInvoice invoice = Invoices.awaitingDocument();

        assertThat(fixed.react(target(PaymentOrders.cancelPending(), invoice), EVENT, NOW)).contains(
                new Ignored(IgnoredEvent.OUT_OF_ORDER, "событие fixed не по порядку: заказ ждёт отмены"));

        assertThat(invoice.view().state()).isEqualTo(InvoiceState.AWAITING_DOCUMENT);
    }

    @Test
    void fixed_forOrderWithoutInvoice_isOutOfOrder() {
        assertThat(fixed.react(withoutInvoice(PaymentOrders.created()), EVENT, NOW))
                .contains(new Ignored(IgnoredEvent.OUT_OF_ORDER, "событие fixed не по порядку: заказ создан"));
    }

    @Test
    void pendingPayment_forInvoiceInCheckout_recordsThatPaymentIsAwaitedWithoutReportingALostReply() {
        assertThat(pending.react(target(PaymentOrders.created(), Invoices.inCheckout()), EVENT, NOW)).isEmpty();

        verify(recording).paymentAwaited(PaymentOrders.CONTRACT);
        verify(recording, never()).replyLost(PaymentOrders.CONTRACT);
    }

    @Test
    void pendingPayment_forIssuedInvoice_changesNothing() {
        assertThat(pending.react(target(PaymentOrders.created(), Invoices.issued()), EVENT, NOW)).contains(
                new Ignored(IgnoredEvent.NOT_APPLICABLE, "событие pendingPayment заказ не меняет"));

        verifyNoInteractions(recording);
    }

    @ParameterizedTest
    @EnumSource(mode = EnumSource.Mode.EXCLUDE, names = {"CHECKOUT", "ISSUED"})
    void pendingPayment_forAnyOtherInvoice_isOutOfOrder(InvoiceState state) {
        Optional<Ignored> ignored = pending.react(target(PaymentOrders.created(), Invoices.in(state)), EVENT, NOW);

        assertThat(ignored).map(Ignored::reason).contains(IgnoredEvent.OUT_OF_ORDER);
        assertThat(ignored).map(Ignored::text).hasValueSatisfying(
                text -> assertThat(text).startsWith("событие pendingPayment не по порядку: заказ "));
        verifyNoInteractions(recording);
    }

    @Test
    void pendingPayment_forOrderWithoutInvoice_isOutOfOrder() {
        assertThat(pending.react(withoutInvoice(PaymentOrders.created()), EVENT, NOW)).contains(
                new Ignored(IgnoredEvent.OUT_OF_ORDER, "событие pendingPayment не по порядку: заказ создан"));

        verifyNoInteractions(recording);
    }
}
