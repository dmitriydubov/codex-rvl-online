package ru.projectx.contracts.service.payment;

import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;
import ru.projectx.contracts.config.payment.PaymentOrderPassesProperties;
import ru.projectx.contracts.config.payment.PaymentSwitch;
import ru.projectx.contracts.entity.payment.PaymentOrder;
import ru.projectx.contracts.entity.payment.PaymentOrderState;
import ru.projectx.contracts.entity.payment.PaymentOrderView;
import ru.projectx.contracts.exception.OrderingRejectedException;
import ru.projectx.contracts.exception.OrderingUnavailableException;
import ru.projectx.contracts.repository.payment.PaymentOrderRepository;
import ru.projectx.contracts.service.leadership.BackgroundLeadership;

@Component
@ConditionalOnBooleanProperty(PaymentSwitch.ENABLED)
public class PaymentOrderPasses {

    private static final Logger log = LoggerFactory.getLogger(PaymentOrderPasses.class);

    private final PaymentOrderRepository orders;
    private final BackgroundLeadership leadership;
    private final Ordering ordering;
    private final OrderingAttempts attempts;
    private final Limit batch;

    public PaymentOrderPasses(PaymentOrderRepository orders, BackgroundLeadership leadership, OrderingCalls calls,
            PaymentOrderPassesProperties passes) {
        this.orders = orders;
        this.leadership = leadership;
        this.ordering = calls.ordering();
        this.attempts = calls.attempts();
        this.batch = Limit.of(passes.batchSize());
    }

    public <T> void callOrdering(PaymentOrderView order, OrderingStep<T> step) {
        try {
            step.succeeded().accept(attempts.run(() -> step.attempt().apply(ordering.session(order.correlationId()))));
        }
        catch (OrderingRejectedException rejected) {
            step.rejected().accept(rejected.code());
        }
        catch (OrderingUnavailableException unavailable) {
            log.warn("заказ на оплату договора КМ {} {}: {}, заказ ждёт следующего прохода", order.contractId(),
                    step.notDone(), unavailable.getMessage());
        }
    }

    public int eachAsLeader(PaymentOrderState state, Consumer<PaymentOrderView> work) {
        return leadership.countAsLeader(() -> each(state, work));
    }

    private int each(PaymentOrderState state, Consumer<PaymentOrderView> work) {
        int handled = 0;
        for (PaymentOrder order : orders.findByStateOrderByCreatedAtAscIdAsc(state, batch)) {
            PaymentOrderView view = order.view();
            PaymentOrderLog.withCorrelation(view.correlationId(), () -> work.accept(view));
            handled++;
        }
        return handled;
    }
}
