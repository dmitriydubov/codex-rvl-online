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
import ru.projectx.contracts.entity.IssueError;
import ru.projectx.contracts.entity.IssuedPolicy;
import ru.projectx.contracts.entity.PrintedFormType;
import ru.projectx.contracts.entity.UskDocument;
import ru.projectx.contracts.exception.ContractViolationException;
import ru.projectx.contracts.exception.TemporaryFailureException;
import ru.projectx.contracts.message.IssuedDocument;
import ru.projectx.contracts.message.LmPolicyResult;
import ru.projectx.contracts.service.inbox.InboxOrdering;
import ru.projectx.contracts.service.inbox.InboxWork;
import ru.projectx.contracts.service.inbox.LmPolicyIssuedParser;

class LmPolicyIssuedHandlerTest {

    private static final Instant NOW = Instant.parse("2026-02-11T06:15:00Z");
    private static final UUID POLICY_ID = UUID.fromString("8e2d0b17-5a44-4c90-b2e3-71f0c6a45d39");
    private static final UUID USK_ID = UUID.fromString("cb904f2a-7d61-4e08-89a5-40b1e3c7f012");
    private static final UUID EXTERNAL_ID = UUID.fromString("5f0b2a77-8c31-4f2e-9d44-7c1a0b93e512");
    private static final UUID OUR_ID = UUID.fromString("13ab5388-61ee-4e21-8a46-c325478b78c0");
    private static final LmPolicyResult.Issued ISSUED = new LmPolicyResult.Issued(POLICY_ID, USK_ID, "LM-000012204",
            EXTERNAL_ID, "0555123456", List.of(new IssuedDocument("POLICY", "https://s3/lm.pdf", 96256L)));
    private static final LmPolicyResult.Failed FAILED = new LmPolicyResult.Failed(POLICY_ID, "INSURED_NOT_COVERED",
            "Стенд: не входит");

    private final LmPolicyIssuedParser parser = mock();
    private final IssuedPolicyLookup lookup = mock();
    private final IssuedPolicyRecording recording = mock();
    private final LmPolicyIssuedHandler handler = new LmPolicyIssuedHandler(parser, lookup, recording);

    @BeforeEach
    void policyAwaitsResult() {
        given(recording.expect(POLICY_ID)).willReturn(PolicyExpectation.AWAITING_RESULT);
    }

    @Test
    void handler_takesIssuedResultsWithoutRequestOrdering() {
        assertThat(handler.eventType()).isEqualTo("LmPolicyIssued");
        assertThat(handler.ordering()).isEqualTo(InboxOrdering.NONE);
    }

    @Test
    void issuedResult_looksOurPolicyUpBeforeTheWorkAndRecordsItWithFormsWhenApplied() {
        given(parser.parse("body")).willReturn(ISSUED);
        given(lookup.find(EXTERNAL_ID)).willReturn(OUR_ID);

        InboxWork work = handler.prepare("body");
        verify(lookup).find(EXTERNAL_ID);
        verify(recording, never()).recordIssued(any(), any(), any(), any());
        work.apply(NOW);

        verify(recording).recordIssued(POLICY_ID, new IssuedPolicy(USK_ID, "LM-000012204", OUR_ID, "0555123456"),
                List.of(new UskDocument(PrintedFormType.POLICY, "https://s3/lm.pdf", 96256L)), NOW);
    }

    @Test
    void failedResult_recordsTheErrorAsReceivedWithoutAnyLookup() {
        given(parser.parse("body")).willReturn(FAILED);

        handler.prepare("body").apply(NOW);

        verify(recording).recordFailure(POLICY_ID, new IssueError("INSURED_NOT_COVERED", "Стенд: не входит"), NOW);
        verifyNoInteractions(lookup);
    }

    @Test
    void resultForAFinishedPolicy_isRecordedAsLateWithoutAnyLookup() {
        given(parser.parse("body")).willReturn(ISSUED);
        given(recording.expect(POLICY_ID)).willReturn(PolicyExpectation.ALREADY_FINISHED);

        handler.prepare("body").apply(NOW);

        verify(recording).recordLate(POLICY_ID);
        verify(recording, never()).recordIssued(any(), any(), any(), any());
        verifyNoInteractions(lookup);
    }

    @Test
    void policyNotYetAtTheReference_postponesThePreparation() {
        given(parser.parse("body")).willReturn(ISSUED);
        given(lookup.find(EXTERNAL_ID)).willThrow(new TemporaryFailureException("полис ещё не получен", null));

        assertThatThrownBy(() -> handler.prepare("body")).isInstanceOf(TemporaryFailureException.class);
        verify(recording, never()).recordIssued(any(), any(), any(), any());
    }

    @Test
    void contractViolationOfTheBody_isNotHidden() {
        given(parser.parse("body")).willThrow(new ContractViolationException("нет обязательного поля data.policy"));

        assertThatThrownBy(() -> handler.prepare("body")).isInstanceOf(ContractViolationException.class);
        verifyNoInteractions(lookup, recording);
    }
}
