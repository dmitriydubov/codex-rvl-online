package ru.projectx.contracts.service.payment.invoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import ch.qos.logback.classic.Level;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import ru.projectx.contracts.entity.contract.Contract;
import ru.projectx.contracts.entity.contract.ContractStatus;
import ru.projectx.contracts.entity.issuance.CancelReason;
import ru.projectx.contracts.entity.issuance.Cancellation;
import ru.projectx.contracts.entity.issuance.IssueError;
import ru.projectx.contracts.entity.payment.InvoiceState;
import ru.projectx.contracts.entity.payment.OrderInvoice;
import ru.projectx.contracts.entity.payment.PaymentOrder;
import ru.projectx.contracts.service.payment.Invoices;
import ru.projectx.contracts.service.payment.LockedInvoice;
import ru.projectx.contracts.service.payment.PaymentMetrics;
import ru.projectx.contracts.service.status.IssuanceSnapshot;
import ru.projectx.contracts.service.status.RequestStatusPublication;
import ru.projectx.contracts.support.CapturedLog;
import ru.projectx.contracts.support.PaymentOrders;
import ru.projectx.contracts.support.Tasks;

class PaymentOutcomesTest {

    private static final Instant NOW = Instant.parse("2026-02-11T10:00:05Z");
    private static final Instant PAID_AT = Instant.parse("2026-02-11T10:00:00.250Z");

    private final RequestStatusPublication statuses = mock();
    private final PaymentMetrics metrics = mock();
    private final PaymentOutcomes outcomes = new PaymentOutcomes(statuses, metrics);
    private final PaymentOrder order = PaymentOrders.created();
    private final IssuanceSnapshot before = mock();
    private CapturedLog log;

    @BeforeEach
    void captureLog() {
        log = CapturedLog.of(PaymentOutcomes.class);
    }

    @AfterEach
    void releaseLog() {
        log.close();
    }

    private LockedInvoice locked(Contract contract, OrderInvoice invoice) {
        ContractStatus watched = contract.status();
        given(statuses.watch(Tasks.REQUEST)).willAnswer(watch -> {
            assertThat(contract.status()).isEqualTo(watched);
            return before;
        });
        return new LockedInvoice(contract, order, invoice);
    }

    @Test
    void paid_marksTheInvoiceAndTheContractPaidAtTheMomentOfPaymentAndTellsRequests() {
        Contract contract = Invoices.invoicedContract();
        OrderInvoice invoice = Invoices.issued();

        assertThat(outcomes.paid(locked(contract, invoice), PAID_AT, NOW)).isTrue();

        assertThat(invoice.view().state()).isEqualTo(InvoiceState.PAID);
        assertThat(invoice.view().paidAt()).contains(PAID_AT);
        assertThat(contract.status()).isEqualTo(ContractStatus.PAID);
        assertThat(contract.progress().paidAt()).contains(PAID_AT);
        assertThat(contract.state().updatedAt()).isEqualTo(NOW);
        verify(statuses).afterRecordChange(before, NOW);
        verifyNoInteractions(metrics);
        assertThat(log.at(Level.INFO)).containsExactly(Invoices.logLine("договор оплачен"));
        assertThat(log.events()).hasSize(1);
    }

    @Test
    void paid_beforeTheCheckoutResultIsRecorded_paysTheAcceptedContractAndForgetsThePayer() {
        Contract contract = PaymentOrders.acceptedContract();
        OrderInvoice invoice = Invoices.inCheckout();

        assertThat(outcomes.paid(locked(contract, invoice), PAID_AT, NOW)).isTrue();

        assertThat(contract.status()).isEqualTo(ContractStatus.PAID);
        assertThat(order.view().payer()).isEmpty();
        assertThat(invoice.view().link()).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(names = {"AWAITING_DOCUMENT", "DOCUMENT_UNCONFIRMED", "READY", "REJECTED", "PAID"})
    void paid_forInvoiceThatCannotBePaid_changesNothing(InvoiceState state) {
        Contract contract = PaymentOrders.acceptedContract();
        OrderInvoice invoice = Invoices.in(state);

        assertThat(outcomes.paid(locked(contract, invoice), PAID_AT, NOW)).isFalse();

        assertThat(invoice.view().state()).isEqualTo(state);
        assertThat(contract.status()).isEqualTo(ContractStatus.ACCEPTED);
        assertThat(order.view().payer()).contains(PaymentOrders.PAYER);
        verify(statuses, never()).afterRecordChange(any(), any());
        verifyNoInteractions(metrics);
        assertThat(log.events()).isEmpty();
    }

    @Test
    void paid_afterDeferredCancellation_putsTheContractInErrorForTheRefund() {
        Contract contract = Invoices.invoicedContract();
        OrderInvoice invoice = Invoices.issued();
        invoice.deferCancellation(Invoices.REFUSED, Invoices.STARTED);

        assertThat(outcomes.paid(locked(contract, invoice), PAID_AT, NOW)).isTrue();

        assertThat(invoice.view().state()).isEqualTo(InvoiceState.PAID);
        assertThat(invoice.view().paidAt()).contains(PAID_AT);
        assertThat(invoice.view().deferredCancellation()).isEmpty();
        assertThat(contract.status()).isEqualTo(ContractStatus.ERROR);
        assertThat(contract.state().error()).contains(new IssueError("KM_CONTRACT_PAID_AFTER_CANCELLATION",
                "оплата поступила после прекращения работы по договору"));
        assertThat(contract.progress().paidAt()).isEmpty();
        verify(metrics).countPaidAfterCancellation();
        verify(statuses).afterRecordChange(before, NOW);
        assertThat(log.at(Level.ERROR))
                .containsExactly(Invoices.logLine("оплата поступила после прекращения работы по договору"));
        assertThat(log.events()).hasSize(1);
    }

    @Test
    void paid_forContractCancelledByOverdue_keepsTheContractAndAsksForTheRefund() {
        Contract contract = Invoices.invoicedContract();
        Cancellation expired = new Cancellation(CancelReason.EXPIRED, null, Invoices.DEADLINE);
        contract.cancel(expired, Invoices.DEADLINE);
        OrderInvoice invoice = Invoices.in(InvoiceState.OVERDUE);

        assertThat(outcomes.paid(locked(contract, invoice), PAID_AT, NOW)).isTrue();

        assertThat(invoice.view().state()).isEqualTo(InvoiceState.PAID);
        assertThat(invoice.view().paidAt()).contains(PAID_AT);
        assertThat(contract.status()).isEqualTo(ContractStatus.CANCELLED);
        assertThat(contract.state().cancellation()).contains(expired);
        assertThat(contract.state().updatedAt()).isEqualTo(Invoices.DEADLINE);
        assertThat(contract.progress().paidAt()).isEmpty();
        verify(metrics).countPaidAfterCancellation();
        assertThat(log.at(Level.ERROR))
                .containsExactly(Invoices.logLine("оплата поступила по договору, прекращённому по просрочке"));
        assertThat(log.events()).hasSize(1);
    }

    @Test
    void paid_forContractStoppedForAnotherReason_namesItsStatusAndAsksForTheRefund() {
        Contract contract = Invoices.invoicedContract();
        contract.cancel(Invoices.REFUSED, Invoices.DEADLINE);
        OrderInvoice invoice = Invoices.in(InvoiceState.OVERDUE);

        assertThat(outcomes.paid(locked(contract, invoice), PAID_AT, NOW)).isTrue();

        assertThat(contract.status()).isEqualTo(ContractStatus.CANCELLED);
        verify(metrics).countPaidAfterCancellation();
        assertThat(log.at(Level.ERROR)).containsExactly(
                Invoices.logLine("оплата поступила по договору, который не ждёт оплаты, статус CANCELLED"));
    }

    @Test
    void overdue_closesTheInvoiceAndCancelsTheContractAsExpiredAtTheMomentOfOverdue() {
        Contract contract = Invoices.invoicedContract();
        OrderInvoice invoice = Invoices.issued();

        assertThat(outcomes.overdue(locked(contract, invoice), Invoices.DEADLINE, NOW)).isTrue();

        assertThat(invoice.view().state()).isEqualTo(InvoiceState.OVERDUE);
        assertThat(contract.status()).isEqualTo(ContractStatus.CANCELLED);
        assertThat(contract.state().cancellation())
                .contains(new Cancellation(CancelReason.EXPIRED, null, Invoices.DEADLINE));
        assertThat(contract.state().updatedAt()).isEqualTo(NOW);
        verify(statuses).afterRecordChange(before, NOW);
        verifyNoInteractions(metrics);
        assertThat(log.at(Level.INFO)).containsExactly(Invoices.logLine("срок оплаты счёта истёк"));
        assertThat(log.events()).hasSize(1);
    }

    @Test
    void overdue_appliesTheDeferredCancellationInsteadOfTheExpiry() {
        Contract contract = Invoices.invoicedContract();
        OrderInvoice invoice = Invoices.issued();
        invoice.deferCancellation(Invoices.REFUSED, Invoices.STARTED);

        assertThat(outcomes.overdue(locked(contract, invoice), Invoices.DEADLINE, NOW)).isTrue();

        assertThat(contract.status()).isEqualTo(ContractStatus.CANCELLED);
        assertThat(contract.state().cancellation()).contains(Invoices.REFUSED);
        assertThat(invoice.view().deferredCancellation()).isEmpty();
        assertThat(invoice.view().state()).isEqualTo(InvoiceState.OVERDUE);
    }

    @Test
    void overdue_forContractThatNoLongerAwaitsPayment_closesOnlyTheInvoice() {
        Contract contract = Invoices.invoicedContract();
        contract.cancel(Invoices.REFUSED, Invoices.STARTED);
        OrderInvoice invoice = Invoices.issued();

        assertThat(outcomes.overdue(locked(contract, invoice), Invoices.DEADLINE, NOW)).isTrue();

        assertThat(invoice.view().state()).isEqualTo(InvoiceState.OVERDUE);
        assertThat(contract.state().cancellation()).contains(Invoices.REFUSED);
        assertThat(contract.state().updatedAt()).isEqualTo(Invoices.STARTED);
    }

    @ParameterizedTest
    @EnumSource(mode = EnumSource.Mode.EXCLUDE, names = {"CHECKOUT", "ISSUED", "PAYMENT_ERROR"})
    void overdue_forInvoiceThatDoesNotAwaitPayment_changesNothing(InvoiceState state) {
        Contract contract = PaymentOrders.acceptedContract();
        OrderInvoice invoice = Invoices.in(state);

        assertThat(outcomes.overdue(locked(contract, invoice), Invoices.DEADLINE, NOW)).isFalse();

        assertThat(invoice.view().state()).isEqualTo(state);
        assertThat(contract.status()).isEqualTo(ContractStatus.ACCEPTED);
        verify(statuses, never()).afterRecordChange(any(), any());
        assertThat(log.events()).isEmpty();
    }

    @Test
    void paymentFailed_marksTheIssuedInvoiceAndCountsTheError() {
        Contract contract = Invoices.invoicedContract();
        OrderInvoice invoice = Invoices.issued();

        assertThat(outcomes.paymentFailed(new LockedInvoice(contract, order, invoice), NOW)).isTrue();

        assertThat(invoice.view().state()).isEqualTo(InvoiceState.PAYMENT_ERROR);
        assertThat(Invoices.updatedAt(invoice)).isEqualTo(NOW);
        assertThat(contract.status()).isEqualTo(ContractStatus.INVOICE_ISSUED);
        verify(metrics).countPaymentError();
        verifyNoInteractions(statuses);
    }

    @ParameterizedTest
    @EnumSource(mode = EnumSource.Mode.EXCLUDE, names = "ISSUED")
    void paymentFailed_forInvoiceThatIsNotIssued_changesNothing(InvoiceState state) {
        OrderInvoice invoice = Invoices.in(state);

        assertThat(outcomes.paymentFailed(new LockedInvoice(Invoices.invoicedContract(), order, invoice), NOW))
                .isFalse();

        assertThat(invoice.view().state()).isEqualTo(state);
        verifyNoInteractions(metrics);
    }
}
