package ru.projectx.contracts.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Код «не найдено» из ответа справочника однозначно называет справочник: по нему задача выпуска сообщает сервису
 * Заявок, чего именно нет. Чужой код справочником не считается.
 */
class ReferenceKindTest {

    @ParameterizedTest
    @CsvSource({
        "PROGRAM_NOT_FOUND, PROGRAM",
        "INSURANCE_CONTRACT_NOT_FOUND, INSURANCE_CONTRACT",
        "POLICY_NOT_FOUND, POLICY"})
    void notFoundCode_namesItsReference(String code, ReferenceKind expected) {
        assertThat(ReferenceKind.byNotFoundCode(code)).contains(expected);
        assertThat(expected.notFoundCode()).isEqualTo(code);
    }

    @ParameterizedTest
    @ValueSource(strings = {"LPU_NOT_FOUND", "VALIDATION_ERROR", "", "policy_not_found"})
    void foreignCode_isNoReference(String code) {
        assertThat(ReferenceKind.byNotFoundCode(code)).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"PROGRAM, PROGRAM_NOT_FOUND", "POLICY, POLICY_NOT_FOUND"})
    void notFoundException_carriesTheCodeOfItsReference(ReferenceKind kind, String code) {
        ReferenceNotFoundException notFound = new ReferenceNotFoundException(kind);

        assertThat(notFound.kind()).isEqualTo(kind);
        assertThat(notFound.code()).isEqualTo(code);
        assertThat(notFound).hasMessageContaining(code);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PROGRAM", "INSURANCE_CONTRACT", "POLICY"})
    void archivedException_namesReferenceAndRecord(ReferenceKind kind) {
        UUID id = UUID.fromString("3b4a9b56-1f0f-4ea3-9f3d-1b4d6b0a8e01");

        ReferenceArchivedException archived = new ReferenceArchivedException(kind, id);

        assertThat(archived.kind()).isEqualTo(kind);
        assertThat(archived.id()).isEqualTo(id);
        assertThat(archived).hasMessageContaining(kind.name()).hasMessageContaining(id.toString());
    }

    @Test
    void rejectedException_carriesStatusAndCodeOfTheReference() {
        ReferenceRejectedException rejected = new ReferenceRejectedException(400, "VALIDATION_ERROR");

        assertThat(rejected.status()).isEqualTo(400);
        assertThat(rejected.code()).isEqualTo("VALIDATION_ERROR");
        assertThat(rejected).hasMessageContaining("400").hasMessageContaining("VALIDATION_ERROR");
    }

    @ParameterizedTest
    @ValueSource(strings = {"SERVICE_UNAVAILABLE", "TIMEOUT", "CONNECTION_FAILED", "UNEXPECTED_RESPONSE",
        "POLICY_NOT_RECEIVED"})
    void unavailableException_namesItsReason(ReferenceUnavailableException.Reason reason) {
        IllegalStateException cause = new IllegalStateException("причина");

        ReferenceUnavailableException plain = new ReferenceUnavailableException(reason);
        ReferenceUnavailableException withCause = new ReferenceUnavailableException(reason, cause);

        assertThat(plain.reason()).isEqualTo(reason);
        assertThat(plain).hasMessageContaining(reason.name()).hasNoCause();
        assertThat(withCause.reason()).isEqualTo(reason);
        assertThat(withCause).hasMessageContaining(reason.name()).hasCause(cause);
    }
}
