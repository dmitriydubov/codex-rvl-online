package ru.projectx.contracts.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import ru.projectx.contracts.entity.CancelReason;
import ru.projectx.contracts.entity.Cancellation;
import ru.projectx.contracts.entity.Contract;
import ru.projectx.contracts.entity.ContractDraft;
import ru.projectx.contracts.entity.IssueError;
import ru.projectx.contracts.support.Issuances;
import ru.projectx.contracts.support.Tasks;

class PaidContractsTest {

    private static final Instant NOW = Instant.parse("2026-02-10T07:35:00Z");
    private static final Instant PAID_AT = Instant.parse("2026-02-10T07:20:15Z");

    private final Contract contract = Contract.open(new ContractDraft("DKM-2026-000001", Issuances.TERMS, Tasks.SPOUSE,
            new BigDecimal("34000.00")), NOW);

    @Test
    void failedAfterPayment_holdsForAPaidContractInError() {
        paid();

        contract.markError(new IssueError("PROGRAM_ARCHIVED", "программа в архиве"), NOW);

        assertThat(PaidContracts.failedAfterPayment(contract)).isTrue();
    }

    @Test
    void failedAfterPayment_doesNotHoldForAnUnpaidContractInError() {
        contract.markError(new IssueError("RELATION_TYPE_REQUIRED", "поле обязательно"), NOW);

        assertThat(PaidContracts.failedAfterPayment(contract)).isFalse();
    }

    @Test
    void failedAfterPayment_doesNotHoldForAPaidContractStillInProgress() {
        paid();

        assertThat(PaidContracts.failedAfterPayment(contract)).isFalse();
    }

    @Test
    void failedAfterPayment_doesNotHoldForAPaidContractCancelled() {
        paid();

        contract.cancel(new Cancellation(CancelReason.OTHER, null, NOW), NOW);

        assertThat(PaidContracts.failedAfterPayment(contract)).isFalse();
    }

    private void paid() {
        contract.offerDraft();
        contract.accept();
        contract.markPaid(PAID_AT, NOW);
    }
}
