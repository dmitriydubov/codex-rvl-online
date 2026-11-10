package ru.projectx.contracts.service.issuance;

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

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import ru.projectx.contracts.entity.contract.Contract;
import ru.projectx.contracts.entity.contract.ContractStatus;
import ru.projectx.contracts.entity.issuance.CancelReason;
import ru.projectx.contracts.entity.issuance.Cancellation;
import ru.projectx.contracts.entity.issuance.DocumentKind;
import ru.projectx.contracts.entity.issuance.RequestParticipant;
import ru.projectx.contracts.entity.policy.LmPolicy;
import ru.projectx.contracts.message.task.ContractTask;
import ru.projectx.contracts.repository.issuance.ContractRepository;
import ru.projectx.contracts.repository.issuance.LmPolicyRepository;
import ru.projectx.contracts.service.payment.cancellation.ContractStops;
import ru.projectx.contracts.support.Issuances;
import ru.projectx.contracts.support.Tasks;

class IssuanceServiceDeferredStopsTest {

    private static final Instant NOW = Instant.parse("2026-02-10T07:35:00Z");

    private final ContractRepository contracts = mock();
    private final LmPolicyRepository policies = mock();
    private final KmContractIssuance kmContracts = mock();
    private final LmPolicyIssuance lmPolicies = mock();
    private final ContractStops contractStops = mock();
    private final IssuanceService service =
            new IssuanceService(contracts, policies, kmContracts, lmPolicies, contractStops);
    private final Contract invoiced = Issuances.invoicedContract("3f1a2b4c-5d6e-4f70-8a9b-0c1d2e3f4a5b", Tasks.SPOUSE,
            "DKM-2026-000001");

    @Test
    void deferredCancellations_areAskedForTheContractsStillHeldAfterTheLock() {
        Contract stopped = Issuances.contract("4a2b3c5d-6e7f-4081-9a2b-1c2d3e4f5a6b", Tasks.INSURED, "DKM-2026-000002");
        active(List.of(invoiced, stopped), List.of());
        willAnswer(lock -> {
            stopped.cancel(new Cancellation(CancelReason.EXPIRED, null, Issuances.OPENED), Issuances.OPENED);
            return null;
        }).given(contracts).lockFresh(List.of(invoiced, stopped));

        service.apply(Tasks.task(Tasks.insured("0.00"), Tasks.spouse("34000.00")), NOW);

        InOrder order = inOrder(contracts, policies, contractStops);
        order.verify(contracts).lockFresh(List.of(invoiced, stopped));
        order.verify(policies).lockFresh(List.of());
        order.verify(contractStops).deferred(List.of(invoiced.id()));
    }

    @Test
    void runningContractOfAPayingParticipant_isReconciledAndThenResumed() {
        ContractTask task = Tasks.task(Tasks.insured("0.00"), Tasks.spouse("34000.00"));
        active(List.of(invoiced), List.of());

        service.apply(task, NOW);

        InOrder order = inOrder(kmContracts, contractStops);
        order.verify(kmContracts).reconcile(new Issue(task, task.participants().get(1)), Optional.of(invoiced), NOW);
        order.verify(contractStops).resume(invoiced, NOW);
        verify(contractStops, never()).stop(any(), any(), any());
    }

    @Test
    void contractWithDeferredCancellation_holdsThePayingParticipantSoNoSecondContractIsOpenedAndItIsResumed() {
        ContractTask task = Tasks.task(Tasks.insured("0.00"), Tasks.spouse("34000.00"));
        active(List.of(invoiced), List.of());
        given(contractStops.deferred(List.of(invoiced.id()))).willReturn(Set.of(invoiced.id()));

        List<RequestParticipant> composition = service.apply(task, NOW);

        verify(kmContracts).reconcile(new Issue(task, task.participants().get(1)), Optional.of(invoiced), NOW);
        verify(kmContracts, never()).reconcile(any(), eq(Optional.empty()), any());
        verify(contractStops).resume(invoiced, NOW);
        verify(contractStops, never()).stop(any(), any(), any());
        assertThat(composition).contains(new RequestParticipant(Tasks.SPOUSE, DocumentKind.KM_CONTRACT));
        verifyNoInteractions(lmPolicies);
    }

    @Test
    void newParticipantWithoutAnyContract_getsOneOpenedAndNothingIsResumed() {
        ContractTask task = Tasks.task(Tasks.insured("0.00"), Tasks.spouse("34000.00"));
        active(List.of(), List.of());

        service.apply(task, NOW);

        verify(kmContracts).reconcile(new Issue(task, task.participants().get(1)), Optional.empty(), NOW);
        verify(contractStops, never()).resume(any(), any());
    }

    @Test
    void contractWithDeferredCancellationOfARelativeWhoStopsPaying_isStoppedAgainAsKindChangedAndAPolicyIsOpened() {
        ContractTask task = Tasks.task(Tasks.insured("0.00"), Tasks.spouse("0.00"));
        active(List.of(invoiced), List.of());
        given(contractStops.deferred(List.of(invoiced.id()))).willReturn(Set.of(invoiced.id()));

        service.apply(task, NOW);

        verify(contractStops).stop(invoiced,
                new Cancellation(CancelReason.OTHER, "вид документа по участнику изменён", NOW), NOW);
        verify(lmPolicies).reconcile(new Issue(task, task.participants().get(1)), Optional.empty(), NOW);
        verify(contractStops, never()).resume(any(), any());
        verifyNoInteractions(kmContracts);
        assertThat(invoiced.status()).isEqualTo(ContractStatus.INVOICE_ISSUED);
    }

    @Test
    void policyOpenedBesideAContractWithDeferredCancellation_isKeptAndNeitherASecondPolicyNorASecondStopFollows() {
        LmPolicy policy = Issuances.policy("9f3e1c28-6b55-4da1-93f4-82a1d7b56e40", Tasks.SPOUSE);
        ContractTask task = Tasks.task(Tasks.insured("0.00"), Tasks.spouse("0.00"));
        active(List.of(invoiced), List.of(policy));
        given(contractStops.deferred(List.of(invoiced.id()))).willReturn(Set.of(invoiced.id()));

        List<RequestParticipant> composition = service.apply(task, NOW);

        verify(lmPolicies).reconcile(new Issue(task, task.participants().get(1)), Optional.of(policy), NOW);
        verify(contractStops, never()).stop(any(), any(), any());
        verifyNoInteractions(kmContracts);
        assertThat(composition).contains(new RequestParticipant(Tasks.SPOUSE, DocumentKind.LM_POLICY));
    }

    @Test
    void contractWithDeferredCancellationOfAParticipantMissingFromTheTask_isStoppedAgainAsExcluded() {
        active(List.of(invoiced), List.of());
        given(contractStops.deferred(List.of(invoiced.id()))).willReturn(Set.of(invoiced.id()));

        service.apply(Tasks.task(Tasks.insured("0.00")), NOW);

        verify(contractStops).stop(invoiced,
                new Cancellation(CancelReason.OTHER, "участник исключён из состава поездки", NOW), NOW);
        verify(contractStops, never()).resume(any(), any());
        verifyNoInteractions(kmContracts, lmPolicies);
    }

    private void active(List<Contract> activeContracts, List<LmPolicy> activePolicies) {
        given(contracts.findRunningOrFailedAfterPayment(Tasks.REQUEST, ContractStatus.ERROR,
                ContractStatus.CANCELLED)).willReturn(activeContracts);
        given(policies.findByRequestIndividualRequestIdAndStatusNotIn(eq(Tasks.REQUEST), any()))
                .willReturn(activePolicies);
    }
}
