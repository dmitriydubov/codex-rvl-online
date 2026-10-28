package ru.projectx.contracts.service.issuance;

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
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import ru.projectx.contracts.entity.issuance.IssueError;
import ru.projectx.contracts.entity.policy.LmPolicy;
import ru.projectx.contracts.entity.policy.LmPolicyStatus;
import ru.projectx.contracts.entity.party.PersonOwner;
import ru.projectx.contracts.entity.party.PersonRole;
import ru.projectx.contracts.entity.issuance.RequestReference;
import ru.projectx.contracts.entity.issuance.TripPeriod;
import ru.projectx.contracts.message.task.ContractTask;
import ru.projectx.contracts.repository.issuance.LmPolicyRepository;
import ru.projectx.contracts.support.Issuances;
import ru.projectx.contracts.support.Tasks;

class LmPolicyIssuanceTest {

    private static final Instant NOW = Instant.parse("2026-02-10T07:05:00Z");
    private static final UUID OTHER_PROGRAM = UUID.fromString("7b3d9e1f-5a2c-4d8e-b0f6-1c2a3b4d5e6f");

    private final LmPolicyRepository policies = mock();
    private final PartySnapshotStore parties = mock();
    private final LmPolicyIssuance issuance = new LmPolicyIssuance(policies, parties);
    private final ContractTask task = Tasks.task(Tasks.insured("0.00"), Tasks.child("0.00"));
    private final ListAppender<ILoggingEvent> journal = new ListAppender<>();

    @BeforeEach
    void repositoryReturnsSavedPolicy() {
        given(policies.save(any())).willAnswer(invocation -> invocation.getArgument(0));
        journal.start();
        logger().addAppender(journal);
    }

    @AfterEach
    void stopJournal() {
        logger().detachAppender(journal);
        journal.stop();
    }

    @Test
    void newPolicy_getsTripDatesProgramPolicyholderAndOneInsuredParty() {
        issuance.reconcile(new Issue(task, Tasks.child("0.00")), Optional.empty(), NOW);

        LmPolicy policy = savedPolicy();
        assertThat(policy.status()).isEqualTo(LmPolicyStatus.NEW);
        assertThat(policy.participantId()).isEqualTo(Tasks.CHILD);
        assertThat(policy.state().policyholderParticipantId()).isEqualTo(Tasks.INSURED);
        assertThat(policy.state().programId()).contains(Tasks.PROGRAM);
        assertThat(policy.state().period()).isEqualTo(new TripPeriod(Tasks.FROM, Tasks.TO));
        assertThat(policy.state().request())
                .isEqualTo(new RequestReference(Tasks.REQUEST, Tasks.INSURANCE_CONTRACT, Tasks.INSURER_POLICY));
        verify(parties).store(PersonOwner.lmPolicy(policy.id()),
                List.of(new Party(PersonRole.INSURED, PersonSnapshots.of(Tasks.child("0.00")))), NOW);
    }

    @Test
    void existingPolicy_movesWithTripDatesAndBumpsVersionOnPartyChange() {
        LmPolicy existing = existing();
        given(parties.replaceIfChanged(eq(PersonOwner.lmPolicy(existing.id())), anyList(), eq(NOW))).willReturn(true);
        ContractTask moved = Tasks.task(Tasks.FROM.plusDays(1), Tasks.TO, Tasks.insured("0.00"), Tasks.child("0.00"));

        issuance.reconcile(new Issue(moved, Tasks.child("0.00")), Optional.of(existing), NOW);

        verify(policies, never()).save(any());
        assertThat(existing.state().period()).isEqualTo(new TripPeriod(Tasks.FROM.plusDays(1), Tasks.TO));
        assertThat(existing.state().entityVersion()).isEqualTo(2);
    }

    @Test
    void existingPolicy_withNewDatesOnly_bumpsVersionOnce() {
        LmPolicy existing = existing();
        given(parties.replaceIfChanged(eq(PersonOwner.lmPolicy(existing.id())), anyList(), eq(NOW))).willReturn(false);
        ContractTask moved = Tasks.task(Tasks.FROM.plusDays(1), Tasks.TO, Tasks.insured("0.00"), Tasks.child("0.00"));

        issuance.reconcile(new Issue(moved, Tasks.child("0.00")), Optional.of(existing), NOW);

        assertThat(existing.state().period()).isEqualTo(new TripPeriod(Tasks.FROM.plusDays(1), Tasks.TO));
        assertThat(existing.state().entityVersion()).isEqualTo(2);
    }

    @Test
    void existingPolicy_withAnotherProgram_takesItAndBumpsVersion() {
        LmPolicy existing = existing();
        given(parties.replaceIfChanged(eq(PersonOwner.lmPolicy(existing.id())), anyList(), eq(NOW))).willReturn(false);
        ContractTask reprogrammed = Tasks.task(Tasks.insured("0.00"),
                Tasks.relative(2, Tasks.CHILD, "0.00", "CHILD", OTHER_PROGRAM));

        issuance.reconcile(new Issue(reprogrammed, reprogrammed.participants().get(1)), Optional.of(existing), NOW);

        assertThat(existing.state().programId()).contains(OTHER_PROGRAM);
        assertThat(existing.state().entityVersion()).isEqualTo(2);
    }

    @Test
    void existingPolicy_withSameDatesProgramAndParty_keepsVersion() {
        LmPolicy existing = existing();
        given(parties.replaceIfChanged(eq(PersonOwner.lmPolicy(existing.id())), anyList(), eq(NOW))).willReturn(false);

        issuance.reconcile(new Issue(task, Tasks.child("0.00")), Optional.of(existing), NOW);

        assertThat(existing.state().entityVersion()).isEqualTo(1);
    }

    @Test
    void invalidParticipant_opensPolicyInErrorWithoutParty() {
        ContractTask invalid = Tasks.task(Tasks.insured("0.00"), Tasks.relative(1, Tasks.CHILD, "0.00", null));

        issuance.reconcile(new Issue(invalid, invalid.participants().get(1)), Optional.empty(), NOW);

        LmPolicy policy = savedPolicy();
        assertThat(policy.status()).isEqualTo(LmPolicyStatus.ERROR);
        assertThat(policy.state().error()).contains(new IssueError("RELATION_TYPE_REQUIRED",
                "поле participants[1].personal_data.relation_type обязательно для родственника"));
        verify(parties, never()).store(any(), anyList(), any());
    }

    @Test
    void participantWithoutProgram_opensPolicyInErrorWithoutProgramAndParty() {
        ContractTask invalid = Tasks.task(Tasks.insured("0.00"), Tasks.relative(1, Tasks.CHILD, "0.00", "CHILD", null));

        issuance.reconcile(new Issue(invalid, invalid.participants().get(1)), Optional.empty(), NOW);

        LmPolicy policy = savedPolicy();
        assertThat(policy.status()).isEqualTo(LmPolicyStatus.ERROR);
        assertThat(policy.state().programId()).isEmpty();
        assertThat(policy.state().error()).contains(new IssueError("PROGRAM_REQUIRED",
                "поле participants[1].program_id обязательно для участника, которому выпускается документ"));
        verify(parties, never()).store(any(), anyList(), any());
    }

    @Test
    void invalidParticipant_withExistingPolicy_marksItInError() {
        LmPolicy existing = existing();
        ContractTask invalid = Tasks.task(Tasks.insured("0.00"), Tasks.relative(1, Tasks.CHILD, "0.00", null));

        issuance.reconcile(new Issue(invalid, invalid.participants().get(1)), Optional.of(existing), NOW);

        assertThat(existing.status()).isEqualTo(LmPolicyStatus.ERROR);
        verify(policies, never()).save(any());
    }

    @Test
    void submittedPolicy_ignoresChangedTaskAndWarnsWithIdentifiersAndTaskVersion() {
        LmPolicy submitted = existing();
        submitted.submit(NOW);
        given(parties.differ(eq(PersonOwner.lmPolicy(submitted.id())), anyList())).willReturn(false);
        ContractTask changedDates = new ContractTask(2, Tasks.REQUEST, Tasks.INSURANCE_CONTRACT, Tasks.INSURER_POLICY,
                Tasks.FROM.plusDays(1), Tasks.TO, List.of(Tasks.insured("0.00"), Tasks.child("0.00")));

        issuance.reconcile(new Issue(changedDates, Tasks.child("0.00")), Optional.of(submitted), NOW);

        assertThat(submitted.state().period()).isEqualTo(new TripPeriod(Tasks.FROM, Tasks.TO));
        assertThat(submitted.state().entityVersion()).isEqualTo(2);
        verify(parties, never()).replaceIfChanged(any(), anyList(), any());
        assertThat(journal.list).filteredOn(event -> event.getLevel() == Level.WARN)
                .extracting(ILoggingEvent::getFormattedMessage)
                .containsExactly("полис ЛМ " + submitted.id() + " участника " + Tasks.CHILD + " заявки " + Tasks.REQUEST
                        + ": полис ЛМ передан в 1С:УСК, изменения из задачи версии 2 не применены");
    }

    @Test
    void submittedPolicy_withChangedPartyOnly_warns() {
        LmPolicy submitted = existing();
        submitted.submit(NOW);
        given(parties.differ(eq(PersonOwner.lmPolicy(submitted.id())), anyList())).willReturn(true);

        issuance.reconcile(new Issue(task, Tasks.child("0.00")), Optional.of(submitted), NOW);

        assertThat(submitted.state().entityVersion()).isEqualTo(2);
        assertThat(journal.list).filteredOn(event -> event.getLevel() == Level.WARN).hasSize(1);
    }

    @Test
    void submittedPolicy_withInvalidParticipantData_staysSubmittedAndWarns() {
        LmPolicy submitted = existing();
        submitted.submit(NOW);
        ContractTask invalid = Tasks.task(Tasks.insured("0.00"), Tasks.relative(1, Tasks.CHILD, "0.00", null));

        issuance.reconcile(new Issue(invalid, invalid.participants().get(1)), Optional.of(submitted), NOW);

        assertThat(submitted.status()).isEqualTo(LmPolicyStatus.SUBMITTED);
        assertThat(journal.list).filteredOn(event -> event.getLevel() == Level.WARN).hasSize(1);
    }

    @Test
    void submittedPolicy_withUnchangedTask_staysSilent() {
        LmPolicy submitted = existing();
        submitted.submit(NOW);
        given(parties.differ(eq(PersonOwner.lmPolicy(submitted.id())), anyList())).willReturn(false);

        issuance.reconcile(new Issue(task, Tasks.child("0.00")), Optional.of(submitted), NOW);

        assertThat(submitted.state().entityVersion()).isEqualTo(2);
        assertThat(journal.list).isEmpty();
    }

    private LmPolicy savedPolicy() {
        ArgumentCaptor<LmPolicy> saved = ArgumentCaptor.captor();
        verify(policies).save(saved.capture());
        return saved.getValue();
    }

    private static LmPolicy existing() {
        return Issuances.policy("8e2d0b17-5a44-4c90-b2e3-71f0c6a45d39", Tasks.CHILD);
    }

    private static Logger logger() {
        return (Logger) LoggerFactory.getLogger(RecordIssuance.class);
    }
}
