package ru.projectx.contracts.service.payment.reconciliation;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.projectx.contracts.config.payment.PaymentSwitch;
import ru.projectx.contracts.entity.contract.ContractStatus;
import ru.projectx.contracts.entity.payment.OrderInvoice;
import ru.projectx.contracts.entity.payment.PaymentOrderState;
import ru.projectx.contracts.repository.payment.OrderInvoiceRepository;
import ru.projectx.contracts.service.leadership.LeadershipFence;
import ru.projectx.contracts.service.payment.LockedInvoice;
import ru.projectx.contracts.service.payment.LockedOrder;
import ru.projectx.contracts.service.payment.OrderingOrder;
import ru.projectx.contracts.service.payment.PaymentOrderLocks;
import ru.projectx.contracts.service.payment.invoice.PaymentTermExpiry;
import ru.projectx.contracts.service.payment.invoice.PaymentTerms;

@Service
@ConditionalOnBooleanProperty(PaymentSwitch.ENABLED)
public class ReconciliationRecording {

    private final PaymentOrderLocks locks;
    private final OrderInvoiceRepository invoices;
    private final PaymentTerms terms;
    private final PaymentTermExpiry expiry;
    private final ReconciliationAlerts alerts;
    private final Map<String, StateReconciler> reconcilers;
    private final LeadershipFence fence;
    private final Clock clock;

    ReconciliationRecording(PaymentOrderLocks locks, OrderInvoiceRepository invoices, PaymentTerms terms,
            PaymentTermExpiry expiry, ReconciliationAlerts alerts, List<StateReconciler> reconcilers,
            LeadershipFence fence, Clock clock) {
        this.locks = locks;
        this.invoices = invoices;
        this.terms = terms;
        this.expiry = expiry;
        this.alerts = alerts;
        this.reconcilers = reconcilers.stream()
                .collect(Collectors.toUnmodifiableMap(StateReconciler::status, Function.identity()));
        this.fence = fence;
        this.clock = clock;
    }

    @Transactional
    public void closeExpired(UUID contractId) {
        LockedOrder locked = locks.lock(contractId);
        fence.holdUntilCommit();
        Instant now = clock.instant();
        boolean invoiced = invoices.findFreshByContractId(contractId)
                .map(OrderInvoice::view)
                .filter(invoice -> invoice.state().invoiced())
                .isPresent();
        if (!invoiced && awaitsInvoice(locked, now)) {
            expiry.close(locked, PaymentTermExpiry.BEFORE_INVOICE, now);
        }
    }

    @Transactional
    public Followup apply(UUID contractId, OrderingOrder order) {
        LockedInvoice locked = locks.lockInvoice(contractId);
        fence.holdUntilCommit();
        Instant now = clock.instant();
        locked.invoice().touch(now);
        if (!awaitsOutcome(locked)) {
            return Followup.NONE;
        }
        return Optional.ofNullable(reconcilers.get(order.status()))
                .map(reconciler -> reconciler.reconcile(locked, order, now))
                .orElseGet(() -> unexpected(locked, order));
    }

    @Transactional
    public void stateUnavailable(UUID contractId) {
        LockedInvoice locked = locks.lockInvoice(contractId);
        fence.holdUntilCommit();
        locked.invoice().touch(clock.instant());
        alerts.stateUnavailable(locked.contract());
    }

    private static boolean awaitsOutcome(LockedInvoice locked) {
        return locked.order().view().state() == PaymentOrderState.CREATED
                && locked.invoice().view().state().awaitsOutcome();
    }

    private boolean awaitsInvoice(LockedOrder locked, Instant now) {
        return locked.contract().status() == ContractStatus.ACCEPTED
                && !locked.order().view().state().withdrawn()
                && terms.boundaryPassed(now, locked.order().view().document().documentTtl());
    }

    private Followup unexpected(LockedInvoice locked, OrderingOrder order) {
        alerts.unexpectedState(locked.contract(), locked.invoice().view(), order.status());
        return Followup.NONE;
    }
}
