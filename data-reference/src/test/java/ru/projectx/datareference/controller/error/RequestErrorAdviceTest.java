package ru.projectx.datareference.controller.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.util.ReflectionUtils;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.UnsatisfiedServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import ru.projectx.datareference.controller.FailingController;
import ru.projectx.datareference.controller.StandaloneMvc;
import ru.projectx.datareference.controller.request.RequestIdFilter;
import ru.projectx.datareference.controller.request.UuidParameters;
import ru.projectx.datareference.exception.InvalidRequestException;

@ExtendWith(OutputCaptureExtension.class)
class RequestErrorAdviceTest {

    private static final String REQUEST_ID = "handler-test-2";

    private MockMvcTester mvc;

    @BeforeEach
    void setUp() {
        mvc = StandaloneMvc.withRequestId(new FailingController(), StandaloneMvc.errorAdvices());
    }

    @Test
    void invalidRequest_isValidationError_namingFieldAndReason() {
        FailingController controller = new FailingController();
        controller.failWith(new InvalidRequestException("limit", "не больше 50"));
        mvc = StandaloneMvc.withRequestId(controller, new RequestErrorAdvice());

        MvcTestResult result = get(FailingController.FAILING);

        assertValidationError(result, "limit", "не больше 50");
    }

    @Test
    void missingParameter_isValidationError_namingParameter() {
        MvcTestResult result = get(FailingController.WITH_PARAMETER);

        assertValidationError(result, "key", "обязательный параметр");
    }

    @ParameterizedTest
    @ValueSource(strings = {" ", "  ", "\t"})
    void blankPathVariable_isValidationError_namingVariableAndExpectedType(String blank, CapturedOutput output) {
        mvc = StandaloneMvc.withRequestId(new FailingController(), new RequestErrorAdvice(),
                StandaloneMvc.serverFailureAdvice(), new UuidParameters());

        MvcTestResult result = mvc.get().uri(FailingController.WITH_ID, blank)
                .header(RequestIdFilter.HEADER, REQUEST_ID).exchange();

        assertValidationError(result, "id", "ожидается UUID");
        assertThat(output).doesNotContain("ERROR");
    }

    @Test
    void mismatchWithoutRequiredType_isValidationError_expectingAnotherType() {
        Method withId = ReflectionUtils.findMethod(FailingController.class, "withId", UUID.class);
        FailingController controller = new FailingController();
        MethodParameter id = new MethodParameter(withId, 0);
        controller.failWith(new MethodArgumentTypeMismatchException(" ", null, "id", id, null));
        mvc = StandaloneMvc.withRequestId(controller, new RequestErrorAdvice());

        MvcTestResult result = get(FailingController.FAILING);

        assertValidationError(result, "id", "ожидается другой тип");
    }

    @Test
    void pathVariableMissingFromTemplate_isInternalError_writtenToJournal(CapturedOutput output) {
        mvc = StandaloneMvc.withRequestId(new FailingController(), StandaloneMvc.errorAdvices());

        MvcTestResult result = get(FailingController.WITHOUT_ID_IN_TEMPLATE);

        assertThat(result).hasStatus(500);
        assertThat(result).bodyJson().extractingPath("$.error.code").isEqualTo("INTERNAL_ERROR");
        assertThat(result).bodyJson().extractingPath("$.error.requestId").isEqualTo(REQUEST_ID);
        assertThat(output).contains("ERROR").contains("MissingPathVariableException");
    }

    @Test
    void unknownPath_isNotFound_namingPath() {
        MvcTestResult result = get(FailingController.NO_RESOURCE);

        assertThat(result).hasStatus(404);
        assertThat(result).bodyJson().extractingPath("$.error.code").isEqualTo("NOT_FOUND");
        assertThat(result).bodyJson().extractingPath("$.error.message").isEqualTo("ресурс не найден: api/unknown");
        assertThat(result).bodyJson().extractingPath("$.error.details").asArray().isEmpty();
        assertThat(result).bodyJson().extractingPath("$.error.requestId").isEqualTo(REQUEST_ID);
    }

    @ParameterizedTest
    @ValueSource(strings = {"text/xml", "application/xml", "text/plain"})
    void unknownPath_acceptingNoJson_isStillNotFound_withJsonBody(String accept, CapturedOutput output) {
        MvcTestResult result = mvc.get().uri(FailingController.NO_RESOURCE)
                .header(RequestIdFilter.HEADER, REQUEST_ID).header(HttpHeaders.ACCEPT, accept).exchange();

        assertThat(result).hasStatus(404);
        assertThat(result).hasContentType(MediaType.APPLICATION_JSON);
        assertProtocolError(result, "NOT_FOUND");
        assertThat(output).doesNotContain("Failure in @ExceptionHandler");
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT", "PATCH", "DELETE"})
    void otherMethod_isMethodNotAllowed_withAllowHeader_inUnifiedFormat(String method, CapturedOutput output) {
        MvcTestResult result = mvc.method(HttpMethod.valueOf(method)).uri(FailingController.READ_ONLY)
                .header(RequestIdFilter.HEADER, REQUEST_ID).exchange();

        assertThat(result).hasStatus(405);
        assertThat(result.getResponse().getHeader(HttpHeaders.ALLOW)).isEqualTo("GET,HEAD,OPTIONS");
        assertThat(result).hasContentType(MediaType.APPLICATION_JSON);
        assertProtocolError(result, "METHOD_NOT_ALLOWED");
        assertThat(result).bodyJson().extractingPath("$.error.message")
                .isEqualTo("метод у этого ресурса не поддерживается");
        assertThat(output).doesNotContain("ERROR");
    }

    @Test
    void allowHeader_listsDeclaredMethods_addingHeadOnlyForGet() {
        FailingController controller = new FailingController();
        controller.failWith(new HttpRequestMethodNotSupportedException("DELETE", List.of("POST", "PUT")));
        mvc = StandaloneMvc.withRequestId(controller, new RequestErrorAdvice());

        MvcTestResult result = get(FailingController.FAILING);

        assertThat(result).hasStatus(405);
        assertThat(result.getResponse().getHeader(HttpHeaders.ALLOW)).isEqualTo("POST,PUT,OPTIONS");
    }

    @Test
    void allowHeader_withoutDeclaredMethods_namesOnlyOptions() {
        FailingController controller = new FailingController();
        controller.failWith(new HttpRequestMethodNotSupportedException("DELETE"));
        mvc = StandaloneMvc.withRequestId(controller, new RequestErrorAdvice());

        MvcTestResult result = get(FailingController.FAILING);

        assertThat(result).hasStatus(405);
        assertThat(result.getResponse().getHeader(HttpHeaders.ALLOW)).isEqualTo("OPTIONS");
    }

    @ParameterizedTest
    @ValueSource(strings = {"text/xml", "application/xml", "text/html", "foo/"})
    void unsupportedAccept_isNotAcceptable_withJsonBody_inUnifiedFormat(String accept, CapturedOutput output) {
        MvcTestResult result = mvc.get().uri(FailingController.READ_ONLY)
                .header(RequestIdFilter.HEADER, REQUEST_ID).header(HttpHeaders.ACCEPT, accept).exchange();

        assertThat(result).hasStatus(406);
        assertThat(result).hasContentType(MediaType.APPLICATION_JSON);
        assertProtocolError(result, "NOT_ACCEPTABLE");
        assertThat(output).doesNotContain("ERROR");
    }

    @Test
    void unsatisfiedParameters_isValidationError_namingExpectedParameterSets() {
        FailingController controller = new FailingController();
        controller.failWith(new UnsatisfiedServletRequestParameterException(List.of(
                new String[] {"external_id"}, new String[] {"number", "contract_external_id"}), Map.of()));
        mvc = StandaloneMvc.withRequestId(controller, new RequestErrorAdvice());

        MvcTestResult result = get(FailingController.FAILING);

        assertThat(result).hasStatus(400);
        assertThat(result).bodyJson().extractingPath("$.error.code").isEqualTo("VALIDATION_ERROR");
        assertThat(result).bodyJson().extractingPath("$.error.message").isEqualTo("нарушен формат запроса");
        assertThat(result).bodyJson().extractingPath("$.error.details[*].field").asArray()
                .containsExactly("external_id", "number,contract_external_id");
        assertThat(result).bodyJson().extractingPath("$.error.details[*].reason").asArray()
                .containsOnly("нужен этот набор параметров");
        assertThat(result).bodyJson().extractingPath("$.error.requestId").isEqualTo(REQUEST_ID);
    }

    private MvcTestResult get(String path) {
        return mvc.get().uri(path).header(RequestIdFilter.HEADER, REQUEST_ID).exchange();
    }

    private static void assertValidationError(MvcTestResult result, String field, String reason) {
        assertThat(result).hasStatus(400);
        assertThat(result).bodyJson().extractingPath("$.error.code").isEqualTo("VALIDATION_ERROR");
        assertThat(result).bodyJson().extractingPath("$.error.message").isEqualTo("нарушен формат запроса");
        assertThat(result).bodyJson().extractingPath("$.error.details[0].field").isEqualTo(field);
        assertThat(result).bodyJson().extractingPath("$.error.details[0].reason").isEqualTo(reason);
        assertThat(result).bodyJson().extractingPath("$.error.requestId").isEqualTo(REQUEST_ID);
    }

    private static void assertProtocolError(MvcTestResult result, String code) {
        assertThat(result).bodyJson().extractingPath("$.error.code").isEqualTo(code);
        assertThat(result).bodyJson().extractingPath("$.error.message").asString().isNotBlank();
        assertThat(result).bodyJson().extractingPath("$.error.details").asArray().isEmpty();
        assertThat(result).bodyJson().extractingPath("$.error.requestId").isEqualTo(REQUEST_ID);
    }
}
