package ru.projectx.contracts.service.payment.invoice;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.projectx.contracts.config.payment.PaymentSwitch;
import ru.projectx.contracts.entity.contract.ContractStatus;
import ru.projectx.contracts.entity.contract.ContractStep;
import ru.projectx.contracts.entity.payment.InvoiceLink;
import ru.projectx.contracts.entity.payment.InvoiceTransition;
import ru.projectx.contracts.service.leadership.LeadershipFence;
import ru.projectx.contracts.service.payment.CheckoutRequest;
import ru.projectx.contracts.service.payment.LockedInvoice;
import ru.projectx.contracts.service.payment.PaymentOrderLocks;
import ru.projectx.contracts.service.payment.PaymentOrderLog;
import ru.projectx.contracts.service.status.IssuanceSnapshot;
import ru.projectx.contracts.service.status.RequestStatusPublication;

@Service
@ConditionalOnBooleanProperty(PaymentSwitch.ENABLED)
public class InvoiceRecording {

    private static final Logger log = LoggerFactory.getLogger(InvoiceRecording.class);

    private final PaymentOrderLocks locks;
    private final CheckoutClaims claims;
    private final RequestStatusPublication statuses;
    private final InvoiceAlerts alerts;
    private final LeadershipFence fence;
    private final Clock clock;

    public InvoiceRecording(PaymentOrderLocks locks, CheckoutClaims claims, RequestStatusPublication statuses,
            InvoiceAlerts alerts, LeadershipFence fence, Clock clock) {
        this.locks = locks;
        this.claims = claims;
        this.statuses = statuses;
        this.alerts = alerts;
        this.fence = fence;
        this.clock = clock;
    }

    @Transactional
    public Optional<CheckoutRequest> start(UUID contractId) {
        LockedInvoice locked = locks.lockInvoice(contractId);
        fence.holdUntilCommit();
        return claims.claim(locked, clock.instant());
    }

    @Transactional
    public void issued(UUID contractId, InvoiceLink link) {
        LockedInvoice locked = locks.lockInvoice(contractId);
        fence.holdUntilCommit();
        Instant now = clock.instant();
        IssuanceSnapshot before = statuses.watch(locked.contract().state().request().individualRequestId());
        if (!locked.invoice().issue(link, now)) {
            PaymentOrderLog.write(log.atInfo(), locked.contract(),
                    "ответ на выставление счёта пришёл по счёту в состоянии " + locked.invoice().view().state());
            return;
        }
        invoiced(locked, now);
        PaymentOrderLog.write(log.atInfo(), locked.contract(), "счёт выставлен, invoiceId " + link.invoiceId());
        statuses.afterRecordChange(before, now);
    }

    @Transactional
    public void paymentAwaited(UUID contractId) {
        LockedInvoice locked = locks.lockInvoice(contractId);
        fence.holdUntilCommit();
        Instant now = clock.instant();
        if (locked.invoice().pass(InvoiceTransition.PAYMENT_AWAITED, now)) {
            invoiced(locked, now);
        }
    }

    @Transactional
    public void replyLost(UUID contractId) {
        LockedInvoice locked = locks.lockInvoice(contractId);
        fence.holdUntilCommit();
        Instant now = clock.instant();
        if (locked.invoice().pass(InvoiceTransition.REPLY_LOST, now)) {
            invoiced(locked, now);
            alerts.replyLost(locked.contract());
            return;
        }
        if (locked.invoice().pass(InvoiceTransition.REPLY_LOSS_REPORTED, now)) {
            alerts.replyLost(locked.contract());
        }
    }

    @Transactional
    public void rejected(UUID contractId, String code) {
        LockedInvoice locked = locks.lockInvoice(contractId);
        fence.holdUntilCommit();
        if (locked.invoice().reject(code, clock.instant())) {
            alerts.invoiceRejected(locked.contract(), code);
        }
    }

    private static void invoiced(LockedInvoice locked, Instant now) {
        locked.order().forgetPayer(now);
        if (locked.contract().status() == ContractStatus.ACCEPTED) {
            locked.contract().advance(ContractStep.INVOICE_ISSUED);
        }
    }
}
