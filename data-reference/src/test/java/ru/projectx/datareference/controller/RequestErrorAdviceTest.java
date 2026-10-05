package ru.projectx.datareference.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.UnsatisfiedServletRequestParameterException;
import ru.projectx.datareference.exception.InvalidRequestException;

/**
 * Ошибки вызывающего уходят в едином формате ошибки с понятным кодом и не пишутся в журнал как ошибки
 * сервиса. Неверный параметр даёт 400 с именем поля, чужой путь даёт 404, чужой метод даёт 405 с
 * перечнем методов в заголовке {@code Allow}, а формат ответа, которого сервис не отдаёт, даёт 406.
 *
 * <p>Тела 404, 405 и 406 отдаются в JSON, даже если вызывающий просил другой формат. Без явного типа содержимого
 * Spring подбирал бы формат по заголовку {@code Accept}, не нашёл бы подходящего и отдал бы ответ без тела.
 */
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

    @Test
    void unknownPath_isNotFound_namingPath() {
        MvcTestResult result = get(FailingController.NO_RESOURCE);

        assertThat(result).hasStatus(404);
        assertThat(result).bodyJson().extractingPath("$.error.code").isEqualTo("NOT_FOUND");
        assertThat(result).bodyJson().extractingPath("$.error.message").isEqualTo("ресурс не найден: api/unknown");
        assertThat(result).bodyJson().extractingPath("$.error.details").asArray().isEmpty();
        assertThat(result).bodyJson().extractingPath("$.error.requestId").isEqualTo(REQUEST_ID);
    }

    /**
     * У чужого пути нет ручки с типом ответа, поэтому {@code Accept} без JSON до отказа не отсекается. Ответ всё равно
     * уходит телом в едином формате, а не пустым: вызывающий видит код ошибки и {@code request_id}.
     */
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

    /** Отказ 405 называет методы, которые есть у ручки, а не только GET. HEAD и OPTIONS прилагаются к GET. */
    @Test
    void allowHeader_listsDeclaredMethods_addingHeadOnlyForGet() {
        FailingController controller = new FailingController();
        controller.failWith(new HttpRequestMethodNotSupportedException("DELETE", List.of("POST", "PUT")));
        mvc = StandaloneMvc.withRequestId(controller, new RequestErrorAdvice());

        MvcTestResult result = get(FailingController.FAILING);

        assertThat(result).hasStatus(405);
        assertThat(result.getResponse().getHeader(HttpHeaders.ALLOW)).isEqualTo("POST,PUT,OPTIONS");
    }

    /** Если Spring не назвал методов ручки, заголовок называет только OPTIONS: на него сервис отвечает всегда. */
    @Test
    void allowHeader_withoutDeclaredMethods_namesOnlyOptions() {
        FailingController controller = new FailingController();
        controller.failWith(new HttpRequestMethodNotSupportedException("DELETE"));
        mvc = StandaloneMvc.withRequestId(controller, new RequestErrorAdvice());

        MvcTestResult result = get(FailingController.FAILING);

        assertThat(result).hasStatus(405);
        assertThat(result.getResponse().getHeader(HttpHeaders.ALLOW)).isEqualTo("OPTIONS");
    }

    /** Неразбираемый {@code Accept} ({@code foo/}) Spring тоже считает неприемлемым, и ответ тот же. */
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

    /**
     * Ни одна ручка с условием на параметры не подошла. Сегодня у каждой такой ручки есть запасная без
     * условия, поэтому по HTTP этот отказ не возникает; если запасная пропадёт, ответ всё равно будет 400
     * с перечнем ожидаемых наборов параметров.
     */
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
