package ru.projectx.contracts.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

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
import ru.projectx.contracts.repository.ContractRepository;
import ru.projectx.contracts.repository.LmPolicyRepository;
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
    private final TaskCancellationService service = new TaskCancellationService(contracts, policies, unknownReasons);

    @Test
    void cancellation_stopsEveryContractAndPolicyOfTheRequestWithMappedReasonCommentAndMoment() {
        Contract contract = Contract.open(new ContractDraft("DKM-2026-000001", Issuances.TERMS, Tasks.SPOUSE,
                new BigDecimal("34000.00")), NOW);
        LmPolicy policy = LmPolicy.open(new LmPolicyDraft(Issuances.TERMS, Tasks.CHILD), NOW);
        running(List.of(contract), List.of(policy));

        service.apply(new ContractTaskCancellation(Tasks.REQUEST, CANCELLED_AT, "HR_REJECTED", "Стенд: комментарий"),
                NOW);

        Cancellation expected = new Cancellation(CancelReason.REQUEST_CANCELLED, "Стенд: комментарий", CANCELLED_AT);
        assertThat(contract.status()).isEqualTo(ContractStatus.CANCELLED);
        assertThat(contract.state().cancellation()).contains(expected);
        assertThat(contract.state().entityVersion()).isEqualTo(2);
        assertThat(policy.status()).isEqualTo(LmPolicyStatus.CANCELLED);
        assertThat(policy.state().cancellation()).contains(expected);
    }

    @Test
    void records_areLockedAndReadAfreshContractsFirstBeforeTheyAreCancelled() {
        Contract contract = Contract.open(new ContractDraft("DKM-2026-000001", Issuances.TERMS, Tasks.SPOUSE,
                new BigDecimal("34000.00")), NOW);
        LmPolicy policy = LmPolicy.open(new LmPolicyDraft(Issuances.TERMS, Tasks.CHILD), NOW);
        running(List.of(contract), List.of(policy));
        willAnswer(lock -> {
            assertThat(contract.status()).isEqualTo(ContractStatus.NEW);
            return null;
        }).given(contracts).lockFresh(List.of(contract));
        willAnswer(lock -> {
            assertThat(contract.status()).isEqualTo(ContractStatus.CANCELLED);
            assertThat(policy.status()).isEqualTo(LmPolicyStatus.NEW);
            return null;
        }).given(policies).lockFresh(List.of(policy));

        service.apply(new ContractTaskCancellation(Tasks.REQUEST, CANCELLED_AT, "EXPIRED", null), NOW);

        InOrder order = inOrder(contracts, policies);
        order.verify(contracts).lockFresh(List.of(contract));
        order.verify(policies).lockFresh(List.of(policy));
        assertThat(policy.status()).isEqualTo(LmPolicyStatus.CANCELLED);
    }

    @Test
    void recordIssuedByTheTimeItIsLocked_isLeftIssued() {
        Contract contract = Contract.open(new ContractDraft("DKM-2026-000001", Issuances.TERMS, Tasks.SPOUSE,
                new BigDecimal("34000.00")), NOW);
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
        assertThat(policy.status()).isEqualTo(LmPolicyStatus.ISSUED);
        assertThat(policy.state().cancellation()).isEmpty();
        assertThat(policy.state().entityVersion()).isEqualTo(3);
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
        Contract contract = Contract.open(new ContractDraft("DKM-2026-000001", Issuances.TERMS, Tasks.SPOUSE,
                new BigDecimal("34000.00")), NOW);
        LmPolicy policy = LmPolicy.open(new LmPolicyDraft(Issuances.TERMS, Tasks.CHILD), NOW);
        ContractTaskCancellation cancellation =
                new ContractTaskCancellation(Tasks.REQUEST, CANCELLED_AT, "SOMETHING_ELSE", null);
        Cancellation asOther = new Cancellation(CancelReason.OTHER, "неизвестный код причины SOMETHING_ELSE",
                CANCELLED_AT);
        given(unknownReasons.asOther(cancellation)).willReturn(asOther);
        running(List.of(contract), List.of(policy));

        service.apply(cancellation, NOW);

        assertThat(contract.status()).isEqualTo(ContractStatus.CANCELLED);
        assertThat(contract.state().cancellation()).contains(asOther);
        assertThat(policy.status()).isEqualTo(LmPolicyStatus.CANCELLED);
        assertThat(policy.state().cancellation()).contains(asOther);
    }

    private void running(List<Contract> runningContracts, List<LmPolicy> runningPolicies) {
        given(contracts.findByRequestIndividualRequestIdAndStatusNotIn(Tasks.REQUEST,
                Set.of(ContractStatus.ISSUED, ContractStatus.CANCELLED))).willReturn(runningContracts);
        given(policies.findByRequestIndividualRequestIdAndStatusNotIn(Tasks.REQUEST,
                Set.of(LmPolicyStatus.ISSUED, LmPolicyStatus.CANCELLED))).willReturn(runningPolicies);
    }
}
