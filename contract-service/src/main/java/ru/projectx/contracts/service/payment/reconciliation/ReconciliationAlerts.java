package ru.projectx.contracts.service.payment.reconciliation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import ru.projectx.contracts.entity.contract.Contract;
import ru.projectx.contracts.entity.payment.InvoiceView;
import ru.projectx.contracts.service.payment.PaymentAlert;
import ru.projectx.contracts.service.payment.PaymentMetrics;
import ru.projectx.contracts.service.payment.PaymentOrderLog;

@Component
class ReconciliationAlerts {

    static final String DOCUMENT_NOT_FIXED = "Ordering не подтвердил документ после повторов подтверждения";
    static final String PAYMENT_ERROR_OVERDUE = "после ошибки оплаты итог не пришёл к сроку оплаты: счёт считается просроченным";
    static final String STATE_UNAVAILABLE = "состояние заказа в Ordering получить не удалось";
    private static final String INVOICED = "со счётом";
    private static final String NOT_INVOICED = "без счёта";
    private static final Logger log = LoggerFactory.getLogger(ReconciliationAlerts.class);

    private final PaymentMetrics metrics;

    ReconciliationAlerts(PaymentMetrics metrics) {
        this.metrics = metrics;
    }

    void documentNotFixed(Contract contract) {
        raise(contract, PaymentAlert.DOCUMENT_NOT_FIXED, DOCUMENT_NOT_FIXED);
    }

    void paymentErrorOverdue(Contract contract) {
        raise(contract, PaymentAlert.PAYMENT_ERROR_OVERDUE, PAYMENT_ERROR_OVERDUE);
    }

    void stateUnavailable(Contract contract) {
        raise(contract, PaymentAlert.STATE_UNAVAILABLE, STATE_UNAVAILABLE);
    }

    void unexpectedState(Contract contract, InvoiceView invoice, String status) {
        raise(contract, PaymentAlert.UNEXPECTED_STATE, "состояние заказа " + status + " в Ordering к заказу "
                + (invoice.state().invoiced() ? INVOICED : NOT_INVOICED) + " не применимо");
    }

    private void raise(Contract contract, PaymentAlert alert, String reason) {
        metrics.countAlert(alert);
        PaymentOrderLog.write(log.atError(), contract, reason);
    }
}
