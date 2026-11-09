package ru.projectx.contracts.service.payment.order;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.stereotype.Service;
import ru.projectx.contracts.config.payment.PaymentSwitch;
import ru.projectx.contracts.entity.payment.PaymentOrderState;
import ru.projectx.contracts.entity.payment.PaymentOrderView;
import ru.projectx.contracts.service.payment.OrderingStep;
import ru.projectx.contracts.service.payment.PaymentOrderPasses;
import ru.projectx.contracts.service.payment.PaymentOrderWork;

@Service
@ConditionalOnBooleanProperty(PaymentSwitch.ENABLED)
public class PaymentOrderCreation implements PaymentOrderWork {

    private final PaymentOrderPasses passes;
    private final PaymentOrderRecording recording;

    public PaymentOrderCreation(PaymentOrderPasses passes, PaymentOrderRecording recording) {
        this.passes = passes;
        this.recording = recording;
    }

    public int createDue() {
        return passes.run(this);
    }

    @Override
    public OrderingStep<?> step(PaymentOrderView order) {
        CreationAttempt attempt = new CreationAttempt(order, recording);
        return new OrderingStep<>("не создан", attempt::apply,
                created -> created.ifPresent(orderId -> recording.created(order.contractId(), orderId)),
                code -> recording.rejected(order.contractId(), code));
    }

    @Override
    public PaymentOrderState state() {
        return PaymentOrderState.AWAITING_CREATION;
    }
}
