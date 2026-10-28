package ru.projectx.contracts.service.km;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;
import ru.projectx.contracts.config.AttachmentProperties;
import ru.projectx.contracts.config.StorageProperties;
import ru.projectx.contracts.entity.Contract;
import ru.projectx.contracts.entity.PrintedFormType;
import ru.projectx.contracts.entity.party.PersonSnapshot;
import ru.projectx.contracts.entity.StoredFile;
import ru.projectx.contracts.message.MessageMeta;
import ru.projectx.contracts.service.PersonSnapshots;
import ru.projectx.contracts.service.reference.ExternalIdentifiers;
import ru.projectx.contracts.service.storage.CertificateLinks;
import ru.projectx.contracts.support.Issuances;
import ru.projectx.contracts.support.Tasks;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

class KmContractSubmissionMessagesTest {

    static final Instant NOW = Instant.parse("2026-02-10T07:32:00Z");
    static final Instant PAID_AT = Instant.parse("2026-02-10T07:20:15Z");
    static final Instant ACCEPTED_AT = Instant.parse("2026-02-10T07:04:58Z");
    static final String CONTRACT_ID = "0f4b8e2a-6c13-4d7e-b9a1-2e5c7d3f8a64";
    static final UUID FIRST_DOCUMENT = UUID.fromString("3c2f8e10-5a7b-4d21-9f6e-1b0a7c4d8e21");
    static final UUID SECOND_DOCUMENT = UUID.fromString("1a4c7e2f-9b30-4d85-a6f1-c2e8b05d7f39");
    static final ExternalIdentifiers IDENTIFIERS = new ExternalIdentifiers(
            UUID.fromString("6b02f1cc-9d47-4e35-8a20-7f1c3e5b09a2"), UUID.fromString("8c41e2ab-55d0-4a17-9b63-2f0e7c1d4a88"),
            UUID.fromString("1d9a6e77-4c02-4b88-a15e-30f7b2c9de41"));
    static final PersonSnapshot POLICYHOLDER = PersonSnapshots.of(Tasks.insured("0.00"));
    static final PersonSnapshot SPOUSE = PersonSnapshots.of(Tasks.spouse("34000.00"));
    static final SubmittedCertificate JPEG = new SubmittedCertificate(FIRST_DOCUMENT, PrintedFormType.MEDICAL_CERTIFICATE, new StoredFile(
            "certificates/0b6c/3c2f.jpg", "7851d78af0fb98bbbda8933f49860a009ac7761c21a1136613c8a6704b156251", 76L,
            "image/jpeg"));
    static final SubmittedCertificate PDF = new SubmittedCertificate(SECOND_DOCUMENT, PrintedFormType.MEDICAL_CERTIFICATE, new StoredFile(
            "certificates/0b6c/1a4c.pdf", "5f06c6aad0549ff7d77933b65f7f1069aba5dde34da22c71517b0f8ff2b45732", 20000L,
            "application/pdf"));

    private final JsonMapper json = JsonMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build();
    private final KmContractSubmissionMessages messages = new KmContractSubmissionMessages(json, properties(),
            new CertificateLinks(storage()));
    private final Contract contract = Issuances.paidContract(CONTRACT_ID, Tasks.SPOUSE, "DKM-2026-000001", PAID_AT);

    @Test
    void meta_namesTheSubmissionEventWithTheRequestAsCorrelationAndTheContractVersionInUtc() {
        contract.submit(NOW);

        MessageMeta meta = messages.meta(contract, NOW);

        assertThat(meta.messageId()).isNotNull();
        assertThat(meta.correlationId()).isEqualTo(Tasks.REQUEST);
        assertThat(meta.topic()).isEqualTo("rvl.km-contract.submitted");
        assertThat(meta.eventType()).isEqualTo("KmContractSubmitted");
        assertThat(meta.schemaVersion()).isEqualTo("1.0");
        assertThat(meta.createdAt().toString()).isEqualTo("2026-02-10T07:32Z");
        assertThat(meta.entityVersion()).isEqualTo(3);
        assertThat(meta.source()).isEqualTo("RVL-CONTRACTS");
    }

    @Test
    void body_carriesTheContractItsIdentifiersMomentsBothPartiesAndTheCertificatesWithoutContent() {
        contract.submit(NOW);
        MessageMeta meta = messages.meta(contract, NOW);

        JsonNode body = json.readTree(messages.write(meta, contract, facts(List.of(JPEG, PDF))));

        JsonNode metaNode = body.path("meta");
        assertThat(metaNode.path("message_id").stringValue()).isEqualTo(meta.messageId().toString());
        assertThat(metaNode.path("created_at").stringValue()).isEqualTo("2026-02-10T07:32:00Z");
        assertThat(metaNode.path("entity_version").intValue()).isEqualTo(3);
        JsonNode data = body.path("data");
        assertThat(data.path("id").stringValue()).isEqualTo(CONTRACT_ID);
        assertThat(data.path("number").stringValue()).isEqualTo("DKM-2026-000001");
        assertThat(data.path("individual_request_id").stringValue()).isEqualTo(Tasks.REQUEST.toString());
        assertThat(data.path("insurance_contract_external_id").stringValue())
                .isEqualTo("8c41e2ab-55d0-4a17-9b63-2f0e7c1d4a88");
        assertThat(data.path("insurer_policy_external_id").stringValue()).isEqualTo("1d9a6e77-4c02-4b88-a15e-30f7b2c9de41");
        assertThat(data.path("program_external_id").stringValue()).isEqualTo("6b02f1cc-9d47-4e35-8a20-7f1c3e5b09a2");
        assertThat(data.path("date_from").stringValue()).isEqualTo("2026-04-10");
        assertThat(data.path("date_to").stringValue()).isEqualTo("2026-04-24");
        assertThat(data.has("insured_amount")).isFalse();
        assertThat(data.path("premium_amount").decimalValue()).isEqualByComparingTo("34000.00");
        assertThat(data.path("paid_at").stringValue()).isEqualTo("2026-02-10T07:20:15Z");
        assertThat(data.path("accepted_at").stringValue()).isEqualTo("2026-02-10T07:04:58Z");
        JsonNode documents = data.path("documents");
        assertThat(documents.size()).isEqualTo(2);
        assertThat(documents.path(0).path("document_id").stringValue()).isEqualTo(FIRST_DOCUMENT.toString());
        assertThat(documents.path(0).path("doc_type").stringValue()).isEqualTo("MEDICAL_CERTIFICATE");
        assertThat(documents.path(0).path("file_name").stringValue()).isEqualTo("certificate-" + FIRST_DOCUMENT + ".jpg");
        assertThat(documents.path(0).path("content_type").stringValue()).isEqualTo("image/jpeg");
        assertThat(documents.path(0).path("file_hash").stringValue()).isEqualTo(JPEG.file().hash());
        assertThat(documents.path(0).path("link").stringValue()).isEqualTo("/rvl-files/certificates/0b6c/3c2f.jpg");
        assertThat(documents.path(0).has("content_base64")).isFalse();
        assertThat(documents.path(1).path("file_name").stringValue()).isEqualTo("certificate-" + SECOND_DOCUMENT + ".pdf");
        assertThat(documents.path(1).path("content_type").stringValue()).isEqualTo("application/pdf");
        assertThat(documents.path(1).path("file_hash").stringValue()).isEqualTo(PDF.file().hash());
        assertThat(documents.path(1).path("link").stringValue()).isEqualTo("/rvl-files/certificates/0b6c/1a4c.pdf");
    }

    @Test
    void parties_areThePolicyholderWithoutRelationAndTheRelativeInsuredWithRelationType() {
        JsonNode data = json.readTree(messages.write(messages.meta(contract, NOW), contract, facts(List.of())))
                .path("data");

        JsonNode policyholder = data.path("policyholder");
        assertThat(policyholder.path("participant_id").stringValue()).isEqualTo(Tasks.INSURED.toString());
        assertThat(policyholder.has("is_relative")).isFalse();
        assertThat(policyholder.has("relation_type")).isFalse();
        assertThat(policyholder.path("last_name").stringValue()).isEqualTo("Тестов");
        assertThat(policyholder.path("first_name").stringValue()).isEqualTo("Тест");
        assertThat(policyholder.path("middle_name").stringValue()).isEqualTo("Тестович");
        assertThat(policyholder.path("birth_date").stringValue()).isEqualTo("1984-03-11");
        assertThat(policyholder.path("gender").stringValue()).isEqualTo("MALE");
        assertThat(policyholder.path("document").path("type").stringValue()).isEqualTo("RF_PASSPORT");
        assertThat(policyholder.path("document").path("series").stringValue()).isEqualTo("4510");
        assertThat(policyholder.path("document").path("number").stringValue()).isEqualTo("123456");
        assertThat(policyholder.path("document").path("issued_by").stringValue()).isEqualTo("Тестовый орган");
        assertThat(policyholder.path("document").path("issued_at").stringValue()).isEqualTo("2010-04-20");
        assertThat(policyholder.path("document").path("division_code").stringValue()).isEqualTo("770-001");
        assertThat(policyholder.path("address").stringValue()).isEqualTo("Тестовый адрес");
        JsonNode insured = data.path("insured");
        assertThat(insured.path("participant_id").stringValue()).isEqualTo(Tasks.SPOUSE.toString());
        assertThat(insured.path("is_relative").booleanValue()).isTrue();
        assertThat(insured.path("relation_type").stringValue()).isEqualTo("SPOUSE");
        assertThat(insured.path("last_name").stringValue()).isEqualTo("Тестова");
        assertThat(insured.path("document").path("number").stringValue()).isEqualTo("123456");
        assertThat(data.path("documents").isArray()).isTrue();
        assertThat(data.path("documents").isEmpty()).isTrue();
    }

    @Test
    void insuredWhoIsThePolicyholder_isSentAsNotARelativeWithoutRelationType() {
        Contract own = Issuances.paidContract(CONTRACT_ID, Tasks.INSURED, "DKM-2026-000002", PAID_AT);
        SubmissionFacts facts = new SubmissionFacts(IDENTIFIERS, ACCEPTED_AT, POLICYHOLDER,
                PersonSnapshots.of(Tasks.relative(0, Tasks.INSURED, "34000.00", "SPOUSE")), List.of());

        JsonNode insured = json.readTree(messages.write(messages.meta(own, NOW), own, facts)).path("data")
                .path("insured");

        assertThat(insured.path("participant_id").stringValue()).isEqualTo(Tasks.INSURED.toString());
        assertThat(insured.path("is_relative").booleanValue()).isFalse();
        assertThat(insured.has("relation_type")).isFalse();
    }

    @Test
    void certificateOfAContentTypeOutsideTheAllowedOnes_isIllegalState() {
        SubmittedCertificate gif = new SubmittedCertificate(FIRST_DOCUMENT, PrintedFormType.MEDICAL_CERTIFICATE,
                new StoredFile("certificates/0b6c/3c2f.gif",
                "0".repeat(64), 10L, "image/gif"));

        assertThatThrownBy(() -> messages.write(messages.meta(contract, NOW), contract, facts(List.of(gif))))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining(FIRST_DOCUMENT.toString())
                .hasMessageNotContaining("gif");
    }

    @Test
    void contractWithoutAMomentOfPayment_isIllegalState() {
        Contract unpaid = Issuances.contract(CONTRACT_ID, Tasks.SPOUSE, "DKM-2026-000001");

        assertThatThrownBy(() -> messages.write(messages.meta(unpaid, NOW), unpaid, facts(List.of())))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining(CONTRACT_ID);
    }

    static SubmissionFacts facts(List<SubmittedCertificate> certificates) {
        return new SubmissionFacts(IDENTIFIERS, ACCEPTED_AT, POLICYHOLDER, SPOUSE, certificates);
    }

    private static StorageProperties storage() {
        Duration timeout = Duration.ofSeconds(1);
        return new StorageProperties("http://storage.test:9000", "us-east-1", "rvl-files", "certificates", "1111111111",
                "222222222", "access", "secret", null, new StorageProperties.Timeouts(timeout, timeout, timeout, timeout));
    }

    static AttachmentProperties properties() {
        return new AttachmentProperties(DataSize.ofMegabytes(5),
                Map.of("image/jpeg", "jpg", "image/png", "png", "application/pdf", "pdf"));
    }
}
