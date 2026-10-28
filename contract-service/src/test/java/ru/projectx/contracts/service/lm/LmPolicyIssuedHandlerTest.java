package ru.projectx.contracts.service.lm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.projectx.contracts.entity.issuance.IssueError;
import ru.projectx.contracts.entity.issuance.IssuedPolicy;
import ru.projectx.contracts.entity.document.PrintedFormType;
import ru.projectx.contracts.entity.document.UskDocument;
import ru.projectx.contracts.exception.ContractViolationException;
import ru.projectx.contracts.exception.TemporaryFailureException;
import ru.projectx.contracts.service.inbox.InboxOrdering;
import ru.projectx.contracts.service.inbox.InboxWork;
import ru.projectx.contracts.service.inbox.parse.MessageBodies;
import ru.projectx.contracts.service.reference.IssuedPolicyLookup;
import ru.projectx.contracts.service.usk.result.ResultExpectation;
import ru.projectx.contracts.support.LmPolicyResultBodies;
import tools.jackson.databind.json.JsonMapper;

class LmPolicyIssuedHandlerTest {

    private static final Instant NOW = Instant.parse("2026-02-11T06:15:00Z");
    private static final UUID POLICY_ID = UUID.fromString(LmPolicyResultBodies.POLICY_ID);
    private static final UUID USK_ID = UUID.fromString(LmPolicyResultBodies.POLICY_USK_ID);
    private static final UUID EXTERNAL_ID = UUID.fromString(LmPolicyResultBodies.POLICY_EXTERNAL_ID);
    private static final UUID OUR_ID = UUID.fromString("13ab5388-61ee-4e21-8a46-c325478b78c0");

    private final IssuedPolicyLookup lookup = mock();
    private final IssuedPolicyRecording recording = mock();
    private final LmPolicyIssuedHandler handler = new LmPolicyIssuedHandler(
            new MessageBodies(JsonMapper.builder().build()), lookup, recording);

    @BeforeEach
    void policyAwaitsResult() {
        given(recording.expect(POLICY_ID)).willReturn(ResultExpectation.AWAITING_RESULT);
    }

    @Test
    void handler_takesIssuedResultsWithoutRequestOrdering() {
        assertThat(handler.eventType()).isEqualTo("LmPolicyIssued");
        assertThat(handler.ordering()).isEqualTo(InboxOrdering.NONE);
    }

    @Test
    void issuedResult_looksOurPolicyUpBeforeTheWorkAndRecordsItWithFormsWhenApplied() {
        given(lookup.find(EXTERNAL_ID)).willReturn(OUR_ID);

        InboxWork work = handler.prepare(LmPolicyResultBodies.issued().toJson());
        verify(lookup).find(EXTERNAL_ID);
        verify(recording, never()).recordIssued(any(), any(), any(), any());
        work.apply(NOW);

        verify(recording).recordIssued(POLICY_ID, new IssuedPolicy(USK_ID, "LM-000012204", OUR_ID, "0555123456"),
                List.of(new UskDocument(PrintedFormType.POLICY, LmPolicyResultBodies.FILE_URL, 96256L)), NOW);
    }

    @Test
    void failedResult_recordsTheErrorAsReceivedWithoutAnyLookup() {
        handler.prepare(LmPolicyResultBodies.failed().toJson()).apply(NOW);

        verify(recording).recordFailure(POLICY_ID, new IssueError("INSURED_NOT_COVERED",
                "Стенд: застрахованный не входит в корпоративный договор"), NOW);
        verifyNoInteractions(lookup);
    }

    @Test
    void resultForAFinishedPolicy_isRecordedAsLateWithoutAnyLookup() {
        given(recording.expect(POLICY_ID)).willReturn(ResultExpectation.ALREADY_FINISHED);

        handler.prepare(LmPolicyResultBodies.issued().toJson()).apply(NOW);

        verify(recording).recordLate(POLICY_ID);
        verify(recording, never()).recordIssued(any(), any(), any(), any());
        verifyNoInteractions(lookup);
    }

    @Test
    void policyNotYetAtTheReference_postponesThePreparation() {
        given(lookup.find(EXTERNAL_ID)).willThrow(new TemporaryFailureException("полис ещё не получен", null));

        assertThatThrownBy(() -> handler.prepare(LmPolicyResultBodies.issued().toJson()))
                .isInstanceOf(TemporaryFailureException.class);
        verify(recording, never()).recordIssued(any(), any(), any(), any());
    }

    @Test
    void contractViolationOfTheBody_isNotHidden() {
        assertThatThrownBy(() -> handler.prepare(LmPolicyResultBodies.issued().withoutDataField("policy").toJson()))
                .isInstanceOf(ContractViolationException.class).hasMessage("нет обязательного поля data.policy");
        verifyNoInteractions(lookup, recording);
    }
}
