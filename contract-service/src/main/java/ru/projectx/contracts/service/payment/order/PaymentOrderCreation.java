package ru.projectx.contracts.service.payment.order;

import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.stereotype.Service;
import ru.projectx.contracts.config.payment.PaymentSwitch;
import ru.projectx.contracts.entity.payment.PaymentOrderState;
import ru.projectx.contracts.entity.payment.PaymentOrderView;
import ru.projectx.contracts.service.payment.OrderingStep;
import ru.projectx.contracts.service.payment.OrderingStepRunner;
import ru.projectx.contracts.service.payment.PaymentOrderPasses;
import ru.projectx.contracts.service.payment.PaymentOrderWork;
import ru.projectx.contracts.service.payment.invoice.PaymentTerms;

@Service
@ConditionalOnBooleanProperty(PaymentSwitch.ENABLED)
public class PaymentOrderCreation implements PaymentOrderWork {

    private final PaymentOrderPasses passes;
    private final PaymentOrderRecording recording;

    private final OrderingStepRunner steps;
    private final PaymentTerms terms;
    private final Clock clock;

    public PaymentOrderCreation(PaymentOrderPasses passes, PaymentOrderRecording recording, OrderingStepRunner steps,
            PaymentTerms terms, Clock clock) {
        this.passes = passes;
        this.recording = recording;
        this.steps = steps;
        this.terms = terms;
        this.clock = clock;
    }

    public int createDue() {
        return passes.eachAsLeader(state(), this::createUnlessExpired);
    }

    private boolean createUnlessExpired(PaymentOrderView order) {
        if (terms.boundaryPassed(clock.instant(), order.document().documentTtl())) {
            recording.expired(order.contractId());
            return true;
        }
        return steps.run(order, step(order));
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
