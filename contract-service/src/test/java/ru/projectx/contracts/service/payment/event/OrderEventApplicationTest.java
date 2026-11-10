package ru.projectx.contracts.service.payment.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;
import ru.projectx.contracts.entity.contract.Contract;
import ru.projectx.contracts.entity.payment.OrderInvoice;
import ru.projectx.contracts.entity.payment.PaymentOrder;
import ru.projectx.contracts.entity.payment.PaymentOrderKeys;
import ru.projectx.contracts.repository.payment.OrderInvoiceRepository;
import ru.projectx.contracts.repository.payment.PaymentOrderRepository;
import ru.projectx.contracts.service.payment.IgnoredEvent;
import ru.projectx.contracts.service.payment.Invoices;
import ru.projectx.contracts.service.payment.LockedOrder;
import ru.projectx.contracts.service.payment.PaymentOrderLocks;
import ru.projectx.contracts.support.PaymentOrders;

class OrderEventApplicationTest {

    private static final Instant NOW = Instant.parse("2026-02-10T07:30:00Z");
    private static final EventDocument.Ours OURS =
            new EventDocument.Ours(PaymentOrders.ORDER, Optional.of(new BigDecimal("34000.00")));
    private static final Ignored OUT_OF_ORDER = new Ignored(IgnoredEvent.OUT_OF_ORDER, "стенд: не по порядку");

    private final PaymentOrderRepository orders = mock();
    private final PaymentOrderLocks locks = mock();
    private final OrderInvoiceRepository invoices = mock();
    private final OrderEventReaction paid = mock();
    private final OrderEventReaction fixed = mock();
    private final Contract contract = Invoices.invoicedContract();
    private final PaymentOrder order = PaymentOrders.created();
    private final OrderInvoice invoice = Invoices.issued();
    private OrderEventApplication application;

    @BeforeEach
    void knownOrderWithInvoice() {
        given(paid.eventType()).willReturn("paid");
        given(fixed.eventType()).willReturn("fixed");
        application = new OrderEventApplication(new OrderEventMatching(orders), locks, invoices, List.of(paid, fixed));
        given(orders.findKeysByExternalOrderId(PaymentOrders.ORDER_ID))
                .willReturn(Optional.of(new PaymentOrderKeys(PaymentOrders.CONTRACT, PaymentOrders.CORRELATION)));
        given(locks.lock(PaymentOrders.CONTRACT)).willReturn(new LockedOrder(contract, order));
        given(invoices.findFreshByContractId(PaymentOrders.CONTRACT)).willReturn(Optional.of(invoice));
    }

    private static OrderEvent event(String type, EventDocument document) {
        return new OrderEvent(Optional.of(type), Optional.of(PaymentOrders.ORDER_ID), document,
                Optional.of(Instant.parse("2026-02-10T07:05:03Z")));
    }

    @Test
    void usableEventOfKnownOrder_isHandedToTheReactionOfItsTypeWithTheLockedOrderAndItsInvoice() {
        OrderEvent event = event("paid", OURS);
        given(paid.react(any(), any(), any())).willReturn(Optional.empty());

        assertThat(application.apply(event, NOW)).isEmpty();

        ArgumentCaptor<EventTarget> target = ArgumentCaptor.forClass(EventTarget.class);
        verify(paid).react(target.capture(), org.mockito.ArgumentMatchers.same(event),
                org.mockito.ArgumentMatchers.eq(NOW));
        assertThat(target.getValue()).isEqualTo(new EventTarget(new LockedOrder(contract, order), Optional.of(invoice)));
        verify(fixed, never()).react(any(), any(), any());
    }

    @Test
    void refusalOfTheReaction_isTheReasonTheEventIsIgnored() {
        given(paid.react(any(), any(), any())).willReturn(Optional.of(OUT_OF_ORDER));

        assertThat(application.apply(event("paid", OURS), NOW)).contains(OUT_OF_ORDER);
    }

    @Test
    void reaction_runsWithTheCorrelationIdOfTheOrderInTheLogContext() {
        given(paid.react(any(), any(), any())).willAnswer(reaction -> {
            assertThat(MDC.get("correlationId")).isEqualTo(PaymentOrders.CORRELATION.toString());
            return Optional.empty();
        });

        application.apply(event("paid", OURS), NOW);

        verify(paid).react(any(), any(), any());
        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test
    void orderWithoutInvoice_isHandedToTheReactionWithoutIt() {
        given(invoices.findFreshByContractId(PaymentOrders.CONTRACT)).willReturn(Optional.empty());
        given(fixed.react(any(), any(), any())).willReturn(Optional.empty());

        application.apply(event("fixed", OURS), NOW);

        verify(fixed).react(new EventTarget(new LockedOrder(contract, order), Optional.empty()),
                event("fixed", OURS), NOW);
    }

    @Test
    void eventOfUnfamiliarType_isNotApplicable() {
        assertThat(application.apply(event("created", OURS), NOW))
                .contains(new Ignored(IgnoredEvent.NOT_APPLICABLE, "событие created заказ не меняет"));

        verify(paid, never()).react(any(), any(), any());
        verify(fixed, never()).react(any(), any(), any());
    }

    @Test
    void eventWhoseTypeIsNotAPlainIdentifier_isNotApplicableWithoutTheTypeInTheText() {
        assertThat(application.apply(event("оплата insured@example.test", OURS), NOW)).contains(
                new Ignored(IgnoredEvent.NOT_APPLICABLE, "событие с типом не по формату заказ не меняет"));
    }

    @Test
    void unusableEvent_isIgnoredBeforeAnyOrderIsLookedUpOrLocked() {
        EventDocument foreign = new EventDocument.Foreign("в событии нет документа продукта Договоров");

        assertThat(application.apply(event("paid", foreign), NOW))
                .contains(new Ignored(IgnoredEvent.FOREIGN_DOCUMENT, "в событии нет документа продукта Договоров"));

        verify(orders, never()).findKeysByExternalOrderId(any());
        verifyNoInteractions(locks, invoices);
    }

    @Test
    void eventOfOrderUnknownToContracts_isIgnoredWithoutLocks() {
        given(orders.findKeysByExternalOrderId(PaymentOrders.ORDER_ID)).willReturn(Optional.empty());
        given(orders.findKeysById(PaymentOrders.ORDER)).willReturn(Optional.empty());

        assertThat(application.apply(event("paid", OURS), NOW))
                .contains(new Ignored(IgnoredEvent.UNKNOWN_ORDER, "заказа с таким orderId у Договоров нет"));

        verifyNoInteractions(locks, invoices);
        verify(paid, never()).react(any(), any(), any());
    }

    @Test
    void eventThatDoesNotMatchTheLockedOrder_isIgnoredWithoutReaction() {
        EventDocument.Ours cheaper = new EventDocument.Ours(PaymentOrders.ORDER, Optional.of(new BigDecimal("33999.99")));

        assertThat(application.apply(event("paid", cheaper), NOW)).contains(
                new Ignored(IgnoredEvent.AMOUNT_MISMATCH, "сумма документа в событии не равна премии договора"));

        verify(locks).lock(PaymentOrders.CONTRACT);
        verifyNoInteractions(invoices);
        verify(paid, never()).react(any(), any(), any());
        assertThat(MDC.get("correlationId")).isNull();
    }
}
