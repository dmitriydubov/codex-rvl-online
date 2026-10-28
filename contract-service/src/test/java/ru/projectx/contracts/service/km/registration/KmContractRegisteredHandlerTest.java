package ru.projectx.contracts.service.km.registration;

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
import ru.projectx.contracts.support.UskResultBodies;
import tools.jackson.databind.json.JsonMapper;

class KmContractRegisteredHandlerTest {

    private static final Instant NOW = Instant.parse("2026-02-11T06:15:00Z");
    private static final UUID CONTRACT_ID = UUID.fromString(UskResultBodies.CONTRACT_ID);
    private static final UUID EXTERNAL_ID = UUID.fromString(UskResultBodies.POLICY_EXTERNAL_ID);
    private static final UUID OUR_ID = UUID.fromString("2c7e9b40-1f5a-4d83-9e6b-0a4c8d2f7e15");

    private final IssuedPolicyLookup lookup = mock();
    private final KmContractRegistrationRecording recording = mock();
    private final KmContractRegisteredHandler handler = new KmContractRegisteredHandler(
            new MessageBodies(JsonMapper.builder().build()), lookup, recording);

    @BeforeEach
    void contractAwaitsResult() {
        given(recording.expect(CONTRACT_ID)).willReturn(ResultExpectation.AWAITING_RESULT);
    }

    @Test
    void handler_takesRegistrationResultsWithoutRequestOrdering() {
        assertThat(handler.eventType()).isEqualTo("KmContractRegistered");
        assertThat(handler.ordering()).isEqualTo(InboxOrdering.NONE);
    }

    @Test
    void registeredResult_looksOurPolicyUpBeforeTheWorkAndRecordsTheRegistrationWithFormsWhenApplied() {
        given(lookup.find(EXTERNAL_ID)).willReturn(OUR_ID);

        InboxWork work = handler.prepare(UskResultBodies.registered().toJson());
        verify(lookup).find(EXTERNAL_ID);
        verify(recording, never()).recordIssued(any(), any(), any(), any());
        work.apply(NOW);

        verify(recording).recordIssued(CONTRACT_ID, new IssuedPolicy(UUID.fromString(UskResultBodies.CONTRACT_USK_ID),
                "DKM-000004417", OUR_ID, "0987654321"),
                List.of(new UskDocument(PrintedFormType.CONTRACT, UskResultBodies.CONTRACT_URL, 205312L),
                        new UskDocument(PrintedFormType.POLICY, UskResultBodies.POLICY_URL, 118784L)), NOW);
    }

    @Test
    void rejectedResult_recordsTheErrorAsReceivedWithoutAnyLookup() {
        handler.prepare(UskResultBodies.rejected().toJson()).apply(NOW);

        verify(recording).recordFailure(CONTRACT_ID, new IssueError("CONTRACT_REJECTED",
                "Стенд: регистрация договора невозможна"), NOW);
        verifyNoInteractions(lookup);
    }

    @Test
    void resultForAFinishedContract_isRecordedAsLateWithoutAnyLookup() {
        given(recording.expect(CONTRACT_ID)).willReturn(ResultExpectation.ALREADY_FINISHED);

        handler.prepare(UskResultBodies.registered().toJson()).apply(NOW);

        verify(recording).recordLate(CONTRACT_ID);
        verify(recording, never()).recordIssued(any(), any(), any(), any());
        verifyNoInteractions(lookup);
    }

    @Test
    void policyNotYetAtTheReference_postponesThePreparation() {
        given(lookup.find(EXTERNAL_ID)).willThrow(new TemporaryFailureException("полис ещё не получен", null));

        assertThatThrownBy(() -> handler.prepare(UskResultBodies.registered().toJson()))
                .isInstanceOf(TemporaryFailureException.class);
        verify(recording, never()).recordIssued(any(), any(), any(), any());
    }

    @Test
    void contractViolationOfTheBody_isNotHidden() {
        assertThatThrownBy(() -> handler.prepare(UskResultBodies.registered().withoutDataField("documents").toJson()))
                .isInstanceOf(ContractViolationException.class).hasMessage("нет обязательного поля data.documents");
        verifyNoInteractions(lookup, recording);
    }
}
