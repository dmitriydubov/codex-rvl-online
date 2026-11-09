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
import ru.projectx.contracts.entity.payment.PaymentOrderState;
import ru.projectx.contracts.service.leadership.LeadershipFence;
import ru.projectx.contracts.service.outbox.OutboxJournal;
import ru.projectx.contracts.service.payment.LockedOrder;
import ru.projectx.contracts.service.payment.OrderingMetrics;
import ru.projectx.contracts.service.payment.PaymentOrderLocks;
import ru.projectx.contracts.service.payment.PaymentOrderLog;

@Service
@ConditionalOnBooleanProperty(PaymentSwitch.ENABLED)
public class PaymentOrderRecording {

    private static final Logger log = LoggerFactory.getLogger(PaymentOrderRecording.class);

    private final PaymentOrderLocks locks;
    private final DocumentFixMessages messages;
    private final OutboxJournal journal;
    private final OrderingMetrics metrics;
    private final LeadershipFence fence;
    private final Clock clock;

    public PaymentOrderRecording(PaymentOrderLocks locks, DocumentFixMessages messages, OutboxJournal journal,
            OrderingMetrics metrics, LeadershipFence fence, Clock clock) {
        this.locks = locks;
        this.messages = messages;
        this.journal = journal;
        this.metrics = metrics;
        this.fence = fence;
        this.clock = clock;
    }

    @Transactional
    public CreationClaim requestCreation(UUID contractId) {
        LockedOrder locked = locks.lock(contractId);
        if (locked.order().state() != PaymentOrderState.AWAITING_CREATION) {
            return CreationClaim.WITHDRAWN;
        }
        fence.holdUntilCommit();
        CreationClaim claim = locked.order().view().creationRequested() ? CreationClaim.REPEATED : CreationClaim.FIRST;
        locked.order().requestCreation(clock.instant());
        return claim;
    }

    @Transactional
    public void created(UUID contractId, String orderId) {
        LockedOrder locked = locks.lock(contractId);
        fence.holdUntilCommit();
        Instant now = clock.instant();
        if (locked.order().recordOrder(orderId, now)) {
            journal.record(messages.draft(locked.contract(), locked.order(), now), now);
            PaymentOrderLog.write(log.atInfo(), locked.contract(), "заказ создан в Ordering, orderId " + orderId);
            return;
        }
        PaymentOrderLog.write(log.atInfo(), locked.contract(),
                "Ordering вернул orderId " + orderId + " по заказу в состоянии " + locked.order().state());
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
        if (locked.order().state() == PaymentOrderState.CANCEL_PENDING) {
            PaymentOrderLog.write(log.atInfo(), locked.contract(),
                    "Ordering отклонил создание заказа к отмене, код " + code + ": запись ждёт поиска при отмене");
        }
    }
}
