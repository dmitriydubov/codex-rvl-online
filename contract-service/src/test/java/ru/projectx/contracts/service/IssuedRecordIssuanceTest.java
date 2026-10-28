package ru.projectx.contracts.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

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
import ru.projectx.contracts.entity.LmPolicy;
import ru.projectx.contracts.entity.LmPolicyStatus;
import ru.projectx.contracts.entity.party.PersonOwner;
import ru.projectx.contracts.entity.issuance.TripPeriod;
import ru.projectx.contracts.message.ContractTask;
import ru.projectx.contracts.repository.ContractRepository;
import ru.projectx.contracts.repository.LmPolicyRepository;
import ru.projectx.contracts.support.Issuances;
import ru.projectx.contracts.support.Tasks;

class IssuedRecordIssuanceTest {

    private static final Instant NOW = Instant.parse("2026-02-10T07:05:00Z");

    private final LmPolicyRepository policies = mock();
    private final ContractRepository contracts = mock();
    private final PartySnapshotStore parties = mock();
    private final LmPolicyIssuance lmPolicies = new LmPolicyIssuance(policies, parties);
    private final KmContractIssuance kmContracts = new KmContractIssuance(contracts, mock(), parties);
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
    void issuedPolicy_withChangedDates_keepsItsPeriodVersionAndWarnsAsIssued() {
        given(parties.differ(eq(PersonOwner.lmPolicy(policy.id())), anyList())).willReturn(false);
        ContractTask moved = new ContractTask(2, Tasks.REQUEST, Tasks.INSURANCE_CONTRACT, Tasks.INSURER_POLICY,
                Tasks.FROM.plusDays(1), Tasks.TO, List.of(Tasks.insured("0.00"), Tasks.child("0.00")));

        lmPolicies.reconcile(new Issue(moved, Tasks.child("0.00")), Optional.of(policy), NOW);

        assertThat(policy.status()).isEqualTo(LmPolicyStatus.ISSUED);
        assertThat(policy.state().period()).isEqualTo(new TripPeriod(Tasks.FROM, Tasks.TO));
        assertThat(policy.state().entityVersion()).isEqualTo(3);
        verify(parties, never()).replaceIfChanged(any(), anyList(), any());
        verify(policies, never()).save(any());
        assertThat(warnings()).containsExactly("полис ЛМ " + policy.id() + " участника " + Tasks.CHILD + " заявки "
                + Tasks.REQUEST + ": полис ЛМ выпущен, изменения из задачи версии 2 не применены");
    }

    @Test
    void issuedPolicy_withInvalidParticipantData_staysIssuedWithoutErrorAndWarns() {
        ContractTask invalid = Tasks.task(Tasks.insured("0.00"), Tasks.relative(1, Tasks.CHILD, "0.00", null));

        lmPolicies.reconcile(new Issue(invalid, invalid.participants().get(1)), Optional.of(policy), NOW);

        assertThat(policy.status()).isEqualTo(LmPolicyStatus.ISSUED);
        assertThat(policy.state().error()).isEmpty();
        assertThat(warnings()).hasSize(1);
    }

    @Test
    void issuedPolicy_withUnchangedTask_staysSilent() {
        given(parties.differ(eq(PersonOwner.lmPolicy(policy.id())), anyList())).willReturn(false);
        ContractTask task = Tasks.task(Tasks.insured("0.00"), Tasks.child("0.00"));

        lmPolicies.reconcile(new Issue(task, Tasks.child("0.00")), Optional.of(policy), NOW);

        assertThat(policy.state().entityVersion()).isEqualTo(3);
        verify(policies, never()).save(any());
        assertThat(journal.list).isEmpty();
    }

    @Test
    void issuedContract_withChangedPremium_keepsItsDataAndWarnsAsIssued() {
        given(parties.differ(eq(PersonOwner.contract(contract.id())), anyList())).willReturn(false);
        ContractTask repriced = new ContractTask(2, Tasks.REQUEST, Tasks.INSURANCE_CONTRACT, Tasks.INSURER_POLICY,
                Tasks.FROM, Tasks.TO, List.of(Tasks.insured("0.00"), Tasks.spouse("36000.00")));

        kmContracts.reconcile(new Issue(repriced, Tasks.spouse("36000.00")), Optional.of(contract), NOW);

        assertThat(contract.status()).isEqualTo(ContractStatus.ISSUED);
        assertThat(contract.premiumAmount()).isEqualByComparingTo("34000.00");
        assertThat(contract.state().entityVersion()).isEqualTo(1);
        verify(contracts, never()).save(any());
        assertThat(warnings()).containsExactly("договор КМ " + contract.id() + " участника " + Tasks.SPOUSE + " заявки "
                + Tasks.REQUEST + ": договор КМ выпущен, изменения из задачи версии 2 не применены");
    }

    @Test
    void issuedContract_withUnchangedTask_staysSilentAndKeepsItsStatus() {
        given(parties.differ(eq(PersonOwner.contract(contract.id())), anyList())).willReturn(false);
        ContractTask task = Tasks.task(Tasks.insured("0.00"), Tasks.spouse("34000.00"));

        kmContracts.reconcile(new Issue(task, Tasks.spouse("34000.00")), Optional.of(contract), NOW);

        assertThat(contract.status()).isEqualTo(ContractStatus.ISSUED);
        assertThat(contract.state().entityVersion()).isEqualTo(1);
        assertThat(journal.list).isEmpty();
    }

    private List<String> warnings() {
        return journal.list.stream().filter(event -> event.getLevel() == Level.WARN)
                .map(ILoggingEvent::getFormattedMessage).toList();
    }

    private static Logger logger() {
        return (Logger) LoggerFactory.getLogger(RecordIssuance.class);
    }
}
