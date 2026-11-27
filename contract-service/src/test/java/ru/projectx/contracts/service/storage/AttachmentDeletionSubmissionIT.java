package ru.projectx.contracts.service.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import ru.projectx.contracts.entity.contract.AcceptanceChannel;
import ru.projectx.contracts.entity.contract.ContractAcceptance;
import ru.projectx.contracts.entity.contract.ContractStep;
import ru.projectx.contracts.entity.contract.OfferAcceptance;
import ru.projectx.contracts.entity.document.DocumentAttachments;
import ru.projectx.contracts.entity.document.PrintedForm;
import ru.projectx.contracts.entity.document.PrintedFormType;
import ru.projectx.contracts.entity.document.StoredFile;
import ru.projectx.contracts.exception.CertificateMissingException;
import ru.projectx.contracts.message.task.ContractTask;
import ru.projectx.contracts.repository.issuance.ContractAcceptanceRepository;
import ru.projectx.contracts.repository.issuance.ContractRepository;
import ru.projectx.contracts.repository.issuance.PrintedFormRepository;
import ru.projectx.contracts.service.RequestIds;
import ru.projectx.contracts.service.integration.storage.MinioAttachmentStore;
import ru.projectx.contracts.service.integration.storage.MinioCertificateStore;
import ru.projectx.contracts.service.issuance.IssuanceService;
import ru.projectx.contracts.service.km.submission.KmContractSubmissions;
import ru.projectx.contracts.service.reference.ExternalIdentifiers;
import ru.projectx.contracts.service.reference.IssuanceReferences;
import ru.projectx.contracts.service.reference.RequestResolution;
import ru.projectx.contracts.support.FixedClockConfig;
import ru.projectx.contracts.support.IntegrationTest;
import ru.projectx.contracts.support.Tasks;

@IntegrationTest
class AttachmentDeletionSubmissionIT {

    private static final long WAIT_SECONDS = 12;
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
    private AttachmentDeletes deletion;
    @Autowired
    private KmContractSubmissions submissions;
    @Autowired
    private TransactionTemplate transaction;
    @Autowired
    private JdbcClient jdbc;
    @MockitoBean
    private MinioAttachmentStore objects;
    @MockitoBean
    private MinioCertificateStore certificates;
    @MockitoBean
    private IssuanceReferences references;

    private final UUID request = UUID.randomUUID();
    private final UUID participant = UUID.randomUUID();
    private final UUID attachment = UUID.randomUUID();
    private final StoredFile file = new StoredFile("certificates/stand/" + attachment + ".pdf", "0".repeat(64),
            3, "application/pdf");
    private UUID contractId;

    @AfterEach
    void removeFixtures() {
        jdbc.sql("delete from contract_documents where attachment_id = ?").param(attachment).update();
        jdbc.sql("delete from outbox_messages where correlation_id = ?").param(request).update();
        jdbc.sql("delete from contract_acceptances where contract_id = ?").param(contractId).update();
        jdbc.sql("delete from contract_persons where contract_id = ?").param(contractId).update();
        contracts.deleteById(contractId);
    }

    @Test
    void preparationInTheGapRefusesTheContractAndManualReturnSubmitsWithoutTheDeletedCertificate() throws Exception {
        paidContractWithCertificate();
        runPreparationBetweenRemovalAndCompletion();

        assertThat(forms.findByAttachmentAttachmentId(attachment).orElseThrow().attachment().orElseThrow().deletedAt())
                .isEqualTo(NOW);
        assertThat(jdbc.sql(manualReturnSql()).param("contractId", contractId).update()).isEqualTo(1);
        assertThat(submissions.prepareDue()).isEqualTo(1);
        assertThat(jdbc.sql("""
                select payload #> '{data,documents}' = '[]'::jsonb
                  from outbox_messages
                 where event_type = 'KmContractSubmitted' and aggregate_id = :contractId
                """).param("contractId", contractId).query(Boolean.class).single()).isTrue();
        verify(certificates).verifyPresent(file.key());
    }

    private void paidContractWithCertificate() {
        var task = new ContractTask(1, request, Tasks.INSURANCE_CONTRACT, Tasks.INSURER_POLICY, Tasks.FROM, Tasks.TO,
                List.of(Tasks.insured("0.00"), Tasks.relative(1, participant, "34000.00", "SPOUSE")));
        transaction.executeWithoutResult(status -> issuance.apply(task, NOW));
        contractId = contracts.findByRequestIndividualRequestIdAndInsuredParticipantId(request, participant).getFirst().id();
        transaction.executeWithoutResult(status -> {
            var contract = contracts.findById(contractId).orElseThrow();
            contract.advance(ContractStep.OFFER_ACCEPTED);
            contract.markPaid(NOW, NOW);
            acceptances.save(ContractAcceptance.record(contractId,
                    new OfferAcceptance(1, Tasks.INSURED, NOW, AcceptanceChannel.WEB, null, null), NOW));
        });
        var owner = DocumentAttachments.certificate(request, participant, attachment);
        forms.saveAndFlush(PrintedForm.receivedAttachment(PrintedFormType.MEDICAL_CERTIFICATE, owner, file, NOW));
        when(references.resolve(any())).thenReturn(new RequestResolution.Resolved(
                new ExternalIdentifiers(Tasks.PROGRAM, Tasks.INSURANCE_CONTRACT, Tasks.INSURER_POLICY)));
    }

    private void runPreparationBetweenRemovalAndCompletion() throws Exception {
        var removed = new CountDownLatch(1);
        var allowCompletion = new CountDownLatch(1);
        doThrow(new CertificateMissingException()).when(certificates).verifyPresent(file.key());
        doAnswer(call -> {
            removed.countDown();
            assertThat(allowCompletion.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
            return null;
        }).when(objects).remove(file.key());

        try (var worker = Executors.newVirtualThreadPerTaskExecutor()) {
            var pending = worker.submit(this::deleteWithTrace);
            try {
                assertThat(removed.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
                assertThat(submissions.prepareDue()).isEqualTo(1);
                assertThat(jdbc.sql("select error_code from contracts where id = ? and status = 'ERROR'")
                        .param(contractId).query(String.class).single()).isEqualTo("KM_CONTRACT_SUBMISSION_PREPARATION_FAILED");
            } finally {
                allowCompletion.countDown();
            }
            pending.get(WAIT_SECONDS, TimeUnit.SECONDS);
        }
    }

    private void deleteWithTrace() {
        MDC.put(RequestIds.MDC_KEY, "delete-before-submission-check");
        try {
            deletion.delete(attachment);
        } finally {
            MDC.remove(RequestIds.MDC_KEY);
        }
    }

    private static String manualReturnSql() throws IOException {
        var operations = Files.readString(Path.of("docs", "operations.md"));
        int heading = operations.indexOf("### Возврат договора КМ, не переданного в 1С:УСК, в оплаченные");
        assertThat(heading).isNotNegative();
        int marker = operations.indexOf("```sql\n", heading);
        assertThat(marker).isNotNegative();
        int start = marker + "```sql\n".length();
        return operations.substring(start, operations.indexOf("```", start)).replace("'<contract-id>'", ":contractId");
    }
}
