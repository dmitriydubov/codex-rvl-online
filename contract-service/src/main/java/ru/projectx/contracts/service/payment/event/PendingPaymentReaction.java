package ru.projectx.contracts.service.payment.event;

import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import ru.projectx.contracts.config.payment.PaymentSwitch;
import ru.projectx.contracts.entity.payment.InvoiceState;
import ru.projectx.contracts.entity.payment.OrderInvoice;
import ru.projectx.contracts.service.payment.invoice.InvoiceRecording;

@Component
@ConditionalOnBooleanProperty(PaymentSwitch.ENABLED)
class PendingPaymentReaction implements OrderEventReaction {

    static final String EVENT_TYPE = "pendingPayment";

    private final InvoiceRecording recording;

    PendingPaymentReaction(InvoiceRecording recording) {
        this.recording = recording;
    }

    @Override
    public String eventType() {
        return EVENT_TYPE;
    }

    @Override
    public Optional<Ignored> react(EventTarget target, OrderEvent event, Instant now) {
        Optional<InvoiceState> invoice = target.invoice().map(OrderInvoice::view).map(view -> view.state());
        if (invoice.filter(InvoiceState.CHECKOUT::equals).isPresent()) {
            recording.replyLost(target.locked().contract().id());
            return Optional.empty();
        }
        if (invoice.filter(InvoiceState.ISSUED::equals).isPresent()) {
            return Optional.of(Ignored.notApplicable(eventType()));
        }
        return Optional.of(Ignored.outOfOrder(eventType(), target.standing()));
    }
}
