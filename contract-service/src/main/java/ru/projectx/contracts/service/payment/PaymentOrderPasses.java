package ru.projectx.contracts.service.payment;

import java.util.function.Predicate;
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
import ru.projectx.contracts.repository.payment.PaymentOrderRepository;
import ru.projectx.contracts.service.database.SqlStates;
import ru.projectx.contracts.service.database.TemporaryFailures;
import ru.projectx.contracts.service.leadership.BackgroundLeadership;
import ru.projectx.contracts.service.leadership.LeadershipLostException;

@Component
@ConditionalOnBooleanProperty(PaymentSwitch.ENABLED)
public class PaymentOrderPasses {

    private static final Logger log = LoggerFactory.getLogger(PaymentOrderPasses.class);

    private final PaymentOrderRepository orders;
    private final BackgroundLeadership leadership;
    private final OrderingStepRunner steps;
    private final Limit batch;

    public PaymentOrderPasses(PaymentOrderRepository orders, BackgroundLeadership leadership, OrderingStepRunner steps,
            PaymentOrderPassesProperties passes) {
        this.orders = orders;
        this.leadership = leadership;
        this.steps = steps;
        this.batch = Limit.of(passes.batchSize());
    }

    public int run(PaymentOrderWork work) {
        return eachAsLeader(work.state(), order -> steps.run(order, work.step(order)));
    }

    public int eachAsLeader(PaymentOrderState state, Predicate<PaymentOrderView> work) {
        return leadership.countAsLeader(() -> each(state, work));
    }

    private int each(PaymentOrderState state, Predicate<PaymentOrderView> work) {
        int handled = 0;
        for (PaymentOrder order : orders.findByStateOrderByCreatedAtAscIdAsc(state, batch)) {
            handled++;
            if (!handle(order.view(), work)) {
                break;
            }
        }
        return handled;
    }

    private static boolean handle(PaymentOrderView order, Predicate<PaymentOrderView> work) {
        try {
            return PaymentOrderLog.withCorrelation(order.correlationId(), () -> work.test(order));
        }
        catch (LeadershipLostException lost) {
            throw lost;
        }
        catch (RuntimeException failure) {
            log.warn("заказ на оплату договора КМ {} не обработан: {}, SQLSTATE {}", order.contractId(),
                    failure.getClass().getName(), SqlStates.deepestOrNone(failure));
            return !TemporaryFailures.temporary(failure);
        }
    }
}
