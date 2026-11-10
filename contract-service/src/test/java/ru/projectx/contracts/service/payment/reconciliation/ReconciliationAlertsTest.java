package ru.projectx.contracts.service.payment.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import ch.qos.logback.classic.Level;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import ru.projectx.contracts.entity.contract.Contract;
import ru.projectx.contracts.entity.payment.InvoiceState;
import ru.projectx.contracts.service.payment.Invoices;
import ru.projectx.contracts.service.payment.PaymentAlert;
import ru.projectx.contracts.service.payment.PaymentMetrics;
import ru.projectx.contracts.support.CapturedLog;
import ru.projectx.contracts.support.PaymentOrders;

class ReconciliationAlertsTest {

    private final PaymentMetrics metrics = mock();
    private final ReconciliationAlerts alerts = new ReconciliationAlerts(metrics);
    private final Contract contract = PaymentOrders.acceptedContract();
    private final CapturedLog log = CapturedLog.of(ReconciliationAlerts.class);

    @AfterEach
    void releaseLog() {
        log.close();
    }

    private void assertRaised(PaymentAlert alert, String reason) {
        verify(metrics).countAlert(alert);
        verifyNoMoreInteractions(metrics);
        assertThat(log.at(Level.ERROR)).containsExactly(Invoices.logLine(reason));
        assertThat(log.events()).hasSize(1);
    }

    @Test
    void documentNotFixed_isCountedAndLoggedAsError() {
        alerts.documentNotFixed(contract);

        assertRaised(PaymentAlert.DOCUMENT_NOT_FIXED, "Ordering не подтвердил документ после повторов подтверждения");
    }

    @Test
    void paymentErrorOverdue_isCountedAndLoggedAsError() {
        alerts.paymentErrorOverdue(contract);

        assertRaised(PaymentAlert.PAYMENT_ERROR_OVERDUE,
                "после ошибки оплаты итог не пришёл к сроку оплаты: счёт считается просроченным");
    }

    @Test
    void stateUnavailable_isCountedAndLoggedAsError() {
        alerts.stateUnavailable(contract);

        assertRaised(PaymentAlert.STATE_UNAVAILABLE, "состояние заказа в Ordering получить не удалось");
    }

    @ParameterizedTest
    @EnumSource(names = {"CHECKOUT", "ISSUED", "PAYMENT_ERROR", "PAID", "OVERDUE"})
    void unexpectedStateOfInvoicedOrder_saysTheOrderHasAnInvoice(InvoiceState state) {
        alerts.unexpectedState(contract, Invoices.in(state).view(), "CANCELED");

        assertRaised(PaymentAlert.UNEXPECTED_STATE,
                "состояние заказа CANCELED в Ordering к заказу со счётом не применимо");
    }

    @ParameterizedTest
    @EnumSource(names = {"AWAITING_DOCUMENT", "DOCUMENT_UNCONFIRMED", "READY", "REJECTED"})
    void unexpectedStateOfOrderWithoutInvoice_saysTheOrderHasNoInvoice(InvoiceState state) {
        alerts.unexpectedState(contract, Invoices.in(state).view(), "PAID");

        assertRaised(PaymentAlert.UNEXPECTED_STATE, "состояние заказа PAID в Ordering к заказу без счёта не применимо");
    }
}
