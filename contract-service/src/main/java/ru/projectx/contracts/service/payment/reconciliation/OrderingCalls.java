package ru.projectx.contracts.service.payment.reconciliation;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.stereotype.Component;
import ru.projectx.contracts.config.payment.PaymentSwitch;
import ru.projectx.contracts.service.payment.Ordering;
import ru.projectx.contracts.service.payment.OrderingAttempts;
import ru.projectx.contracts.service.payment.invoice.InvoiceIssuing;

@Component
@ConditionalOnBooleanProperty(PaymentSwitch.ENABLED)
record OrderingCalls(Ordering ordering, OrderingAttempts attempts, InvoiceIssuing issuing) {
}
