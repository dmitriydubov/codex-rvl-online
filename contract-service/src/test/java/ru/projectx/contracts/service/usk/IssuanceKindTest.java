package ru.projectx.contracts.service.usk;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.util.ReflectionTestUtils;
import ru.projectx.contracts.entity.Contract;
import ru.projectx.contracts.entity.ContractStatus;
import ru.projectx.contracts.entity.IssueError;
import ru.projectx.contracts.entity.PersonOwner;
import ru.projectx.contracts.support.Issuances;
import ru.projectx.contracts.support.Tasks;

class IssuanceKindTest {

    private static final UUID CONTRACT_ID = UUID.fromString("0f4b8e2a-6c13-4d7e-b9a1-2e5c7d3f8a64");

    private final IssuanceKind<Contract, ContractStatus> kind = new IssuanceKind<>(
            new UskWording("договор КМ", "договора КМ", "договору КМ", "результат", "не отправлялась", "отложена",
                    "{} {}", "{}"),
            "CONTRACT", "KmContractRegistered", ContractStatus.PAID, ContractStatus.SUBMITTED,
            new IssueError("KM_CONTRACT_SUBMISSION_UNDELIVERED", "не передан"), PersonOwner::contract,
            Contract::markIssued);
    private final Contract contract = Issuances.contract(CONTRACT_ID.toString(), Tasks.SPOUSE, "DKM-2026-000001");

    @ParameterizedTest
    @EnumSource(ContractStatus.class)
    void ready_holdsOnlyInTheStatusThatWaitsToBeSent(ContractStatus status) {
        ReflectionTestUtils.setField(contract, "status", status);

        assertThat(kind.ready(contract)).isEqualTo(status == ContractStatus.PAID);
    }

    @ParameterizedTest
    @EnumSource(ContractStatus.class)
    void awaiting_holdsOnlyInTheStatusSentToTheUsk(ContractStatus status) {
        ReflectionTestUtils.setField(contract, "status", status);

        assertThat(kind.awaiting(contract)).isEqualTo(status == ContractStatus.SUBMITTED);
    }

    @ParameterizedTest
    @EnumSource(value = ContractStatus.class, names = {"SUBMITTED", "ISSUED", "ERROR", "CANCELLED"})
    void requested_holdsOnceSentOrFinished(ContractStatus status) {
        ReflectionTestUtils.setField(contract, "status", status);

        assertThat(kind.requested(contract)).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = ContractStatus.class, names = {"NEW", "DRAFT_READY", "ACCEPTED", "INVOICE_ISSUED", "PAID"})
    void requested_doesNotHoldBeforeTheSending(ContractStatus status) {
        ReflectionTestUtils.setField(contract, "status", status);

        assertThat(kind.requested(contract)).isFalse();
    }

    @Test
    void owner_andCompletion_areTheOnesOfTheKind() {
        assertThat(kind.owner().apply(CONTRACT_ID)).isEqualTo(PersonOwner.contract(CONTRACT_ID));
        assertThat(kind.completion()).isNotNull();
        assertThat(kind.undelivered().code()).isEqualTo("KM_CONTRACT_SUBMISSION_UNDELIVERED");
        assertThat(kind.aggregateType()).isEqualTo("CONTRACT");
        assertThat(kind.resultEventType()).isEqualTo("KmContractRegistered");
        assertThat(Instant.EPOCH).isBefore(Instant.now());
    }
}
