package ru.projectx.contracts.service.payment.invoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import ch.qos.logback.classic.Level;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import ru.projectx.contracts.service.payment.Invoices;
import ru.projectx.contracts.service.payment.OrderingMetrics;
import ru.projectx.contracts.service.payment.PaymentAlert;
import ru.projectx.contracts.service.payment.PaymentMetrics;
import ru.projectx.contracts.support.CapturedLog;
import ru.projectx.contracts.support.PaymentOrders;

class InvoiceAlertsTest {

    private final PaymentMetrics payments = mock();
    private final OrderingMetrics ordering = mock();
    private final InvoiceAlerts alerts = new InvoiceAlerts(payments, ordering);
    private final CapturedLog log = CapturedLog.of(InvoiceAlerts.class);

    @AfterEach
    void releaseLog() {
        log.close();
    }

    @Test
    void lostReply_isCountedAsAnAlertAndLoggedAsError() {
        alerts.replyLost(PaymentOrders.acceptedContract());

        verify(payments).countAlert(PaymentAlert.CHECKOUT_REPLY_LOST);
        verifyNoMoreInteractions(payments);
        verifyNoInteractions(ordering);
        assertThat(log.at(Level.ERROR))
                .containsExactly(Invoices.logLine("ответ на выставление счёта потерян: ссылки на оплату нет"));
        assertThat(log.events()).hasSize(1);
    }

    @Test
    void rejectedInvoice_isCountedWithRejectedOrdersAndLoggedAsErrorWithTheCodeOnly() {
        alerts.invoiceRejected(PaymentOrders.acceptedContract(), "CheckoutValidatorService.valid");

        verify(ordering).countOrderRejected();
        verifyNoMoreInteractions(ordering);
        verifyNoInteractions(payments);
        assertThat(log.at(Level.ERROR)).containsExactly(
                Invoices.logLine("Ordering отклонил выставление счёта, код CheckoutValidatorService.valid"));
        assertThat(log.events()).hasSize(1);
    }
}
