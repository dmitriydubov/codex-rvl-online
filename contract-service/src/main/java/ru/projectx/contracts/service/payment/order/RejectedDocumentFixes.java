package ru.projectx.contracts.service.payment.order;

import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import ru.projectx.contracts.entity.payment.PaymentOrder;
import ru.projectx.contracts.message.payment.PaymentOrderEvents;
import ru.projectx.contracts.repository.payment.PaymentOrderRepository;
import ru.projectx.contracts.service.outbox.RejectedOutboxMessages;
import ru.projectx.contracts.service.payment.LockedOrder;
import ru.projectx.contracts.service.payment.OrderingMetrics;
import ru.projectx.contracts.service.payment.PaymentOrderLocks;
import ru.projectx.contracts.service.payment.PaymentOrderLog;

@Component
public class RejectedDocumentFixes implements RejectedOutboxMessages {

    static final String FIXED_UNDELIVERED = "FIXED_UNDELIVERED";

    private static final Logger log = LoggerFactory.getLogger(RejectedDocumentFixes.class);

    private final PaymentOrderRepository orders;
    private final PaymentOrderLocks locks;
    private final OrderingMetrics metrics;

    public RejectedDocumentFixes(PaymentOrderRepository orders, PaymentOrderLocks locks, OrderingMetrics metrics) {
        this.orders = orders;
        this.locks = locks;
        this.metrics = metrics;
    }

    @Override
    public String aggregateType() {
        return PaymentOrderEvents.AGGREGATE_TYPE;
    }

    @Override
    public void rejected(UUID aggregateId, Instant now) {
        PaymentOrder order = orders.findById(aggregateId)
                .orElseThrow(() -> new IllegalStateException("заказа на оплату " + aggregateId + " нет"));
        PaymentOrderLog.withCorrelation(order.view().correlationId(), () -> reject(order.view().contractId(), now));
    }

    private boolean reject(UUID contractId, Instant now) {
        LockedOrder locked = locks.lock(contractId);
        boolean rejected = locked.order().reject(FIXED_UNDELIVERED, now);
        if (rejected) {
            metrics.countOrderRejected();
            PaymentOrderLog.write(log.atError(), locked.contract(), "подтверждение документа не доставлено");
        }
        return rejected;
    }
}
