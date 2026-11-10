package ru.projectx.contracts.service.payment.invoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import ch.qos.logback.classic.Level;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import ru.projectx.contracts.entity.contract.Contract;
import ru.projectx.contracts.entity.contract.ContractStatus;
import ru.projectx.contracts.entity.issuance.CancelReason;
import ru.projectx.contracts.entity.issuance.Cancellation;
import ru.projectx.contracts.entity.payment.PaymentOrder;
import ru.projectx.contracts.entity.payment.PaymentOrderState;
import ru.projectx.contracts.service.payment.Invoices;
import ru.projectx.contracts.service.payment.LockedOrder;
import ru.projectx.contracts.service.payment.PaymentMetrics;
import ru.projectx.contracts.service.status.IssuanceSnapshot;
import ru.projectx.contracts.service.status.RequestStatusPublication;
import ru.projectx.contracts.support.CapturedLog;
import ru.projectx.contracts.support.PaymentOrders;
import ru.projectx.contracts.support.Tasks;

class PaymentTermExpiryTest {

    private static final Instant NOW = Instant.parse("2026-04-09T21:00:00Z");

    private final RequestStatusPublication statuses = mock();
    private final PaymentMetrics metrics = mock();
    private final PaymentTermExpiry expiry = new PaymentTermExpiry(statuses, metrics);
    private final Contract contract = PaymentOrders.acceptedContract();
    private final IssuanceSnapshot before = mock();
    private CapturedLog log;

    @BeforeEach
    void watchTheRequestBeforeTheChange() {
        log = CapturedLog.of(PaymentTermExpiry.class);
        given(statuses.watch(Tasks.REQUEST)).willAnswer(watch -> {
            assertThat(contract.status()).isEqualTo(ContractStatus.ACCEPTED);
            return before;
        });
    }

    @AfterEach
    void releaseLog() {
        log.close();
    }

    @Test
    void orderNeverSentToOrdering_isClosedByTermAndTheContractIsCancelledAsExpired() {
        PaymentOrder order = PaymentOrders.awaiting();

        expiry.close(new LockedOrder(contract, order), PaymentTermExpiry.BEFORE_ORDER, NOW);

        assertThat(order.view().state()).isEqualTo(PaymentOrderState.EXPIRED);
        assertThat(order.view().payer()).isEmpty();
        assertThat(contract.status()).isEqualTo(ContractStatus.CANCELLED);
        assertThat(contract.state().cancellation()).contains(new Cancellation(CancelReason.EXPIRED, null, NOW));
        assertThat(contract.state().updatedAt()).isEqualTo(NOW);
        verify(metrics).countTermExpired();
        assertThat(log.at(Level.WARN))
                .containsExactly(Invoices.logLine("срок оплаты истёк до создания заказа, договор прекращён"));
        assertThat(log.events()).hasSize(1);
    }

    @Test
    void requestsLearnAboutTheChangeFromTheSnapshotTakenBeforeIt() {
        expiry.close(new LockedOrder(contract, PaymentOrders.awaiting()), PaymentTermExpiry.BEFORE_ORDER, NOW);

        InOrder sequence = inOrder(statuses);
        sequence.verify(statuses).watch(Tasks.REQUEST);
        sequence.verify(statuses).afterRecordChange(before, NOW);
    }

    @Test
    void orderWhoseCreationWasSent_isLeftForCancellationInOrdering() {
        PaymentOrder order = PaymentOrders.awaiting();
        order.markRequested(NOW.minusSeconds(60));

        expiry.close(new LockedOrder(contract, order), PaymentTermExpiry.BEFORE_ORDER, NOW);

        assertThat(order.view().state()).isEqualTo(PaymentOrderState.CANCEL_PENDING);
        assertThat(order.view().payer()).isEmpty();
        assertThat(contract.status()).isEqualTo(ContractStatus.CANCELLED);
    }

    @Test
    void createdOrderWithoutInvoice_goesToCancellationWithTheReasonOfTheCaller() {
        PaymentOrder order = PaymentOrders.created();

        expiry.close(new LockedOrder(contract, order), PaymentTermExpiry.BEFORE_INVOICE, NOW);

        assertThat(order.view().state()).isEqualTo(PaymentOrderState.CANCEL_PENDING);
        assertThat(order.view().payer()).isEmpty();
        assertThat(contract.state().cancellation()).contains(new Cancellation(CancelReason.EXPIRED, null, NOW));
        verify(metrics).countTermExpired();
        assertThat(log.at(Level.WARN))
                .containsExactly(Invoices.logLine("срок оплаты истёк до выставления счёта, договор прекращён"));
        verify(statuses).afterRecordChange(before, NOW);
    }
}
