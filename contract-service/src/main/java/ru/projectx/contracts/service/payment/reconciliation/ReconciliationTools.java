package ru.projectx.contracts.service.payment.reconciliation;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.stereotype.Component;
import ru.projectx.contracts.config.payment.PaymentSwitch;
import ru.projectx.contracts.service.payment.invoice.PaymentTermExpiry;
import ru.projectx.contracts.service.payment.invoice.PaymentTerms;

@Component
@ConditionalOnBooleanProperty(PaymentSwitch.ENABLED)
record ReconciliationTools(PaymentTerms terms, PaymentTermExpiry expiry, ReconciliationAlerts alerts) {
}
