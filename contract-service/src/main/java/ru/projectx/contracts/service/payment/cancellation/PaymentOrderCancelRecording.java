package ru.projectx.contracts.service.payment.cancellation;

import ru.projectx.contracts.entity.payment.OrderRequest;
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
import ru.projectx.contracts.service.payment.LockedOrder;
import ru.projectx.contracts.service.payment.OrderingMetrics;
import ru.projectx.contracts.service.payment.PaymentOrderLocks;
import ru.projectx.contracts.service.payment.PaymentOrderLog;

@Service
@ConditionalOnBooleanProperty(PaymentSwitch.ENABLED)
public class PaymentOrderCancelRecording {

    private static final Logger log = LoggerFactory.getLogger(PaymentOrderCancelRecording.class);

    private final PaymentOrderLocks locks;
    private final OrderingMetrics metrics;
    private final LeadershipFence fence;
    private final Clock clock;

    public PaymentOrderCancelRecording(PaymentOrderLocks locks, OrderingMetrics metrics, LeadershipFence fence,
            Clock clock) {
        this.locks = locks;
        this.metrics = metrics;
        this.fence = fence;
        this.clock = clock;
    }

    @Transactional
    public boolean requestCancellation(UUID contractId, String orderId) {
        LockedOrder locked = pending(contractId);
        if (locked.order().view().state() != PaymentOrderState.CANCEL_PENDING) {
            return false;
        }
        Instant now = clock.instant();
        locked.order().recordOrder(orderId, now);
        locked.order().markRequested(OrderRequest.CANCELLATION, now);
        return true;
    }

    @Transactional
    public void cancelled(UUID contractId, String orderId) {
        LockedOrder locked = pending(contractId);
        if (locked.order().recordCancelled(orderId, clock.instant())) {
            PaymentOrderLog.write(log.atInfo(), locked.contract(), "заказ отменён в Ordering, orderId " + orderId);
        }
    }

    @Transactional
    public void notFound(UUID contractId) {
        LockedOrder locked = pending(contractId);
        if (locked.order().recordNotFound(clock.instant())) {
            PaymentOrderLog.write(log.atInfo(), locked.contract(),
                    "заказ в Ordering не найден, запись закрыта без отмены");
            return;
        }
        locked.order().view().orderId()
                .filter(orderId -> locked.order().view().state() == PaymentOrderState.CANCEL_PENDING)
                .ifPresent(orderId -> PaymentOrderLog.write(log.atInfo(), locked.contract(),
                        "заказ получил orderId " + orderId + ", пока шёл поиск: отменится следующим проходом"));
    }

    @Transactional
    public void rejected(UUID contractId, String code) {
        LockedOrder locked = pending(contractId);
        if (locked.order().rejectCancellation(code, clock.instant())) {
            metrics.countCancellationRejected();
            PaymentOrderLog.write(log.atError(), locked.contract(), "Ordering отклонил отмену заказа, код " + code);
        }
    }

    private LockedOrder pending(UUID contractId) {
        LockedOrder locked = locks.lock(contractId);
        fence.holdUntilCommit();
        return locked;
    }
}
