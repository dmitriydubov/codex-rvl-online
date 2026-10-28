package ru.projectx.contracts.service.km;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.dao.CannotAcquireLockException;
import ru.projectx.contracts.entity.issuance.CancelReason;
import ru.projectx.contracts.entity.issuance.Cancellation;
import ru.projectx.contracts.entity.Contract;
import ru.projectx.contracts.entity.ContractRegistration;
import ru.projectx.contracts.entity.ContractStatus;
import ru.projectx.contracts.entity.issuance.IssueError;
import ru.projectx.contracts.entity.issuance.IssuedPolicy;
import ru.projectx.contracts.repository.ContractRepository;
import ru.projectx.contracts.service.status.IssuanceSnapshot;
import ru.projectx.contracts.service.status.RequestStatusPublication;
import ru.projectx.contracts.support.Issuances;
import ru.projectx.contracts.support.Tasks;

class UndeliveredKmContractSubmissionsTest {

    private static final Instant NOW = Instant.parse("2026-02-10T07:40:00Z");
    private static final Instant PAID_AT = Instant.parse("2026-02-10T07:20:15Z");
    private static final UUID CONTRACT_ID = UUID.fromString("0f4b8e2a-6c13-4d7e-b9a1-2e5c7d3f8a64");
    private static final IssuedPolicy ISSUED = new IssuedPolicy(
            UUID.fromString("a71f3c98-4e20-4a55-93b1-5d0c7e2f8814"), "DKM-000004417",
            UUID.fromString("2c7e9b40-1f5a-4d83-9e6b-0a4c8d2f7e15"), "0987654321");

    private final ContractRepository contracts = mock();
    private final RequestStatusPublication statuses = mock();
    private final IssuanceSnapshot before = mock();
    private final UndeliveredKmContractSubmissions undelivered = new UndeliveredKmContractSubmissions(contracts,
            statuses);
    private final Contract contract = Issuances.paidContract(CONTRACT_ID.toString(), Tasks.SPOUSE, "DKM-2026-000001",
            PAID_AT);

    @BeforeEach
    void contractIsStored() {
        given(contracts.findLockedById(CONTRACT_ID)).willReturn(Optional.of(contract));
        given(statuses.watch(Tasks.REQUEST)).willReturn(before);
    }

    @Test
    void reactsToRejectedSubmissionsOfKmContracts() {
        assertThat(undelivered.aggregateType()).isEqualTo("CONTRACT");
    }

    @Test
    void submittedContract_goesToErrorWithTheUndeliveredCode_andTheRequestServiceIsAnswered() {
        contract.submit(Issuances.OPENED);

        undelivered.rejected(CONTRACT_ID, NOW);

        assertThat(contract.status()).isEqualTo(ContractStatus.ERROR);
        assertThat(contract.state().error()).contains(new IssueError("KM_CONTRACT_SUBMISSION_UNDELIVERED",
                "договор КМ не передан в 1С:УСК"));
        assertThat(contract.state().entityVersion()).isEqualTo(4);
        assertThat(contract.progress().paidAt()).contains(PAID_AT);
        InOrder order = inOrder(statuses);
        order.verify(statuses).watch(Tasks.REQUEST);
        order.verify(statuses).afterRecordChange(before, NOW);
    }

    @Test
    void contractCancelledBeforeTheRejection_staysCancelledWithoutErrorOrAnswer() {
        contract.submit(Issuances.OPENED);
        contract.cancel(new Cancellation(CancelReason.INSURED_REFUSED, null, NOW), NOW);

        undelivered.rejected(CONTRACT_ID, NOW);

        assertThat(contract.status()).isEqualTo(ContractStatus.CANCELLED);
        assertThat(contract.state().error()).isEmpty();
        assertThat(contract.state().entityVersion()).isEqualTo(4);
        verifyNoInteractions(statuses);
    }

    @Test
    void paidContract_isNotTakenForSubmitted() {
        undelivered.rejected(CONTRACT_ID, NOW);

        assertThat(contract.status()).isEqualTo(ContractStatus.PAID);
        verifyNoInteractions(statuses);
    }

    @Test
    void issuedContract_keepsTheRegistrationWithoutErrorOrAnswer() {
        contract.submit(Issuances.OPENED);
        contract.markIssued(ISSUED, NOW);

        undelivered.rejected(CONTRACT_ID, NOW);

        assertThat(contract.status()).isEqualTo(ContractStatus.ISSUED);
        assertThat(contract.progress().registration()).contains(new ContractRegistration(ISSUED.uskId(),
                ISSUED.uskNumber(), ISSUED.issuedPolicyId(), ISSUED.issuedPolicyNumber()));
        assertThat(contract.state().entityVersion()).isEqualTo(4);
        verifyNoInteractions(statuses);
    }

    @Test
    void contractRowHeldLongerThanTheLockTimeout_failsTheReactionBeforeAnyDecision() {
        CannotAcquireLockException busy = new CannotAcquireLockException("строка договора занята");
        given(contracts.findLockedById(CONTRACT_ID)).willThrow(busy);

        assertThatThrownBy(() -> undelivered.rejected(CONTRACT_ID, NOW)).isSameAs(busy);
        assertThat(contract.status()).isEqualTo(ContractStatus.PAID);
        verifyNoInteractions(statuses);
        verify(contracts, never()).findById(any());
    }

    @Test
    void unknownContract_isIllegalState() {
        UUID unknown = UUID.fromString("00000000-0000-4000-8000-000000000001");
        given(contracts.findLockedById(unknown)).willReturn(Optional.empty());

        assertThatThrownBy(() -> undelivered.rejected(unknown, NOW)).isInstanceOf(IllegalStateException.class)
                .hasMessage("договора КМ " + unknown + " нет");
        verifyNoInteractions(statuses);
    }
}
