package ru.projectx.contracts.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;
import ru.projectx.contracts.entity.DocumentKind;
import ru.projectx.contracts.entity.IssuanceRequest;
import ru.projectx.contracts.entity.ParticipantAttachment;
import ru.projectx.contracts.entity.PrintedForm;
import ru.projectx.contracts.entity.PrintedFormType;
import ru.projectx.contracts.entity.RequestParticipant;
import ru.projectx.contracts.entity.StoredFile;
import ru.projectx.contracts.support.IntegrationTest;

@IntegrationTest
class PrintedFormRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-02-10T07:05:00Z");
    private static final UUID REQUEST = UUID.fromString("61a7982c-a0b4-4bbf-9527-60b40b752803");
    private static final UUID PARTICIPANT = UUID.fromString("6778e8c2-985a-4d54-9bd3-0f6403b104d6");
    private static final UUID ATTACHMENT = UUID.fromString("3c2f8e10-5a7b-4d21-9f6e-1b0a7c4d8e21");
    private static final ParticipantAttachment OWNER = new ParticipantAttachment(REQUEST, PARTICIPANT, ATTACHMENT);
    private static final StoredFile FILE = new StoredFile("certificates/0b6c1d2e/" + ATTACHMENT + ".jpg",
            "7851d78af0fb98bbbda8933f49860a009ac7761c21a1136613c8a6704b156251", 76, "image/jpeg");

    @Autowired
    private PrintedFormRepository documents;

    @Autowired
    private IssuanceRequestRepository requests;

    @Autowired
    private TransactionTemplate transaction;

    @Autowired
    private JdbcClient jdbc;

    @BeforeEach
    void knownRequest() {
        jdbc.sql("truncate table contract_documents, request_participants, issuance_requests cascade").update();
        transaction.executeWithoutResult(status -> {
            IssuanceRequest request = IssuanceRequest.start(REQUEST, NOW);
            request.takeComposition(List.of(new RequestParticipant(PARTICIPANT, DocumentKind.LM_POLICY)), NOW);
            requests.save(request);
        });
    }

    @Test
    void certificate_isFoundByAttachmentWithItsOwnerAndFile() {
        documents.saveAndFlush(PrintedForm.receivedCertificate(OWNER, FILE, NOW));

        assertThat(documents.findByAttachmentAttachmentId(ATTACHMENT)).get().satisfies(found -> {
            assertThat(found.attachment()).contains(OWNER);
            assertThat(found.storedFile()).contains(FILE);
            assertThat(found.docType()).isEqualTo(PrintedFormType.MEDICAL_CERTIFICATE);
            assertThat(found.lmPolicyId()).isEmpty();
            assertThat(found.createdAt()).isEqualTo(NOW);
        });
        assertThat(jdbc.sql("select doc_type::text from contract_documents").query(String.class).single())
                .isEqualTo("MEDICAL_CERTIFICATE");
    }

    @Test
    void unknownAttachment_isNotFound() {
        assertThat(documents.findByAttachmentAttachmentId(ATTACHMENT)).isEmpty();
    }

    @Test
    void secondRecordOfTheSameAttachment_isRefusedByTheDatabase() {
        documents.saveAndFlush(PrintedForm.receivedCertificate(OWNER, FILE, NOW));
        ParticipantAttachment again = new ParticipantAttachment(REQUEST, PARTICIPANT, ATTACHMENT);

        assertThatThrownBy(() -> documents.saveAndFlush(PrintedForm.receivedCertificate(again, FILE, NOW)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void certificateOfAnUnknownRequest_isRefusedByTheDatabase() {
        ParticipantAttachment stranger = new ParticipantAttachment(UUID.randomUUID(), PARTICIPANT, UUID.randomUUID());

        assertThatThrownBy(() -> documents.saveAndFlush(PrintedForm.receivedCertificate(stranger, FILE, NOW)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void certificateWithoutFileOrParticipant_isRefusedByTheDatabase() {
        assertThatThrownBy(() -> insert("individual_request_id, participant_id, attachment_id",
                ":request, :participant, :attachment"))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_contract_documents_attachment_file");
        assertThatThrownBy(() -> insert("individual_request_id, attachment_id, file_key, file_hash, file_size, content_type",
                ":request, :attachment, 'k', 'h', 1, 'image/png'"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_contract_documents_participant_attachment");
    }

    @Test
    void documentWithoutAnyOwner_isRefusedByTheDatabase() {
        assertThatThrownBy(() -> insert("file_key", "'k'"))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_contract_documents_single_owner");
    }

    private void insert(String columns, String values) {
        jdbc.sql("insert into contract_documents (id, kind, doc_type, version, created_at, " + columns + ") values "
                        + "(gen_random_uuid(), 'FINAL', 'MEDICAL_CERTIFICATE', 1, now(), " + values + ")")
                .param("request", REQUEST).param("participant", PARTICIPANT).param("attachment", ATTACHMENT)
                .update();
    }
}
