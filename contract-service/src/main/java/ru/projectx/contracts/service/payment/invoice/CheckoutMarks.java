package ru.projectx.contracts.service.payment.invoice;

import java.time.Duration;
import java.time.Instant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.stereotype.Component;
import ru.projectx.contracts.config.payment.PaymentOrderPassesProperties;
import ru.projectx.contracts.config.payment.PaymentSwitch;
import ru.projectx.contracts.entity.payment.InvoiceState;
import ru.projectx.contracts.entity.payment.InvoiceView;

@Component
@ConditionalOnBooleanProperty(PaymentSwitch.ENABLED)
public class CheckoutMarks {

    private final Duration abandonedAfter;

    public CheckoutMarks(PaymentOrderPassesProperties passes) {
        this.abandonedAfter = passes.reconciliation().threshold();
    }

    public boolean abandoned(InvoiceView invoice, Instant now) {
        return invoice.state() == InvoiceState.CHECKOUT && invoice.checkoutStartedBy(now.minus(abandonedAfter));
    }
}
