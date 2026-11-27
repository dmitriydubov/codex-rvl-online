package ru.projectx.contracts.service.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import ru.projectx.contracts.entity.contract.AcceptanceChannel;
import ru.projectx.contracts.entity.contract.ContractAcceptance;
import ru.projectx.contracts.entity.contract.ContractStatus;
import ru.projectx.contracts.entity.contract.ContractStep;
import ru.projectx.contracts.entity.contract.OfferAcceptance;
import ru.projectx.contracts.entity.document.DocumentAttachment;
import ru.projectx.contracts.entity.document.DocumentAttachments;
import ru.projectx.contracts.entity.document.PrintedForm;
import ru.projectx.contracts.entity.document.PrintedFormType;
import ru.projectx.contracts.entity.document.StoredFile;
import ru.projectx.contracts.exception.AttachmentBusyException;
import ru.projectx.contracts.exception.AttachmentLockedException;
import ru.projectx.contracts.message.task.ContractTask;
import ru.projectx.contracts.repository.issuance.ContractAcceptanceRepository;
import ru.projectx.contracts.repository.issuance.ContractRepository;
import ru.projectx.contracts.repository.issuance.PrintedFormRepository;
import ru.projectx.contracts.repository.journal.OutboxMessageRepository;
import ru.projectx.contracts.service.RequestIds;
import ru.projectx.contracts.service.integration.storage.MinioAttachmentStore;
import ru.projectx.contracts.service.issuance.IssuanceService;
import ru.projectx.contracts.service.km.submission.KmContractSubmissionRecording;
import ru.projectx.contracts.service.reference.ExternalIdentifiers;
import ru.projectx.contracts.support.FixedClockConfig;
import ru.projectx.contracts.support.IntegrationTest;
import ru.projectx.contracts.support.RowRaces;
import ru.projectx.contracts.support.Tasks;

@IntegrationTest
@TestPropertySource(properties = "projectx.database.session-timeouts.lock-timeout=400ms")
class AttachmentDeletionRaceIT {

    private static final long WAIT_SECONDS = 12;
    private static final String LOCK_CONTRACT = "select id from contracts where id = ? for update";
    private static final byte[] CONTENT = {1, 2, 3};
    private static final Instant NOW = FixedClockConfig.NOW;

    @Autowired
    private IssuanceService issuance;

    @Autowired
    private ContractRepository contracts;

    @Autowired
    private ContractAcceptanceRepository acceptances;

    @Autowired
    private PrintedFormRepository forms;

    @Autowired
    private OutboxMessageRepository outbox;

    @Autowired
    private AttachmentContentReads downloads;

    @Autowired
    private AttachmentDeletes deletion;

    @Autowired
    private KmContractSubmissionRecording submission;

    @Autowired
    private TransactionTemplate transaction;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private DataSource dataSource;

    @MockitoBean
    private MinioAttachmentStore storage;

    private final UUID request = UUID.randomUUID();
    private final UUID participant = UUID.randomUUID();
    private final UUID attachment = UUID.randomUUID();
    private final StoredFile file = new StoredFile("certificates/stand/" + attachment + ".pdf", "0".repeat(64),
            CONTENT.length, "application/pdf");
    private UUID contractId;
    private RowRaces races;

    @BeforeEach
    void activeCertificate() {
        var task = new ContractTask(1, request, Tasks.INSURANCE_CONTRACT, Tasks.INSURER_POLICY, Tasks.FROM, Tasks.TO,
                List.of(Tasks.insured("0.00"), Tasks.relative(1, participant, "34000.00", "SPOUSE")));
        transaction.executeWithoutResult(status -> issuance.apply(task, NOW));
        contractId = contracts.findByRequestIndividualRequestIdAndInsuredParticipantId(request, participant).getFirst().id();
        var owner = DocumentAttachments.certificate(request, participant, attachment);
        forms.saveAndFlush(PrintedForm.receivedAttachment(PrintedFormType.MEDICAL_CERTIFICATE, owner, file, NOW));
        races = new RowRaces(dataSource, jdbc);
    }

    @AfterEach
    void removeFixtures() {
        jdbc.sql("delete from contract_documents where attachment_id = ?").param(attachment).update();
        jdbc.sql("delete from outbox_messages where correlation_id = ?").param(request).update();
        jdbc.sql("delete from contract_acceptances where contract_id = ?").param(contractId).update();
        jdbc.sql("delete from contract_persons where contract_id = ?").param(contractId).update();
        contracts.deleteById(contractId);
    }

    @Test
    void readingAndRemovingTheObjectBothRunOutsideTheDatabaseTransaction() throws Exception {
        when(storage.open(file.key())).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return new ByteArrayInputStream(CONTENT);
        });
        doAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return null;
        }).when(storage).remove(file.key());

        try (var stream = downloads.open(attachment).stream()) {
            assertThat(stream.readAllBytes()).isEqualTo(CONTENT);
        }
        deleteWithTrace("outside-transaction");
        assertThat(audit().deletedRequestId()).isEqualTo("outside-transaction");
    }

    @Test
    void firstLockTimeoutNeverReachesStorage() throws Exception {
        races.whileTheRowIsHeld(LOCK_CONTRACT, contractId,
                () -> assertThatThrownBy(() -> deleteWithTrace("blocked-first"))
                        .isInstanceOf(AttachmentBusyException.class));

        verifyNoInteractions(storage);
        assertThat(audit().deletedAt()).isNull();
    }

    @Test
    void twoDeletesCanBothReachStorageAndOnlyTheFirstCompletionOwnsTheAudit() throws Exception {
        var firstAtStorage = new CountDownLatch(1);
        var secondAtStorage = new CountDownLatch(1);
        var releaseSecond = new CountDownLatch(1);
        doAnswer(call -> {
            if ("first-delete".equals(RequestIds.current())) {
                firstAtStorage.countDown();
                await(secondAtStorage);
            } else {
                secondAtStorage.countDown();
                await(releaseSecond);
            }
            return null;
        }).when(storage).remove(file.key());

        try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = workers.submit(() -> deleteWithTrace("first-delete"));
            await(firstAtStorage);
            var second = workers.submit(() -> deleteWithTrace("second-delete"));
            try {
                first.get(WAIT_SECONDS, TimeUnit.SECONDS);
            } finally {
                releaseSecond.countDown();
            }
            second.get(WAIT_SECONDS, TimeUnit.SECONDS);
        }

        assertThat(audit().deletedAt()).isEqualTo(NOW);
        assertThat(audit().deletedRequestId()).isEqualTo("first-delete");
    }

    @Test
    void secondLockTimeoutLeavesTheRowActiveAndARetryCompletesTheDeletion() throws Exception {
        var objectRemoved = new CountDownLatch(1);
        var beginSecondPhase = new CountDownLatch(1);
        delayCompletion(objectRemoved, beginSecondPhase);

        try (var worker = Executors.newVirtualThreadPerTaskExecutor()) {
            var pending = worker.submit(() -> deleteWithTrace("blocked-second"));
            await(objectRemoved);
            races.whileTheRowIsHeld(LOCK_CONTRACT, contractId, () -> {
                beginSecondPhase.countDown();
                assertThatThrownBy(() -> pending.get(WAIT_SECONDS, TimeUnit.SECONDS))
                        .isInstanceOf(ExecutionException.class).hasCauseInstanceOf(AttachmentBusyException.class);
            });
        }

        assertThat(audit().deletedAt()).isNull();
        deleteWithTrace("retry-delete");
        assertThat(audit().deletedRequestId()).isEqualTo("retry-delete");
    }

    @Test
    void submissionInTheGapCommitsAndTheSecondDeletionCheckProtectsTheRegistry() throws Exception {
        markPaidAndAccepted();
        var objectRemoved = new CountDownLatch(1);
        var beginSecondPhase = new CountDownLatch(1);
        delayCompletion(objectRemoved, beginSecondPhase);

        try (var worker = Executors.newVirtualThreadPerTaskExecutor()) {
            var pending = worker.submit(() -> deleteWithTrace("racing-submission"));
            try {
                await(objectRemoved);
                submission.submit(contractId, new ExternalIdentifiers(Tasks.PROGRAM, Tasks.INSURANCE_CONTRACT,
                        Tasks.INSURER_POLICY));
            } finally {
                beginSecondPhase.countDown();
            }
            assertThatThrownBy(() -> pending.get(WAIT_SECONDS, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class).hasCauseInstanceOf(AttachmentLockedException.class);
        }

        assertThat(outbox.existsKmContractSubmission(request, List.of(contractId))).isTrue();
        assertThat(contracts.findById(contractId).orElseThrow().status()).isEqualTo(ContractStatus.SUBMITTED);
        assertThat(audit().deletedAt()).isNull();
    }

    private void markPaidAndAccepted() {
        transaction.executeWithoutResult(status -> {
            var contract = contracts.findById(contractId).orElseThrow();
            contract.advance(ContractStep.OFFER_ACCEPTED);
            contract.markPaid(NOW, NOW);
            acceptances.save(ContractAcceptance.record(contractId,
                    new OfferAcceptance(1, Tasks.INSURED, NOW, AcceptanceChannel.WEB, null, null), NOW));
        });
    }

    private void delayCompletion(CountDownLatch removed, CountDownLatch proceed) {
        doAnswer(call -> {
            removed.countDown();
            await(proceed);
            return null;
        }).when(storage).remove(file.key());
    }

    private DocumentAttachment audit() {
        return forms.findByAttachmentAttachmentId(attachment).orElseThrow().attachment().orElseThrow();
    }

    private void deleteWithTrace(String requestId) {
        MDC.put(RequestIds.MDC_KEY, requestId);
        try {
            deletion.delete(attachment);
        } finally {
            MDC.remove(RequestIds.MDC_KEY);
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError(interrupted);
        }
    }
}
