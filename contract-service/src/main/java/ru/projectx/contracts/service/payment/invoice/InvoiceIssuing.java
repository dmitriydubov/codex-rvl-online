package ru.projectx.contracts.service.payment.invoice;

import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.stereotype.Service;
import ru.projectx.contracts.config.payment.PaymentSwitch;
import ru.projectx.contracts.entity.payment.InvoiceState;
import ru.projectx.contracts.entity.payment.PaymentOrderState;
import ru.projectx.contracts.entity.payment.PaymentOrderView;
import ru.projectx.contracts.service.payment.CheckoutRequest;
import ru.projectx.contracts.service.payment.OrderingStep;
import ru.projectx.contracts.service.payment.OrderingStepRunner;
import ru.projectx.contracts.service.payment.PaymentOrderPasses;

@Service
@ConditionalOnBooleanProperty(PaymentSwitch.ENABLED)
public class InvoiceIssuing {

    private final PaymentOrderPasses passes;
    private final OrderingStepRunner steps;
    private final InvoiceRecording recording;

    public InvoiceIssuing(PaymentOrderPasses passes, OrderingStepRunner steps, InvoiceRecording recording) {
        this.passes = passes;
        this.steps = steps;
        this.recording = recording;
    }

    public int issueDue() {
        return passes.eachWithInvoice(PaymentOrderState.CREATED, InvoiceState.READY, this::issue);
    }

    public boolean issue(PaymentOrderView order) {
        Optional<CheckoutRequest> claimed = recording.start(order.contractId());
        return claimed.map(request -> steps.run(order, step(order, request))).orElse(true);
    }

    private OrderingStep<CheckoutOutcome> step(PaymentOrderView order, CheckoutRequest request) {
        CheckoutAttempt attempt = new CheckoutAttempt(request);
        return new OrderingStep<>("счёт не выставлен", attempt::apply,
                outcome -> outcome.recordTo(recording, order.contractId()),
                code -> recording.rejected(order.contractId(), code));
    }
}
