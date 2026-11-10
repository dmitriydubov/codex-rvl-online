package ru.projectx.contracts.service.payment.reconciliation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.projectx.contracts.config.payment.PaymentSwitch;
import ru.projectx.contracts.service.ScheduledPasses;
import ru.projectx.contracts.service.payment.invoice.InvoiceIssuing;
import ru.projectx.contracts.service.payment.invoice.InvoiceSummaries;

@Component
@ConditionalOnBooleanProperty(PaymentSwitch.ENABLED)
public class PaymentInvoiceScheduler {

    private static final Logger log = LoggerFactory.getLogger(PaymentInvoiceScheduler.class);

    private final InvoiceIssuing issuing;
    private final PaymentReconciliation reconciliation;
    private final InvoiceSummaries summaries;

    public PaymentInvoiceScheduler(InvoiceIssuing issuing, PaymentReconciliation reconciliation,
            InvoiceSummaries summaries) {
        this.issuing = issuing;
        this.reconciliation = reconciliation;
        this.summaries = summaries;
    }

    @Scheduled(fixedDelayString = "${projectx.payment-orders.invoices.checkout-interval}")
    public void issueDue() {
        ScheduledPasses.run(log, "выставления счетов", issuing::issueDue);
    }

    @Scheduled(fixedDelayString = "${projectx.payment-orders.reconciliation.interval}")
    public void reconcileDue() {
        ScheduledPasses.run(log, "сверки заказов на оплату", reconciliation::reconcileDue);
    }

    @Scheduled(fixedDelayString = "${projectx.payment-orders.reconciliation.summary-interval}")
    public void refreshSummaries() {
        ScheduledPasses.run(log, "сводки счетов и оплат", summaries::refresh);
    }
}
