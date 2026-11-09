package ru.projectx.contracts.service.payment.cancellation;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.stereotype.Service;
import ru.projectx.contracts.config.payment.PaymentSwitch;
import ru.projectx.contracts.entity.payment.PaymentOrderState;
import ru.projectx.contracts.entity.payment.PaymentOrderView;
import ru.projectx.contracts.service.payment.OrderingSession;
import ru.projectx.contracts.service.payment.OrderingStep;
import ru.projectx.contracts.service.payment.PaymentOrderPasses;
import ru.projectx.contracts.service.payment.PaymentOrderWork;

@Service
@ConditionalOnBooleanProperty(PaymentSwitch.ENABLED)
public class PaymentOrderCancelling implements PaymentOrderWork {

    static final String CANCELED = "CANCELED";

    private static final Logger log = LoggerFactory.getLogger(PaymentOrderCancelling.class);

    private final PaymentOrderPasses passes;
    private final PaymentOrderCancelRecording recording;

    public PaymentOrderCancelling(PaymentOrderPasses passes, PaymentOrderCancelRecording recording) {
        this.passes = passes;
        this.recording = recording;
    }

    public int cancelDue() {
        return passes.run(this);
    }

    @Override
    public PaymentOrderState state() {
        return PaymentOrderState.CANCEL_PENDING;
    }

    @Override
    public OrderingStep<?> step(PaymentOrderView order) {
        AtomicBoolean requested = new AtomicBoolean(order.cancelRequested());
        return new OrderingStep<>("не отменён",
                session -> attempt(session, order, requested),
                result -> record(order, result),
                code -> recording.rejected(order.contractId(), code));
    }

    private void record(PaymentOrderView order, CancelResult result) {
        switch (result) {
            case CancelResult.Cancelled cancelled -> recording.cancelled(order.contractId(), cancelled.orderId());
            case CancelResult.NotFound notFound -> recording.notFound(order.contractId());
            case CancelResult.Skipped skipped -> log.info("заказ на оплату договора КМ {} уже не ждёт отмены",
                    order.contractId());
        }
    }

    private CancelResult attempt(OrderingSession session, PaymentOrderView order, AtomicBoolean requested) {
        Optional<String> orderId = order.orderId().or(() -> session.findOrder(order.clientId(), order.id()));
        return orderId.map(known -> cancel(session, order, known, requested))
                .orElseGet(CancelResult.NotFound::new);
    }

    private static boolean alreadyCancelled(OrderingSession session, String orderId, AtomicBoolean requested) {
        return requested.get() && CANCELED.equals(session.orderStatus(orderId));
    }

    private CancelResult cancel(OrderingSession session, PaymentOrderView order, String orderId,
            AtomicBoolean requested) {
        if (alreadyCancelled(session, orderId, requested)) {
            return new CancelResult.Cancelled(orderId);
        }
        if (!recording.requestCancellation(order.contractId(), orderId)) {
            return new CancelResult.Skipped();
        }
        requested.set(true);
        session.cancelOrder(orderId);
        return new CancelResult.Cancelled(orderId);
    }
}
