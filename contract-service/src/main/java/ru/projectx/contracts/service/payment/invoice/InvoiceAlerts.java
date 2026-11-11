package ru.projectx.contracts.service.payment.invoice;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import ru.projectx.contracts.entity.contract.Contract;
import ru.projectx.contracts.service.payment.OrderingMetrics;
import ru.projectx.contracts.service.payment.PaymentAlert;
import ru.projectx.contracts.service.payment.PaymentMetrics;
import ru.projectx.contracts.service.payment.PaymentOrderLog;

@Component
public class InvoiceAlerts {

    static final String REPLY_LOST = "ответ на выставление счёта потерян: ссылки на оплату нет";
    static final String INVOICE_REJECTED = "Ordering отклонил выставление счёта, код ";
    private static final Logger log = LoggerFactory.getLogger(InvoiceAlerts.class);

    private final PaymentMetrics payments;
    private final OrderingMetrics ordering;

    public InvoiceAlerts(PaymentMetrics payments, OrderingMetrics ordering) {
        this.payments = payments;
        this.ordering = ordering;
    }

    public void replyLost(Contract contract) {
        payments.countAlert(PaymentAlert.CHECKOUT_REPLY_LOST);
        PaymentOrderLog.write(log.atError(), contract, REPLY_LOST);
    }

    public void invoiceRejected(Contract contract, String code) {
        ordering.countOrderRejected();
        PaymentOrderLog.write(log.atError(), contract, INVOICE_REJECTED + code);
    }
}
