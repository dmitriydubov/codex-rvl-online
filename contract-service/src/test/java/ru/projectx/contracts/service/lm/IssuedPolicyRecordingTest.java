package ru.projectx.contracts.service.lm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

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
import org.mockito.InOrder;
import org.slf4j.LoggerFactory;
import org.springframework.dao.CannotAcquireLockException;
import ru.projectx.contracts.entity.CancelReason;
import ru.projectx.contracts.entity.Cancellation;
import ru.projectx.contracts.entity.IssueError;
import ru.projectx.contracts.entity.IssuedPolicy;
import ru.projectx.contracts.entity.LmPolicy;
import ru.projectx.contracts.entity.LmPolicyStatus;
import ru.projectx.contracts.entity.PrintedForm;
import ru.projectx.contracts.entity.PrintedFormKind;
import ru.projectx.contracts.entity.PrintedFormType;
import ru.projectx.contracts.entity.UskDocument;
import ru.projectx.contracts.exception.ContractViolationException;
import ru.projectx.contracts.repository.LmPolicyRepository;
import ru.projectx.contracts.repository.PrintedFormRepository;
import ru.projectx.contracts.service.status.IssuanceSnapshot;
import ru.projectx.contracts.service.status.RequestStatusPublication;
import ru.projectx.contracts.support.Issuances;
import ru.projectx.contracts.support.Tasks;

class IssuedPolicyRecordingTest {

    private static final Instant NOW = Instant.parse("2026-02-11T06:15:00Z");
    private static final UUID POLICY_ID = UUID.fromString("8e2d0b17-5a44-4c90-b2e3-71f0c6a45d39");
    private static final IssuedPolicy ISSUED = new IssuedPolicy(UUID.fromString("cb904f2a-7d61-4e08-89a5-40b1e3c7f012"),
            "LM-000012204", UUID.fromString("13ab5388-61ee-4e21-8a46-c325478b78c0"), "0555123456");
    private static final UskDocument FORM = new UskDocument(PrintedFormType.POLICY,
            "https://s3.sogaz.ru/rvl/lm/2026/02/0555123456.pdf", 96256L);
    private static final IssueError NOT_COVERED = new IssueError("INSURED_NOT_COVERED", "Стенд: не входит");

    private final LmPolicyRepository policies = mock();
    private final PrintedFormRepository forms = mock();
    private final RequestStatusPublication statuses = mock();
    private final IssuanceSnapshot before = mock();
    private final IssuedPolicyRecording recording = new IssuedPolicyRecording(policies, forms, statuses);
    private final LmPolicy policy = Issuances.policy(POLICY_ID.toString(), Tasks.CHILD);
    private final ListAppender<ILoggingEvent> journal = new ListAppender<>();

    @BeforeEach
    void submittedPolicyIsStored() {
        policy.submit(NOW);
        given(policies.findById(POLICY_ID)).willReturn(Optional.of(policy));
        given(policies.findLockedById(POLICY_ID)).willReturn(Optional.of(policy));
        given(statuses.watch(Tasks.REQUEST)).willReturn(before);
        journal.start();
        logger().addAppender(journal);
    }

    @AfterEach
    void stopJournal() {
        logger().detachAppender(journal);
        journal.stop();
    }

    @Test
    void submittedPolicy_awaitsTheResult() {
        assertThat(recording.expect(POLICY_ID)).isEqualTo(PolicyExpectation.AWAITING_RESULT);
    }

    @Test
    void finishedPolicy_isAlreadyFinished() {
        policy.cancel(new Cancellation(CancelReason.EXPIRED, null, NOW), NOW);

        assertThat(recording.expect(POLICY_ID)).isEqualTo(PolicyExpectation.ALREADY_FINISHED);
    }

    @Test
    void newPolicy_isContractViolationBecauseNothingWasRequested() {
        LmPolicy fresh = Issuances.policy(POLICY_ID.toString(), Tasks.CHILD);
        given(policies.findById(POLICY_ID)).willReturn(Optional.of(fresh));

        assertThatThrownBy(() -> recording.expect(POLICY_ID)).isInstanceOf(ContractViolationException.class)
                .hasMessage("запрос на выпуск полиса ЛМ ещё не отправлялся");
    }

    @Test
    void unknownPolicy_isContractViolationNamingTheIdentifier() {
        UUID unknown = UUID.fromString("00000000-0000-4000-8000-000000000001");
        given(policies.findById(unknown)).willReturn(Optional.empty());

        assertThatThrownBy(() -> recording.expect(unknown)).isInstanceOf(ContractViolationException.class)
                .hasMessage("полиса ЛМ " + unknown + " в Договорах нет");
    }

    @Test
    void resultForAnUnknownPolicy_isContractViolationNamingTheIdentifier() {
        UUID unknown = UUID.fromString("00000000-0000-4000-8000-000000000001");
        given(policies.findLockedById(unknown)).willReturn(Optional.empty());

        assertThatThrownBy(() -> recording.recordIssued(unknown, ISSUED, List.of(FORM), NOW))
                .isInstanceOf(ContractViolationException.class)
                .hasMessage("полиса ЛМ " + unknown + " в Договорах нет");
        verifyNoInteractions(statuses, forms);
    }

    @Test
    void recordIssued_marksThePolicyIssuedStoresFinalFormsAndReportsBetweenWatchAndChange() {
        recording.recordIssued(POLICY_ID, ISSUED, List.of(FORM), NOW);

        assertThat(policy.status()).isEqualTo(LmPolicyStatus.ISSUED);
        assertThat(policy.issued()).contains(ISSUED);
        assertThat(policy.state().entityVersion()).isEqualTo(3);
        ArgumentCaptor<List<PrintedForm>> saved = ArgumentCaptor.captor();
        InOrder order = inOrder(statuses, forms);
        order.verify(statuses).watch(Tasks.REQUEST);
        order.verify(forms).saveAll(saved.capture());
        order.verify(statuses).afterRecordChange(before, NOW);
        assertThat(saved.getValue()).hasSize(1);
        PrintedForm form = saved.getValue().get(0);
        assertThat(form.lmPolicyId()).contains(POLICY_ID);
        assertThat(form.kind()).isEqualTo(PrintedFormKind.FINAL);
        assertThat(form.docType()).isEqualTo(PrintedFormType.POLICY);
        assertThat(form.uskFileUrl()).contains("https://s3.sogaz.ru/rvl/lm/2026/02/0555123456.pdf");
        assertThat(journal.list).isEmpty();
    }

    @Test
    void recordIssued_withoutDocuments_storesNoForms() {
        recording.recordIssued(POLICY_ID, ISSUED, List.of(), NOW);

        assertThat(policy.status()).isEqualTo(LmPolicyStatus.ISSUED);
        verify(forms).saveAll(List.of());
    }

    @Test
    void recordFailure_marksThePolicyInErrorAsReceivedAndReports() {
        recording.recordFailure(POLICY_ID, NOT_COVERED, NOW);

        assertThat(policy.status()).isEqualTo(LmPolicyStatus.ERROR);
        assertThat(policy.state().error()).contains(NOT_COVERED);
        InOrder order = inOrder(statuses);
        order.verify(statuses).watch(Tasks.REQUEST);
        order.verify(statuses).afterRecordChange(before, NOW);
        verifyNoInteractions(forms);
    }

    @Test
    void resultForAFinishedPolicy_changesNothingAndWarnsWithIdentifiersAndStatus() {
        policy.cancel(new Cancellation(CancelReason.INSURED_REFUSED, null, NOW), NOW);

        recording.recordIssued(POLICY_ID, ISSUED, List.of(FORM), NOW);
        recording.recordFailure(POLICY_ID, NOT_COVERED, NOW);
        recording.recordLate(POLICY_ID);

        assertThat(policy.status()).isEqualTo(LmPolicyStatus.CANCELLED);
        assertThat(policy.issued()).isEmpty();
        verify(forms, never()).saveAll(any());
        verifyNoInteractions(statuses);
        assertThat(journal.list).filteredOn(event -> event.getLevel() == Level.WARN)
                .extracting(ILoggingEvent::getFormattedMessage)
                .containsOnly("полис ЛМ " + POLICY_ID + " участника " + Tasks.CHILD + " заявки " + Tasks.REQUEST
                        + ": результат выпуска получен по полису ЛМ в статусе CANCELLED, запись не изменена")
                .hasSize(3);
    }

    @Test
    void result_isDecidedOnThePolicyReadWithTheRowLock_notOnTheUnlockedRead() {
        LmPolicy undelivered = Issuances.policy(POLICY_ID.toString(), Tasks.CHILD);
        undelivered.submit(NOW);
        undelivered.markError(new IssueError("LM_POLICY_REQUEST_UNDELIVERED", "не доставлен"), NOW);
        given(policies.findLockedById(POLICY_ID)).willReturn(Optional.of(undelivered));

        assertThat(recording.expect(POLICY_ID)).isEqualTo(PolicyExpectation.AWAITING_RESULT);
        recording.recordIssued(POLICY_ID, ISSUED, List.of(FORM), NOW);

        assertThat(undelivered.status()).isEqualTo(LmPolicyStatus.ERROR);
        assertThat(undelivered.issued()).isEmpty();
        assertThat(policy.status()).isEqualTo(LmPolicyStatus.SUBMITTED);
        verify(forms, never()).saveAll(any());
        verifyNoInteractions(statuses);
        assertThat(journal.list).singleElement().satisfies(event -> assertThat(event.getFormattedMessage())
                .endsWith("результат выпуска получен по полису ЛМ в статусе ERROR, запись не изменена"));
    }

    @Test
    void expectationAndLateResult_readWithoutTheRowLock() {
        given(policies.findLockedById(POLICY_ID)).willThrow(new CannotAcquireLockException("строка полиса занята"));

        assertThat(recording.expect(POLICY_ID)).isEqualTo(PolicyExpectation.AWAITING_RESULT);
        recording.recordLate(POLICY_ID);

        assertThat(journal.list).singleElement().satisfies(event -> assertThat(event.getLevel()).isEqualTo(Level.WARN));
    }

    @Test
    void policyRowHeldLongerThanTheLockTimeout_failsTheResultBeforeAnyDecision() {
        CannotAcquireLockException busy = new CannotAcquireLockException("строка полиса занята");
        given(policies.findLockedById(POLICY_ID)).willThrow(busy);

        assertThatThrownBy(() -> recording.recordIssued(POLICY_ID, ISSUED, List.of(FORM), NOW)).isSameAs(busy);
        assertThatThrownBy(() -> recording.recordFailure(POLICY_ID, NOT_COVERED, NOW)).isSameAs(busy);

        assertThat(policy.status()).isEqualTo(LmPolicyStatus.SUBMITTED);
        verify(forms, never()).saveAll(any());
        verify(policies, never()).findById(any());
        verifyNoInteractions(statuses);
        assertThat(journal.list).isEmpty();
    }

    private static Logger logger() {
        return (Logger) LoggerFactory.getLogger(IssuedPolicyRecording.class);
    }
}
