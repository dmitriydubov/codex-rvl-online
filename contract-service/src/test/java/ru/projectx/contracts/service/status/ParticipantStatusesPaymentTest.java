package ru.projectx.contracts.service.status;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import ru.projectx.contracts.entity.contract.Contract;
import ru.projectx.contracts.entity.issuance.CancelReason;
import ru.projectx.contracts.entity.issuance.Cancellation;
import ru.projectx.contracts.entity.issuance.DocumentKind;
import ru.projectx.contracts.entity.issuance.IssueError;
import ru.projectx.contracts.entity.issuance.RequestParticipant;
import ru.projectx.contracts.entity.payment.OrderInvoice;
import ru.projectx.contracts.message.status.KmContractState;
import ru.projectx.contracts.message.status.ParticipantStatus;
import ru.projectx.contracts.message.status.PaymentStatus;
import ru.projectx.contracts.support.Invoices;
import ru.projectx.contracts.support.Issuances;
import ru.projectx.contracts.support.Tasks;

class ParticipantStatusesPaymentTest {

    private static final Instant NOW = Instant.parse("2026-02-10T07:35:00Z");
    private static final Instant PAID_AT = Instant.parse("2026-02-10T07:20:15Z");
    private static final RequestParticipant SPOUSE_KM = new RequestParticipant(Tasks.SPOUSE, DocumentKind.KM_CONTRACT);
    private static final String CONTRACT_ID = "01960000-0000-7000-8000-000000000001";
    private static final String NUMBER = "DKM-2026-000001";

    private final Contract invoiced = Issuances.invoicedContract(CONTRACT_ID, Tasks.SPOUSE, NUMBER);

    @Test
    void contractWithInvoiceLink_isDraftedWithPendingPaymentAndTheLink() {
        ParticipantStatus status = statusOf(invoiced, Invoices.issued(invoiced.id()));

        assertThat(status.kmContractState()).isEqualTo(KmContractState.DRAFTED);
        assertThat(status.paymentStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(status.paymentUrl()).isEqualTo(Invoices.PAYMENT_URL);
        assertThat(status.kmContractId()).isEqualTo(invoiced.id());
        assertThat(status.kmContractNumber()).isEqualTo(NUMBER);
        assertThat(status.errorCode()).isNull();
    }

    @Test
    void contractWhoseInvoiceReplyWasLost_isDraftedWithoutPaymentAndLink() {
        ParticipantStatus status = statusOf(invoiced, Invoices.issuedWithoutLink(invoiced.id()));

        assertThat(status.kmContractState()).isEqualTo(KmContractState.DRAFTED);
        assertThat(status.paymentStatus()).isEqualTo(PaymentStatus.NOT_REQUIRED);
        assertThat(status.paymentUrl()).isNull();
    }

    @Test
    void acceptedContractWhoseInvoiceIsStillInCheckout_showsNoPayment() {
        Contract accepted = Issuances.acceptedContract(CONTRACT_ID, Tasks.SPOUSE, NUMBER);

        ParticipantStatus status = statusOf(accepted, Invoices.inCheckout(accepted.id()));

        assertThat(status.kmContractState()).isEqualTo(KmContractState.DRAFTED);
        assertThat(status.paymentStatus()).isEqualTo(PaymentStatus.NOT_REQUIRED);
        assertThat(status.paymentUrl()).isNull();
    }

    @Test
    void contractWithDeferredCancellation_isCancelledWithoutPaymentAndLink() {
        ParticipantStatus status =
                statusOf(invoiced, Invoices.cancellationDeferred(Invoices.issued(invoiced.id())));

        assertThat(status.kmContractState()).isEqualTo(KmContractState.CANCELLED);
        assertThat(status.paymentStatus()).isEqualTo(PaymentStatus.NOT_REQUIRED);
        assertThat(status.paymentUrl()).isNull();
        assertThat(status.errorCode()).isNull();
    }

    @Test
    void paidContract_isPaidByTheMomentOfPaymentAndCarriesNoLinkAnyMore() {
        invoiced.markPaid(PAID_AT, NOW);

        ParticipantStatus status = statusOf(invoiced, Invoices.issued(invoiced.id()));

        assertThat(status.kmContractState()).isEqualTo(KmContractState.DRAFTED);
        assertThat(status.paymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(status.paymentUrl()).isNull();
    }

    @Test
    void contractFailedOrCancelledAfterPayment_staysPaid() {
        Contract failed = Issuances.paidContract(CONTRACT_ID, Tasks.SPOUSE, NUMBER, PAID_AT);
        failed.markError(new IssueError("KM_CONTRACT_SUBMISSION_UNDELIVERED", "договор КМ не передан в 1С:УСК"), NOW);
        Contract cancelled = Issuances.paidContract(CONTRACT_ID, Tasks.SPOUSE, NUMBER, PAID_AT);
        cancelled.cancel(new Cancellation(CancelReason.INSURED_REFUSED, null, NOW), NOW);

        assertThat(statusOf(failed, Invoices.issued(failed.id())).paymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(statusOf(cancelled, Invoices.issued(cancelled.id())))
                .returns(PaymentStatus.PAID, ParticipantStatus::paymentStatus)
                .returns(KmContractState.CANCELLED, ParticipantStatus::kmContractState)
                .returns(null, ParticipantStatus::paymentUrl);
    }

    @Test
    void contractCancelledWithoutPayment_showsNeitherPaymentNorLinkOfItsInvoice() {
        invoiced.cancel(new Cancellation(CancelReason.EXPIRED, null, NOW), NOW);

        ParticipantStatus status = statusOf(invoiced, Invoices.issued(invoiced.id()));

        assertThat(status.kmContractState()).isEqualTo(KmContractState.CANCELLED);
        assertThat(status.paymentStatus()).isEqualTo(PaymentStatus.NOT_REQUIRED);
        assertThat(status.paymentUrl()).isNull();
    }

    @Test
    void policyAndParticipantWithoutDocument_carryNoPaymentLink() {
        RequestParticipant childLm = new RequestParticipant(Tasks.CHILD, DocumentKind.LM_POLICY);
        RequestParticipant insured = new RequestParticipant(Tasks.INSURED, DocumentKind.NONE);
        IssuanceSnapshot snapshot = new IssuanceSnapshot(Tasks.REQUEST, false, List.of(insured, childLm), List.of(),
                List.of(Issuances.policy("01960000-0000-7000-8000-000000000002", Tasks.CHILD)), List.of());

        assertThat(ParticipantStatuses.of(childLm, snapshot))
                .returns(PaymentStatus.NOT_REQUIRED, ParticipantStatus::paymentStatus)
                .returns(null, ParticipantStatus::paymentUrl);
        assertThat(ParticipantStatuses.of(insured, snapshot))
                .returns(PaymentStatus.NOT_REQUIRED, ParticipantStatus::paymentStatus)
                .returns(null, ParticipantStatus::paymentUrl);
    }

    private static ParticipantStatus statusOf(Contract contract, OrderInvoice invoice) {
        return ParticipantStatuses.of(SPOUSE_KM, new IssuanceSnapshot(Tasks.REQUEST, false, List.of(SPOUSE_KM),
                List.of(contract), List.of(), List.of(), List.of(invoice.view())));
    }
}
