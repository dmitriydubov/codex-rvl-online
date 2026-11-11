package ru.projectx.contracts.service.payment.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import ru.projectx.contracts.entity.payment.PaymentOrderKeys;
import ru.projectx.contracts.entity.payment.PaymentOrderView;
import ru.projectx.contracts.repository.payment.PaymentOrderRepository;
import ru.projectx.contracts.service.payment.IgnoredEvent;
import ru.projectx.contracts.support.PaymentOrders;

class OrderEventMatchingTest {

    private static final EventDocument.Ours OURS =
            new EventDocument.Ours(PaymentOrders.ORDER, Optional.of(new BigDecimal("34000.00")));
    private static final PaymentOrderKeys KEYS = new PaymentOrderKeys(PaymentOrders.CONTRACT, PaymentOrders.CORRELATION);
    private static final UUID ANOTHER = UUID.fromString("0196f0a2-0000-7000-8000-0000000000ff");

    private final PaymentOrderRepository orders = mock();
    private final OrderEventMatching matching = new OrderEventMatching(orders);
    private final PaymentOrderView created = PaymentOrders.created().view();

    private static OrderEvent event(Optional<String> type, Optional<String> orderId, EventDocument document) {
        return new OrderEvent(type, orderId, document, Optional.of(Instant.parse("2026-02-10T07:05:03Z")));
    }

    @Test
    void eventWithTypeOrderAndOurDocument_isUsable() {
        assertThat(matching.unusable(event(Optional.of("paid"), Optional.of(PaymentOrders.ORDER_ID), OURS))).isEmpty();
    }

    @Test
    void eventWithoutType_isNotApplicable() {
        assertThat(matching.unusable(event(Optional.empty(), Optional.of(PaymentOrders.ORDER_ID), OURS)))
                .contains(new Ignored(IgnoredEvent.NOT_APPLICABLE, "в событии нет eventType"));
    }

    @Test
    void eventWithoutType_isNotApplicableEvenWhenTheDocumentIsForeign() {
        assertThat(matching.unusable(event(Optional.empty(), Optional.empty(), new EventDocument.Foreign("чужое"))))
                .contains(new Ignored(IgnoredEvent.NOT_APPLICABLE, "в событии нет eventType"));
    }

    @Test
    void eventWithForeignDocument_isIgnoredWithTheReasonOfTheDocument() {
        EventDocument foreign = new EventDocument.Foreign("в событии нет документа продукта Договоров");

        assertThat(matching.unusable(event(Optional.of("paid"), Optional.empty(), foreign)))
                .contains(new Ignored(IgnoredEvent.FOREIGN_DOCUMENT, "в событии нет документа продукта Договоров"));
    }

    @Test
    void eventWithoutOrderId_isAnUnknownOrder() {
        assertThat(matching.unusable(event(Optional.of("paid"), Optional.empty(), OURS)))
                .contains(new Ignored(IgnoredEvent.UNKNOWN_ORDER, "в событии нет orderId"));
    }

    @Test
    void order_isFoundByTheOrderingNumberFirst() {
        given(orders.findKeysByExternalOrderId(PaymentOrders.ORDER_ID)).willReturn(Optional.of(KEYS));

        assertThat(matching.orderOf(PaymentOrders.ORDER_ID, PaymentOrders.ORDER)).contains(KEYS);

        verify(orders, never()).findKeysById(any());
    }

    @Test
    void orderWhoseNumberIsNotRecordedYet_isFoundByTheDocument() {
        given(orders.findKeysByExternalOrderId(PaymentOrders.ORDER_ID)).willReturn(Optional.empty());
        given(orders.findKeysById(PaymentOrders.ORDER)).willReturn(Optional.of(KEYS));

        assertThat(matching.orderOf(PaymentOrders.ORDER_ID, PaymentOrders.ORDER)).contains(KEYS);
    }

    @Test
    void orderKnownByNeitherNumberNorDocument_isNotFound() {
        given(orders.findKeysByExternalOrderId(PaymentOrders.ORDER_ID)).willReturn(Optional.empty());
        given(orders.findKeysById(PaymentOrders.ORDER)).willReturn(Optional.empty());

        assertThat(matching.orderOf(PaymentOrders.ORDER_ID, PaymentOrders.ORDER)).isEmpty();
    }

    @Test
    void eventOfTheSameOrderDocumentAndAmount_matches() {
        assertThat(matching.mismatch(created, PaymentOrders.ORDER_ID, OURS)).isEmpty();
    }

    @Test
    void amountWrittenWithAnotherScale_matches() {
        EventDocument.Ours sameAmount = new EventDocument.Ours(PaymentOrders.ORDER, Optional.of(new BigDecimal("34000")));

        assertThat(matching.mismatch(created, PaymentOrders.ORDER_ID, sameAmount)).isEmpty();
    }

    @Test
    void orderWithoutRecordedNumber_matchesByDocumentAndAmount() {
        assertThat(matching.mismatch(PaymentOrders.awaiting().view(), "order-of-the-event", OURS)).isEmpty();
    }

    @Test
    void anotherOrderNumber_isAnUnknownOrder() {
        assertThat(matching.mismatch(created, "0196f0a2-5b1c-7d3e-9f40-1a2b3c4d5eff", OURS))
                .contains(new Ignored(IgnoredEvent.UNKNOWN_ORDER, "заказа с таким orderId у Договоров нет"));
    }

    @Test
    void anotherDocument_isADocumentMismatch() {
        EventDocument.Ours another = new EventDocument.Ours(ANOTHER, Optional.of(new BigDecimal("34000.00")));

        assertThat(matching.mismatch(created, PaymentOrders.ORDER_ID, another)).contains(
                new Ignored(IgnoredEvent.DOCUMENT_MISMATCH, "документ события не совпадает с документом заказа"));
    }

    @Test
    void anotherAmount_isAnAmountMismatch() {
        EventDocument.Ours cheaper = new EventDocument.Ours(PaymentOrders.ORDER, Optional.of(new BigDecimal("33999.99")));

        assertThat(matching.mismatch(created, PaymentOrders.ORDER_ID, cheaper)).contains(
                new Ignored(IgnoredEvent.AMOUNT_MISMATCH, "сумма документа в событии не равна премии договора"));
    }

    @Test
    void missingAmount_isAnAmountMismatch() {
        EventDocument.Ours withoutAmount = new EventDocument.Ours(PaymentOrders.ORDER, Optional.empty());

        assertThat(matching.mismatch(created, PaymentOrders.ORDER_ID, withoutAmount)).contains(
                new Ignored(IgnoredEvent.AMOUNT_MISMATCH, "сумма документа в событии не равна премии договора"));
    }

    @Test
    void anotherOrderNumber_isReportedBeforeTheDocumentAndTheAmount() {
        EventDocument.Ours another = new EventDocument.Ours(ANOTHER, Optional.empty());

        assertThat(matching.mismatch(created, "order-x", another)).map(Ignored::reason)
                .contains(IgnoredEvent.UNKNOWN_ORDER);
    }
}
