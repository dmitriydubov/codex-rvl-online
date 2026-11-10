package ru.projectx.contracts.service.payment.reconciliation;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import ru.projectx.contracts.config.payment.PaymentSwitch;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.projectx.contracts.message.payment.PaymentOrderEvents;
import ru.projectx.contracts.service.payment.OrderingStatuses;
import ru.projectx.contracts.service.payment.invoice.PaymentOutcomes;

@Configuration(proxyBeanMethods = false)
@ConditionalOnBooleanProperty(PaymentSwitch.ENABLED)
class OutcomeReconcilers {

    @Bean
    StateReconciler paidOrderReconciler(PaymentOutcomes outcomes, ReconciliationAlerts alerts) {
        return new OutcomeReconciler(OrderingStatuses.PAID, PaymentOrderEvents.PAID, outcomes::paid, alerts);
    }

    @Bean
    StateReconciler overdueOrderReconciler(PaymentOutcomes outcomes, ReconciliationAlerts alerts) {
        return new OutcomeReconciler(OrderingStatuses.PAYMENT_OVERDUE, PaymentOrderEvents.PAYMENT_OVERDUE,
                outcomes::overdue, alerts);
    }
}
