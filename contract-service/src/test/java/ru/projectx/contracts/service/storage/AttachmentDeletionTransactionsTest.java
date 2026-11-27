package ru.projectx.contracts.service.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import ru.projectx.contracts.entity.contract.Contract;
import ru.projectx.contracts.entity.contract.ContractStatus;
import ru.projectx.contracts.entity.document.DocumentAttachments;
import ru.projectx.contracts.entity.document.PrintedForm;
import ru.projectx.contracts.entity.document.PrintedFormType;
import ru.projectx.contracts.entity.document.StoredFile;
import ru.projectx.contracts.exception.AttachmentLockedException;
import ru.projectx.contracts.repository.issuance.ContractRepository;
import ru.projectx.contracts.repository.issuance.PrintedFormRepository;
import ru.projectx.contracts.repository.journal.OutboxMessageRepository;
import ru.projectx.contracts.service.RequestIds;

class AttachmentDeletionTransactionsTest {

    private static final UUID ATTACHMENT = UUID.fromString("3c2f8e10-5a7b-4d21-9f6e-1b0a7c4d8e21");
    private static final UUID REQUEST = UUID.fromString("61a7982c-a0b4-4bbf-9527-60b40b752803");
    private static final UUID PARTICIPANT = UUID.fromString("9275471c-d2fe-484f-97a6-d6d7b1267b5e");
    private static final UUID CONTRACT = UUID.fromString("0f4b8e2a-6c13-4d7e-b9a1-2e5c7d3f8a64");
    private static final Instant NOW = Instant.parse("2026-02-10T07:05:00Z");
    private static final StoredFile FILE = new StoredFile("private/object.pdf", "ab", 3, "application/pdf");

    private final AttachmentRegistry registry = mock();
    private final PrintedFormRepository documents = mock();
    private final ContractRepository contracts = mock();
    private final OutboxMessageRepository outbox = mock();
    private final AttachmentDeletionTransactions transactions = new AttachmentDeletionTransactions(registry, documents,
            new CertificateDeletionGuard(contracts, outbox), Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void earlyCertificateNeedsNoOutboxCheck() {
        PrintedForm certificate = certificate();

        assertThat(transactions.prepare(ATTACHMENT)).contains(certificate);
        verifyNoInteractions(outbox, documents);
    }

    @Test
    void consentNeitherLocksNorChecksAnyContract() {
        var owner = DocumentAttachments.consent(REQUEST, PARTICIPANT, ATTACHMENT);
        var consent = PrintedForm.receivedAttachment(PrintedFormType.PD_CONSENT, owner, FILE, NOW);
        when(registry.active(ATTACHMENT)).thenReturn(Optional.of(consent));

        assertThat(transactions.prepare(ATTACHMENT)).contains(consent);

        MDC.put(RequestIds.MDC_KEY, "first-delete");
        try {
            transactions.complete(ATTACHMENT, consent);
        } finally {
            MDC.remove(RequestIds.MDC_KEY);
        }
        verify(documents).markAttachmentDeleted(ATTACHMENT, NOW, "first-delete");
        verifyNoInteractions(contracts, outbox);
    }

    @Test
    void entirePairIsLockedAndEvenTheOlderContractCanProtectACertificate() {
        certificate();
        Contract paid = contract(ContractStatus.PAID);
        Contract cancelled = mock();
        UUID older = UUID.randomUUID();
        when(cancelled.status()).thenReturn(ContractStatus.CANCELLED);
        when(cancelled.id()).thenReturn(older);
        List<Contract> pair = List.of(paid, cancelled);
        when(contracts.findByRequestIndividualRequestIdAndInsuredParticipantId(REQUEST, PARTICIPANT)).thenReturn(pair);
        when(outbox.existsKmContractSubmission(REQUEST, List.of(CONTRACT, older))).thenReturn(true);

        assertThatThrownBy(() -> transactions.prepare(ATTACHMENT)).isInstanceOf(AttachmentLockedException.class);

        verify(contracts).lockFresh(pair);
        verifyNoInteractions(documents);
    }

    @Test
    void submittedAndIssuedContractsEachProtectTheCertificateWithoutOutboxData() {
        certificate();
        var submitted = List.of(contract(ContractStatus.SUBMITTED));
        var issued = List.of(contract(ContractStatus.ISSUED));
        when(contracts.findByRequestIndividualRequestIdAndInsuredParticipantId(REQUEST, PARTICIPANT))
                .thenReturn(submitted).thenReturn(issued);

        assertThatThrownBy(() -> transactions.prepare(ATTACHMENT)).isInstanceOf(AttachmentLockedException.class);
        assertThatThrownBy(() -> transactions.prepare(ATTACHMENT)).isInstanceOf(AttachmentLockedException.class);
        verifyNoInteractions(outbox, documents);
    }

    @Test
    void firstCheckAllowsPaidContractAndSecondCheckRefusesAfterSubmission() {
        PrintedForm certificate = certificate();
        Contract paid = contract(ContractStatus.PAID);
        var pair = List.of(paid);
        when(contracts.findByRequestIndividualRequestIdAndInsuredParticipantId(REQUEST, PARTICIPANT)).thenReturn(pair);
        when(outbox.existsKmContractSubmission(REQUEST, List.of(CONTRACT))).thenReturn(false, true);

        assertThat(transactions.prepare(ATTACHMENT)).contains(certificate);
        assertThatThrownBy(() -> transactions.complete(ATTACHMENT, certificate))
                .isInstanceOf(AttachmentLockedException.class);

        verify(contracts, times(2)).lockFresh(pair);
        verifyNoInteractions(documents);
    }

    @Test
    void aConcurrentCompletionVoidsTheSecondAuditWrite() {
        PrintedForm certificate = certificate();
        when(registry.active(ATTACHMENT)).thenReturn(Optional.empty());

        transactions.complete(ATTACHMENT, certificate);

        verifyNoInteractions(documents, outbox);
    }

    private PrintedForm certificate() {
        var owner = DocumentAttachments.certificate(REQUEST, PARTICIPANT, ATTACHMENT);
        var certificate = PrintedForm.receivedAttachment(PrintedFormType.MEDICAL_CERTIFICATE, owner, FILE, NOW);
        when(registry.active(ATTACHMENT)).thenReturn(Optional.of(certificate));
        return certificate;
    }

    private static Contract contract(ContractStatus state) {
        Contract contract = mock();
        when(contract.status()).thenReturn(state);
        when(contract.id()).thenReturn(CONTRACT);
        return contract;
    }
}
