package ru.projectx.contracts.service.payment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.stereotype.Component;
import ru.projectx.contracts.config.payment.PaymentSwitch;
import ru.projectx.contracts.entity.payment.PaymentOrderView;
import ru.projectx.contracts.exception.OrderingRejectedException;
import ru.projectx.contracts.exception.OrderingUnavailableException;

@Component
@ConditionalOnBooleanProperty(PaymentSwitch.ENABLED)
public class OrderingStepRunner {

    private static final Logger log = LoggerFactory.getLogger(OrderingStepRunner.class);

    private final Ordering ordering;
    private final OrderingAttempts attempts;

    public OrderingStepRunner(Ordering ordering, OrderingAttempts attempts) {
        this.ordering = ordering;
        this.attempts = attempts;
    }

    public <T> boolean run(PaymentOrderView order, OrderingStep<T> step) {
        try {
            step.succeeded().accept(attempts.run(() -> step.attempt().apply(ordering.session(order.correlationId()))));
        }
        catch (OrderingRejectedException rejected) {
            step.rejected().accept(rejected.code());
        }
        catch (OrderingUnavailableException unavailable) {
            log.warn("заказ на оплату договора КМ {} {}: {}, {}", order.contractId(), step.notDone(),
                    unavailable.getMessage(), consequence(unavailable));
            return !unavailable.serviceWide();
        }
        return true;
    }

    private static String consequence(OrderingUnavailableException unavailable) {
        return unavailable.serviceWide() ? "проход остановлен до следующего раза" : "заказ ждёт следующего прохода";
    }
}
