package ru.projectx.contracts.service.status;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import ru.projectx.contracts.entity.contract.Contract;
import ru.projectx.contracts.entity.contract.ContractStatus;
import ru.projectx.contracts.entity.issuance.CancelReason;
import ru.projectx.contracts.entity.issuance.Cancellation;
import ru.projectx.contracts.entity.issuance.IssueError;
import ru.projectx.contracts.entity.payment.InvoiceLink;
import ru.projectx.contracts.entity.payment.InvoiceView;
import ru.projectx.contracts.entity.payment.OrderInvoice;
import ru.projectx.contracts.entity.policy.LmPolicy;
import ru.projectx.contracts.entity.policy.LmPolicyStatus;
import ru.projectx.contracts.support.Invoices;
import ru.projectx.contracts.support.Issuances;
import ru.projectx.contracts.support.Tasks;

class RecordOutcomeTest {

    private static final Instant NOW = Instant.parse("2026-02-10T07:35:00Z");
    private static final String CONTRACT_ID = "01960000-0000-7000-8000-000000000001";
    private static final UUID CONTRACT = UUID.fromString(CONTRACT_ID);
    private static final PaymentOffer NOTHING = new PaymentOffer(false, Optional.empty(), Optional.empty());
    private static final IssueError RELATION_REQUIRED = new IssueError("RELATION_TYPE_REQUIRED", "поле обязательно");

    @Test
    void recordWithoutInvoice_keepsItsStatusAndErrorAndOffersNoPayment() {
        LmPolicy policy = Issuances.policy("01960000-0000-7000-8000-000000000002", Tasks.CHILD);
        policy.markError(RELATION_REQUIRED, NOW);

        RecordOutcome outcome = RecordOutcome.of(policy);

        assertThat(outcome).isEqualTo(
                new RecordOutcome(policy.id(), Tasks.CHILD, LmPolicyStatus.ERROR, RELATION_REQUIRED, NOTHING));
        assertThat(outcome.linked()).isFalse();
        assertThat(outcome.stopped()).isTrue();
    }

    @Test
    void invoicedContractWithLink_isLinkedAndKeepsItsStatus() {
        RecordOutcome outcome = RecordOutcome.of(invoiced(), view(Invoices.issued(CONTRACT)));

        assertThat(outcome.status()).isEqualTo(ContractStatus.INVOICE_ISSUED);
        assertThat(outcome.offer())
                .isEqualTo(new PaymentOffer(false, Optional.of(Invoices.PAYMENT_URL), Optional.of(Invoices.DEADLINE)));
        assertThat(outcome.linked()).isTrue();
        assertThat(outcome.stopped()).isFalse();
        assertThat(outcome.error()).isNull();
    }

    @Test
    void invoicedContractWhoseReplyWasLost_isNotLinked() {
        RecordOutcome outcome = RecordOutcome.of(invoiced(), view(Invoices.issuedWithoutLink(CONTRACT)));

        assertThat(outcome.status()).isEqualTo(ContractStatus.INVOICE_ISSUED);
        assertThat(outcome.linked()).isFalse();
    }

    @Test
    void contractWithDeferredCancellation_isShownAsCancelledAndStoppedWithoutLink() {
        Contract contract = invoiced();

        RecordOutcome outcome =
                RecordOutcome.of(contract, view(Invoices.cancellationDeferred(Invoices.issued(CONTRACT))));

        assertThat(outcome).isEqualTo(new RecordOutcome(CONTRACT, Tasks.SPOUSE, ContractStatus.CANCELLED, null,
                new PaymentOffer(true, Optional.empty(), Optional.empty())));
        assertThat(outcome.stopped()).isTrue();
        assertThat(outcome.linked()).isFalse();
        assertThat(contract.status()).isEqualTo(ContractStatus.INVOICE_ISSUED);
    }

    @Test
    void outcome_looksLikeAnEarlierOneWithTheSameStatusErrorAndPresenceOfTheLink() {
        RecordOutcome linked = RecordOutcome.of(invoiced(), view(Invoices.issued(CONTRACT)));
        OrderInvoice another = Invoices.inCheckout(CONTRACT);
        another.issue(new InvoiceLink("8d2f6ba1-4c53-4e70-9b28-0f1a3c7d5e42", "https://pay.example.test/pay/8d2f6ba1"),
                NOW);

        assertThat(linked.looksLike(RecordOutcome.of(invoiced(), view(Invoices.issued(CONTRACT))))).isTrue();
        assertThat(linked.looksLike(RecordOutcome.of(invoiced(), view(another)))).isTrue();
    }

    @Test
    void outcome_doesNotLookLikeNothingOrAnEarlierOneThatDiffersInStatusErrorOrPresenceOfTheLink() {
        RecordOutcome linked = RecordOutcome.of(invoiced(), view(Invoices.issued(CONTRACT)));
        RecordOutcome unlinked = RecordOutcome.of(invoiced(), view(Invoices.issuedWithoutLink(CONTRACT)));
        Contract failed = Issuances.contract(CONTRACT_ID, Tasks.SPOUSE, "DKM-2026-000001");
        failed.markError(RELATION_REQUIRED, NOW);
        Contract failedOtherwise = Issuances.contract(CONTRACT_ID, Tasks.SPOUSE, "DKM-2026-000001");
        failedOtherwise.markError(new IssueError("UNKNOWN_GENDER", "недопустимое значение поля"), NOW);

        assertThat(linked.looksLike(null)).isFalse();
        assertThat(linked.looksLike(unlinked)).isFalse();
        assertThat(unlinked.looksLike(linked)).isFalse();
        assertThat(unlinked.looksLike(RecordOutcome.of(Issuances.acceptedContract(CONTRACT_ID, Tasks.SPOUSE,
                "DKM-2026-000001")))).isFalse();
        assertThat(RecordOutcome.of(failed).looksLike(RecordOutcome.of(failedOtherwise))).isFalse();
        assertThat(RecordOutcome.of(failed).looksLike(RecordOutcome.of(failed))).isTrue();
    }

    @Test
    void outcome_isResumedWhenTheEarlierOneWasDeferredAndThisOneIsNeitherDeferredNorStopped() {
        RecordOutcome deferred =
                RecordOutcome.of(invoiced(), view(Invoices.cancellationDeferred(Invoices.issued(CONTRACT))));

        assertThat(RecordOutcome.of(invoiced(), view(Invoices.issued(CONTRACT))).resumedSince(deferred)).isTrue();
        assertThat(RecordOutcome.of(invoiced(), view(Invoices.issuedWithoutLink(CONTRACT))).resumedSince(deferred))
                .isTrue();
    }

    @Test
    void outcome_isNotResumedWithoutAnEarlierDeferredOneOrWhileStillDeferredOrOnceStopped() {
        RecordOutcome deferred =
                RecordOutcome.of(invoiced(), view(Invoices.cancellationDeferred(Invoices.issued(CONTRACT))));
        RecordOutcome running = RecordOutcome.of(invoiced(), view(Invoices.issued(CONTRACT)));
        Contract cancelled = invoiced();
        cancelled.cancel(new Cancellation(CancelReason.INSURED_REFUSED, null, NOW), NOW);

        assertThat(running.resumedSince(null)).isFalse();
        assertThat(running.resumedSince(running)).isFalse();
        assertThat(deferred.resumedSince(deferred)).isFalse();
        assertThat(RecordOutcome.of(cancelled, view(Invoices.issued(CONTRACT))).resumedSince(deferred)).isFalse();
    }

    private static Contract invoiced() {
        return Issuances.invoicedContract(CONTRACT_ID, Tasks.SPOUSE, "DKM-2026-000001");
    }

    private static Optional<InvoiceView> view(OrderInvoice invoice) {
        return Optional.of(invoice.view());
    }
}
