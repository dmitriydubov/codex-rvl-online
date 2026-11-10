package ru.projectx.contracts.service.payment.invoice;

import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.projectx.contracts.entity.contract.Contract;
import ru.projectx.contracts.entity.issuance.CancelReason;
import ru.projectx.contracts.entity.issuance.Cancellation;
import ru.projectx.contracts.entity.payment.InvoiceTransition;
import ru.projectx.contracts.service.IssueErrors;
import ru.projectx.contracts.service.payment.LockedInvoice;
import ru.projectx.contracts.service.payment.PaymentMetrics;
import ru.projectx.contracts.service.payment.PaymentOrderLog;
import ru.projectx.contracts.service.status.IssuanceSnapshot;
import ru.projectx.contracts.service.status.RequestStatusPublication;

@Service
public class PaymentOutcomes {

    static final String PAID = "договор оплачен";
    static final String PAID_AFTER_OVERDUE = "оплата поступила по договору, прекращённому по просрочке";
    static final String PAID_UNEXPECTEDLY = "оплата поступила по договору, который не ждёт оплаты, статус ";
    static final String OVERDUE = "срок оплаты счёта истёк";
    private static final Logger log = LoggerFactory.getLogger(PaymentOutcomes.class);

    private final RequestStatusPublication statuses;
    private final PaymentMetrics metrics;

    public PaymentOutcomes(RequestStatusPublication statuses, PaymentMetrics metrics) {
        this.statuses = statuses;
        this.metrics = metrics;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean paid(LockedInvoice locked, Instant paidAt, Instant now) {
        IssuanceSnapshot before = watch(locked.contract());
        if (!locked.invoice().pay(paidAt, now)) {
            return false;
        }
        locked.order().forgetPayer(now);
        settle(locked, paidAt, now);
        statuses.afterRecordChange(before, now);
        return true;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean overdue(LockedInvoice locked, Instant moment, Instant now) {
        IssuanceSnapshot before = watch(locked.contract());
        if (!locked.invoice().pass(InvoiceTransition.PAYMENT_OVERDUE, now)) {
            return false;
        }
        Cancellation cancellation = locked.invoice().liftDeferredCancellation(now)
                .orElseGet(() -> new Cancellation(CancelReason.EXPIRED, null, moment));
        if (locked.contract().status().awaitsPayment()) {
            locked.contract().cancel(cancellation, now);
        }
        PaymentOrderLog.write(log.atInfo(), locked.contract(), OVERDUE);
        statuses.afterRecordChange(before, now);
        return true;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean paymentFailed(LockedInvoice locked, Instant now) {
        if (!locked.invoice().pass(InvoiceTransition.PAYMENT_FAILED, now)) {
            return false;
        }
        metrics.countPaymentError();
        return true;
    }

    private void settle(LockedInvoice locked, Instant paidAt, Instant now) {
        Contract contract = locked.contract();
        Optional<Cancellation> deferred = locked.invoice().liftDeferredCancellation(now);
        if (deferred.isPresent()) {
            contract.markError(IssueErrors.kmContractPaidAfterCancellation(), now);
            refundNeeded(contract, IssueErrors.kmContractPaidAfterCancellation().message());
            return;
        }
        if (!contract.status().awaitsPayment()) {
            refundNeeded(contract, unexpectedPayment(contract));
            return;
        }
        contract.markPaid(paidAt, now);
        PaymentOrderLog.write(log.atInfo(), contract, PAID);
    }

    private void refundNeeded(Contract contract, String reason) {
        metrics.countPaidAfterCancellation();
        PaymentOrderLog.write(log.atError(), contract, reason);
    }

    private static String unexpectedPayment(Contract contract) {
        boolean expired = contract.state().cancellation()
                .filter(cancellation -> cancellation.reason() == CancelReason.EXPIRED)
                .isPresent();
        return expired ? PAID_AFTER_OVERDUE : PAID_UNEXPECTEDLY + contract.status();
    }

    private IssuanceSnapshot watch(Contract contract) {
        return statuses.watch(contract.state().request().individualRequestId());
    }
}
