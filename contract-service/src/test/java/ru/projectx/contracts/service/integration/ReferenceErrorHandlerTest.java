package ru.projectx.contracts.service.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.client.MockClientHttpResponse;
import ru.projectx.contracts.exception.ReferenceKind;
import ru.projectx.contracts.exception.ReferenceNotFoundException;
import ru.projectx.contracts.exception.ReferenceUnavailableException;
import ru.projectx.contracts.exception.ReferenceUnavailableException.Reason;
import tools.jackson.databind.json.JsonMapper;

/**
 * Ответ справочника со статусом ошибки читается по его единому формату: 404 с кодом справочника — «не найдено»,
 * 503 с кодом недоступности — временная недоступность, всё остальное, включая тело не в формате, — неожиданный ответ,
 * которому не верят.
 */
class ReferenceErrorHandlerTest {

    private final ReferenceErrorBodies bodies = new ReferenceErrorBodies(JsonMapper.shared());
    private final ReferenceErrorHandler handler = new ReferenceErrorHandler(bodies);

    @ParameterizedTest
    @CsvSource({
        "PROGRAM_NOT_FOUND, PROGRAM",
        "INSURANCE_CONTRACT_NOT_FOUND, INSURANCE_CONTRACT",
        "POLICY_NOT_FOUND, POLICY"})
    void notFoundWithReferenceCode_namesTheReference(String code, ReferenceKind kind) {
        assertThatThrownBy(() -> handler.handle(null, response(HttpStatus.NOT_FOUND, error(code))))
                .isInstanceOfSatisfying(ReferenceNotFoundException.class,
                        notFound -> assertThat(notFound.kind()).isEqualTo(kind));
    }

    @Test
    void notFoundWithForeignCode_isUnexpectedResponse() {
        assertThatThrownBy(() -> handler.handle(null, response(HttpStatus.NOT_FOUND, error("LPU_NOT_FOUND"))))
                .isInstanceOfSatisfying(ReferenceUnavailableException.class,
                        unavailable -> assertThat(unavailable.reason()).isEqualTo(Reason.UNEXPECTED_RESPONSE));
    }

    @Test
    void notFoundWithoutJsonBody_isUnexpectedResponse() {
        assertThatThrownBy(() -> handler.handle(null, response(HttpStatus.NOT_FOUND, "<html>404</html>")))
                .isInstanceOfSatisfying(ReferenceUnavailableException.class,
                        unavailable -> assertThat(unavailable.reason()).isEqualTo(Reason.UNEXPECTED_RESPONSE));
    }

    @Test
    void serviceUnavailableWithCode_isServiceUnavailable() {
        assertThatThrownBy(
                () -> handler.handle(null, response(HttpStatus.SERVICE_UNAVAILABLE, error("SERVICE_UNAVAILABLE"))))
                .isInstanceOfSatisfying(ReferenceUnavailableException.class,
                        unavailable -> assertThat(unavailable.reason()).isEqualTo(Reason.SERVICE_UNAVAILABLE));
    }

    @ParameterizedTest
    @ValueSource(strings = {"INTERNAL_ERROR", ""})
    void serviceUnavailableWithOtherCode_isUnexpectedResponse(String code) {
        assertThatThrownBy(() -> handler.handle(null, response(HttpStatus.SERVICE_UNAVAILABLE, error(code))))
                .isInstanceOfSatisfying(ReferenceUnavailableException.class,
                        unavailable -> assertThat(unavailable.reason()).isEqualTo(Reason.UNEXPECTED_RESPONSE));
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 409, 500, 502, 504})
    void otherErrorStatus_isUnexpectedResponseWhateverTheCode(int status) {
        assertThatThrownBy(
                () -> handler.handle(null, response(HttpStatus.valueOf(status), error("SERVICE_UNAVAILABLE"))))
                .isInstanceOfSatisfying(ReferenceUnavailableException.class,
                        unavailable -> assertThat(unavailable.reason()).isEqualTo(Reason.UNEXPECTED_RESPONSE));
    }

    @Test
    void errorCode_isReadFromTheContourFormat() {
        assertThat(bodies.code(stream(error("POLICY_NOT_FOUND")))).contains("POLICY_NOT_FOUND");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "<html>503</html>",
        "",
        "{\"code\":\"POLICY_NOT_FOUND\"}",
        "{\"error\":{\"code\":404}}",
        "{\"error\":{\"message\":\"без кода\"}}",
        "{\"error\":\"POLICY_NOT_FOUND\"}",
        "{\"error\":{\"code\":\"POLICY_NOT_FOUND\""})
    void bodyOutsideTheFormat_hasNoCode(String body) {
        assertThat(bodies.code(stream(body))).isEmpty();
    }

    @Test
    void unreadableBody_hasNoCode() {
        InputStream failing = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("обрыв");
            }
        };

        assertThat(bodies.code(failing)).isEmpty();
    }

    @Test
    void failure_isComputedFromStatusAndCodeAlone() {
        assertThat(ReferenceErrorHandler.failure(HttpStatus.NOT_FOUND, Optional.of("POLICY_NOT_FOUND")))
                .isInstanceOf(ReferenceNotFoundException.class);
        assertThat(ReferenceErrorHandler.failure(HttpStatus.NOT_FOUND, Optional.empty()))
                .isInstanceOf(ReferenceUnavailableException.class);
    }

    private static String error(String code) {
        return "{\"error\":{\"code\":\"" + code + "\",\"message\":\"текст для журнала\",\"details\":[],"
                + "\"request_id\":\"stand-11\"}}";
    }

    private static MockClientHttpResponse response(HttpStatus status, String body) {
        return new MockClientHttpResponse(body.getBytes(StandardCharsets.UTF_8), status);
    }

    private static InputStream stream(String body) {
        return new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));
    }
}
