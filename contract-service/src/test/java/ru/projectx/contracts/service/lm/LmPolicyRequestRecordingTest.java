package ru.projectx.contracts.service.lm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import ru.projectx.contracts.entity.ContractPerson;
import ru.projectx.contracts.entity.IssueError;
import ru.projectx.contracts.entity.LmPolicy;
import ru.projectx.contracts.entity.LmPolicyStatus;
import ru.projectx.contracts.entity.MessageEnvelope;
import ru.projectx.contracts.entity.OutboxDraft;
import ru.projectx.contracts.entity.PersonOwner;
import ru.projectx.contracts.entity.PersonRole;
import ru.projectx.contracts.message.MessageMeta;
import ru.projectx.contracts.repository.ContractPersonRepository;
import ru.projectx.contracts.repository.LmPolicyRepository;
import ru.projectx.contracts.service.PersonSnapshots;
import ru.projectx.contracts.service.outbox.OutboxJournal;
import ru.projectx.contracts.service.status.IssuanceSnapshot;
import ru.projectx.contracts.service.status.RequestStatusPublication;
import ru.projectx.contracts.support.Issuances;
import ru.projectx.contracts.support.Tasks;

class LmPolicyRequestRecordingTest {

    private static final Instant NOW = Instant.parse("2026-02-10T07:12:00Z");
    private static final UUID POLICY_ID = UUID.fromString("8e2d0b17-5a44-4c90-b2e3-71f0c6a45d39");
    private static final UUID MESSAGE_ID = UUID.fromString("2a7c5e90-4b18-4d63-91f7-8c0a3e6b1d47");
    private static final ExternalIdentifiers IDENTIFIERS = new ExternalIdentifiers(
            UUID.fromString("6b02f1cc-9d47-4e35-8a20-7f1c3e5b09a2"), UUID.fromString("8c41e2ab-55d0-4a17-9b63-2f0e7c1d4a88"),
            UUID.fromString("1d9a6e77-4c02-4b88-a15e-30f7b2c9de41"));
    private static final IssueError ARCHIVED = new IssueError("PROGRAM_ARCHIVED", "программа страхования в архиве");

    private final LmPolicyRepository policies = mock();
    private final ContractPersonRepository persons = mock();
    private final LmPolicyRequestMessages messages = mock();
    private final OutboxJournal journal = mock();
    private final RequestStatusPublication statuses = mock();
    private final LmPolicyRequestRecording recording = new LmPolicyRequestRecording(policies, persons, messages,
            journal, statuses, Clock.fixed(NOW, ZoneOffset.UTC));
    private final LmPolicy policy = Issuances.policy(POLICY_ID.toString(), Tasks.CHILD);
    private final MessageMeta meta = new MessageMeta(MESSAGE_ID, Tasks.REQUEST, "rvl.lm-policy.requested",
            "LmPolicyRequested", "1.0", OffsetDateTime.parse("2026-02-10T10:12:00+03:00"), 2, "RVL-CONTRACTS");

    @BeforeEach
    void policyIsStoredWithItsInsuredParty() {
        given(policies.findById(POLICY_ID)).willReturn(Optional.of(policy));
        given(persons.findByLmPolicyIdOrderByRole(POLICY_ID)).willReturn(List.of(ContractPerson.snapshot(
                PersonOwner.lmPolicy(POLICY_ID), PersonRole.INSURED, PersonSnapshots.of(Tasks.child("0.00")), NOW)));
        given(messages.meta(policy, NOW)).willReturn(meta);
        given(messages.write(eq(meta), same(policy), any(), eq(IDENTIFIERS))).willReturn("{\"data\": {}}");
    }

    @Test
    void submit_turnsThePolicyIntoSubmittedAndRecordsTheRequestWithTheMetaOfTheSubmittedPolicy() {
        recording.submit(POLICY_ID, IDENTIFIERS);

        assertThat(policy.status()).isEqualTo(LmPolicyStatus.SUBMITTED);
        InOrder order = inOrder(messages, journal);
        order.verify(messages).meta(policy, NOW);
        order.verify(messages).write(eq(meta), same(policy), eq(PersonSnapshots.of(Tasks.child("0.00"))), eq(IDENTIFIERS));
        ArgumentCaptor<OutboxDraft> draft = ArgumentCaptor.captor();
        order.verify(journal).record(draft.capture(), eq(NOW));
        assertThat(draft.getValue().aggregateType()).isEqualTo("LM_POLICY");
        assertThat(draft.getValue().aggregateId()).isEqualTo(POLICY_ID);
        assertThat(draft.getValue().envelope()).isEqualTo(new MessageEnvelope(MESSAGE_ID, Tasks.REQUEST,
                "rvl.lm-policy.requested", "LmPolicyRequested", 2));
        assertThat(draft.getValue().payload()).isEqualTo("{\"data\": {}}");
        verifyNoInteractions(statuses);
    }

    @Test
    void submit_sendsTheInsuredPartyEvenWhenAnotherRoleIsStoredBeforeIt() {
        given(persons.findByLmPolicyIdOrderByRole(POLICY_ID)).willReturn(List.of(
                ContractPerson.snapshot(PersonOwner.lmPolicy(POLICY_ID), PersonRole.POLICYHOLDER,
                        PersonSnapshots.of(Tasks.insured("0.00")), NOW),
                ContractPerson.snapshot(PersonOwner.lmPolicy(POLICY_ID), PersonRole.INSURED,
                        PersonSnapshots.of(Tasks.child("0.00")), NOW)));

        recording.submit(POLICY_ID, IDENTIFIERS);

        verify(messages).write(eq(meta), same(policy), eq(PersonSnapshots.of(Tasks.child("0.00"))), eq(IDENTIFIERS));
    }

    @Test
    void submit_leavesAPolicyThatIsNoLongerNewAlone() {
        policy.submit(NOW);

        recording.submit(POLICY_ID, IDENTIFIERS);

        assertThat(policy.state().entityVersion()).isEqualTo(2);
        verifyNoInteractions(journal, messages);
    }

    @Test
    void submit_withoutInsuredSnapshot_isIllegalState() {
        given(persons.findByLmPolicyIdOrderByRole(POLICY_ID)).willReturn(List.of());

        assertThatThrownBy(() -> recording.submit(POLICY_ID, IDENTIFIERS)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(POLICY_ID.toString());
        verifyNoInteractions(journal);
    }

    @Test
    void refuse_marksThePolicyInErrorBetweenWatchingAndReportingTheRequest() {
        IssuanceSnapshot before = mock();
        given(statuses.watch(Tasks.REQUEST)).willReturn(before);

        recording.refuse(POLICY_ID, ARCHIVED);

        assertThat(policy.status()).isEqualTo(LmPolicyStatus.ERROR);
        assertThat(policy.state().error()).contains(ARCHIVED);
        InOrder order = inOrder(statuses);
        order.verify(statuses).watch(Tasks.REQUEST);
        order.verify(statuses).afterRecordChange(before, NOW);
        verifyNoInteractions(journal);
    }

    @Test
    void refuse_leavesAPolicyThatIsNoLongerNewAlone() {
        policy.submit(NOW);

        recording.refuse(POLICY_ID, ARCHIVED);

        assertThat(policy.status()).isEqualTo(LmPolicyStatus.SUBMITTED);
        verifyNoInteractions(statuses);
    }

    @Test
    void unknownPolicy_isIllegalState() {
        UUID unknown = UUID.fromString("00000000-0000-4000-8000-000000000001");
        given(policies.findById(unknown)).willReturn(Optional.empty());

        assertThatThrownBy(() -> recording.submit(unknown, IDENTIFIERS)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(unknown.toString());
        verify(journal, org.mockito.Mockito.never()).record(any(), any());
    }
}
