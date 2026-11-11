package ru.projectx.contracts.service.payment.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import ch.qos.logback.classic.Level;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import ru.projectx.contracts.entity.payment.InvoiceState;
import ru.projectx.contracts.repository.payment.OrderInvoiceRepository;
import ru.projectx.contracts.service.payment.Invoices;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import ru.projectx.contracts.entity.payment.PaymentOrder;
import ru.projectx.contracts.entity.payment.PaymentOrderKeys;
import ru.projectx.contracts.entity.payment.PaymentOrderState;
import ru.projectx.contracts.repository.payment.PaymentOrderRepository;
import ru.projectx.contracts.service.payment.LockedOrder;
import ru.projectx.contracts.service.payment.OrderingMetrics;
import ru.projectx.contracts.service.payment.PaymentOrderLocks;
import ru.projectx.contracts.support.CapturedLog;
import ru.projectx.contracts.support.PaymentOrders;

class RejectedDocumentFixesTest {

    private static final Instant NOW = Instant.parse("2026-02-10T07:20:00Z");

    private final PaymentOrderRepository orders = mock();
    private final PaymentOrderLocks locks = mock();
    private final OrderInvoiceRepository invoices = mock();
    private final OrderingMetrics metrics = mock();
    private final RejectedDocumentFixes reaction = new RejectedDocumentFixes(orders, locks, invoices, metrics);
    private final PaymentOrderKeys keys = new PaymentOrderKeys(PaymentOrders.CONTRACT, PaymentOrders.CORRELATION);
    private CapturedLog log;

    @BeforeEach
    void captureLog() {
        log = CapturedLog.of(RejectedDocumentFixes.class);
    }

    @AfterEach
    void releaseLog() {
        log.close();
    }

    @Test
    void reactsToPaymentOrderMessages() {
        assertThat(reaction.aggregateType()).isEqualTo("PAYMENT_ORDER");
    }

    @Test
    void undeliveredFix_rejectsTheCreatedOrderWithItsCodeAndAlerts() {
        PaymentOrder order = PaymentOrders.created();
        given(orders.findKeysById(PaymentOrders.ORDER)).willReturn(Optional.of(keys));
        willAnswer(lock -> {
            assertThat(MDC.get("correlationId")).isEqualTo(PaymentOrders.CORRELATION.toString());
            return new LockedOrder(PaymentOrders.acceptedContract(), order);
        }).given(locks).lock(PaymentOrders.CONTRACT);

        reaction.rejected(PaymentOrders.ORDER, NOW);

        assertThat(order.view().state()).isEqualTo(PaymentOrderState.REJECTED);
        assertThat(order.view().rejectionCode()).contains("FIXED_UNDELIVERED");
        assertThat(order.view().orderId()).contains(PaymentOrders.ORDER_ID);
        verify(metrics).countOrderRejected();
        verify(orders, never()).findById(PaymentOrders.ORDER);
        assertThat(log.at(Level.ERROR)).singleElement().asString()
                .endsWith(": подтверждение документа не доставлено");
        assertThat(MDC.get("correlationId")).isNull();
    }

    @ParameterizedTest
    @EnumSource(names = {"AWAITING_DOCUMENT", "DOCUMENT_UNCONFIRMED"})
    void undeliveredFixOfInvoiceAwaitingTheDocument_rejectsTheOrder(InvoiceState state) {
        PaymentOrder order = PaymentOrders.created();
        given(orders.findKeysById(PaymentOrders.ORDER)).willReturn(Optional.of(keys));
        given(locks.lock(PaymentOrders.CONTRACT)).willReturn(new LockedOrder(PaymentOrders.acceptedContract(), order));
        given(invoices.findFreshByContractId(PaymentOrders.CONTRACT)).willReturn(Optional.of(Invoices.in(state)));

        reaction.rejected(PaymentOrders.ORDER, NOW);

        assertThat(order.view().state()).isEqualTo(PaymentOrderState.REJECTED);
        verify(metrics).countOrderRejected();
    }

    @ParameterizedTest
    @EnumSource(mode = EnumSource.Mode.EXCLUDE, names = {"AWAITING_DOCUMENT", "DOCUMENT_UNCONFIRMED"})
    void undeliveredRepeatOfConfirmedDocument_keepsTheOrderAndOnlyNotes(InvoiceState state) {
        PaymentOrder order = PaymentOrders.created();
        given(orders.findKeysById(PaymentOrders.ORDER)).willReturn(Optional.of(keys));
        given(locks.lock(PaymentOrders.CONTRACT)).willReturn(new LockedOrder(PaymentOrders.acceptedContract(), order));
        given(invoices.findFreshByContractId(PaymentOrders.CONTRACT)).willReturn(Optional.of(Invoices.in(state)));

        reaction.rejected(PaymentOrders.ORDER, NOW);

        assertThat(order.view().state()).isEqualTo(PaymentOrderState.CREATED);
        assertThat(order.view().rejectionCode()).isEmpty();
        verify(metrics, never()).countOrderRejected();
        assertThat(log.at(Level.ERROR)).isEmpty();
        assertThat(log.at(Level.INFO)).singleElement().asString()
                .endsWith(": повторное подтверждение документа не доставлено, документ уже подтверждён");
    }

    @Test
    void undeliveredFixOfWithdrawnOrder_changesNothing() {
        PaymentOrder order = PaymentOrders.cancelPending();
        given(orders.findKeysById(PaymentOrders.ORDER)).willReturn(Optional.of(keys));
        given(locks.lock(PaymentOrders.CONTRACT)).willReturn(new LockedOrder(PaymentOrders.acceptedContract(), order));

        reaction.rejected(PaymentOrders.ORDER, NOW);

        assertThat(order.view().state()).isEqualTo(PaymentOrderState.CANCEL_PENDING);
        verify(metrics, never()).countOrderRejected();
        assertThat(log.events()).isEmpty();
    }

    @Test
    void unknownOrder_failsLoudly() {
        given(orders.findKeysById(PaymentOrders.ORDER)).willReturn(Optional.empty());

        assertThatThrownBy(() -> reaction.rejected(PaymentOrders.ORDER, NOW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(PaymentOrders.ORDER.toString());
    }
}
