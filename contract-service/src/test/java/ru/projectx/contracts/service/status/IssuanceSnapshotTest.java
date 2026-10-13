package ru.projectx.contracts.service.status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import ru.projectx.contracts.entity.CancelReason;
import ru.projectx.contracts.entity.Cancellation;
import ru.projectx.contracts.entity.Contract;
import ru.projectx.contracts.entity.ContractStatus;
import ru.projectx.contracts.entity.DocumentKind;
import ru.projectx.contracts.entity.IssueError;
import ru.projectx.contracts.entity.LmPolicy;
import ru.projectx.contracts.entity.LmPolicyStatus;
import ru.projectx.contracts.entity.RequestParticipant;
import ru.projectx.contracts.message.RequestStatus;
import ru.projectx.contracts.support.Issuances;
import ru.projectx.contracts.support.Tasks;

class IssuanceSnapshotTest {

    private static final Instant NOW = Instant.parse("2026-02-10T07:35:00Z");
    private static final RequestParticipant INSURED = new RequestParticipant(Tasks.INSURED, DocumentKind.NONE);
    private static final RequestParticipant SPOUSE_KM = new RequestParticipant(Tasks.SPOUSE, DocumentKind.KM_CONTRACT);
    private static final RequestParticipant CHILD_LM = new RequestParticipant(Tasks.CHILD, DocumentKind.LM_POLICY);
    private static final String EARLIER = "01960000-0000-7000-8000-000000000001";
    private static final String LATER = "01960000-0000-7000-8000-000000000002";
    private static final String POLICY_ID = "01960000-0000-7000-8000-000000000003";
    private static final String LATEST = "01960000-0000-7000-8000-000000000004";
    private static final IssueError RELATION_REQUIRED = new IssueError("RELATION_TYPE_REQUIRED", "поле обязательно");

    @Test
    void latestRecordOfAParticipant_isTheOneWithTheGreatestIdentifier() {
        Contract first = Issuances.contract(EARLIER, Tasks.SPOUSE, "DKM-2026-000001");
        first.cancel(new Cancellation(CancelReason.OTHER, "вид документа по участнику изменён", NOW), NOW);
        Contract second = Issuances.contract(LATER, Tasks.SPOUSE, "DKM-2026-000002");
        LmPolicy policy = Issuances.policy(POLICY_ID, Tasks.CHILD);

        IssuanceSnapshot snapshot = new IssuanceSnapshot(Tasks.REQUEST, false, List.of(INSURED, SPOUSE_KM, CHILD_LM),
                List.of(second, first), List.of(policy), List.of());

        assertThat(snapshot.contractOf(Tasks.SPOUSE)).isSameAs(second);
        assertThat(snapshot.policyOf(Tasks.CHILD)).isSameAs(policy);
        assertThat(snapshot.statusOf(SPOUSE_KM)).contains(ContractStatus.NEW);
        assertThat(snapshot.statusOf(CHILD_LM)).contains(LmPolicyStatus.NEW);
        assertThat(snapshot.statusOf(INSURED)).isEmpty();
        assertThat(snapshot.records()).extracting(RecordOutcome::recordId)
                .containsExactly(second.id(), first.id(), policy.id());
        assertThat(snapshot.individualRequestId()).isEqualTo(Tasks.REQUEST);
        assertThat(snapshot.cancelled()).isFalse();
        assertThat(snapshot.composition()).containsExactly(INSURED, SPOUSE_KM, CHILD_LM);
    }

    @Test
    void statusOfAParticipantWhoseKindChanged_isTheOneOfTheNewestRecord() {
        Contract cancelled = Issuances.contract(EARLIER, Tasks.SPOUSE, "DKM-2026-000001");
        cancelled.cancel(new Cancellation(CancelReason.OTHER, "вид документа по участнику изменён", NOW), NOW);
        LmPolicy policy = Issuances.policy(POLICY_ID, Tasks.SPOUSE);
        RequestParticipant spouseLm = new RequestParticipant(Tasks.SPOUSE, DocumentKind.LM_POLICY);

        IssuanceSnapshot snapshot = new IssuanceSnapshot(Tasks.REQUEST, false, List.of(spouseLm),
                List.of(cancelled), List.of(policy), List.of());

        assertThat(snapshot.statusOf(spouseLm)).contains(LmPolicyStatus.NEW);
        assertThat(snapshot.contractOf(Tasks.SPOUSE)).isSameAs(cancelled);
    }

    @Test
    void knownOutcome_isTheRecordsOwnOrOtherwiseTheLatestOfItsParticipant() {
        Contract cancelled = Issuances.contract(EARLIER, Tasks.SPOUSE, "DKM-2026-000001");
        cancelled.cancel(new Cancellation(CancelReason.OTHER, "вид документа по участнику изменён", NOW), NOW);
        Contract failed = Issuances.contract(LATER, Tasks.SPOUSE, "DKM-2026-000002");
        failed.markError(RELATION_REQUIRED, NOW);
        IssuanceSnapshot snapshot = new IssuanceSnapshot(Tasks.REQUEST, false, List.of(SPOUSE_KM),
                List.of(cancelled, failed), List.of(), List.of());
        Contract failedAgain = Issuances.contract(LATEST, Tasks.SPOUSE, "DKM-2026-000003");
        failedAgain.markError(RELATION_REQUIRED, NOW);
        Contract failedOtherwise = Issuances.contract(LATEST, Tasks.SPOUSE, "DKM-2026-000003");
        failedOtherwise.markError(new IssueError("UNKNOWN_GENDER", "недопустимое значение поля"), NOW);
        LmPolicy failedPolicy = Issuances.policy(POLICY_ID, Tasks.SPOUSE);
        failedPolicy.markError(RELATION_REQUIRED, NOW);

        assertThat(snapshot.knows(RecordOutcome.of(cancelled))).as("своя запись, тот же исход").isTrue();
        assertThat(snapshot.knows(RecordOutcome.of(failed))).isTrue();
        assertThat(snapshot.knows(RecordOutcome.of(failedAgain))).as("новая запись с той же ошибкой").isTrue();
        assertThat(snapshot.knows(RecordOutcome.of(failedOtherwise))).as("новая запись с другой ошибкой").isFalse();
        assertThat(snapshot.knows(RecordOutcome.of(failedPolicy))).as("та же ошибка, но другой вид документа")
                .isFalse();
        assertThat(snapshot.knows(RecordOutcome.of(Issuances.policy(POLICY_ID, Tasks.CHILD))))
                .as("участник без записей").isFalse();
    }

    @Test
    void participantWithoutItsRecord_isAnIllegalState() {
        IssuanceSnapshot snapshot = new IssuanceSnapshot(Tasks.REQUEST, false, List.of(SPOUSE_KM, CHILD_LM),
                List.of(), List.of(), List.of());

        assertThatThrownBy(() -> snapshot.contractOf(Tasks.SPOUSE)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(Tasks.SPOUSE.toString());
        assertThatThrownBy(() -> snapshot.statusOf(CHILD_LM)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(Tasks.CHILD.toString());
    }

    @Test
    void undraftedKmContract_isOneStillInNewAmongTheComposition() {
        Contract fresh = Issuances.contract(EARLIER, Tasks.SPOUSE, "DKM-2026-000001");
        Contract failed = Issuances.contract(LATER, Tasks.INSURED, "DKM-2026-000002");
        failed.markError(new IssueError("UNKNOWN_GENDER", "недопустимое значение поля"), NOW);
        RequestParticipant insuredKm = new RequestParticipant(Tasks.INSURED, DocumentKind.KM_CONTRACT);

        assertThat(KmContractStates.anyUndrafted(new IssuanceSnapshot(Tasks.REQUEST, false,
                List.of(SPOUSE_KM, insuredKm), List.of(fresh, failed), List.of(), List.of()))).isTrue();
        assertThat(KmContractStates.anyUndrafted(new IssuanceSnapshot(Tasks.REQUEST, false,
                List.of(insuredKm), List.of(fresh, failed), List.of(), List.of()))).isFalse();
        assertThat(KmContractStates.anyUndrafted(new IssuanceSnapshot(Tasks.REQUEST, false,
                List.of(INSURED, CHILD_LM), List.of(fresh), List.of(Issuances.policy(POLICY_ID, Tasks.CHILD)),
                List.of()))).isFalse();
    }

    @Test
    void requestStatus_isComputedByTheRules() {
        assertThat(new IssuanceSnapshot(Tasks.REQUEST, true, List.of(), List.of(), List.of(), List.of()).requestStatus())
                .isEqualTo(RequestStatus.CANCELLED);
        assertThat(new IssuanceSnapshot(Tasks.REQUEST, false, List.of(INSURED), List.of(), List.of(), List.of()).requestStatus())
                .isEqualTo(RequestStatus.COMPLETED);
    }
}
