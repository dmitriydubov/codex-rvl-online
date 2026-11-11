package ru.projectx.contracts.service.status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import ru.projectx.contracts.entity.contract.Contract;
import ru.projectx.contracts.entity.contract.ContractStatus;
import ru.projectx.contracts.entity.issuance.DocumentKind;
import ru.projectx.contracts.entity.issuance.RequestParticipant;
import ru.projectx.contracts.entity.payment.OrderInvoice;
import ru.projectx.contracts.support.Invoices;
import ru.projectx.contracts.support.Issuances;
import ru.projectx.contracts.support.Tasks;

class IssuanceSnapshotInvoicesTest {

    private static final RequestParticipant INSURED_KM = new RequestParticipant(Tasks.INSURED, DocumentKind.KM_CONTRACT);
    private static final RequestParticipant SPOUSE_KM = new RequestParticipant(Tasks.SPOUSE, DocumentKind.KM_CONTRACT);
    private static final String SPOUSE_CONTRACT = "01960000-0000-7000-8000-000000000001";
    private static final String INSURED_CONTRACT = "01960000-0000-7000-8000-000000000002";
    private static final PaymentOffer NOTHING = new PaymentOffer(false, Optional.empty(), Optional.empty());
    private static final PaymentOffer LINKED =
            new PaymentOffer(false, Optional.of(Invoices.PAYMENT_URL), Optional.of(Invoices.DEADLINE));

    private final Contract spouseContract = Issuances.invoicedContract(SPOUSE_CONTRACT, Tasks.SPOUSE, "DKM-2026-000001");
    private final Contract insuredContract =
            Issuances.invoicedContract(INSURED_CONTRACT, Tasks.INSURED, "DKM-2026-000002");

    @Test
    void paymentOffer_isTakenFromTheInvoiceOfThatVeryContract() {
        IssuanceSnapshot snapshot = snapshot(Invoices.issued(spouseContract.id()));

        assertThat(snapshot.paymentOf(spouseContract)).isEqualTo(LINKED);
        assertThat(snapshot.paymentOf(insuredContract)).isEqualTo(NOTHING);
    }

    @Test
    void records_carryThePaymentOfferOfTheirOwnInvoice() {
        IssuanceSnapshot snapshot = snapshot(Invoices.issued(spouseContract.id()));

        assertThat(snapshot.records()).extracting(RecordOutcome::recordId, RecordOutcome::offer).containsExactly(
                tuple(spouseContract.id(), LINKED),
                tuple(insuredContract.id(), NOTHING));
    }

    @Test
    void snapshotBuiltWithoutInvoices_offersNoPayment() {
        IssuanceSnapshot snapshot = new IssuanceSnapshot(Tasks.REQUEST, false, List.of(INSURED_KM, SPOUSE_KM),
                List.of(spouseContract, insuredContract), List.of(), List.of());

        assertThat(snapshot.paymentOf(spouseContract)).isEqualTo(NOTHING);
        assertThat(snapshot.records()).extracting(RecordOutcome::offer).containsOnly(NOTHING);
    }

    @Test
    void contractWithDeferredCancellation_countsAsCancelledForItsParticipantWhileItsOwnStatusStays() {
        IssuanceSnapshot snapshot = snapshot(Invoices.cancellationDeferred(Invoices.issued(spouseContract.id())),
                Invoices.cancellationDeferred(invoiceOf(insuredContract.id())));

        assertThat(snapshot.statusOf(SPOUSE_KM)).contains(ContractStatus.CANCELLED);
        assertThat(snapshot.paymentOf(spouseContract))
                .isEqualTo(new PaymentOffer(true, Optional.empty(), Optional.empty()));
        assertThat(spouseContract.status()).isEqualTo(ContractStatus.INVOICE_ISSUED);
    }

    @Test
    void record_isResumedOnlySinceASnapshotThatHeldItsOwnDeferredCancellation() {
        IssuanceSnapshot deferred = snapshot(Invoices.cancellationDeferred(Invoices.issued(spouseContract.id())));
        IssuanceSnapshot running = snapshot(Invoices.issued(spouseContract.id()));
        RecordOutcome spouse = running.records().get(0);
        RecordOutcome insured = running.records().get(1);

        assertThat(deferred.resumed(spouse)).isTrue();
        assertThat(deferred.resumed(insured)).isFalse();
        assertThat(running.resumed(spouse)).isFalse();
    }

    @Test
    void recordUnknownToTheEarlierSnapshot_isNotResumedEvenWhenAnotherRecordOfItsParticipantWasDeferred() {
        IssuanceSnapshot deferred = snapshot(Invoices.cancellationDeferred(Invoices.issued(spouseContract.id())));
        Contract later = Issuances.invoicedContract("01960000-0000-7000-8000-000000000009", Tasks.SPOUSE,
                "DKM-2026-000003");

        assertThat(deferred.resumed(RecordOutcome.of(later, Optional.of(invoiceOf(later.id()).view())))).isFalse();
    }

    private IssuanceSnapshot snapshot(OrderInvoice... invoices) {
        return new IssuanceSnapshot(Tasks.REQUEST, false, List.of(INSURED_KM, SPOUSE_KM),
                List.of(spouseContract, insuredContract), List.of(), List.of(),
                List.of(invoices).stream().map(OrderInvoice::view).toList());
    }

    private static OrderInvoice invoiceOf(UUID contractId) {
        return Invoices.issued(contractId);
    }
}
