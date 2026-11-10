package ru.projectx.contracts.service.payment.invoice;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.stereotype.Component;
import ru.projectx.contracts.config.payment.PaymentOrderPassesProperties;
import ru.projectx.contracts.config.payment.PaymentSwitch;

@Component
@ConditionalOnBooleanProperty(PaymentSwitch.ENABLED)
public class PaymentTerms {

    private final Duration term;
    private final Duration minimum;

    public PaymentTerms(PaymentOrderPassesProperties passes) {
        this.term = passes.invoices().paymentTerm();
        this.minimum = passes.invoices().minPaymentTerm();
    }

    public Optional<Instant> deadline(Instant now, Instant boundary) {
        if (Duration.between(now, boundary).compareTo(minimum) < 0) {
            return Optional.empty();
        }
        Instant byTerm = now.plus(term);
        return Optional.of(byTerm.isBefore(boundary) ? byTerm : boundary);
    }

    public boolean boundaryPassed(Instant now, Instant boundary) {
        return now.isAfter(boundary);
    }
}
