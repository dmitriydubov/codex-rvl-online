package ru.projectx.datareference.controller;

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
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.TransactionTimedOutException;

/**
 * Сбой сервиса уходит потребителю в едином формате с верным статусом, а в журнал попадает без текста
 * чужого исключения. Это защищает две вещи сразу. Сервисы Заявок и Договоров повторяют запрос только
 * при 503, поэтому временный отказ базы нельзя выдать за внутреннюю ошибку. Текст исключения базы может
 * нести значения строки с персональными данными, поэтому он не должен попасть ни в ответ, ни в журнал.
 *
 * <p>Стек MVC настоящий: заглушка-контроллер бросает исключение, обработчик решает статус и код.
 * Фильтр {@code X-Request-Id} подключён, чтобы {@code request_id} в теле сверялся с заголовком.
 */
@ExtendWith(OutputCaptureExtension.class)
class ServerFailureAdviceTest {

    /** Текст чужого исключения — с «данными», которые не должны уйти ни наружу, ни в журнал. */
    private static final String FOREIGN_TEXT = "Иванов Иван 1980-05-17";
    private static final String REQUEST_ID = "handler-test-1";

    private final FailingController controller = new FailingController();
    private MockMvcTester mvc;

    @BeforeEach
    void setUp() {
        mvc = StandaloneMvc.withRequestId(controller, StandaloneMvc.serverFailureAdvice());
    }

    /**
     * Временный отказ базы: отказ соединения при входе в транзакцию, обрыв посреди запроса,
     * таймауты оператора и транзакции, ожидание блокировки. Вызывающий может повторить запрос.
     * Последние два — сырые исключения Hibernate, какими они приходят с ленивой догрузки в сервисе,
     * мимо перевода репозитория.
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
                new JpaSystemException(new TransactionException("Transaction timeout expired")),
                new org.hibernate.QueryTimeoutException(FOREIGN_TEXT, new SQLException(FOREIGN_TEXT, "57014"), FOREIGN_TEXT),
                new TransactionException("Transaction timeout expired"));
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

    /** При обрыве соединения драйвер кладёт SQLSTATE класса 08 поверх IOException — в журнале нужен именно он. */
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

    /** Постоянные ошибки доступа к данным — это дефект или данные, а не временная недоступность: 500. */
    static Stream<RuntimeException> permanentFailures() {
        return Stream.of(
                new InvalidDataAccessResourceUsageException(FOREIGN_TEXT),
                new DataIntegrityViolationException(FOREIGN_TEXT),
                new JpaSystemException(new IllegalStateException(FOREIGN_TEXT)),
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

    /**
     * Непредвиденная ошибка попадает в журнал с уровнем ERROR, типом исключения и идентификатором запроса,
     * но без текста и без стека: текст чужого исключения может нести значения строки с персональными
     * данными, а стек печатает тот же текст у каждой причины.
     */
    @Test
    void permanentFailure_isLoggedWithTypeAndRequestId_withoutForeignText(CapturedOutput output) {
        controller.failWith(new DataIntegrityViolationException(FOREIGN_TEXT, new SQLException(FOREIGN_TEXT)));

        request();

        assertThat(output).contains("ERROR", "DataIntegrityViolationException", "request_id=" + REQUEST_ID)
                .doesNotContain(FOREIGN_TEXT, "SQLException");
    }

    private MvcTestResult request() {
        return mvc.get().uri(FailingController.FAILING).header(RequestIdFilter.HEADER, REQUEST_ID).exchange();
    }
}
