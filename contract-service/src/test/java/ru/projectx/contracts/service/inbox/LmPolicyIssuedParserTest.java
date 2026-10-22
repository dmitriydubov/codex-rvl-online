package ru.projectx.contracts.service.inbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import ru.projectx.contracts.exception.ContractViolationException;
import ru.projectx.contracts.message.IssuedDocument;
import ru.projectx.contracts.message.LmPolicyResult;
import ru.projectx.contracts.support.LmPolicyResultBodies;
import tools.jackson.databind.json.JsonMapper;

class LmPolicyIssuedParserTest {

    private final LmPolicyIssuedParser parser = new LmPolicyIssuedParser(new MessageBodies(JsonMapper.builder().build()));

    @Test
    void issuedResult_isParsedWithUskIdentifiersPolicyAndDocuments() {
        LmPolicyResult result = parser.parse(LmPolicyResultBodies.issued().toJson());

        assertThat(result).isEqualTo(new LmPolicyResult.Issued(UUID.fromString(LmPolicyResultBodies.POLICY_ID),
                UUID.fromString(LmPolicyResultBodies.POLICY_USK_ID), "LM-000012204",
                UUID.fromString(LmPolicyResultBodies.POLICY_EXTERNAL_ID), "0555123456",
                List.of(new IssuedDocument("POLICY", LmPolicyResultBodies.FILE_URL, 96256L))));
    }

    @Test
    void failedResult_isParsedWithCodeAndMessageAsReceived() {
        LmPolicyResult result = parser.parse(LmPolicyResultBodies.failed().toJson());

        assertThat(result).isEqualTo(new LmPolicyResult.Failed(UUID.fromString(LmPolicyResultBodies.POLICY_ID),
                "INSURED_NOT_COVERED", "Стенд: застрахованный не входит в корпоративный договор"));
    }

    @Test
    void absentOrEmptyDocuments_giveNoDocuments() {
        assertThat(parser.parse(LmPolicyResultBodies.issued().withoutDataField("documents").toJson()))
                .extracting(result -> ((LmPolicyResult.Issued) result).documents()).isEqualTo(List.of());
        assertThat(parser.parse(LmPolicyResultBodies.issued().withEmptyDocuments().toJson()))
                .extracting(result -> ((LmPolicyResult.Issued) result).documents()).isEqualTo(List.of());
    }

    @Test
    void uskNumberAndFileSize_mayBeAbsent() {
        LmPolicyResult.Issued result = (LmPolicyResult.Issued) parser.parse(LmPolicyResultBodies.issued()
                .withoutDataField("policy_usk_number").withoutDocumentField(0, "file_size").toJson());

        assertThat(result.policyUskNumber()).isNull();
        assertThat(result.documents()).containsExactly(new IssuedDocument("POLICY", LmPolicyResultBodies.FILE_URL, null));
    }

    @Test
    void missingPolicyWhenIssued_isViolationNamingTheField() {
        assertThatThrownBy(() -> parser.parse(LmPolicyResultBodies.issued().withoutDataField("policy").toJson()))
                .isInstanceOf(ContractViolationException.class)
                .hasMessage("нет обязательного поля data.policy");
    }

    @Test
    void missingUskIdWhenIssued_isViolationNamingTheField() {
        assertThatThrownBy(() -> parser.parse(LmPolicyResultBodies.issued().withoutDataField("policy_usk_id").toJson()))
                .isInstanceOf(ContractViolationException.class)
                .hasMessage("нет обязательного поля data.policy_usk_id");
    }

    @Test
    void missingErrorCodeWhenFailed_isViolationNamingTheField() {
        assertThatThrownBy(() -> parser.parse(LmPolicyResultBodies.failed().withoutDataField("error_code").toJson()))
                .isInstanceOf(ContractViolationException.class)
                .hasMessage("нет обязательного поля data.error_code");
    }

    @Test
    void unknownStatus_isViolationNamingTheField() {
        assertThatThrownBy(() -> parser.parse(LmPolicyResultBodies.issued().withDataField("status", "FAILED").toJson()))
                .isInstanceOf(ContractViolationException.class)
                .hasMessage("недопустимое значение поля data.status");
    }

    @Test
    void unknownDocumentType_isViolationNamingTheDocument() {
        assertThatThrownBy(() -> parser.parse(LmPolicyResultBodies.issued()
                .withDocumentField(0, "doc_type", "RECEIPT").toJson()))
                .isInstanceOf(ContractViolationException.class)
                .hasMessage("недопустимое значение поля data.documents[0].doc_type");
    }

    @Test
    void medicalCertificateType_isNotADocumentTheUskIssues() {
        assertThatThrownBy(() -> parser.parse(LmPolicyResultBodies.issued()
                .withDocumentField(0, "doc_type", "MEDICAL_CERTIFICATE").toJson()))
                .isInstanceOf(ContractViolationException.class)
                .hasMessage("недопустимое значение поля data.documents[0].doc_type");
    }

    @Test
    void documentWithoutUrl_isViolationNamingTheField() {
        assertThatThrownBy(() -> parser.parse(LmPolicyResultBodies.issued()
                .withDocumentField(0, "file_url", null).toJson()))
                .isInstanceOf(ContractViolationException.class)
                .hasMessage("нет обязательного поля data.documents[0].file_url");
    }

    @Test
    void unreadableFileSize_isViolationNamingTheField() {
        assertThatThrownBy(() -> parser.parse(LmPolicyResultBodies.issued()
                .withDocumentField(0, "file_size", "много").toJson()))
                .isInstanceOf(ContractViolationException.class)
                .hasMessage("недопустимое значение поля data.documents[0].file_size");
    }

    @Test
    void documentsThatAreNotAnArray_isViolationNamingTheField() {
        assertThatThrownBy(() -> parser.parse(LmPolicyResultBodies.issued().withDataField("documents", "нет").toJson()))
                .isInstanceOf(ContractViolationException.class)
                .hasMessage("недопустимое значение поля data.documents");
    }
}
