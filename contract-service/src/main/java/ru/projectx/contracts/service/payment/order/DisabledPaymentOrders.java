package ru.projectx.contracts.service.payment.order;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.stereotype.Service;
import ru.projectx.contracts.config.payment.PaymentSwitch;
import ru.projectx.contracts.service.payment.OrderOpening;
import ru.projectx.contracts.service.payment.PaymentOrderOpening;

@Service
@ConditionalOnBooleanProperty(name = PaymentSwitch.ENABLED, havingValue = false)
public class DisabledPaymentOrders implements PaymentOrderOpening {

    private static final Logger log = LoggerFactory.getLogger(DisabledPaymentOrders.class);

    @Override
    public OrderOpening forPayer(@Nullable String email) {
        return (contract, offer, now) ->
                log.debug("оплата выключена: заказ на оплату договора КМ {} не заводится", contract.id());
    }
}
