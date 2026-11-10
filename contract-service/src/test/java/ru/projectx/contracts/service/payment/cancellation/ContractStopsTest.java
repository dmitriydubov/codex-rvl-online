package ru.projectx.contracts.service.payment.cancellation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import ch.qos.logback.classic.Level;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.util.ReflectionTestUtils;
import ru.projectx.contracts.entity.contract.Contract;
import ru.projectx.contracts.entity.contract.ContractStatus;
import ru.projectx.contracts.entity.issuance.CancelReason;
import ru.projectx.contracts.entity.issuance.Cancellation;
import ru.projectx.contracts.entity.payment.InvoiceState;
import ru.projectx.contracts.entity.payment.OrderInvoice;
import ru.projectx.contracts.entity.payment.PaymentOrder;
import ru.projectx.contracts.entity.payment.PaymentOrderState;
import ru.projectx.contracts.repository.payment.OrderInvoiceRepository;
import ru.projectx.contracts.repository.payment.PaymentOrderRepository;
import ru.projectx.contracts.service.IssueErrors;
import ru.projectx.contracts.service.payment.Invoices;
import ru.projectx.contracts.service.payment.PaymentMetrics;
import ru.projectx.contracts.support.CapturedLog;
import ru.projectx.contracts.support.PaymentOrders;

class ContractStopsTest {

    private static final Instant NOW = Instant.parse("2026-02-12T11:30:05Z");
    private static final Cancellation CANCELLATION =
            new Cancellation(CancelReason.REQUEST_CANCELLED, null, Instant.parse("2026-02-12T11:30:00Z"));

    private final PaymentOrderRepository orders = mock();
    private final OrderInvoiceRepository invoices = mock();
    private final PaymentMetrics metrics = mock();
    private final ContractStops stops = new ContractStops(orders, invoices, metrics);
    private final CapturedLog log = CapturedLog.of(ContractStops.class);

    @AfterEach
    void releaseLog() {
        log.close();
    }

    private PaymentOrder found(PaymentOrder order) {
        given(orders.findLockedByContractId(PaymentOrders.CONTRACT)).willReturn(Optional.of(order));
        return order;
    }

    private OrderInvoice found(OrderInvoice invoice) {
        given(invoices.findFreshByContractId(PaymentOrders.CONTRACT)).willReturn(Optional.of(invoice));
        return invoice;
    }

    private void noRecords() {
        given(orders.findLockedByContractId(PaymentOrders.CONTRACT)).willReturn(Optional.empty());
        given(invoices.findFreshByContractId(PaymentOrders.CONTRACT)).willReturn(Optional.empty());
    }

    @ParameterizedTest
    @EnumSource(names = {"CHECKOUT", "ISSUED", "PAYMENT_ERROR"})
    void stop_ofContractWithStartedInvoice_isDeferredUntilThePaymentIsOverdue(InvoiceState state) {
        Contract contract = Invoices.invoicedContract();
        PaymentOrder order = found(PaymentOrders.created());
        OrderInvoice invoice = found(Invoices.in(state));

        stops.stop(contract, CANCELLATION, NOW);

        assertThat(invoice.view().deferredCancellation()).contains(CANCELLATION);
        assertThat(Invoices.updatedAt(invoice)).isEqualTo(NOW);
        assertThat(contract.status()).isEqualTo(ContractStatus.INVOICE_ISSUED);
        assertThat(contract.state().cancellation()).isEmpty();
        assertThat(order.view().state()).isEqualTo(PaymentOrderState.CREATED);
        assertThat(order.view().payer()).contains(PaymentOrders.PAYER);
        verifyNoInteractions(metrics);
        assertThat(log.at(Level.INFO)).containsExactly(
                Invoices.logLine("договор со счётом: прекращение отложено до просрочки оплаты"));
        assertThat(log.events()).hasSize(1);
    }

    @Test
    void stop_ofAcceptedContractWhoseCheckoutIsInProgress_isDeferredToo() {
        Contract contract = PaymentOrders.acceptedContract();
        found(PaymentOrders.created());
        OrderInvoice invoice = found(Invoices.inCheckout());

        stops.stop(contract, CANCELLATION, NOW);

        assertThat(invoice.view().deferredCancellation()).contains(CANCELLATION);
        assertThat(contract.status()).isEqualTo(ContractStatus.ACCEPTED);
    }

    @ParameterizedTest
    @EnumSource(names = {"AWAITING_DOCUMENT", "DOCUMENT_UNCONFIRMED", "READY", "REJECTED"})
    void stop_ofContractWhoseInvoiceWasNotStartedOrWasRejected_cancelsAtOnceAndSendsTheOrderToCancellation(
            InvoiceState state) {
        Contract contract = PaymentOrders.acceptedContract();
        PaymentOrder order = found(PaymentOrders.created());
        OrderInvoice invoice = found(Invoices.in(state));

        stops.stop(contract, CANCELLATION, NOW);

        assertThat(contract.status()).isEqualTo(ContractStatus.CANCELLED);
        assertThat(contract.state().cancellation()).contains(CANCELLATION);
        assertThat(contract.state().updatedAt()).isEqualTo(NOW);
        assertThat(order.view().state()).isEqualTo(PaymentOrderState.CANCEL_PENDING);
        assertThat(order.view().payer()).isEmpty();
        assertThat(invoice.view().deferredCancellation()).isEmpty();
        verifyNoInteractions(metrics);
        assertThat(log.events()).isEmpty();
    }

    @Test
    void stop_ofContractWithOrderButWithoutInvoice_cancelsAtOnceAndClosesTheOrder() {
        Contract contract = PaymentOrders.acceptedContract();
        PaymentOrder order = found(PaymentOrders.awaiting());
        given(invoices.findFreshByContractId(PaymentOrders.CONTRACT)).willReturn(Optional.empty());

        stops.stop(contract, CANCELLATION, NOW);

        assertThat(contract.status()).isEqualTo(ContractStatus.CANCELLED);
        assertThat(order.view().state()).isEqualTo(PaymentOrderState.CANCELLED);
        assertThat(order.view().payer()).isEmpty();
        verifyNoInteractions(metrics);
    }

    @Test
    void stop_ofContractWithoutOrder_cancelsItWithoutLookingForInvoices() {
        Contract contract = PaymentOrders.acceptedContract();
        given(orders.findLockedByContractId(PaymentOrders.CONTRACT)).willReturn(Optional.empty());

        stops.stop(contract, CANCELLATION, NOW);

        assertThat(contract.status()).isEqualTo(ContractStatus.CANCELLED);
        assertThat(contract.state().cancellation()).contains(CANCELLATION);
        verify(invoices, never()).findFreshByContractId(any());
        verifyNoInteractions(metrics);
    }

    @Test
    void stop_ofContractThatDoesNotAwaitPayment_isNotDeferredEvenWithStartedInvoice() {
        Contract contract = Invoices.invoicedContract();
        contract.markError(IssueErrors.programMissing(), Invoices.STARTED);
        found(PaymentOrders.created());
        OrderInvoice invoice = found(Invoices.issued());

        stops.stop(contract, CANCELLATION, NOW);

        assertThat(contract.status()).isEqualTo(ContractStatus.CANCELLED);
        assertThat(contract.state().cancellation()).contains(CANCELLATION);
        assertThat(invoice.view().deferredCancellation()).isEmpty();
    }

    @Test
    void stop_ofPaidContract_cancelsItCountsTheRefundAndLeavesThePaidOrderAlone() {
        Contract contract = Invoices.invoicedContract();
        contract.markPaid(Invoices.STARTED, Invoices.STARTED);
        PaymentOrder order = found(PaymentOrders.created());
        found(Invoices.in(InvoiceState.PAID));

        stops.stop(contract, CANCELLATION, NOW);

        assertThat(contract.status()).isEqualTo(ContractStatus.CANCELLED);
        assertThat(contract.state().cancellation()).contains(CANCELLATION);
        assertThat(order.view().state()).isEqualTo(PaymentOrderState.CREATED);
        verify(metrics).countPaidCancelled();
        assertThat(log.at(Level.INFO)).containsExactly(Invoices.logLine(
                "работа по оплаченному договору прекращена: заказ не отменяется, деньги возвращают"));
        assertThat(log.events()).hasSize(1);
    }

    @Test
    void stop_ofContractInErrorWhoseInvoiceIsPaid_countsTheRefundByTheInvoiceAlone() {
        Contract contract = Invoices.invoicedContract();
        contract.markError(IssueErrors.kmContractPaidAfterCancellation(), Invoices.STARTED);
        PaymentOrder order = found(PaymentOrders.created());
        found(Invoices.in(InvoiceState.PAID));

        stops.stop(contract, CANCELLATION, NOW);

        assertThat(contract.status()).isEqualTo(ContractStatus.CANCELLED);
        assertThat(order.view().state()).isEqualTo(PaymentOrderState.CREATED);
        verify(metrics).countPaidCancelled();
        assertThat(log.at(Level.INFO)).hasSize(1);
        assertThat(log.at(Level.WARN)).isEmpty();
    }

    @Test
    void stop_ofContractWithMomentOfPaymentButWithoutRecords_countsTheRefundByTheContractAlone() {
        Contract contract = PaymentOrders.acceptedContract();
        contract.markPaid(Invoices.STARTED, Invoices.STARTED);
        noRecords();

        stops.stop(contract, CANCELLATION, NOW);

        assertThat(contract.status()).isEqualTo(ContractStatus.CANCELLED);
        verify(metrics).countPaidCancelled();
        assertThat(log.at(Level.INFO)).hasSize(1);
        assertThat(log.at(Level.WARN)).isEmpty();
    }

    @Test
    void resume_liftsTheDeferredCancellationOfTheContractInvoice() {
        Contract contract = Invoices.invoicedContract();
        found(PaymentOrders.created());
        OrderInvoice invoice = found(Invoices.issued());
        invoice.deferCancellation(CANCELLATION, Invoices.STARTED);

        stops.resume(contract, NOW);

        assertThat(invoice.view().deferredCancellation()).isEmpty();
        assertThat(Invoices.updatedAt(invoice)).isEqualTo(NOW);
        assertThat(contract.status()).isEqualTo(ContractStatus.INVOICE_ISSUED);
    }

    @Test
    void resume_locksTheOrderBeforeReadingItsInvoice() {
        Contract contract = Invoices.invoicedContract();
        found(PaymentOrders.created());
        found(Invoices.issued());

        stops.resume(contract, NOW);

        org.mockito.InOrder sequence = org.mockito.Mockito.inOrder(orders, invoices);
        sequence.verify(orders).findLockedByContractId(PaymentOrders.CONTRACT);
        sequence.verify(invoices).findFreshByContractId(PaymentOrders.CONTRACT);
    }

    @Test
    void resume_ofContractWithoutOrder_doesNotLookForInvoices() {
        given(orders.findLockedByContractId(PaymentOrders.CONTRACT)).willReturn(Optional.empty());

        stops.resume(PaymentOrders.acceptedContract(), NOW);

        verifyNoInteractions(invoices);
    }

    @Test
    void resume_ofContractWithOrderButWithoutInvoice_changesNothing() {
        PaymentOrder order = found(PaymentOrders.awaiting());
        given(invoices.findFreshByContractId(PaymentOrders.CONTRACT)).willReturn(Optional.empty());

        stops.resume(PaymentOrders.acceptedContract(), NOW);

        assertThat(order.view().state()).isEqualTo(PaymentOrderState.AWAITING_CREATION);
    }

    @Test
    void deferred_namesOnlyTheContractsWhoseCancellationWaitsForOverdue() {
        UUID other = UUID.fromString("0196f0a2-0000-7000-8000-000000000002");
        OrderInvoice waiting = Invoices.issued();
        waiting.deferCancellation(CANCELLATION, NOW);
        OrderInvoice running = Invoices.issued();
        ReflectionTestUtils.setField(running, "contractId", other);
        given(invoices.findByContractIdIn(List.of(PaymentOrders.CONTRACT, other))).willReturn(List.of(waiting, running));

        Set<UUID> deferred = stops.deferred(List.of(PaymentOrders.CONTRACT, other));

        assertThat(deferred).containsExactly(PaymentOrders.CONTRACT);
        assertThatThrownBy(() -> deferred.add(other)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void deferred_ofContractsWithoutInvoices_isEmpty() {
        given(invoices.findByContractIdIn(List.of(PaymentOrders.CONTRACT))).willReturn(List.of());

        assertThat(stops.deferred(List.of(PaymentOrders.CONTRACT))).isEmpty();
    }
}
