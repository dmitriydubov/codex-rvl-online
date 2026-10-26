package ru.projectx.contracts.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.inOrder;
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
import org.mockito.InOrder;
import ru.projectx.contracts.entity.CancelReason;
import ru.projectx.contracts.entity.Cancellation;
import ru.projectx.contracts.entity.Contract;
import ru.projectx.contracts.entity.ContractDraft;
import ru.projectx.contracts.entity.ContractStatus;
import ru.projectx.contracts.entity.DocumentKind;
import ru.projectx.contracts.entity.IssueError;
import ru.projectx.contracts.entity.LmPolicy;
import ru.projectx.contracts.entity.LmPolicyDraft;
import ru.projectx.contracts.entity.LmPolicyStatus;
import ru.projectx.contracts.entity.RequestParticipant;
import ru.projectx.contracts.message.ContractTask;
import ru.projectx.contracts.repository.ContractRepository;
import ru.projectx.contracts.repository.LmPolicyRepository;
import ru.projectx.contracts.support.Issuances;
import ru.projectx.contracts.support.Tasks;

class IssuanceServiceTest {

    private static final Instant NOW = Instant.parse("2026-02-10T07:35:00Z");

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
    void composition_namesEachParticipantOfTheTaskWithTheDocumentDueToThem() {
        ContractTask task = Tasks.task(Tasks.insured("0.00"), Tasks.spouse("34000.00"), Tasks.child("0.00"));
        active(List.of(), List.of());

        assertThat(service.apply(task, NOW)).containsExactly(
                new RequestParticipant(Tasks.INSURED, DocumentKind.NONE),
                new RequestParticipant(Tasks.SPOUSE, DocumentKind.KM_CONTRACT),
                new RequestParticipant(Tasks.CHILD, DocumentKind.LM_POLICY));
    }

    @Test
    void activeRecords_areLoadedAmongNotStoppedAndPaidFailedContractsAndNotStoppedPoliciesAndHandedToTheirIssuance() {
        Contract contract = contract();
        LmPolicy policy = policy();
        ContractTask task = Tasks.task(Tasks.insured("0.00"), Tasks.spouse("34000.00"), Tasks.child("0.00"));
        active(List.of(contract), List.of(policy));

        service.apply(task, NOW);

        verify(contracts).findRunningOrFailedAfterPayment(Tasks.REQUEST, ContractStatus.ERROR,
                ContractStatus.CANCELLED);
        verify(policies).findByRequestIndividualRequestIdAndStatusNotIn(Tasks.REQUEST,
                Set.of(LmPolicyStatus.ERROR, LmPolicyStatus.CANCELLED));
        verify(kmContracts).reconcile(new Issue(task, task.participants().get(1)), Optional.of(contract), NOW);
        verify(lmPolicies).reconcile(new Issue(task, task.participants().get(2)), Optional.of(policy), NOW);
        assertThat(contract.status()).isEqualTo(ContractStatus.NEW);
        assertThat(policy.status()).isEqualTo(LmPolicyStatus.NEW);
    }

    @Test
    void activeRecords_areLockedAndReadAfreshContractsFirstBeforeTheirIssuanceSeesThem() {
        Contract contract = contract();
        LmPolicy policy = policy();
        ContractTask task = Tasks.task(Tasks.insured("0.00"), Tasks.spouse("34000.00"), Tasks.child("0.00"));
        active(List.of(contract), List.of(policy));

        service.apply(task, NOW);

        InOrder order = inOrder(contracts, policies, kmContracts, lmPolicies);
        order.verify(contracts).lockFresh(List.of(contract));
        order.verify(policies).lockFresh(List.of(policy));
        order.verify(kmContracts).reconcile(new Issue(task, task.participants().get(1)), Optional.of(contract), NOW);
        order.verify(lmPolicies).reconcile(new Issue(task, task.participants().get(2)), Optional.of(policy), NOW);
    }

    @Test
    void policyStoppedByTheTimeItIsLocked_isNotTheParticipantsActiveRecordAnyMore() {
        LmPolicy policy = policy();
        ContractTask task = Tasks.task(Tasks.insured("0.00"), Tasks.child("0.00"));
        active(List.of(), List.of(policy));
        willAnswer(lock -> {
            policy.markError(new IssueError("LM_POLICY_REQUEST_UNDELIVERED", "запрос не доставлен"), NOW);
            return null;
        }).given(policies).lockFresh(List.of(policy));

        service.apply(task, NOW);

        verify(lmPolicies).reconcile(new Issue(task, task.participants().get(1)), Optional.empty(), NOW);
        assertThat(policy.status()).isEqualTo(LmPolicyStatus.ERROR);
    }

    @Test
    void leftoverStoppedByTheTimeItIsLocked_isNotCancelledAgain() {
        LmPolicy policy = policy();
        Cancellation elsewhere = new Cancellation(CancelReason.REQUEST_CANCELLED, null, Issuances.OPENED);
        active(List.of(), List.of(policy));
        willAnswer(lock -> {
            policy.cancel(elsewhere, Issuances.OPENED);
            return null;
        }).given(policies).lockFresh(List.of(policy));

        service.apply(Tasks.task(Tasks.insured("0.00")), NOW);

        assertThat(policy.state().cancellation()).contains(elsewhere);
        assertThat(policy.state().entityVersion()).isEqualTo(2);
    }

    @Test
    void contractStoppedByTheTimeItIsLocked_isNotTheParticipantsActiveRecordAnyMore() {
        Contract contract = contract();
        ContractTask task = Tasks.task(Tasks.insured("0.00"), Tasks.spouse("34000.00"));
        active(List.of(contract), List.of());
        willAnswer(lock -> {
            contract.cancel(new Cancellation(CancelReason.EXPIRED, null, Issuances.OPENED), Issuances.OPENED);
            return null;
        }).given(contracts).lockFresh(List.of(contract));

        service.apply(task, NOW);

        verify(kmContracts).reconcile(new Issue(task, task.participants().get(1)), Optional.empty(), NOW);
    }

    @Test
    void paidContractInError_staysTheParticipantsRecordAndIsHandedToItsIssuance() {
        Contract failed = paidAndFailed();
        ContractTask task = Tasks.task(Tasks.insured("0.00"), Tasks.spouse("36000.00"));
        active(List.of(failed), List.of());

        assertThat(service.apply(task, NOW)).contains(new RequestParticipant(Tasks.SPOUSE, DocumentKind.KM_CONTRACT));

        verify(kmContracts).reconcile(new Issue(task, task.participants().get(1)), Optional.of(failed), NOW);
        assertThat(failed.status()).isEqualTo(ContractStatus.ERROR);
    }

    @Test
    void unpaidContractInError_isNotTheParticipantsRecordAndANewOneIsReconciled() {
        Contract failed = contract();
        failed.markError(new IssueError("RELATION_TYPE_REQUIRED", "поле обязательно"), NOW);
        ContractTask task = Tasks.task(Tasks.insured("0.00"), Tasks.spouse("34000.00"));
        active(List.of(failed), List.of());

        service.apply(task, NOW);

        verify(kmContracts).reconcile(new Issue(task, task.participants().get(1)), Optional.empty(), NOW);
    }

    @Test
    void paidContractInError_ofParticipantMissingFromTask_isCancelledAsExcludedFromTrip() {
        Contract failed = paidAndFailed();
        active(List.of(failed), List.of());

        service.apply(Tasks.task(Tasks.insured("0.00")), NOW);

        assertThat(failed.status()).isEqualTo(ContractStatus.CANCELLED);
        assertThat(failed.state().cancellation()).contains(
                new Cancellation(CancelReason.OTHER, "участник исключён из состава поездки", NOW));
    }

    @Test
    void paidContractInError_ofRelativeWhoStopsPaying_isCancelledAsKindChangedAndPolicyOpened() {
        Contract failed = paidAndFailed();
        ContractTask task = Tasks.task(Tasks.insured("0.00"), Tasks.spouse("0.00"));
        active(List.of(failed), List.of());

        service.apply(task, NOW);

        assertThat(failed.status()).isEqualTo(ContractStatus.CANCELLED);
        assertThat(failed.state().cancellation()).map(Cancellation::comment).contains("вид документа по участнику изменён");
        verify(lmPolicies).reconcile(new Issue(task, task.participants().get(1)), Optional.empty(), NOW);
        verifyNoInteractions(kmContracts);
    }

    @Test
    void paidContractFailingWhileTheTaskWaitsForTheLock_staysTheParticipantsRecord() {
        Contract contract = paid();
        ContractTask task = Tasks.task(Tasks.insured("0.00"), Tasks.spouse("34000.00"));
        active(List.of(contract), List.of());
        willAnswer(lock -> {
            contract.markError(new IssueError("KM_CONTRACT_SUBMISSION_UNDELIVERED", "договор КМ не передан в 1С:УСК"), NOW);
            return null;
        }).given(contracts).lockFresh(List.of(contract));

        service.apply(task, NOW);

        verify(kmContracts).reconcile(new Issue(task, task.participants().get(1)), Optional.of(contract), NOW);
    }

    @Test
    void unpaidContractFailingWhileTheTaskWaitsForTheLock_givesWayToANewRecord() {
        Contract contract = contract();
        ContractTask task = Tasks.task(Tasks.insured("0.00"), Tasks.spouse("34000.00"));
        active(List.of(contract), List.of());
        willAnswer(lock -> {
            contract.markError(new IssueError("PROGRAM_ARCHIVED", "программа в архиве"), NOW);
            return null;
        }).given(contracts).lockFresh(List.of(contract));

        service.apply(task, NOW);

        verify(kmContracts).reconcile(new Issue(task, task.participants().get(1)), Optional.empty(), NOW);
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
        Contract contract = Contract.open(new ContractDraft("DKM-2026-000001", Issuances.TERMS, Tasks.INSURED,
                new BigDecimal("12500.00")), NOW);
        active(List.of(contract), List.of());

        service.apply(Tasks.task(Tasks.insured("0.00")), NOW);

        assertThat(contract.status()).isEqualTo(ContractStatus.CANCELLED);
        verifyNoInteractions(kmContracts, lmPolicies);
    }

    private void active(List<Contract> activeContracts, List<LmPolicy> activePolicies) {
        given(contracts.findRunningOrFailedAfterPayment(Tasks.REQUEST, ContractStatus.ERROR,
                ContractStatus.CANCELLED)).willReturn(activeContracts);
        given(policies.findByRequestIndividualRequestIdAndStatusNotIn(eq(Tasks.REQUEST), any()))
                .willReturn(activePolicies);
    }

    private static Contract contract() {
        return Contract.open(new ContractDraft("DKM-2026-000001", Issuances.TERMS, Tasks.SPOUSE,
                new BigDecimal("34000.00")), NOW);
    }

    private static Contract paid() {
        Contract contract = contract();
        contract.offerDraft();
        contract.accept();
        contract.markPaid(Issuances.OPENED, NOW);
        return contract;
    }

    private static Contract paidAndFailed() {
        Contract contract = paid();
        contract.markError(new IssueError("KM_CONTRACT_SUBMISSION_UNDELIVERED", "договор КМ не передан в 1С:УСК"), NOW);
        return contract;
    }

    private static LmPolicy policy() {
        return LmPolicy.open(new LmPolicyDraft(Issuances.TERMS, Tasks.CHILD), NOW);
    }
}
