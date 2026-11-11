package ru.projectx.contracts.service.payment.order;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.projectx.contracts.config.payment.PaymentSwitch;
import ru.projectx.contracts.entity.payment.InvoiceTransition;
import ru.projectx.contracts.entity.payment.OrderInvoice;
import ru.projectx.contracts.entity.payment.OrderRequest;
import ru.projectx.contracts.entity.payment.PaymentOrderState;
import ru.projectx.contracts.repository.payment.OrderInvoiceRepository;
import ru.projectx.contracts.service.leadership.LeadershipFence;
import ru.projectx.contracts.service.payment.LockedOrder;
import ru.projectx.contracts.service.payment.OrderingMetrics;
import ru.projectx.contracts.service.payment.PaymentOrderLocks;
import ru.projectx.contracts.service.payment.PaymentOrderLog;
import ru.projectx.contracts.service.payment.invoice.PaymentTermExpiry;

@Service
@ConditionalOnBooleanProperty(PaymentSwitch.ENABLED)
public class PaymentOrderRecording {

    private static final Logger log = LoggerFactory.getLogger(PaymentOrderRecording.class);

    private final PaymentOrderLocks locks;
    private final DocumentFixes fixes;
    private final OrderingMetrics metrics;
    private final LeadershipFence fence;
    private final Clock clock;

    private final OrderInvoiceRepository invoices;
    private final PaymentTermExpiry expiry;

    public PaymentOrderRecording(PaymentOrderLocks locks, DocumentFixes fixes, OrderInvoiceRepository invoices,
            PaymentTermExpiry expiry, OrderingMetrics metrics, LeadershipFence fence, Clock clock) {
        this.locks = locks;
        this.fixes = fixes;
        this.invoices = invoices;
        this.expiry = expiry;
        this.metrics = metrics;
        this.fence = fence;
        this.clock = clock;
    }

    @Transactional
    public CreationClaim requestCreation(UUID contractId) {
        LockedOrder locked = locks.lock(contractId);
        if (locked.order().view().state() != PaymentOrderState.AWAITING_CREATION) {
            return CreationClaim.WITHDRAWN;
        }
        fence.holdUntilCommit();
        CreationClaim claim = locked.order().view().creationRequested() ? CreationClaim.REPEATED : CreationClaim.FIRST;
        locked.order().markRequested(OrderRequest.CREATION, clock.instant());
        return claim;
    }

    @Transactional
    public void created(UUID contractId, String orderId) {
        LockedOrder locked = locks.lock(contractId);
        fence.holdUntilCommit();
        Instant now = clock.instant();
        if (locked.order().recordOrder(orderId, now)) {
            openInvoice(locked, now);
            fixes.record(locked.contract(), locked.order(), now);
            PaymentOrderLog.write(log.atInfo(), locked.contract(), "заказ создан в Ordering, orderId " + orderId);
            return;
        }
        PaymentOrderLog.write(log.atInfo(), locked.contract(),
                "Ordering вернул orderId " + orderId + " по заказу в состоянии " + locked.order().view().state());
    }

    private void openInvoice(LockedOrder locked, Instant now) {
        UUID contractId = locked.contract().id();
        invoices.findFreshByContractId(contractId).ifPresentOrElse(
                known -> known.pass(InvoiceTransition.DOCUMENT_RESENT, now),
                () -> invoices.save(OrderInvoice.open(locked.order().view().id(), contractId, now)));
    }

    @Transactional
    public void expired(UUID contractId) {
        LockedOrder locked = locks.lock(contractId);
        fence.holdUntilCommit();
        if (locked.order().view().state() == PaymentOrderState.AWAITING_CREATION) {
            expiry.close(locked, PaymentTermExpiry.BEFORE_ORDER, clock.instant());
        }
    }

    @Transactional
    public void rejected(UUID contractId, String code) {
        LockedOrder locked = locks.lock(contractId);
        fence.holdUntilCommit();
        if (locked.order().reject(code, clock.instant())) {
            metrics.countOrderRejected();
            PaymentOrderLog.write(log.atError(), locked.contract(), "Ordering отклонил создание заказа, код " + code);
            return;
        }
        if (locked.order().view().state() == PaymentOrderState.CANCEL_PENDING) {
            PaymentOrderLog.write(log.atInfo(), locked.contract(),
                    "Ordering отклонил создание заказа к отмене, код " + code + ": запись ждёт поиска при отмене");
        }
    }
}
