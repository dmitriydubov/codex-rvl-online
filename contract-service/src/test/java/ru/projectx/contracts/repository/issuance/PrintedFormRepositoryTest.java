package ru.projectx.contracts.repository.issuance;

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
import ru.projectx.contracts.entity.issuance.DocumentKind;
import ru.projectx.contracts.entity.issuance.IssuanceRequest;
import ru.projectx.contracts.entity.document.AttachmentOwner;
import ru.projectx.contracts.entity.document.AttachmentOwners;
import ru.projectx.contracts.entity.document.PrintedForm;
import ru.projectx.contracts.entity.document.PrintedFormType;
import ru.projectx.contracts.entity.issuance.RequestParticipant;
import ru.projectx.contracts.entity.document.StoredFile;
import ru.projectx.contracts.support.IntegrationTest;

@IntegrationTest
class PrintedFormRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-02-10T07:05:00Z");
    private static final UUID REQUEST = UUID.fromString("61a7982c-a0b4-4bbf-9527-60b40b752803");
    private static final UUID PARTICIPANT = UUID.fromString("6778e8c2-985a-4d54-9bd3-0f6403b104d6");
    private static final UUID ATTACHMENT = UUID.fromString("3c2f8e10-5a7b-4d21-9f6e-1b0a7c4d8e21");
    private static final AttachmentOwner OWNER = AttachmentOwners.certificate(REQUEST, PARTICIPANT, ATTACHMENT);
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
        documents.saveAndFlush(PrintedForm.receivedAttachment(OWNER, FILE, NOW));

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
        documents.saveAndFlush(PrintedForm.receivedAttachment(OWNER, FILE, NOW));
        AttachmentOwner again = AttachmentOwners.certificate(REQUEST, PARTICIPANT, ATTACHMENT);

        assertThatThrownBy(() -> documents.saveAndFlush(PrintedForm.receivedAttachment(again, FILE, NOW)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void consentAndCertificateCannotShareTheSameAttachmentId() {
        AttachmentOwner consent = AttachmentOwners.consent(REQUEST, PARTICIPANT, ATTACHMENT);
        documents.saveAndFlush(PrintedForm.receivedAttachment(consent, FILE, NOW));

        assertThatThrownBy(() -> documents.saveAndFlush(PrintedForm.receivedAttachment(OWNER, FILE, NOW)))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("uq_contract_documents_attachment_id");
        assertThat(documents.findByAttachmentAttachmentId(ATTACHMENT)).get()
                .satisfies(found -> assertThat(found.attachment()).contains(consent));
        assertThat(documents.findActiveParticipantDocuments(REQUEST, PARTICIPANT, PrintedFormType.MEDICAL_CERTIFICATE))
                .isEmpty();
    }

    @Test
    void certificateOfAnUnknownRequest_canBeStoredBeforeIssuance() {
        AttachmentOwner stranger = AttachmentOwners.certificate(UUID.randomUUID(), PARTICIPANT, UUID.randomUUID());

        documents.saveAndFlush(PrintedForm.receivedAttachment(stranger, FILE, NOW));

        assertThat(documents.findByAttachmentAttachmentId(stranger.attachmentId())).get()
                .satisfies(found -> assertThat(found.attachment()).contains(stranger));
    }

    @Test
    void activeCertificates_omitSoftDeletedRowsAndOtherParticipants() {
        PrintedForm active = documents.saveAndFlush(PrintedForm.receivedAttachment(OWNER, FILE, NOW));
        PrintedForm deleted = documents.saveAndFlush(PrintedForm.receivedAttachment(
                AttachmentOwners.certificate(REQUEST, PARTICIPANT, UUID.randomUUID()), FILE, NOW));
        documents.saveAndFlush(PrintedForm.receivedAttachment(
                AttachmentOwners.certificate(REQUEST, UUID.randomUUID(), UUID.randomUUID()), FILE, NOW));
        jdbc.sql("update contract_documents set deleted_at = now(), delete_request_id = 'stand-delete' where id = :id")
                .param("id", deleted.id()).update();

        assertThat(documents.findActiveParticipantDocuments(REQUEST, PARTICIPANT, PrintedFormType.MEDICAL_CERTIFICATE))
                .extracting(PrintedForm::id).containsExactly(active.id());
        assertThat(documents.findById(deleted.id())).get().satisfies(found -> assertThat(found.deleted()).isTrue());
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
    void medicalCertificateTypeOutsideAParticipantAttachment_isRefusedByTheDatabase() {
        assertThatThrownBy(() -> jdbc.sql("insert into contract_documents (id, kind, doc_type, version, created_at, "
                        + "individual_request_id, usk_file_url) values (gen_random_uuid(), 'FINAL', 'MEDICAL_CERTIFICATE', 1, "
                        + "now(), :request, 'https://s3.example.test/x.pdf')").param("request", REQUEST).update())
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.sql("insert into contract_documents (id, kind, doc_type, version, created_at, "
                        + "individual_request_id, participant_id, attachment_id, file_key, file_hash, file_size, content_type) "
                        + "values (gen_random_uuid(), 'FINAL', 'POLICY', 1, now(), :request, :participant, :attachment, "
                        + "'k', 'h', 1, 'image/png')")
                .param("request", REQUEST).param("participant", PARTICIPANT).param("attachment", ATTACHMENT).update())
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_contract_documents_certificate_type");
    }

    @Test
    void documentWithoutAnyOwner_isRefusedByTheDatabase() {
        assertThatThrownBy(() -> jdbc.sql("insert into contract_documents (id, kind, doc_type, version, created_at, file_key) "
                        + "values (gen_random_uuid(), 'FINAL', 'POLICY', 1, now(), 'k')").update())
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_contract_documents_single_owner");
    }

    private void insert(String columns, String values) {
        jdbc.sql("insert into contract_documents (id, kind, doc_type, version, created_at, " + columns + ") values "
                        + "(gen_random_uuid(), 'FINAL', 'MEDICAL_CERTIFICATE', 1, now(), " + values + ")")
                .param("request", REQUEST).param("participant", PARTICIPANT).param("attachment", ATTACHMENT)
                .update();
    }
}
