package ru.projectx.contracts.service.cancellation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;
import ru.projectx.contracts.entity.issuance.CancelReason;
import ru.projectx.contracts.entity.issuance.Cancellation;
import ru.projectx.contracts.entity.contract.Contract;
import ru.projectx.contracts.entity.contract.ContractDraft;
import ru.projectx.contracts.entity.contract.ContractStatus;
import ru.projectx.contracts.entity.issuance.IssueError;
import ru.projectx.contracts.entity.issuance.IssuedPolicy;
import ru.projectx.contracts.entity.policy.LmPolicy;
import ru.projectx.contracts.entity.policy.LmPolicyDraft;
import ru.projectx.contracts.entity.policy.LmPolicyStatus;
import ru.projectx.contracts.message.task.ContractTaskCancellation;
import ru.projectx.contracts.repository.issuance.ContractRepository;
import ru.projectx.contracts.repository.issuance.LmPolicyRepository;
import ru.projectx.contracts.service.payment.cancellation.ContractStops;
import ru.projectx.contracts.support.Issuances;
import ru.projectx.contracts.support.Tasks;

class TaskCancellationServiceTest {

    private static final Instant NOW = Instant.parse("2026-02-12T12:00:00Z");
    private static final Instant CANCELLED_AT = Instant.parse("2026-02-12T11:30:00Z");
    private static final IssuedPolicy ISSUED = new IssuedPolicy(UUID.fromString("cb904f2a-7d61-4e08-89a5-40b1e3c7f012"),
            "LM-000012204", UUID.fromString("13ab5388-61ee-4e21-8a46-c325478b78c0"), "0555123456");

    private final ContractRepository contracts = mock();
    private final LmPolicyRepository policies = mock();
    private final CancellationUnknownReasons unknownReasons = mock();
    private final ContractStops contractStops = mock();
    private final TaskCancellationService service =
            new TaskCancellationService(contracts, policies, unknownReasons, contractStops);

    @Test
    void cancellation_stopsEveryContractAndCancelsEveryPolicyOfTheRequestWithMappedReasonCommentAndMoment() {
        Contract contract = contract();
        LmPolicy policy = LmPolicy.open(new LmPolicyDraft(Issuances.TERMS, Tasks.CHILD), NOW);
        running(List.of(contract), List.of(policy));

        service.apply(new ContractTaskCancellation(Tasks.REQUEST, CANCELLED_AT, "HR_REJECTED", "Стенд: комментарий"),
                NOW);

        Cancellation expected = new Cancellation(CancelReason.REQUEST_CANCELLED, "Стенд: комментарий", CANCELLED_AT);
        verify(contractStops).stop(contract, expected, NOW);
        verifyNoMoreInteractions(contractStops);
        assertThat(policy.status()).isEqualTo(LmPolicyStatus.CANCELLED);
        assertThat(policy.state().cancellation()).contains(expected);
        assertThat(policy.state().entityVersion()).isEqualTo(2);
    }

    @Test
    void contractStop_decidesTheContractsFateItself() {
        Contract contract = contract();
        running(List.of(contract), List.of());

        service.apply(new ContractTaskCancellation(Tasks.REQUEST, CANCELLED_AT, "EXPIRED", null), NOW);

        verify(contractStops).stop(contract, new Cancellation(CancelReason.EXPIRED, null, CANCELLED_AT), NOW);
        assertThat(contract.status()).isEqualTo(ContractStatus.NEW);
        assertThat(contract.state().cancellation()).isEmpty();
    }

    @Test
    void everyRunningContract_isStoppedInTheOrderItWasRead() {
        Contract first = contract();
        Contract second = Contract.open(new ContractDraft("DKM-2026-000002", Issuances.TERMS, Tasks.INSURED,
                new BigDecimal("12500.00")), NOW);
        running(List.of(first, second), List.of());

        service.apply(new ContractTaskCancellation(Tasks.REQUEST, CANCELLED_AT, "EXPIRED", null), NOW);

        Cancellation expired = new Cancellation(CancelReason.EXPIRED, null, CANCELLED_AT);
        InOrder order = inOrder(contractStops);
        order.verify(contractStops).stop(first, expired, NOW);
        order.verify(contractStops).stop(second, expired, NOW);
    }

    @Test
    void records_areLockedAndReadAfreshContractsFirstBeforeTheyAreStopped() {
        Contract contract = contract();
        LmPolicy policy = LmPolicy.open(new LmPolicyDraft(Issuances.TERMS, Tasks.CHILD), NOW);
        running(List.of(contract), List.of(policy));
        willAnswer(lock -> {
            assertThat(policy.status()).isEqualTo(LmPolicyStatus.NEW);
            return null;
        }).given(policies).lockFresh(List.of(policy));

        service.apply(new ContractTaskCancellation(Tasks.REQUEST, CANCELLED_AT, "EXPIRED", null), NOW);

        InOrder order = inOrder(contracts, contractStops, policies);
        order.verify(contracts).lockFresh(List.of(contract));
        order.verify(contractStops).stop(contract, new Cancellation(CancelReason.EXPIRED, null, CANCELLED_AT), NOW);
        order.verify(policies).lockFresh(List.of(policy));
        assertThat(policy.status()).isEqualTo(LmPolicyStatus.CANCELLED);
    }

    @Test
    void recordIssuedByTheTimeItIsLocked_isLeftIssued() {
        Contract contract = contract();
        LmPolicy policy = LmPolicy.open(new LmPolicyDraft(Issuances.TERMS, Tasks.CHILD), NOW);
        running(List.of(contract), List.of(policy));
        willAnswer(lock -> {
            ReflectionTestUtils.setField(contract, "status", ContractStatus.ISSUED);
            return null;
        }).given(contracts).lockFresh(List.of(contract));
        willAnswer(lock -> {
            policy.submit(NOW);
            policy.markIssued(ISSUED, NOW);
            return null;
        }).given(policies).lockFresh(List.of(policy));

        service.apply(new ContractTaskCancellation(Tasks.REQUEST, CANCELLED_AT, "EXPIRED", null), NOW);

        assertThat(contract.status()).isEqualTo(ContractStatus.ISSUED);
        assertThat(contract.state().cancellation()).isEmpty();
        verifyNoInteractions(contractStops);
        assertThat(policy.status()).isEqualTo(LmPolicyStatus.ISSUED);
        assertThat(policy.state().cancellation()).isEmpty();
        assertThat(policy.state().entityVersion()).isEqualTo(3);
    }

    @Test
    void finishedRecordsAmongRunningOnes_areLeftAndTheRunningOnesAfterThemAreStillStopped() {
        Contract issuedMeanwhile = contract();
        Contract running = Contract.open(new ContractDraft("DKM-2026-000002", Issuances.TERMS, Tasks.INSURED,
                new BigDecimal("12500.00")), NOW);
        LmPolicy issuedPolicy = LmPolicy.open(new LmPolicyDraft(Issuances.TERMS, Tasks.CHILD), NOW);
        LmPolicy runningPolicy = LmPolicy.open(new LmPolicyDraft(Issuances.TERMS, Tasks.SPOUSE), NOW);
        running(List.of(issuedMeanwhile, running), List.of(issuedPolicy, runningPolicy));
        willAnswer(lock -> {
            ReflectionTestUtils.setField(issuedMeanwhile, "status", ContractStatus.ISSUED);
            return null;
        }).given(contracts).lockFresh(List.of(issuedMeanwhile, running));
        willAnswer(lock -> {
            issuedPolicy.submit(NOW);
            issuedPolicy.markIssued(ISSUED, NOW);
            return null;
        }).given(policies).lockFresh(List.of(issuedPolicy, runningPolicy));

        service.apply(new ContractTaskCancellation(Tasks.REQUEST, CANCELLED_AT, "EXPIRED", null), NOW);

        verify(contractStops).stop(running, new Cancellation(CancelReason.EXPIRED, null, CANCELLED_AT), NOW);
        verifyNoMoreInteractions(contractStops);
        assertThat(issuedPolicy.status()).isEqualTo(LmPolicyStatus.ISSUED);
        assertThat(issuedPolicy.state().cancellation()).isEmpty();
        assertThat(runningPolicy.status()).isEqualTo(LmPolicyStatus.CANCELLED);
    }

    @Test
    void contractCancelledByTheTimeItIsLocked_isNotStoppedAgain() {
        Contract contract = contract();
        running(List.of(contract), List.of());
        willAnswer(lock -> {
            contract.cancel(new Cancellation(CancelReason.INSURED_REFUSED, "первое", Issuances.OPENED), Issuances.OPENED);
            return null;
        }).given(contracts).lockFresh(List.of(contract));

        service.apply(new ContractTaskCancellation(Tasks.REQUEST, CANCELLED_AT, "EXPIRED", null), NOW);

        verifyNoInteractions(contractStops);
    }

    @Test
    void recordCancelledByTheTimeItIsLocked_keepsItsFirstCancellation() {
        LmPolicy policy = LmPolicy.open(new LmPolicyDraft(Issuances.TERMS, Tasks.CHILD), NOW);
        Cancellation first = new Cancellation(CancelReason.INSURED_REFUSED, "первое", Issuances.OPENED);
        running(List.of(), List.of(policy));
        willAnswer(lock -> {
            policy.cancel(first, Issuances.OPENED);
            return null;
        }).given(policies).lockFresh(List.of(policy));

        service.apply(new ContractTaskCancellation(Tasks.REQUEST, CANCELLED_AT, "EXPIRED", null), NOW);

        assertThat(policy.state().cancellation()).contains(first);
        assertThat(policy.state().entityVersion()).isEqualTo(2);
    }

    @Test
    void recordInErrorByTheTimeItIsLocked_isCancelledKeepingItsError() {
        LmPolicy policy = LmPolicy.open(new LmPolicyDraft(Issuances.TERMS, Tasks.CHILD), NOW);
        IssueError undelivered = new IssueError("LM_POLICY_REQUEST_UNDELIVERED", "запрос не доставлен");
        running(List.of(), List.of(policy));
        willAnswer(lock -> {
            policy.submit(Issuances.OPENED);
            policy.markError(undelivered, Issuances.OPENED);
            return null;
        }).given(policies).lockFresh(List.of(policy));

        service.apply(new ContractTaskCancellation(Tasks.REQUEST, CANCELLED_AT, "EXPIRED", null), NOW);

        assertThat(policy.status()).isEqualTo(LmPolicyStatus.CANCELLED);
        assertThat(policy.state().error()).contains(undelivered);
        assertThat(policy.state().cancellation()).map(Cancellation::reason).contains(CancelReason.EXPIRED);
    }

    @Test
    void knownReasonCode_doesNotAskForTheUnknownCodeRule() {
        service.apply(new ContractTaskCancellation(Tasks.REQUEST, CANCELLED_AT, "EXPIRED", null), NOW);

        verifyNoInteractions(unknownReasons);
    }

    @Test
    void unknownReasonCode_cancelsEveryRecordWithTheOutcomeOfTheUnknownCodeRule() {
        Contract contract = contract();
        LmPolicy policy = LmPolicy.open(new LmPolicyDraft(Issuances.TERMS, Tasks.CHILD), NOW);
        ContractTaskCancellation cancellation =
                new ContractTaskCancellation(Tasks.REQUEST, CANCELLED_AT, "SOMETHING_ELSE", null);
        Cancellation asOther = new Cancellation(CancelReason.OTHER, "неизвестный код причины SOMETHING_ELSE",
                CANCELLED_AT);
        given(unknownReasons.asOther(cancellation)).willReturn(asOther);
        running(List.of(contract), List.of(policy));

        service.apply(cancellation, NOW);

        verify(contractStops).stop(contract, asOther, NOW);
        assertThat(policy.status()).isEqualTo(LmPolicyStatus.CANCELLED);
        assertThat(policy.state().cancellation()).contains(asOther);
    }

    private static Contract contract() {
        return Contract.open(new ContractDraft("DKM-2026-000001", Issuances.TERMS, Tasks.SPOUSE,
                new BigDecimal("34000.00")), NOW);
    }

    private void running(List<Contract> runningContracts, List<LmPolicy> runningPolicies) {
        given(contracts.findByRequestIndividualRequestIdAndStatusNotIn(Tasks.REQUEST,
                Set.of(ContractStatus.ISSUED, ContractStatus.CANCELLED))).willReturn(runningContracts);
        given(policies.findByRequestIndividualRequestIdAndStatusNotIn(Tasks.REQUEST,
                Set.of(LmPolicyStatus.ISSUED, LmPolicyStatus.CANCELLED))).willReturn(runningPolicies);
    }
}
