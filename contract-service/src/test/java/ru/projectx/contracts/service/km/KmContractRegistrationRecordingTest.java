package ru.projectx.contracts.service.km;

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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.slf4j.LoggerFactory;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.test.util.ReflectionTestUtils;
import ru.projectx.contracts.entity.issuance.CancelReason;
import ru.projectx.contracts.entity.issuance.Cancellation;
import ru.projectx.contracts.entity.contract.Contract;
import ru.projectx.contracts.entity.contract.ContractRegistration;
import ru.projectx.contracts.entity.contract.ContractStatus;
import ru.projectx.contracts.entity.issuance.IssueError;
import ru.projectx.contracts.entity.issuance.IssuedPolicy;
import ru.projectx.contracts.entity.document.PrintedForm;
import ru.projectx.contracts.entity.document.PrintedFormKind;
import ru.projectx.contracts.entity.document.PrintedFormType;
import ru.projectx.contracts.entity.document.UskDocument;
import ru.projectx.contracts.exception.ContractViolationException;
import ru.projectx.contracts.repository.ContractRepository;
import ru.projectx.contracts.repository.PrintedFormRepository;
import ru.projectx.contracts.service.status.IssuanceSnapshot;
import ru.projectx.contracts.service.status.RequestStatusPublication;
import ru.projectx.contracts.service.usk.ResultExpectation;
import ru.projectx.contracts.support.Issuances;
import ru.projectx.contracts.support.Tasks;

class KmContractRegistrationRecordingTest {

    private static final Instant NOW = Instant.parse("2026-02-11T06:15:00Z");
    private static final Instant PAID_AT = Instant.parse("2026-02-10T07:20:15Z");
    private static final UUID CONTRACT_ID = UUID.fromString("0f4b8e2a-6c13-4d7e-b9a1-2e5c7d3f8a64");
    private static final IssuedPolicy ISSUED = new IssuedPolicy(UUID.fromString("a71f3c98-4e20-4a55-93b1-5d0c7e2f8814"),
            "DKM-000004417", UUID.fromString("2c7e9b40-1f5a-4d83-9e6b-0a4c8d2f7e15"), "0987654321");
    private static final UskDocument CONTRACT_FORM = new UskDocument(PrintedFormType.CONTRACT,
            "https://1c-usk.example/rvl/km/DKM-2026-000001.pdf", 205312L);
    private static final UskDocument POLICY_FORM = new UskDocument(PrintedFormType.POLICY,
            "https://1c-usk.example/rvl/km/0987654321.pdf", 118784L);
    private static final IssueError REJECTED = new IssueError("CONTRACT_REJECTED", "Стенд: регистрация невозможна");

    private final ContractRepository contracts = mock();
    private final PrintedFormRepository forms = mock();
    private final RequestStatusPublication statuses = mock();
    private final IssuanceSnapshot before = mock();
    private final KmContractRegistrationRecording recording = new KmContractRegistrationRecording(contracts, forms,
            statuses);
    private final Contract contract = Issuances.paidContract(CONTRACT_ID.toString(), Tasks.SPOUSE, "DKM-2026-000001",
            PAID_AT);
    private final ListAppender<ILoggingEvent> journal = new ListAppender<>();

    @BeforeEach
    void submittedContractIsStored() {
        contract.submit(NOW);
        given(contracts.findById(CONTRACT_ID)).willReturn(Optional.of(contract));
        given(contracts.findLockedById(CONTRACT_ID)).willReturn(Optional.of(contract));
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
    void submittedContract_awaitsTheResult() {
        assertThat(recording.expect(CONTRACT_ID)).isEqualTo(ResultExpectation.AWAITING_RESULT);
    }

    @Test
    void finishedContract_isAlreadyFinished() {
        contract.cancel(new Cancellation(CancelReason.EXPIRED, null, NOW), NOW);

        assertThat(recording.expect(CONTRACT_ID)).isEqualTo(ResultExpectation.ALREADY_FINISHED);
    }

    @ParameterizedTest
    @EnumSource(value = ContractStatus.class, names = {"NEW", "DRAFT_READY", "ACCEPTED", "INVOICE_ISSUED", "PAID"})
    void contractNotYetSubmitted_isContractViolationBecauseNothingWasSent(ContractStatus status) {
        ReflectionTestUtils.setField(contract, "status", status);

        assertThatThrownBy(() -> recording.expect(CONTRACT_ID)).isInstanceOf(ContractViolationException.class)
                .hasMessage("передача договора КМ в 1С:УСК ещё не отправлялась");
    }

    @Test
    void unknownContract_isContractViolationNamingTheIdentifier() {
        UUID unknown = UUID.fromString("3f77b1c8-2a55-4c19-9e30-6b8d2f0a71c4");
        given(contracts.findById(unknown)).willReturn(Optional.empty());
        given(contracts.findLockedById(unknown)).willReturn(Optional.empty());

        assertThatThrownBy(() -> recording.expect(unknown)).isInstanceOf(ContractViolationException.class)
                .hasMessage("договора КМ " + unknown + " в Договорах нет");
        assertThatThrownBy(() -> recording.recordIssued(unknown, ISSUED, List.of(), NOW))
                .isInstanceOf(ContractViolationException.class)
                .hasMessage("договора КМ " + unknown + " в Договорах нет");
        verifyNoInteractions(statuses, forms);
    }

    @Test
    void recordIssued_marksTheContractIssuedStoresItsFinalFormsInOrderAndReportsBetweenWatchAndChange() {
        recording.recordIssued(CONTRACT_ID, ISSUED, List.of(CONTRACT_FORM, POLICY_FORM), NOW);

        assertThat(contract.status()).isEqualTo(ContractStatus.ISSUED);
        assertThat(contract.progress().registration()).contains(new ContractRegistration(ISSUED.uskId(),
                ISSUED.uskNumber(), ISSUED.issuedPolicyId(), ISSUED.issuedPolicyNumber()));
        assertThat(contract.state().entityVersion()).isEqualTo(4);
        ArgumentCaptor<List<PrintedForm>> saved = ArgumentCaptor.captor();
        InOrder order = inOrder(statuses, forms);
        order.verify(statuses).watch(Tasks.REQUEST);
        order.verify(forms).saveAll(saved.capture());
        order.verify(statuses).afterRecordChange(before, NOW);
        assertThat(saved.getValue()).hasSize(2);
        assertThat(saved.getValue()).allSatisfy(form -> {
            assertThat(form.contractId()).contains(CONTRACT_ID);
            assertThat(form.lmPolicyId()).isEmpty();
            assertThat(form.kind()).isEqualTo(PrintedFormKind.FINAL);
        });
        assertThat(saved.getValue()).extracting(PrintedForm::docType)
                .containsExactly(PrintedFormType.CONTRACT, PrintedFormType.POLICY);
        assertThat(saved.getValue().get(1).uskFileUrl()).contains("https://1c-usk.example/rvl/km/0987654321.pdf");
        assertThat(journal.list).isEmpty();
    }

    @Test
    void recordIssued_withoutDocuments_storesNoForms() {
        recording.recordIssued(CONTRACT_ID, ISSUED, List.of(), NOW);

        assertThat(contract.status()).isEqualTo(ContractStatus.ISSUED);
        verify(forms).saveAll(List.of());
    }

    @Test
    void recordFailure_marksTheContractInErrorAsReceivedKeepsThePaymentAndReports() {
        recording.recordFailure(CONTRACT_ID, REJECTED, NOW);

        assertThat(contract.status()).isEqualTo(ContractStatus.ERROR);
        assertThat(contract.state().error()).contains(REJECTED);
        assertThat(contract.state().entityVersion()).isEqualTo(4);
        assertThat(contract.progress().paidAt()).contains(PAID_AT);
        assertThat(contract.progress().registration()).isEmpty();
        InOrder order = inOrder(statuses);
        order.verify(statuses).watch(Tasks.REQUEST);
        order.verify(statuses).afterRecordChange(before, NOW);
        verifyNoInteractions(forms);
    }

    @Test
    void resultForAFinishedContract_changesNothingAndWarnsWithIdentifiersAndStatus() {
        contract.markError(new IssueError("KM_CONTRACT_SUBMISSION_UNDELIVERED", "не передан"), NOW);

        recording.recordIssued(CONTRACT_ID, ISSUED, List.of(CONTRACT_FORM), NOW);
        recording.recordFailure(CONTRACT_ID, REJECTED, NOW);
        recording.recordLate(CONTRACT_ID);

        assertThat(contract.status()).isEqualTo(ContractStatus.ERROR);
        assertThat(contract.state().error()).contains(new IssueError("KM_CONTRACT_SUBMISSION_UNDELIVERED", "не передан"));
        assertThat(contract.progress().registration()).isEmpty();
        verify(forms, never()).saveAll(any());
        verifyNoInteractions(statuses);
        assertThat(journal.list).filteredOn(event -> event.getLevel() == Level.WARN)
                .extracting(ILoggingEvent::getFormattedMessage)
                .containsOnly("договор КМ " + CONTRACT_ID + " участника " + Tasks.SPOUSE + " заявки " + Tasks.REQUEST
                        + ": результат регистрации получен по договору КМ в статусе ERROR, запись не изменена")
                .hasSize(3);
    }

    @Test
    void result_isDecidedOnTheContractReadWithTheRowLock_notOnTheUnlockedRead() {
        Contract cancelled = Issuances.paidContract(CONTRACT_ID.toString(), Tasks.SPOUSE, "DKM-2026-000001", PAID_AT);
        cancelled.submit(NOW);
        cancelled.cancel(new Cancellation(CancelReason.INSURED_REFUSED, null, NOW), NOW);
        given(contracts.findLockedById(CONTRACT_ID)).willReturn(Optional.of(cancelled));

        assertThat(recording.expect(CONTRACT_ID)).isEqualTo(ResultExpectation.AWAITING_RESULT);
        recording.recordIssued(CONTRACT_ID, ISSUED, List.of(CONTRACT_FORM), NOW);

        assertThat(cancelled.status()).isEqualTo(ContractStatus.CANCELLED);
        assertThat(contract.status()).isEqualTo(ContractStatus.SUBMITTED);
        verify(forms, never()).saveAll(any());
        verifyNoInteractions(statuses);
        assertThat(journal.list).singleElement().satisfies(event -> assertThat(event.getFormattedMessage())
                .endsWith("результат регистрации получен по договору КМ в статусе CANCELLED, запись не изменена"));
    }

    @Test
    void contractRowHeldLongerThanTheLockTimeout_failsTheResultBeforeAnyDecision() {
        CannotAcquireLockException busy = new CannotAcquireLockException("строка договора занята");
        given(contracts.findLockedById(CONTRACT_ID)).willThrow(busy);

        assertThatThrownBy(() -> recording.recordIssued(CONTRACT_ID, ISSUED, List.of(CONTRACT_FORM), NOW)).isSameAs(busy);
        assertThatThrownBy(() -> recording.recordFailure(CONTRACT_ID, REJECTED, NOW)).isSameAs(busy);
        assertThat(recording.expect(CONTRACT_ID)).isEqualTo(ResultExpectation.AWAITING_RESULT);

        assertThat(contract.status()).isEqualTo(ContractStatus.SUBMITTED);
        verify(forms, never()).saveAll(any());
        verifyNoInteractions(statuses);
    }

    private static Logger logger() {
        return (Logger) LoggerFactory.getLogger(KmContractRegistrationRecording.class);
    }
}
