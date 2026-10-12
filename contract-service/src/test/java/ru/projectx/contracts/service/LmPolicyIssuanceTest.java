package ru.projectx.contracts.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import ru.projectx.contracts.entity.IssueError;
import ru.projectx.contracts.entity.LmPolicy;
import ru.projectx.contracts.entity.LmPolicyDraft;
import ru.projectx.contracts.entity.LmPolicyStatus;
import ru.projectx.contracts.entity.PersonOwner;
import ru.projectx.contracts.entity.PersonRole;
import ru.projectx.contracts.entity.RequestReference;
import ru.projectx.contracts.entity.TripPeriod;
import ru.projectx.contracts.message.ContractTask;
import ru.projectx.contracts.repository.LmPolicyRepository;
import ru.projectx.contracts.support.Tasks;

class LmPolicyIssuanceTest {

    private static final Instant NOW = Instant.parse("2026-02-10T07:05:00Z");

    private final LmPolicyRepository policies = mock();
    private final PartySnapshotStore parties = mock();
    private final LmPolicyIssuance issuance = new LmPolicyIssuance(policies, parties);
    private final ContractTask task = Tasks.task(Tasks.insured("0.00"), Tasks.child("0.00"));

    @BeforeEach
    void repositoryReturnsSavedPolicy() {
        given(policies.save(any())).willAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void newPolicy_getsTripDatesPolicyholderAndOneInsuredParty() {
        issuance.reconcile(new Issue(task, Tasks.child("0.00")), Optional.empty(), NOW);

        LmPolicy policy = savedPolicy();
        assertThat(policy.status()).isEqualTo(LmPolicyStatus.NEW);
        assertThat(policy.participantId()).isEqualTo(Tasks.CHILD);
        assertThat(policy.state().policyholderParticipantId()).isEqualTo(Tasks.INSURED);
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
        assertThat(existing.state().entityVersion()).isEqualTo(3);
    }

    @Test
    void existingPolicy_withSameDatesAndParty_keepsVersion() {
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
    void invalidParticipant_withExistingPolicy_marksItInError() {
        LmPolicy existing = existing();
        ContractTask invalid = Tasks.task(Tasks.insured("0.00"), Tasks.relative(1, Tasks.CHILD, "0.00", null));

        issuance.reconcile(new Issue(invalid, invalid.participants().get(1)), Optional.of(existing), NOW);

        assertThat(existing.status()).isEqualTo(LmPolicyStatus.ERROR);
        verify(policies, never()).save(any());
    }

    private LmPolicy savedPolicy() {
        ArgumentCaptor<LmPolicy> saved = ArgumentCaptor.captor();
        verify(policies).save(saved.capture());
        return saved.getValue();
    }

    private static LmPolicy existing() {
        return LmPolicy.open(new LmPolicyDraft(
                new RequestReference(Tasks.REQUEST, Tasks.INSURANCE_CONTRACT, Tasks.INSURER_POLICY),
                Tasks.CHILD, Tasks.INSURED, new TripPeriod(Tasks.FROM, Tasks.TO)), NOW);
    }
}
