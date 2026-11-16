package ru.projectx.contracts.service.payment.cancellation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.projectx.contracts.config.payment.PaymentSwitch;
import ru.projectx.contracts.service.ScheduledPasses;

@Component
@ConditionalOnBooleanProperty(PaymentSwitch.ENABLED)
public class PaymentOrderCancellingScheduler {

    private static final Logger log = LoggerFactory.getLogger(PaymentOrderCancellingScheduler.class);

    private final PaymentOrderCancelling cancelling;

    public PaymentOrderCancellingScheduler(PaymentOrderCancelling cancelling) {
        this.cancelling = cancelling;
    }

    @Scheduled(fixedDelayString = "${projectx.payment-orders.cancellation-interval}")
    public void cancelDue() {
        ScheduledPasses.run(log, "отмены заказов на оплату", cancelling::cancelDue);
    }
}
