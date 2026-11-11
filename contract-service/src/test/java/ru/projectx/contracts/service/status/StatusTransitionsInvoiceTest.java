package ru.projectx.contracts.service.status;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import ru.projectx.contracts.entity.contract.Contract;
import ru.projectx.contracts.entity.issuance.CancelReason;
import ru.projectx.contracts.entity.issuance.Cancellation;
import ru.projectx.contracts.entity.issuance.DocumentKind;
import ru.projectx.contracts.entity.issuance.RequestParticipant;
import ru.projectx.contracts.entity.payment.OrderInvoice;
import ru.projectx.contracts.support.Invoices;
import ru.projectx.contracts.support.Issuances;
import ru.projectx.contracts.support.Tasks;

class StatusTransitionsInvoiceTest {

    private static final Instant NOW = Instant.parse("2026-02-10T07:35:00Z");
    private static final RequestParticipant INSURED = new RequestParticipant(Tasks.INSURED, DocumentKind.NONE);
    private static final RequestParticipant SPOUSE_KM = new RequestParticipant(Tasks.SPOUSE, DocumentKind.KM_CONTRACT);
    private static final String CONTRACT_ID = "01960000-0000-7000-8000-000000000001";
    private static final String NUMBER = "DKM-2026-000001";

    private final Contract accepted = Issuances.acceptedContract(CONTRACT_ID, Tasks.SPOUSE, NUMBER);
    private final Contract invoiced = Issuances.invoicedContract(CONTRACT_ID, Tasks.SPOUSE, NUMBER);

    @Test
    void invoiceWithLink_isPublishedOnceWhenTheLinkAppears() {
        IssuanceSnapshot before = snapshot(accepted, Invoices.inCheckout(accepted.id()));
        IssuanceSnapshot after = snapshot(invoiced, Invoices.issued(invoiced.id()));

        assertThat(StatusTransitions.publishAfterChange(before, after)).isTrue();
        assertThat(StatusTransitions.publishAfterChange(after, after)).isFalse();
    }

    @Test
    void invoiceWhoseReplyWasLost_isNotPublishedWhileThereIsNoLink() {
        IssuanceSnapshot before = snapshot(accepted, Invoices.inCheckout(accepted.id()));
        IssuanceSnapshot after = snapshot(invoiced, Invoices.issuedWithoutLink(invoiced.id()));

        assertThat(StatusTransitions.publishAfterChange(before, after)).isFalse();
    }

    @Test
    void linkAppearingAtAnInvoicedContract_isPublished() {
        IssuanceSnapshot before = snapshot(invoiced, Invoices.issuedWithoutLink(invoiced.id()));
        IssuanceSnapshot after = snapshot(invoiced, Invoices.issued(invoiced.id()));

        assertThat(StatusTransitions.publishAfterChange(before, after)).isTrue();
    }

    @Test
    void acceptedContractWithoutInvoice_isStillNotPublished() {
        Contract drafted = Issuances.contract(CONTRACT_ID, Tasks.SPOUSE, NUMBER);
        drafted.offerDraft();
        IssuanceSnapshot before = new IssuanceSnapshot(Tasks.REQUEST, false, List.of(INSURED, SPOUSE_KM),
                List.of(drafted), List.of(), List.of());
        IssuanceSnapshot after = new IssuanceSnapshot(Tasks.REQUEST, false, List.of(INSURED, SPOUSE_KM),
                List.of(accepted), List.of(), List.of());

        assertThat(StatusTransitions.publishAfterChange(before, after)).isFalse();
    }

    @Test
    void deferredCancellation_isPublishedAsAStopAndTheLaterRealCancellationIsNot() {
        IssuanceSnapshot linked = snapshot(invoiced, Invoices.issued(invoiced.id()));
        IssuanceSnapshot deferred = snapshot(invoiced, Invoices.cancellationDeferred(Invoices.issued(invoiced.id())));
        Contract cancelled = Issuances.invoicedContract(CONTRACT_ID, Tasks.SPOUSE, NUMBER);
        cancelled.cancel(new Cancellation(CancelReason.INSURED_REFUSED, null, NOW), NOW);

        assertThat(StatusTransitions.publishAfterChange(linked, deferred)).isTrue();
        assertThat(StatusTransitions.publishAfterChange(deferred, deferred)).isFalse();
        assertThat(StatusTransitions.publishAfterChange(deferred, snapshot(cancelled, Invoices.issued(cancelled.id()))))
                .isFalse();
    }

    @Test
    void liftedDeferredCancellation_isPublishedWithTheLinkBack() {
        IssuanceSnapshot deferred = snapshot(invoiced, Invoices.cancellationDeferred(Invoices.issued(invoiced.id())));
        IssuanceSnapshot resumed = snapshot(invoiced, Invoices.issued(invoiced.id()));

        assertThat(StatusTransitions.publishAfterChange(deferred, resumed)).isTrue();
        assertThat(StatusTransitions.publishAfterChange(resumed, resumed)).isFalse();
    }

    @Test
    void liftedDeferredCancellation_isPublishedEvenWhenThereIsNoLinkToShow() {
        IssuanceSnapshot deferred =
                snapshot(invoiced, Invoices.cancellationDeferred(Invoices.issuedWithoutLink(invoiced.id())));
        IssuanceSnapshot resumed = snapshot(invoiced, Invoices.issuedWithoutLink(invoiced.id()));

        assertThat(StatusTransitions.publishAfterChange(deferred, resumed)).isTrue();
        assertThat(StatusTransitions.publishAfterChange(resumed, resumed)).isFalse();
    }

    @Test
    void liftedDeferredCancellationOfAnAcceptedContractInCheckout_isPublished() {
        IssuanceSnapshot deferred =
                snapshot(accepted, Invoices.cancellationDeferred(Invoices.inCheckout(accepted.id())));
        IssuanceSnapshot resumed = snapshot(accepted, Invoices.inCheckout(accepted.id()));

        assertThat(StatusTransitions.publishAfterChange(deferred, resumed)).isTrue();
    }

    private static IssuanceSnapshot snapshot(Contract contract, OrderInvoice invoice) {
        return new IssuanceSnapshot(Tasks.REQUEST, false, List.of(INSURED, SPOUSE_KM), List.of(contract), List.of(),
                List.of(), List.of(invoice.view()));
    }
}
