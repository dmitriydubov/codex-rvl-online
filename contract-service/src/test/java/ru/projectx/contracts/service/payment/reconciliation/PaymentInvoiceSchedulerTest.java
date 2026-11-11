package ru.projectx.contracts.service.payment.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import ch.qos.logback.classic.Level;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import ru.projectx.contracts.service.payment.invoice.InvoiceIssuing;
import ru.projectx.contracts.service.payment.invoice.InvoiceSummaries;
import ru.projectx.contracts.support.CapturedLog;

class PaymentInvoiceSchedulerTest {

    private final InvoiceIssuing issuing = mock();
    private final PaymentReconciliation reconciliation = mock();
    private final InvoiceSummaries summaries = mock();
    private final PaymentInvoiceScheduler scheduler = new PaymentInvoiceScheduler(issuing, reconciliation, summaries);
    private final CapturedLog log = CapturedLog.of(PaymentInvoiceScheduler.class);

    @AfterEach
    void releaseLog() {
        log.close();
    }

    @Test
    void issueDue_runsOnlyTheInvoicePass() {
        scheduler.issueDue();

        verify(issuing).issueDue();
        verifyNoInteractions(reconciliation, summaries);
    }

    @Test
    void reconcileDue_runsOnlyTheReconciliationPass() {
        scheduler.reconcileDue();

        verify(reconciliation).reconcileDue();
        verifyNoInteractions(issuing, summaries);
    }

    @Test
    void refreshSummaries_runsOnlyTheSummary() {
        scheduler.refreshSummaries();

        verify(summaries).refresh();
        verifyNoInteractions(issuing, reconciliation);
    }

    @Test
    void failuresOfThePasses_areLoggedAtTheBoundaryWithoutTheirTexts() {
        given(issuing.issueDue()).willThrow(new IllegalStateException("значение поля"));
        given(reconciliation.reconcileDue()).willThrow(new IllegalStateException("значение поля"));
        willThrow(new IllegalStateException("значение поля")).given(summaries).refresh();

        assertThatCode(scheduler::issueDue).doesNotThrowAnyException();
        assertThatCode(scheduler::reconcileDue).doesNotThrowAnyException();
        assertThatCode(scheduler::refreshSummaries).doesNotThrowAnyException();

        assertThat(log.at(Level.WARN)).containsExactly(
                "проход выставления счетов не выполнен: java.lang.IllegalStateException, SQLSTATE нет",
                "проход сверки заказов на оплату не выполнен: java.lang.IllegalStateException, SQLSTATE нет",
                "проход сводки счетов и оплат не выполнен: java.lang.IllegalStateException, SQLSTATE нет");
    }
}
