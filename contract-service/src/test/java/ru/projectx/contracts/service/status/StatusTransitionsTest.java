package ru.projectx.contracts.service.status;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import ru.projectx.contracts.entity.CancelReason;
import ru.projectx.contracts.entity.Cancellation;
import ru.projectx.contracts.entity.Contract;
import ru.projectx.contracts.entity.DocumentKind;
import ru.projectx.contracts.entity.IssueError;
import ru.projectx.contracts.entity.LmPolicy;
import ru.projectx.contracts.entity.RequestParticipant;
import ru.projectx.contracts.support.Issuances;
import ru.projectx.contracts.support.Tasks;

class StatusTransitionsTest {

    private static final Instant NOW = Instant.parse("2026-02-10T07:35:00Z");
    private static final RequestParticipant INSURED = new RequestParticipant(Tasks.INSURED, DocumentKind.NONE);
    private static final RequestParticipant SPOUSE_KM = new RequestParticipant(Tasks.SPOUSE, DocumentKind.KM_CONTRACT);
    private static final RequestParticipant CHILD_LM = new RequestParticipant(Tasks.CHILD, DocumentKind.LM_POLICY);
    private static final String CONTRACT_ID = "01960000-0000-7000-8000-000000000001";
    private static final String POLICY_ID = "01960000-0000-7000-8000-000000000002";
    private static final String LATER_POLICY_ID = "01960000-0000-7000-8000-000000000003";
    private static final IssuanceSnapshot NOTHING = snapshot(false, List.of(), List.of(), List.of());

    @Test
    void firstTaskLeavingNothingToIssue_isPublishedAsCompleted() {
        assertThat(StatusTransitions.publishAfterChange(NOTHING, snapshot(false, List.of(INSURED), List.of(), List.of())))
                .isTrue();
    }

    @Test
    void sameCompletedStateAgain_isNotPublished() {
        IssuanceSnapshot completed = snapshot(false, List.of(INSURED), List.of(), List.of());

        assertThat(StatusTransitions.publishAfterChange(completed, completed)).isFalse();
    }

    @Test
    void completedStateWithAnotherComposition_isPublished() {
        IssuanceSnapshot completed = snapshot(false, List.of(INSURED), List.of(), List.of());
        RequestParticipant another = new RequestParticipant(Tasks.CHILD, DocumentKind.NONE);

        assertThat(StatusTransitions.publishAfterChange(completed, snapshot(false, List.of(INSURED, another),
                List.of(), List.of()))).isTrue();
    }

    @Test
    void taskLeavingOnlyNewRecords_isNotPublished() {
        LmPolicy pending = Issuances.policy(POLICY_ID, Tasks.CHILD);

        assertThat(StatusTransitions.publishAfterChange(NOTHING, snapshot(false, List.of(INSURED, CHILD_LM), List.of(),
                List.of(pending)))).isFalse();
    }

    @Test
    void recordThatEnteredError_isPublished() {
        LmPolicy failed = Issuances.policy(POLICY_ID, Tasks.CHILD);
        failed.markError(new IssueError("RELATION_TYPE_REQUIRED", "поле обязательно"), NOW);
        IssuanceSnapshot after = snapshot(false, List.of(INSURED, CHILD_LM), List.of(), List.of(failed));

        assertThat(StatusTransitions.publishAfterChange(NOTHING, after)).isTrue();
        assertThat(StatusTransitions.publishAfterChange(after, after)).isFalse();
    }

    @Test
    void recordThatEnteredCancelled_isPublishedEvenWhenItsParticipantLeftTheComposition() {
        LmPolicy pending = Issuances.policy(POLICY_ID, Tasks.CHILD);
        IssuanceSnapshot before = snapshot(false, List.of(INSURED, CHILD_LM), List.of(), List.of(pending));
        LmPolicy cancelled = Issuances.policy(POLICY_ID, Tasks.CHILD);
        cancelled.cancel(new Cancellation(CancelReason.OTHER, "участник исключён из состава поездки", NOW), NOW);

        assertThat(StatusTransitions.publishAfterChange(before, snapshot(false, List.of(INSURED), List.of(),
                List.of(cancelled)))).isTrue();
    }

    @Test
    void newRecordInTheSameErrorAsTheParticipantsLastOne_isNotPublished() {
        IssueError relationRequired = new IssueError("RELATION_TYPE_REQUIRED", "поле обязательно");
        LmPolicy failed = Issuances.policy(POLICY_ID, Tasks.CHILD);
        failed.markError(relationRequired, NOW);
        IssuanceSnapshot before = snapshot(false, List.of(INSURED, CHILD_LM), List.of(), List.of(failed));
        LmPolicy failedAgain = Issuances.policy(LATER_POLICY_ID, Tasks.CHILD);
        failedAgain.markError(relationRequired, NOW);

        assertThat(StatusTransitions.publishAfterChange(before, snapshot(false, List.of(INSURED, CHILD_LM), List.of(),
                List.of(failed, failedAgain)))).isFalse();
    }

    @Test
    void newRecordInAnotherError_isPublished() {
        LmPolicy failed = Issuances.policy(POLICY_ID, Tasks.CHILD);
        failed.markError(new IssueError("RELATION_TYPE_REQUIRED", "поле обязательно"), NOW);
        IssuanceSnapshot before = snapshot(false, List.of(INSURED, CHILD_LM), List.of(), List.of(failed));
        LmPolicy failedOtherwise = Issuances.policy(LATER_POLICY_ID, Tasks.CHILD);
        failedOtherwise.markError(new IssueError("UNKNOWN_GENDER", "недопустимое значение поля"), NOW);

        assertThat(StatusTransitions.publishAfterChange(before, snapshot(false, List.of(INSURED, CHILD_LM), List.of(),
                List.of(failed, failedOtherwise)))).isTrue();
    }

    @Test
    void newLmPolicyAfterTheParticipantsErrorOne_isPublished() {
        LmPolicy failed = Issuances.policy(POLICY_ID, Tasks.CHILD);
        failed.markError(new IssueError("RELATION_TYPE_REQUIRED", "поле обязательно"), NOW);
        IssuanceSnapshot before = snapshot(false, List.of(INSURED, CHILD_LM), List.of(), List.of(failed));
        IssuanceSnapshot after = snapshot(false, List.of(INSURED, CHILD_LM), List.of(),
                List.of(failed, Issuances.policy(LATER_POLICY_ID, Tasks.CHILD)));

        assertThat(StatusTransitions.publishAfterChange(before, after)).isTrue();
        assertThat(StatusTransitions.publishAfterChange(after, after)).isFalse();
    }

    @Test
    void oneOfTwoParticipantsLeavingTheirErrors_isPublishedWhileTheOtherStaysInError() {
        Contract failedContract = Issuances.contract(CONTRACT_ID, Tasks.SPOUSE, "DKM-2026-000001");
        failedContract.markError(new IssueError("RELATION_TYPE_REQUIRED", "поле обязательно"), NOW);
        LmPolicy failed = Issuances.policy(POLICY_ID, Tasks.CHILD);
        failed.markError(new IssueError("RELATION_TYPE_REQUIRED", "поле обязательно"), NOW);
        List<RequestParticipant> composition = List.of(INSURED, SPOUSE_KM, CHILD_LM);
        IssuanceSnapshot before = snapshot(false, composition, List.of(failedContract), List.of(failed));

        assertThat(StatusTransitions.publishAfterChange(before, snapshot(false, composition, List.of(failedContract),
                List.of(failed, Issuances.policy(LATER_POLICY_ID, Tasks.CHILD))))).isTrue();
    }

    @Test
    void participantWhoseKmContractFailedAndWhoNowGetsAnLmPolicy_isPublished() {
        Contract failed = Issuances.contract(CONTRACT_ID, Tasks.SPOUSE, "DKM-2026-000001");
        failed.markError(new IssueError("RELATION_TYPE_REQUIRED", "поле обязательно"), NOW);
        IssuanceSnapshot before = snapshot(false, List.of(INSURED, SPOUSE_KM), List.of(failed), List.of());
        RequestParticipant spouseLm = new RequestParticipant(Tasks.SPOUSE, DocumentKind.LM_POLICY);

        assertThat(StatusTransitions.publishAfterChange(before, snapshot(false, List.of(INSURED, spouseLm),
                List.of(failed), List.of(Issuances.policy(LATER_POLICY_ID, Tasks.SPOUSE))))).isTrue();
    }

    @Test
    void participantInErrorWhoLeftTheComposition_isPublished() {
        LmPolicy failed = Issuances.policy(POLICY_ID, Tasks.CHILD);
        failed.markError(new IssueError("RELATION_TYPE_REQUIRED", "поле обязательно"), NOW);
        Contract drafted = Issuances.contract(CONTRACT_ID, Tasks.SPOUSE, "DKM-2026-000001");
        drafted.offerDraft();
        IssuanceSnapshot before = snapshot(false, List.of(INSURED, SPOUSE_KM, CHILD_LM), List.of(drafted),
                List.of(failed));

        assertThat(StatusTransitions.publishAfterChange(before, snapshot(false, List.of(INSURED, SPOUSE_KM),
                List.of(drafted), List.of(failed)))).isTrue();
    }

    @Test
    void kmContractThatBecameReadyForAcceptance_isPublished() {
        Contract drafted = Issuances.contract(CONTRACT_ID, Tasks.SPOUSE, "DKM-2026-000001");
        drafted.offerDraft();
        IssuanceSnapshot after = snapshot(false, List.of(INSURED, SPOUSE_KM), List.of(drafted), List.of());

        assertThat(StatusTransitions.publishAfterChange(NOTHING, after)).isTrue();
        assertThat(StatusTransitions.publishAfterChange(after, after)).isFalse();
    }

    @Test
    void kmContractReadyForAcceptanceNextToANewLmPolicy_isPublished() {
        Contract drafted = Issuances.contract(CONTRACT_ID, Tasks.SPOUSE, "DKM-2026-000001");
        drafted.offerDraft();
        LmPolicy pending = Issuances.policy(POLICY_ID, Tasks.CHILD);

        assertThat(StatusTransitions.publishAfterChange(NOTHING, snapshot(false, List.of(INSURED, SPOUSE_KM, CHILD_LM),
                List.of(drafted), List.of(pending)))).isTrue();
    }

    @Test
    void newKmContractReadyForAcceptanceAfterTheParticipantsErrorOne_isPublished() {
        Contract failed = Issuances.contract(CONTRACT_ID, Tasks.SPOUSE, "DKM-2026-000001");
        failed.markError(new IssueError("RELATION_TYPE_REQUIRED", "поле обязательно"), NOW);
        IssuanceSnapshot before = snapshot(false, List.of(INSURED, SPOUSE_KM), List.of(failed), List.of());
        Contract corrected = Issuances.contract(LATER_POLICY_ID, Tasks.SPOUSE, "DKM-2026-000002");
        corrected.offerDraft();

        assertThat(StatusTransitions.publishAfterChange(before, snapshot(false, List.of(INSURED, SPOUSE_KM),
                List.of(failed, corrected), List.of()))).isTrue();
    }

    @Test
    void anyUndraftedKmContract_defersThePublication() {
        LmPolicy failed = Issuances.policy(POLICY_ID, Tasks.CHILD);
        failed.markError(new IssueError("RELATION_TYPE_REQUIRED", "поле обязательно"), NOW);
        Contract fresh = Issuances.contract(CONTRACT_ID, Tasks.SPOUSE, "DKM-2026-000001");

        assertThat(StatusTransitions.publishAfterChange(NOTHING, snapshot(false, List.of(INSURED, SPOUSE_KM, CHILD_LM),
                List.of(fresh), List.of(failed)))).isFalse();
    }

    private static IssuanceSnapshot snapshot(boolean cancelled, List<RequestParticipant> composition,
            List<Contract> contracts, List<LmPolicy> policies) {
        return new IssuanceSnapshot(Tasks.REQUEST, cancelled, composition, contracts, policies, List.of());
    }
}
