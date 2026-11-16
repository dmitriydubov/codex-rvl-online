package ru.projectx.contracts.service.payment.order;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.projectx.contracts.config.payment.PaymentSwitch;
import ru.projectx.contracts.service.ScheduledPasses;

@Component
@ConditionalOnBooleanProperty(PaymentSwitch.ENABLED)
public class PaymentOrderScheduler {

    private static final Logger log = LoggerFactory.getLogger(PaymentOrderScheduler.class);

    private final PaymentOrderCreation creation;
    private final PaymentOrderAwaiting awaiting;

    public PaymentOrderScheduler(PaymentOrderCreation creation, PaymentOrderAwaiting awaiting) {
        this.creation = creation;
        this.awaiting = awaiting;
    }

    @Scheduled(fixedDelayString = "${projectx.payment-orders.creation-interval}")
    public void createDue() {
        ScheduledPasses.run(log, "создания заказов на оплату", creation::createDue);
    }

    @Scheduled(fixedDelayString = "${projectx.payment-orders.awaiting-interval}")
    public void refreshAwaiting() {
        ScheduledPasses.run(log, "сводки договоров КМ без заказа на оплату", awaiting::refresh);
    }
}
