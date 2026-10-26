package ru.projectx.contracts.service.km;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import java.sql.SQLException;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Limit;
import org.springframework.util.unit.DataSize;
import ru.projectx.contracts.config.KmContractSubmissionProperties;
import ru.projectx.contracts.entity.Contract;
import ru.projectx.contracts.entity.ContractStatus;
import ru.projectx.contracts.entity.IssueError;
import ru.projectx.contracts.exception.MessageTooLargeException;
import ru.projectx.contracts.exception.ReferenceKind;
import ru.projectx.contracts.repository.ContractRepository;
import ru.projectx.contracts.service.RequestIds;
import ru.projectx.contracts.service.reference.ExternalIdentifiers;
import ru.projectx.contracts.service.reference.IssuanceReferences;
import ru.projectx.contracts.service.reference.ReferenceMetrics;
import ru.projectx.contracts.service.reference.RequestResolution;
import ru.projectx.contracts.service.usk.UskPreparationMetrics;
import ru.projectx.contracts.support.Issuances;
import ru.projectx.contracts.support.Tasks;

class KmContractSubmissionsTest {

    private static final Instant PAID_AT = Instant.parse("2026-02-10T07:20:15Z");
    private static final ExternalIdentifiers IDENTIFIERS = new ExternalIdentifiers(
            UUID.fromString("6b02f1cc-9d47-4e35-8a20-7f1c3e5b09a2"), UUID.fromString("8c41e2ab-55d0-4a17-9b63-2f0e7c1d4a88"),
            UUID.fromString("1d9a6e77-4c02-4b88-a15e-30f7b2c9de41"));
    private static final IssueError PREPARATION_FAILED = new IssueError("KM_CONTRACT_SUBMISSION_PREPARATION_FAILED",
            "передача договора КМ в 1С:УСК не подготовлена: внутренняя ошибка сервиса Договоров");
    private static final IssueError ARCHIVED = new IssueError("PROGRAM_ARCHIVED", "программа страхования в архиве");

    private final ContractRepository contracts = mock();
    private final IssuanceReferences references = mock();
    private final KmContractSubmissionRecording recording = mock();
    private final ReferenceMetrics metrics = mock();
    private final UskPreparationMetrics preparationMetrics = mock();
    private final KmContractSubmissions submissions = new KmContractSubmissions(contracts, recording, references,
            metrics, preparationMetrics, new KmContractSubmissionProperties(Duration.ofSeconds(10), 7, DataSize.ofBytes(65536),
                    Duration.ofMinutes(1), Duration.ofHours(1)));
    private final Contract first = Issuances.paidContract("0f4b8e2a-6c13-4d7e-b9a1-2e5c7d3f8a64", Tasks.SPOUSE,
            "DKM-2026-000001", PAID_AT);
    private final Contract second = Issuances.paidContract("9f3e1c28-6b55-4da1-83f4-82a1d7b56e4a", Tasks.CHILD,
            "DKM-2026-000002", PAID_AT);
    private final ListAppender<ILoggingEvent> journal = new ListAppender<>();

    @BeforeEach
    void twoPaidContractsAreDue() {
        given(contracts.findByStatusOrderByCreatedAtAscIdAsc(ContractStatus.PAID, Limit.of(7)))
                .willReturn(List.of(first, second));
        journal.start();
        logger().addAppender(journal);
    }

    @AfterEach
    void stopJournal() {
        logger().detachAppender(journal);
        journal.stop();
    }

    @Test
    void resolvedContracts_areSubmittedInOrderWithTheirIdentifiers() {
        given(references.resolve(any())).willReturn(new RequestResolution.Resolved(IDENTIFIERS));

        assertThat(submissions.prepareDue()).isEqualTo(2);

        InOrder order = inOrder(recording);
        order.verify(recording).submit(first.id(), IDENTIFIERS);
        order.verify(recording).submit(second.id(), IDENTIFIERS);
        verifyNoInteractions(metrics, preparationMetrics);
        assertThat(journal.list).isEmpty();
    }

    @Test
    void brokenInternalCondition_entersErrorWithThePreparationCodeAndThePassGoesOn() {
        given(references.resolve(any())).willReturn(new RequestResolution.Resolved(IDENTIFIERS));
        willThrow(new IllegalStateException("у договора КМ нет записи акцепта Тестова"))
                .given(recording).submit(first.id(), IDENTIFIERS);
        given(recording.refuse(first.id(), PREPARATION_FAILED)).willReturn(true);

        assertThat(submissions.prepareDue()).isEqualTo(2);

        InOrder order = inOrder(recording);
        order.verify(recording).submit(first.id(), IDENTIFIERS);
        order.verify(recording).refuse(first.id(), PREPARATION_FAILED);
        order.verify(recording).submit(second.id(), IDENTIFIERS);
        verify(preparationMetrics).countFailed("KmContractSubmitted");
        assertThat(journal.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.ERROR);
            assertThat(event.getFormattedMessage()).isEqualTo("договор КМ " + first.id() + " участника " + Tasks.SPOUSE
                    + " заявки " + Tasks.REQUEST + ": передача договора КМ не подготовлена из-за внутренней ошибки,"
                    + " договор переведён в ERROR, тип отказа java.lang.IllegalStateException, SQLSTATE нет");
            assertThat(event.getThrowableProxy()).isNull();
        });
    }

    @Test
    void databaseRefusalThatRepeatsOnRetry_entersErrorAndNamesItsSqlState() {
        given(references.resolve(any())).willReturn(new RequestResolution.Resolved(IDENTIFIERS));
        willThrow(new DataIntegrityViolationException("нарушено ограничение", new SQLException("ограничение", "23505")))
                .given(recording).submit(first.id(), IDENTIFIERS);
        given(recording.refuse(first.id(), PREPARATION_FAILED)).willReturn(true);

        assertThat(submissions.prepareDue()).isEqualTo(2);

        verify(recording).submit(second.id(), IDENTIFIERS);
        assertThat(journal.list).extracting(ILoggingEvent::getFormattedMessage).singleElement().asString()
                .endsWith(", тип отказа org.springframework.dao.DataIntegrityViolationException, SQLSTATE 23505");
    }

    @Test
    void brokenInternalCondition_ofAContractNoLongerPaid_isNeitherCountedNorLoggedAndThePassGoesOn() {
        given(references.resolve(any())).willReturn(new RequestResolution.Resolved(IDENTIFIERS));
        willThrow(new IllegalStateException("у договора КМ нет момента оплаты"))
                .given(recording).submit(first.id(), IDENTIFIERS);

        assertThat(submissions.prepareDue()).isEqualTo(2);

        verify(recording).refuse(first.id(), PREPARATION_FAILED);
        verify(recording).submit(second.id(), IDENTIFIERS);
        verifyNoInteractions(preparationMetrics);
        assertThat(journal.list).isEmpty();
    }

    @Test
    void refusedContract_isRefusedWithItsErrorAndThePassGoesOn() {
        given(references.resolve(any())).willReturn(new RequestResolution.Refused(ARCHIVED),
                new RequestResolution.Resolved(IDENTIFIERS));

        assertThat(submissions.prepareDue()).isEqualTo(2);

        InOrder order = inOrder(recording);
        order.verify(recording).refuse(first.id(), ARCHIVED);
        order.verify(recording).submit(second.id(), IDENTIFIERS);
    }

    @Test
    void messageOverTheLimit_refusesTheContractWithTheLimitCodeAndAWarningAndThePassGoesOn() {
        given(references.resolve(any())).willReturn(new RequestResolution.Resolved(IDENTIFIERS));
        willThrow(new MessageTooLargeException(70004, 65536, 2, "KM_CONTRACT_MESSAGE_TOO_LARGE",
                "сообщение о договоре КМ больше предела 65536 байт, справок — 2")).given(recording).submit(first.id(), IDENTIFIERS);

        assertThat(submissions.prepareDue()).isEqualTo(2);

        InOrder order = inOrder(recording);
        order.verify(recording).submit(first.id(), IDENTIFIERS);
        order.verify(recording).refuse(first.id(), new IssueError("KM_CONTRACT_MESSAGE_TOO_LARGE",
                "сообщение о договоре КМ больше предела 65536 байт, справок — 2"));
        order.verify(recording).submit(second.id(), IDENTIFIERS);
        assertThat(journal.list).filteredOn(event -> event.getLevel() == Level.WARN)
                .extracting(ILoggingEvent::getFormattedMessage)
                .containsExactly("договор КМ " + first.id() + " участника " + Tasks.SPOUSE + " заявки " + Tasks.REQUEST
                        + ": сообщение в 70004 байт больше предела 65536 байт, вложений — 2");
    }

    @Test
    void unavailableReference_endsThePassWithAWarningAndACountedKindLeavingTheRestForTheNextPass() {
        given(references.resolve(first.state())).willReturn(new RequestResolution.Unavailable(ReferenceKind.POLICY));

        assertThat(submissions.prepareDue()).isZero();

        verify(references).resolve(first.state());
        verifyNoInteractions(recording);
        verify(metrics).countUnavailable(ReferenceKind.POLICY);
        assertThat(journal.list).filteredOn(event -> event.getLevel() == Level.WARN)
                .extracting(ILoggingEvent::getFormattedMessage)
                .containsExactly("договор КМ " + first.id() + " участника " + Tasks.SPOUSE + " заявки " + Tasks.REQUEST
                        + ": справочный сервис недоступен, передача отложена до следующего прохода");
    }

    @Test
    void eachContract_isPreparedUnderItsOwnIdAsTheRequestIdOfCallsToTheReferenceService() {
        List<String> requestIds = new ArrayList<>();
        given(references.resolve(any())).willAnswer(call -> {
            requestIds.add(RequestIds.current());
            return new RequestResolution.Resolved(IDENTIFIERS);
        });

        submissions.prepareDue();

        assertThat(requestIds).containsExactly(first.id().toString(), second.id().toString());
        assertThat(MDC.get(RequestIds.MDC_KEY)).isNull();
    }

    @Test
    void contractRowBusyLongerThanTheLockTimeout_endsThePassWithThatFailureLeavingTheRestForTheNextPass() {
        CannotAcquireLockException busy = new CannotAcquireLockException("строка занята");
        given(references.resolve(any())).willReturn(new RequestResolution.Resolved(IDENTIFIERS));
        willThrow(busy).given(recording).submit(first.id(), IDENTIFIERS);

        assertThatThrownBy(submissions::prepareDue).isSameAs(busy);

        verify(recording, never()).submit(second.id(), IDENTIFIERS);
        verify(recording, never()).refuse(any(), any());
        verifyNoInteractions(preparationMetrics);
    }

    @Test
    void nothingDue_preparesNothing() {
        given(contracts.findByStatusOrderByCreatedAtAscIdAsc(ContractStatus.PAID, Limit.of(7))).willReturn(List.of());

        assertThat(submissions.prepareDue()).isZero();
        verifyNoInteractions(references, recording);
    }

    private static Logger logger() {
        return (Logger) LoggerFactory.getLogger(KmContractSubmissions.class);
    }
}
