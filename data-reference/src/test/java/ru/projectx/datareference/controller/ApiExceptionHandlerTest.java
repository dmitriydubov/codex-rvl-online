package ru.projectx.datareference.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.hibernate.TransactionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.TransactionTimedOutException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import ru.projectx.datareference.exception.AmbiguousReferenceException;
import ru.projectx.datareference.exception.IncompleteReferenceException;
import ru.projectx.datareference.exception.ReferenceNotFoundException;

/**
 * Единый формат отказов на настоящем стеке MVC: заглушка-контроллер бросает исключение,
 * обработчик решает статус и код. Фильтр X-Request-Id подключён, чтобы {@code request_id}
 * в теле сверялся с заголовком.
 */
@ExtendWith(OutputCaptureExtension.class)
class ApiExceptionHandlerTest {

    private static final String FAILING = "/failing";
    /** Текст чужого исключения — с «данными», которые не должны уйти ни наружу, ни в журнал. */
    private static final String FOREIGN_TEXT = "Иванов Иван 1980-05-17";
    private static final String REQUEST_ID = "handler-test-1";

    private final FailingController controller = new FailingController();
    private MockMvcTester mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcTester.create(MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .addFilters(new RequestIdFilter())
                .build());
    }

    /**
     * Временный отказ базы: отказ соединения при входе в транзакцию, обрыв посреди запроса,
     * таймауты оператора и транзакции, ожидание блокировки. Вызывающий может повторить запрос.
     */
    static Stream<RuntimeException> databaseOutages() {
        return Stream.of(
                new CannotCreateTransactionException(FOREIGN_TEXT, new SQLException(FOREIGN_TEXT)),
                new CannotGetJdbcConnectionException(FOREIGN_TEXT, new SQLException(FOREIGN_TEXT)),
                new DataAccessResourceFailureException(FOREIGN_TEXT),
                new RecoverableDataAccessException(FOREIGN_TEXT),
                new QueryTimeoutException(FOREIGN_TEXT),
                new CannotAcquireLockException(FOREIGN_TEXT),
                new PessimisticLockingFailureException(FOREIGN_TEXT),
                new TransactionTimedOutException(FOREIGN_TEXT),
                new JpaSystemException(new TransactionException("Transaction timeout expired")));
    }

    @ParameterizedTest
    @MethodSource("databaseOutages")
    void databaseOutage_isServiceUnavailable_inUnifiedFormat(RuntimeException outage)
            throws UnsupportedEncodingException {

        controller.failWith(outage);

        MvcTestResult result = request();

        assertThat(result).hasStatus(503);
        assertThat(result).bodyJson().extractingPath("$.error.code").isEqualTo("SERVICE_UNAVAILABLE");
        assertThat(result).bodyJson().extractingPath("$.error.details").asArray().isEmpty();
        assertThat(result).bodyJson().extractingPath("$.error.requestId").isEqualTo(REQUEST_ID);
        assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).doesNotContain(FOREIGN_TEXT);
    }

    /** В журнал — тип отказа и идентификатор запроса, но не текст исключения: в нём SQL и значения. */
    @Test
    void databaseOutage_isLoggedWithTypeAndRequestId_withoutForeignText(CapturedOutput output) {
        controller.failWith(new QueryTimeoutException(FOREIGN_TEXT));

        request();

        assertThat(output).contains("WARN", "QueryTimeoutException", REQUEST_ID).doesNotContain(FOREIGN_TEXT);
    }

    /** SQLSTATE драйвера отличает блокировку от отмены и от отказа соединения — без текста исключения. */
    @Test
    void databaseOutage_logsDeepestSqlState(CapturedOutput output) {
        controller.failWith(new CannotAcquireLockException(FOREIGN_TEXT,
                new RuntimeException(FOREIGN_TEXT, new SQLException(FOREIGN_TEXT, "55P03"))));

        request();

        assertThat(output).contains("sqlstate=55P03").doesNotContain(FOREIGN_TEXT);
    }

    @Test
    void databaseOutage_withoutSqlException_logsNoSqlState(CapturedOutput output) {
        controller.failWith(new TransactionTimedOutException(FOREIGN_TEXT));

        request();

        assertThat(output).contains("TransactionTimedOutException, sqlstate=нет, request_id=" + REQUEST_ID);
    }

    /** Постоянные ошибки доступа к данным — это дефект или данные, а не временная недоступность: 500. */
    static Stream<RuntimeException> permanentFailures() {
        return Stream.of(
                new InvalidDataAccessResourceUsageException(FOREIGN_TEXT),
                new DataIntegrityViolationException(FOREIGN_TEXT),
                new JpaSystemException(new IllegalStateException(FOREIGN_TEXT)),
                new IllegalStateException(FOREIGN_TEXT));
    }

    @ParameterizedTest
    @MethodSource("permanentFailures")
    void permanentFailure_isInternalError_withoutForeignText(RuntimeException failure)
            throws UnsupportedEncodingException {

        controller.failWith(failure);

        MvcTestResult result = request();

        assertThat(result).hasStatus(500);
        assertThat(result).bodyJson().extractingPath("$.error.code").isEqualTo("INTERNAL_ERROR");
        assertThat(result).bodyJson().extractingPath("$.error.requestId").isEqualTo(REQUEST_ID);
        assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).doesNotContain(FOREIGN_TEXT);
    }

    @Test
    void ambiguousReference_isConflict_withCandidates() {
        UUID candidate = UUID.fromString("0a9e0000-0000-4000-8000-000000000003");
        controller.failWith(AmbiguousReferenceException.policy(List.of(candidate)));

        MvcTestResult result = request();

        assertThat(result).hasStatus(409);
        assertThat(result).bodyJson().extractingPath("$.error.code").isEqualTo("AMBIGUOUS_POLICY");
        assertThat(result).bodyJson().extractingPath("$.error.details[0].policyId").isEqualTo(candidate.toString());
    }

    @Test
    void incompleteReference_isUnprocessable_withCode() {
        controller.failWith(IncompleteReferenceException.noActivePriceContract(UUID.randomUUID()));

        MvcTestResult result = request();

        assertThat(result).hasStatus(422);
        assertThat(result).bodyJson().extractingPath("$.error.code").isEqualTo("NO_ACTIVE_PRICE_CONTRACT");
    }

    /** Записи справочника нет: 404 с кодом справочника, его текстом и идентификатором запроса. */
    @Test
    void referenceNotFound_isNotFound_withReferenceCode() {
        UUID programId = UUID.fromString("0a9e0000-0000-4000-8000-000000000007");
        controller.failWith(ReferenceNotFoundException.program(programId));

        MvcTestResult result = request();

        assertThat(result).hasStatus(404);
        assertThat(result).bodyJson().extractingPath("$.error.code").isEqualTo("PROGRAM_NOT_FOUND");
        assertThat(result).bodyJson().extractingPath("$.error.message")
                .isEqualTo("программа страхования " + programId + " не найдена");
        assertThat(result).bodyJson().extractingPath("$.error.details").asArray().isEmpty();
        assertThat(result).bodyJson().extractingPath("$.error.requestId").isEqualTo(REQUEST_ID);
    }

    /** Такого пути нет: 404 NOT_FOUND с путём в тексте и идентификатором запроса. */
    @Test
    void unknownPath_isNotFound_namingPath() {
        MvcTestResult result = mvc.get().uri("/no-resource").header(RequestIdFilter.HEADER, REQUEST_ID).exchange();

        assertThat(result).hasStatus(404);
        assertThat(result).bodyJson().extractingPath("$.error.code").isEqualTo("NOT_FOUND");
        assertThat(result).bodyJson().extractingPath("$.error.message").isEqualTo("ресурс не найден: api/unknown");
        assertThat(result).bodyJson().extractingPath("$.error.requestId").isEqualTo(REQUEST_ID);
    }

    @Test
    void missingParameter_isValidationError_namingParameter() {
        MvcTestResult result = mvc.get().uri("/with-parameter").header(RequestIdFilter.HEADER, REQUEST_ID).exchange();

        assertThat(result).hasStatus(400);
        assertThat(result).bodyJson().extractingPath("$.error.code").isEqualTo("VALIDATION_ERROR");
        assertThat(result).bodyJson().extractingPath("$.error.details[0].field").isEqualTo("key");
    }

    private MvcTestResult request() {
        return mvc.get().uri(FAILING).header(RequestIdFilter.HEADER, REQUEST_ID).exchange();
    }

    /** Бросает то исключение, которое задал тест. */
    @RestController
    static class FailingController {

        private RuntimeException failure = new IllegalStateException("отказ не задан");

        void failWith(RuntimeException next) {
            this.failure = next;
        }

        @GetMapping(FAILING)
        String fail() {
            throw failure;
        }

        @GetMapping("/no-resource")
        String noResource() throws NoResourceFoundException {
            throw new NoResourceFoundException(HttpMethod.GET, "/no-resource", "api/unknown");
        }

        @GetMapping("/with-parameter")
        String withParameter(@RequestParam String key) {
            return key;
        }
    }
}
