package ru.projectx.contracts.service.status;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import ru.projectx.contracts.entity.Contract;
import ru.projectx.contracts.entity.DocumentKind;
import ru.projectx.contracts.entity.IssueError;
import ru.projectx.contracts.entity.LmPolicy;
import ru.projectx.contracts.entity.RequestParticipant;
import ru.projectx.contracts.message.RequestStatus;
import ru.projectx.contracts.support.Issuances;
import ru.projectx.contracts.support.Tasks;

class RequestStatusRulesTest {

    private static final Instant NOW = Instant.parse("2026-02-10T07:35:00Z");
    private static final RequestParticipant INSURED = new RequestParticipant(Tasks.INSURED, DocumentKind.NONE);
    private static final RequestParticipant SPOUSE_KM = new RequestParticipant(Tasks.SPOUSE, DocumentKind.KM_CONTRACT);
    private static final RequestParticipant CHILD_LM = new RequestParticipant(Tasks.CHILD, DocumentKind.LM_POLICY);
    private static final String CONTRACT_ID = "01960000-0000-7000-8000-000000000001";
    private static final String POLICY_ID = "01960000-0000-7000-8000-000000000002";

    @Test
    void cancelledRequest_isCancelledWhateverTheRecordsSay() {
        LmPolicy failed = Issuances.policy(POLICY_ID, Tasks.CHILD);
        failed.markError(new IssueError("RELATION_TYPE_REQUIRED", "поле обязательно"), NOW);

        assertThat(RequestStatusRules.of(new IssuanceSnapshot(Tasks.REQUEST, true, List.of(INSURED, CHILD_LM),
                List.of(), List.of(failed)))).isEqualTo(RequestStatus.CANCELLED);
    }

    @Test
    void anyParticipantInError_makesTheRequestError() {
        LmPolicy failed = Issuances.policy(POLICY_ID, Tasks.CHILD);
        failed.markError(new IssueError("RELATION_TYPE_REQUIRED", "поле обязательно"), NOW);

        assertThat(RequestStatusRules.of(new IssuanceSnapshot(Tasks.REQUEST, false, List.of(INSURED, CHILD_LM),
                List.of(), List.of(failed)))).isEqualTo(RequestStatus.ERROR);
    }

    @Test
    void everyParticipantWithoutIssuanceOrIssued_makesTheRequestCompleted() {
        Contract issued = Issuances.contract(CONTRACT_ID, Tasks.SPOUSE, "DKM-2026-000001");
        org.springframework.test.util.ReflectionTestUtils.setField(issued, "status",
                ru.projectx.contracts.entity.ContractStatus.ISSUED);

        assertThat(RequestStatusRules.of(new IssuanceSnapshot(Tasks.REQUEST, false, List.of(INSURED), List.of(),
                List.of()))).isEqualTo(RequestStatus.COMPLETED);
        assertThat(RequestStatusRules.of(new IssuanceSnapshot(Tasks.REQUEST, false, List.of(), List.of(),
                List.of()))).isEqualTo(RequestStatus.COMPLETED);
        assertThat(RequestStatusRules.of(new IssuanceSnapshot(Tasks.REQUEST, false, List.of(INSURED, SPOUSE_KM),
                List.of(issued), List.of()))).isEqualTo(RequestStatus.COMPLETED);
    }

    @Test
    void participantStillBeingIssued_keepsTheRequestInProgress() {
        LmPolicy pending = Issuances.policy(POLICY_ID, Tasks.CHILD);
        Contract fresh = Issuances.contract(CONTRACT_ID, Tasks.SPOUSE, "DKM-2026-000001");

        assertThat(RequestStatusRules.of(new IssuanceSnapshot(Tasks.REQUEST, false, List.of(INSURED, CHILD_LM),
                List.of(), List.of(pending)))).isEqualTo(RequestStatus.IN_PROGRESS);
        assertThat(RequestStatusRules.of(new IssuanceSnapshot(Tasks.REQUEST, false, List.of(SPOUSE_KM, CHILD_LM),
                List.of(fresh), List.of(pending)))).isEqualTo(RequestStatus.IN_PROGRESS);
    }
}
