package ru.projectx.contracts.service.payment;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.stereotype.Component;
import ru.projectx.contracts.config.payment.PaymentSwitch;

@Component
@ConditionalOnBooleanProperty(PaymentSwitch.ENABLED)
public record OrderingCalls(Ordering ordering, OrderingAttempts attempts) {
}
