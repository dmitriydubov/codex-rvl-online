package ru.projectx.contracts.service.km;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
import org.springframework.dao.CannotAcquireLockException;
import ru.projectx.contracts.entity.CancelReason;
import ru.projectx.contracts.entity.Cancellation;
import ru.projectx.contracts.entity.Contract;
import ru.projectx.contracts.entity.ContractStatus;
import ru.projectx.contracts.entity.IssueError;
import ru.projectx.contracts.entity.MessageEnvelope;
import ru.projectx.contracts.entity.OutboxDraft;
import ru.projectx.contracts.exception.MessageTooLargeException;
import ru.projectx.contracts.message.MessageMeta;
import ru.projectx.contracts.repository.ContractRepository;
import ru.projectx.contracts.service.outbox.OutboxJournal;
import ru.projectx.contracts.service.status.IssuanceSnapshot;
import ru.projectx.contracts.service.status.RequestStatusPublication;
import ru.projectx.contracts.support.Issuances;
import ru.projectx.contracts.support.Tasks;

class KmContractSubmissionRecordingTest {

    private static final Instant NOW = Instant.parse("2026-02-10T07:32:00Z");
    private static final Instant PAID_AT = Instant.parse("2026-02-10T07:20:15Z");
    private static final UUID CONTRACT_ID = UUID.fromString("0f4b8e2a-6c13-4d7e-b9a1-2e5c7d3f8a64");
    private static final UUID MESSAGE_ID = UUID.fromString("2a7c5e90-4b18-4d63-91f7-8c0a3e6b1d47");
    private static final IssueError ARCHIVED = new IssueError("PROGRAM_ARCHIVED", "программа страхования в архиве");

    private final ContractRepository contracts = mock();
    private final KmContractSubmissionFacts facts = mock();
    private final KmContractSubmissionMessages messages = mock();
    private final KmContractMessageLimit limit = mock();
    private final OutboxJournal journal = mock();
    private final RequestStatusPublication statuses = mock();
    private final KmContractSubmissionRecording recording = new KmContractSubmissionRecording(contracts, facts,
            messages, limit, journal, statuses, Clock.fixed(NOW, ZoneOffset.UTC));
    private final Contract contract = Issuances.paidContract(CONTRACT_ID.toString(), Tasks.SPOUSE, "DKM-2026-000001",
            PAID_AT);
    private final SubmissionFacts gathered = KmContractSubmissionMessagesTest.facts(List.of(
            KmContractSubmissionMessagesTest.JPEG, KmContractSubmissionMessagesTest.PDF));
    private final MessageMeta meta = new MessageMeta(MESSAGE_ID, Tasks.REQUEST, "rvl.km-contract.submitted",
            "KmContractSubmitted", "1.0", OffsetDateTime.parse("2026-02-10T07:32:00Z"), 3, "RVL-CONTRACTS");

    @BeforeEach
    void contractIsStoredWithItsFacts() {
        given(contracts.findLockedById(CONTRACT_ID)).willReturn(Optional.of(contract));
        given(facts.gather(contract, KmContractSubmissionMessagesTest.IDENTIFIERS)).willReturn(gathered);
        given(messages.meta(contract, NOW)).willReturn(meta);
        given(messages.write(meta, contract, gathered)).willReturn("{\"data\": {}}");
    }

    @Test
    void submit_turnsThePaidContractIntoSubmittedAndRecordsTheSubmissionWithItsCertificatesAsAttachments() {
        recording.submit(CONTRACT_ID, KmContractSubmissionMessagesTest.IDENTIFIERS);

        assertThat(contract.status()).isEqualTo(ContractStatus.SUBMITTED);
        assertThat(contract.state().entityVersion()).isEqualTo(3);
        InOrder order = inOrder(facts, messages, limit, journal);
        order.verify(facts).gather(contract, KmContractSubmissionMessagesTest.IDENTIFIERS);
        order.verify(messages).meta(contract, NOW);
        order.verify(messages).write(meta, contract, gathered);
        order.verify(limit).verify("{\"data\": {}}", gathered.certificates());
        ArgumentCaptor<OutboxDraft> draft = ArgumentCaptor.captor();
        order.verify(journal).record(draft.capture(), eq(gathered.certificates()), eq(NOW));
        assertThat(draft.getValue().aggregateType()).isEqualTo("CONTRACT");
        assertThat(draft.getValue().aggregateId()).isEqualTo(CONTRACT_ID);
        assertThat(draft.getValue().envelope()).isEqualTo(new MessageEnvelope(MESSAGE_ID, Tasks.REQUEST,
                "rvl.km-contract.submitted", "KmContractSubmitted", 3));
        assertThat(draft.getValue().payload()).isEqualTo("{\"data\": {}}");
        verifyNoInteractions(statuses);
    }

    @Test
    void submit_withoutCertificates_recordsNoAttachments() {
        SubmissionFacts bare = KmContractSubmissionMessagesTest.facts(List.of());
        given(facts.gather(contract, KmContractSubmissionMessagesTest.IDENTIFIERS)).willReturn(bare);
        given(messages.write(meta, contract, bare)).willReturn("{\"data\": {}}");

        recording.submit(CONTRACT_ID, KmContractSubmissionMessagesTest.IDENTIFIERS);

        verify(journal).record(any(), eq(List.of()), eq(NOW));
    }

    @Test
    void submit_leavesAContractThatIsNoLongerPaidAlone() {
        contract.submit(NOW);

        recording.submit(CONTRACT_ID, KmContractSubmissionMessagesTest.IDENTIFIERS);

        assertThat(contract.state().entityVersion()).isEqualTo(3);
        verifyNoInteractions(facts, messages, limit, journal, statuses);
    }

    @Test
    void submit_leavesACancelledContractAloneWithoutAnyRequestOrAnswer() {
        contract.cancel(new Cancellation(CancelReason.REQUEST_CANCELLED, null, NOW), NOW);

        recording.submit(CONTRACT_ID, KmContractSubmissionMessagesTest.IDENTIFIERS);

        assertThat(contract.status()).isEqualTo(ContractStatus.CANCELLED);
        verifyNoInteractions(facts, journal, statuses);
    }

    @Test
    void messageOverTheLimit_failsTheSubmissionBeforeAnythingIsRecorded() {
        MessageTooLargeException tooLarge = new MessageTooLargeException(70000, 65536, 2, "KM_CONTRACT_MESSAGE_TOO_LARGE", "сообщение");
        willThrow(tooLarge).given(limit).verify("{\"data\": {}}", gathered.certificates());

        assertThatThrownBy(() -> recording.submit(CONTRACT_ID, KmContractSubmissionMessagesTest.IDENTIFIERS))
                .isSameAs(tooLarge);

        verifyNoInteractions(journal, statuses);
    }

    @Test
    void refuse_marksThePaidContractInErrorBetweenWatchingAndReportingTheRequest() {
        IssuanceSnapshot before = mock();
        given(statuses.watch(Tasks.REQUEST)).willReturn(before);

        assertThat(recording.refuse(CONTRACT_ID, ARCHIVED)).isTrue();

        assertThat(contract.status()).isEqualTo(ContractStatus.ERROR);
        assertThat(contract.state().error()).contains(ARCHIVED);
        assertThat(contract.state().entityVersion()).isEqualTo(3);
        assertThat(contract.progress().paidAt()).contains(PAID_AT);
        InOrder order = inOrder(statuses);
        order.verify(statuses).watch(Tasks.REQUEST);
        order.verify(statuses).afterRecordChange(before, NOW);
        verifyNoInteractions(journal);
    }

    @Test
    void refuse_leavesAContractThatIsNoLongerPaidAlone() {
        contract.submit(NOW);

        assertThat(recording.refuse(CONTRACT_ID, ARCHIVED)).isFalse();

        assertThat(contract.status()).isEqualTo(ContractStatus.SUBMITTED);
        assertThat(contract.state().error()).isEmpty();
        verifyNoInteractions(statuses);
    }

    @Test
    void contractRowThatCannotBeLocked_failsTheSubmissionBeforeAnythingIsWritten() {
        CannotAcquireLockException busy = new CannotAcquireLockException("строка занята");
        given(contracts.findLockedById(CONTRACT_ID)).willThrow(busy);

        assertThatThrownBy(() -> recording.submit(CONTRACT_ID, KmContractSubmissionMessagesTest.IDENTIFIERS))
                .isSameAs(busy);
        assertThatThrownBy(() -> recording.refuse(CONTRACT_ID, ARCHIVED)).isSameAs(busy);

        assertThat(contract.status()).isEqualTo(ContractStatus.PAID);
        verifyNoInteractions(facts, journal, statuses);
        verify(contracts, never()).findById(any());
    }

    @Test
    void unknownContract_isIllegalStateNamingIt() {
        UUID unknown = UUID.fromString("00000000-0000-4000-8000-000000000001");
        given(contracts.findLockedById(unknown)).willReturn(Optional.empty());

        assertThatThrownBy(() -> recording.submit(unknown, KmContractSubmissionMessagesTest.IDENTIFIERS))
                .isInstanceOf(IllegalStateException.class).hasMessage("договора КМ " + unknown + " нет");
        verify(journal, never()).record(any(), any(), any());
        verify(messages, never()).write(any(), same(contract), any());
    }
}
