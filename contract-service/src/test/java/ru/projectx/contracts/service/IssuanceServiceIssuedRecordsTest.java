package ru.projectx.contracts.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import ru.projectx.contracts.entity.contract.Contract;
import ru.projectx.contracts.entity.contract.ContractStatus;
import ru.projectx.contracts.entity.issuance.DocumentKind;
import ru.projectx.contracts.entity.policy.LmPolicy;
import ru.projectx.contracts.entity.policy.LmPolicyStatus;
import ru.projectx.contracts.entity.issuance.RequestParticipant;
import ru.projectx.contracts.message.task.ContractTask;
import ru.projectx.contracts.repository.ContractRepository;
import ru.projectx.contracts.repository.LmPolicyRepository;
import ru.projectx.contracts.support.Issuances;
import ru.projectx.contracts.support.Tasks;

class IssuanceServiceIssuedRecordsTest {

    private static final Instant NOW = Instant.parse("2026-02-10T07:35:00Z");

    private final ContractRepository contracts = mock();
    private final LmPolicyRepository policies = mock();
    private final KmContractIssuance kmContracts = mock();
    private final LmPolicyIssuance lmPolicies = mock();
    private final IssuanceService service = new IssuanceService(contracts, policies, kmContracts, lmPolicies);
    private final LmPolicy policy = Issuances.issuedPolicy("8e2d0b17-5a44-4c90-b2e3-71f0c6a45d39", Tasks.CHILD);
    private final Contract contract = Issuances.issuedContract("3f1a2b4c-5d6e-4f70-8a9b-0c1d2e3f4a5b", Tasks.SPOUSE,
            "DKM-2026-000001");
    private final ListAppender<ILoggingEvent> journal = new ListAppender<>();

    @BeforeEach
    void startJournal() {
        journal.start();
        logger().addAppender(journal);
    }

    @AfterEach
    void stopJournal() {
        logger().detachAppender(journal);
        journal.stop();
    }

    @Test
    void issuedPolicy_isHandedToItsIssuanceAndNamedInTheComposition() {
        ContractTask task = Tasks.task(Tasks.insured("0.00"), Tasks.child("0.00"));
        active(List.of(), List.of(policy));

        List<RequestParticipant> composition = service.apply(task, NOW);

        verify(lmPolicies).reconcile(new Issue(task, task.participants().get(1)), Optional.of(policy), NOW);
        assertThat(composition).containsExactly(new RequestParticipant(Tasks.INSURED, DocumentKind.NONE),
                new RequestParticipant(Tasks.CHILD, DocumentKind.LM_POLICY));
        assertThat(journal.list).isEmpty();
    }

    @Test
    void relativeWithIssuedPolicyWhoStartsPaying_keepsThePolicyWarnsAndGetsNoContract() {
        ContractTask task = Tasks.task(Tasks.insured("0.00"), Tasks.relative(1, Tasks.CHILD, "5000.00", "CHILD"));
        active(List.of(), List.of(policy));

        List<RequestParticipant> composition = service.apply(task, NOW);

        assertThat(policy.status()).isEqualTo(LmPolicyStatus.ISSUED);
        assertThat(policy.state().cancellation()).isEmpty();
        assertThat(policy.state().entityVersion()).isEqualTo(3);
        verifyNoInteractions(kmContracts, lmPolicies);
        assertThat(composition).containsExactly(new RequestParticipant(Tasks.INSURED, DocumentKind.NONE),
                new RequestParticipant(Tasks.CHILD, DocumentKind.LM_POLICY));
        assertThat(warnings()).containsExactly("полис ЛМ " + policy.id() + " участника " + Tasks.CHILD + " заявки "
                + Tasks.REQUEST + ": полис ЛМ выпущен, смена вида документа из задачи версии 1 не применена");
    }

    @Test
    void participantWithIssuedPolicyMissingFromTask_keepsThePolicyAndWarns() {
        ContractTask task = Tasks.task(Tasks.insured("0.00"));
        active(List.of(), List.of(policy));

        List<RequestParticipant> composition = service.apply(task, NOW);

        assertThat(policy.status()).isEqualTo(LmPolicyStatus.ISSUED);
        assertThat(policy.state().cancellation()).isEmpty();
        assertThat(composition).containsExactly(new RequestParticipant(Tasks.INSURED, DocumentKind.NONE));
        assertThat(warnings()).containsExactly("полис ЛМ " + policy.id() + " участника " + Tasks.CHILD + " заявки "
                + Tasks.REQUEST + ": полис ЛМ выпущен, исключение участника задачей версии 1 не применено");
    }

    @Test
    void relativeWithIssuedContractWhoStopsPaying_keepsTheContractWarnsAndGetsNoPolicy() {
        ContractTask task = Tasks.task(Tasks.insured("0.00"), Tasks.spouse("0.00"));
        active(List.of(contract), List.of());

        List<RequestParticipant> composition = service.apply(task, NOW);

        assertThat(contract.status()).isEqualTo(ContractStatus.ISSUED);
        verifyNoInteractions(kmContracts, lmPolicies);
        assertThat(composition).containsExactly(new RequestParticipant(Tasks.INSURED, DocumentKind.NONE),
                new RequestParticipant(Tasks.SPOUSE, DocumentKind.KM_CONTRACT));
        assertThat(warnings()).containsExactly("договор КМ " + contract.id() + " участника " + Tasks.SPOUSE + " заявки "
                + Tasks.REQUEST + ": договор КМ выпущен, смена вида документа из задачи версии 1 не применена");
    }

    @Test
    void insuredWithIssuedContractWhoStopsPaying_keepsTheContractInTheCompositionAndWarns() {
        Contract insuredContract = Issuances.issuedContract("4a2b3c5d-6e7f-4081-9a2b-1c2d3e4f5a6b", Tasks.INSURED,
                "DKM-2026-000002");
        active(List.of(insuredContract), List.of());

        List<RequestParticipant> composition = service.apply(Tasks.task(Tasks.insured("0.00")), NOW);

        assertThat(insuredContract.status()).isEqualTo(ContractStatus.ISSUED);
        verifyNoInteractions(kmContracts, lmPolicies);
        assertThat(composition).containsExactly(new RequestParticipant(Tasks.INSURED, DocumentKind.KM_CONTRACT));
        assertThat(warnings()).hasSize(1);
    }

    @Test
    void openContractBesideAnIssuedPolicy_neitherHidesThePolicyNorOpensAnotherOneWhenThePaymentIsGone() {
        Contract openContract = Issuances.contract("3f1a2b4c-5d6e-4f70-8a9b-0c1d2e3f4a5b", Tasks.CHILD,
                "DKM-2026-000002");
        ContractTask task = Tasks.task(Tasks.insured("0.00"), Tasks.child("0.00"));
        active(List.of(openContract), List.of(policy));

        List<RequestParticipant> composition = service.apply(task, NOW);

        verify(lmPolicies).reconcile(new Issue(task, task.participants().get(1)), Optional.of(policy), NOW);
        verifyNoInteractions(kmContracts);
        assertThat(openContract.status()).isEqualTo(ContractStatus.NEW);
        assertThat(composition).contains(new RequestParticipant(Tasks.CHILD, DocumentKind.LM_POLICY));
    }

    @Test
    void openPolicyBesideAnIssuedOne_isLeftAndNoContractIsOpenedWhenThePaymentAppears() {
        LmPolicy open = Issuances.policy("9f3e1c28-6b55-4da1-93f4-82a1d7b56e40", Tasks.CHILD);
        ContractTask task = Tasks.task(Tasks.insured("0.00"), Tasks.relative(1, Tasks.CHILD, "5000.00", "CHILD"));
        active(List.of(), List.of(open, policy));

        List<RequestParticipant> composition = service.apply(task, NOW);

        verifyNoInteractions(kmContracts, lmPolicies);
        assertThat(open.status()).isEqualTo(LmPolicyStatus.NEW);
        assertThat(policy.status()).isEqualTo(LmPolicyStatus.ISSUED);
        assertThat(composition).contains(new RequestParticipant(Tasks.CHILD, DocumentKind.LM_POLICY));
        assertThat(warnings()).hasSize(1);
    }

    @Test
    void participantWithIssuedContractMissingFromTask_keepsTheContractAndWarns() {
        active(List.of(contract), List.of());

        service.apply(Tasks.task(Tasks.insured("0.00")), NOW);

        assertThat(contract.status()).isEqualTo(ContractStatus.ISSUED);
        assertThat(contract.state().cancellation()).isEmpty();
        assertThat(warnings()).containsExactly("договор КМ " + contract.id() + " участника " + Tasks.SPOUSE + " заявки "
                + Tasks.REQUEST + ": договор КМ выпущен, исключение участника задачей версии 1 не применено");
    }

    private void active(List<Contract> activeContracts, List<LmPolicy> activePolicies) {
        given(contracts.findRunningOrFailedAfterPayment(Tasks.REQUEST, ContractStatus.ERROR, ContractStatus.CANCELLED))
                .willReturn(activeContracts);
        given(policies.findByRequestIndividualRequestIdAndStatusNotIn(eq(Tasks.REQUEST), any()))
                .willReturn(activePolicies);
    }

    private List<String> warnings() {
        return journal.list.stream().filter(event -> event.getLevel() == Level.WARN)
                .map(ILoggingEvent::getFormattedMessage).toList();
    }

    private static Logger logger() {
        return (Logger) LoggerFactory.getLogger(IssuedRecords.class);
    }
}
