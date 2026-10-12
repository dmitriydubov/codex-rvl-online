package ru.projectx.contracts.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import ru.projectx.contracts.entity.CancelReason;
import ru.projectx.contracts.entity.Cancellation;
import ru.projectx.contracts.entity.Contract;
import ru.projectx.contracts.entity.ContractDraft;
import ru.projectx.contracts.entity.ContractStatus;
import ru.projectx.contracts.entity.LmPolicy;
import ru.projectx.contracts.entity.LmPolicyDraft;
import ru.projectx.contracts.entity.LmPolicyStatus;
import ru.projectx.contracts.entity.RequestReference;
import ru.projectx.contracts.entity.TripPeriod;
import ru.projectx.contracts.message.ContractTask;
import ru.projectx.contracts.repository.ContractRepository;
import ru.projectx.contracts.repository.LmPolicyRepository;
import ru.projectx.contracts.support.Tasks;

class IssuanceServiceTest {

    private static final Instant NOW = Instant.parse("2026-02-10T07:35:00Z");
    private static final RequestReference REQUEST =
            new RequestReference(Tasks.REQUEST, Tasks.INSURANCE_CONTRACT, Tasks.INSURER_POLICY);
    private static final TripPeriod PERIOD = new TripPeriod(Tasks.FROM, Tasks.TO);

    private final ContractRepository contracts = mock();
    private final LmPolicyRepository policies = mock();
    private final KmContractIssuance kmContracts = mock();
    private final LmPolicyIssuance lmPolicies = mock();
    private final IssuanceService service = new IssuanceService(contracts, policies, kmContracts, lmPolicies);

    @Test
    void firstTask_reconcilesEachPayingOrRelativeParticipantAndSkipsInsuredWithoutPayment() {
        ContractTask task = Tasks.task(Tasks.insured("0.00"), Tasks.spouse("34000.00"), Tasks.child("0.00"));
        active(List.of(), List.of());

        service.apply(task, NOW);

        verify(kmContracts).reconcile(new Issue(task, task.participants().get(1)), Optional.empty(), NOW);
        verify(lmPolicies).reconcile(new Issue(task, task.participants().get(2)), Optional.empty(), NOW);
        verify(kmContracts, never()).reconcile(eq(new Issue(task, task.participants().get(0))), any(), eq(NOW));
        verify(lmPolicies, never()).reconcile(eq(new Issue(task, task.participants().get(0))), any(), eq(NOW));
    }

    @Test
    void activeRecords_areLoadedAmongUnfinishedOnlyAndHandedToTheirIssuance() {
        Contract contract = contract();
        LmPolicy policy = policy();
        ContractTask task = Tasks.task(Tasks.insured("0.00"), Tasks.spouse("34000.00"), Tasks.child("0.00"));
        active(List.of(contract), List.of(policy));

        service.apply(task, NOW);

        verify(contracts).findByRequestIndividualRequestIdAndStatusNotIn(Tasks.REQUEST,
                Set.of(ContractStatus.ISSUED, ContractStatus.ERROR, ContractStatus.CANCELLED));
        verify(policies).findByRequestIndividualRequestIdAndStatusNotIn(Tasks.REQUEST,
                Set.of(LmPolicyStatus.ISSUED, LmPolicyStatus.ERROR, LmPolicyStatus.CANCELLED));
        verify(kmContracts).reconcile(new Issue(task, task.participants().get(1)), Optional.of(contract), NOW);
        verify(lmPolicies).reconcile(new Issue(task, task.participants().get(2)), Optional.of(policy), NOW);
        assertThat(contract.status()).isEqualTo(ContractStatus.NEW);
        assertThat(policy.status()).isEqualTo(LmPolicyStatus.NEW);
    }

    @Test
    void participantMissingFromTask_isCancelledAsExcludedFromTrip() {
        LmPolicy policy = policy();
        active(List.of(), List.of(policy));

        service.apply(Tasks.task(Tasks.insured("0.00"), Tasks.spouse("34000.00")), NOW);

        assertThat(policy.status()).isEqualTo(LmPolicyStatus.CANCELLED);
        assertThat(policy.state().cancellation()).contains(
                new Cancellation(CancelReason.OTHER, "участник исключён из состава поездки", NOW));
    }

    @Test
    void relativeWhoStartsPaying_hasPolicyCancelledAsKindChangedAndContractOpened() {
        LmPolicy policy = policy();
        ContractTask task = Tasks.task(Tasks.insured("0.00"), Tasks.relative(1, Tasks.CHILD, "5000.00", "CHILD"));
        active(List.of(), List.of(policy));

        service.apply(task, NOW);

        assertThat(policy.status()).isEqualTo(LmPolicyStatus.CANCELLED);
        assertThat(policy.state().cancellation()).contains(
                new Cancellation(CancelReason.OTHER, "вид документа по участнику изменён", NOW));
        verify(kmContracts).reconcile(new Issue(task, task.participants().get(1)), Optional.empty(), NOW);
        verifyNoInteractions(lmPolicies);
    }

    @Test
    void relativeWhoStopsPaying_hasContractCancelledAsKindChangedAndPolicyOpened() {
        Contract contract = contract();
        ContractTask task = Tasks.task(Tasks.insured("0.00"), Tasks.spouse("0.00"));
        active(List.of(contract), List.of());

        service.apply(task, NOW);

        assertThat(contract.status()).isEqualTo(ContractStatus.CANCELLED);
        assertThat(contract.state().cancellation()).map(Cancellation::comment).contains("вид документа по участнику изменён");
        verify(lmPolicies).reconcile(new Issue(task, task.participants().get(1)), Optional.empty(), NOW);
        verifyNoInteractions(kmContracts);
    }

    @Test
    void insuredWhoStopsPaying_hasContractCancelledAndNothingOpened() {
        Contract contract = Contract.open(new ContractDraft("DKM-2026-000001", REQUEST, Tasks.INSURED, Tasks.INSURED,
                PERIOD, new BigDecimal("12500.00")), NOW);
        active(List.of(contract), List.of());

        service.apply(Tasks.task(Tasks.insured("0.00")), NOW);

        assertThat(contract.status()).isEqualTo(ContractStatus.CANCELLED);
        verifyNoInteractions(kmContracts, lmPolicies);
    }

    private void active(List<Contract> activeContracts, List<LmPolicy> activePolicies) {
        given(contracts.findByRequestIndividualRequestIdAndStatusNotIn(eq(Tasks.REQUEST), any()))
                .willReturn(activeContracts);
        given(policies.findByRequestIndividualRequestIdAndStatusNotIn(eq(Tasks.REQUEST), any()))
                .willReturn(activePolicies);
    }

    private static Contract contract() {
        return Contract.open(new ContractDraft("DKM-2026-000001", REQUEST, Tasks.SPOUSE, Tasks.INSURED, PERIOD,
                new BigDecimal("34000.00")), NOW);
    }

    private static LmPolicy policy() {
        return LmPolicy.open(new LmPolicyDraft(REQUEST, Tasks.CHILD, Tasks.INSURED, PERIOD), NOW);
    }
}
