package ru.projectx.contracts.service.payment;

import ru.projectx.contracts.entity.payment.PaymentOrderState;
import ru.projectx.contracts.entity.payment.PaymentOrderView;

public interface PaymentOrderWork {

    PaymentOrderState state();

    OrderingStep<?> step(PaymentOrderView order);
}
