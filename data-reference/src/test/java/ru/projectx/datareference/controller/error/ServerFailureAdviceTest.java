package ru.projectx.datareference.controller.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
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
import org.springframework.data.jpa.repository.query.BadJpqlGrammarException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.TransactionTimedOutException;
import ru.projectx.datareference.controller.FailingController;
import ru.projectx.datareference.controller.StandaloneMvc;
import ru.projectx.datareference.controller.request.RequestIdFilter;
import ru.projectx.datareference.exception.RestOverloadException;

@ExtendWith(OutputCaptureExtension.class)
class ServerFailureAdviceTest {

    private static final String FOREIGN_TEXT = "Иванов Иван 1980-05-17";
    private static final String REQUEST_ID = "handler-test-1";
    private static final String JPQL = "select p from Policy p where p.externalId = ?1";
    private static final int OVERLOAD_LIMIT = 4242;

    private final FailingController controller = new FailingController();
    private MockMvcTester mvc;

    @BeforeEach
    void setUp() {
        mvc = StandaloneMvc.withRequestId(controller, StandaloneMvc.serverFailureAdvice());
    }

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
                new JpaSystemException(new TransactionException("Transaction timeout expired")),
                new org.hibernate.QueryTimeoutException(FOREIGN_TEXT, new SQLException(FOREIGN_TEXT, "57014"), FOREIGN_TEXT),
                new TransactionException("Transaction timeout expired"),
                new BadJpqlGrammarException(FOREIGN_TEXT, JPQL, new TransactionTimedOutException(FOREIGN_TEXT)));
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

    @Test
    void databaseOutage_isLoggedWithTypeAndRequestId_withoutForeignText(CapturedOutput output) {
        controller.failWith(new QueryTimeoutException(FOREIGN_TEXT));

        request();

        assertThat(output).contains("WARN", "QueryTimeoutException", REQUEST_ID).doesNotContain(FOREIGN_TEXT);
    }

    @Test
    void databaseOutage_logsDeepestSqlState(CapturedOutput output) {
        controller.failWith(new CannotAcquireLockException(FOREIGN_TEXT,
                new RuntimeException(FOREIGN_TEXT, new SQLException(FOREIGN_TEXT, "55P03"))));

        request();

        assertThat(output).contains("sqlstate=55P03").doesNotContain(FOREIGN_TEXT);
    }

    @Test
    void connectionLoss_logsSqlStateOfDeepestSqlException_notOfRootIoException(CapturedOutput output) {
        controller.failWith(new DataAccessResourceFailureException(FOREIGN_TEXT,
                new SQLException(FOREIGN_TEXT, "08006", new IOException(FOREIGN_TEXT))));

        request();

        assertThat(output).contains("sqlstate=08006").doesNotContain(FOREIGN_TEXT);
    }

    @Test
    void databaseOutage_withoutSqlException_logsNoSqlState(CapturedOutput output) {
        controller.failWith(new TransactionTimedOutException(FOREIGN_TEXT));

        request();

        assertThat(output).contains("TransactionTimedOutException, sqlstate=нет, request_id=" + REQUEST_ID);
    }

    @Test
    void readDeadlineHiddenByDerivedQuery_isLoggedAsWarningWithWrapperAndRequestId_notAsError(CapturedOutput output) {
        controller.failWith(new BadJpqlGrammarException(FOREIGN_TEXT, JPQL, new TransactionTimedOutException(FOREIGN_TEXT)));

        request();

        assertThat(output)
                .contains("WARN", "BadJpqlGrammarException/TransactionTimedOutException, sqlstate=нет, request_id=" + REQUEST_ID)
                .doesNotContain("ERROR", FOREIGN_TEXT);
    }

    @Test
    void queryTextError_isLoggedAsError(CapturedOutput output) {
        controller.failWith(new BadJpqlGrammarException(FOREIGN_TEXT, JPQL, new IllegalArgumentException(FOREIGN_TEXT)));

        request();

        assertThat(output).contains("ERROR", "BadJpqlGrammarException at ", "request_id=" + REQUEST_ID)
                .doesNotContain("WARN", FOREIGN_TEXT);
    }

    @Test
    void overload_isServiceUnavailable_inUnifiedFormat_withItsOwnMessage() {
        controller.failWith(new RestOverloadException(OVERLOAD_LIMIT));

        MvcTestResult result = request();

        assertThat(result).hasStatus(503);
        assertThat(result).bodyJson().extractingPath("$.error.code").isEqualTo("SERVICE_UNAVAILABLE");
        assertThat(result).bodyJson().extractingPath("$.error.message")
                .isEqualTo("сервис перегружен, повторите запрос позже");
        assertThat(result).bodyJson().extractingPath("$.error.details").asArray().isEmpty();
        assertThat(result).bodyJson().extractingPath("$.error.requestId").isEqualTo(REQUEST_ID);
        assertThat(result.getResponse().getHeader("Retry-After")).isNull();
    }

    @Test
    void overload_isNotLogged(CapturedOutput output) {
        controller.failWith(new RestOverloadException(OVERLOAD_LIMIT));

        request();

        assertThat(output).doesNotContain("WARN", "ERROR", String.valueOf(OVERLOAD_LIMIT));
    }

    static Stream<RuntimeException> permanentFailures() {
        return Stream.of(
                new InvalidDataAccessResourceUsageException(FOREIGN_TEXT),
                new DataIntegrityViolationException(FOREIGN_TEXT),
                new JpaSystemException(new IllegalStateException(FOREIGN_TEXT)),
                new BadJpqlGrammarException(FOREIGN_TEXT, JPQL, new IllegalArgumentException(FOREIGN_TEXT)),
                new org.hibernate.exception.ConstraintViolationException(FOREIGN_TEXT, new SQLException(FOREIGN_TEXT, "23505"), "uq"),
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
    void permanentFailure_isLoggedWithCauseChainAndRequestId_withoutForeignText(CapturedOutput output) {
        controller.failWith(new DataIntegrityViolationException(FOREIGN_TEXT, new SQLException(FOREIGN_TEXT)));

        request();

        assertThat(output).contains("ERROR", "DataIntegrityViolationException at ", " <- java.sql.SQLException at ",
                "ServerFailureAdviceTest#permanentFailure_isLoggedWithCauseChainAndRequestId_withoutForeignText:",
                "request_id=" + REQUEST_ID).doesNotContain(FOREIGN_TEXT, "\tat ");
    }

    private MvcTestResult request() {
        return mvc.get().uri(FailingController.FAILING).header(RequestIdFilter.HEADER, REQUEST_ID).exchange();
    }
}
