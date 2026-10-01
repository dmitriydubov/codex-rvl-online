package ru.projectx.datareference.controller;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.TransactionTimedOutException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import ru.projectx.datareference.dto.ErrorResponse;
import ru.projectx.datareference.exception.AmbiguousReferenceException;
import ru.projectx.datareference.exception.IncompleteReferenceException;
import ru.projectx.datareference.exception.InvalidRequestException;
import ru.projectx.datareference.exception.ReferenceNotFoundException;

/**
 * Все отказы REST — в одном формате {@link ErrorResponse}. Исходы: запрос неверен
 * по форме или по смыслу (400 с полем), записи нет (404 с кодом справочника), данные 1С
 * неоднозначны (409 с перечнем записей), данных 1С не хватает для ответа (422),
 * такого пути нет (404), база временно недоступна или не ответила вовремя (503),
 * всё остальное — 500.
 *
 * <p>503 ({@code SERVICE_UNAVAILABLE}, решение Р24) — отказ, после которого тот же запрос
 * стоит повторить: вызывающие повторяют GET ограниченное число раз. По иерархии Spring
 * Framework 7.0.8 это: соединения нет при входе в транзакцию
 * ({@link CannotCreateTransactionException}); соединение оборвалось или его не дал пул
 * ({@link DataAccessResourceFailureException}, {@link RecoverableDataAccessException});
 * временный отказ — таймаут оператора, ожидание блокировки ({@link TransientDataAccessException});
 * истёк лимит времени транзакции чтения ({@link TransactionTimedOutException} от Spring, а от
 * Hibernate — {@link JpaSystemException} с {@code org.hibernate.TransactionException} в причине:
 * его Spring ни во что точнее не переводит). Прочие ошибки доступа к данным — неверный SQL,
 * нарушение ограничений — постоянные: повтор их не исправит, это 500.
 *
 * <p>409 и 422 — это проблема данных в 1С, а не вызывающего, поэтому они пишутся в журнал
 * на уровне WARN: по таким записям собирается очередь на исправление. В журнал идут
 * идентификаторы из {@code details}, не ПДн.
 *
 * <p>Последний обработчик — та самая «верхняя граница», где {@code catch (Exception)}
 * допустим: он не подавляет, а классифицирует. Наружу уходит код и общий текст —
 * текст чужого исключения потребителю ни к чему, — а в журнал ошибка целиком,
 * с идентификатором запроса. Путь ручек несёт только UUID, ПДн в такой записи нет.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    private static final String VALIDATION_ERROR = "VALIDATION_ERROR";
    private static final String NOT_FOUND = "NOT_FOUND";
    private static final String INTERNAL_ERROR = "INTERNAL_ERROR";
    private static final String SERVICE_UNAVAILABLE = "SERVICE_UNAVAILABLE";

    @ExceptionHandler(InvalidRequestException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse invalidRequest(InvalidRequestException invalid) {
        var violation = new ErrorResponse.Violation(invalid.field(), invalid.getMessage());
        return ErrorResponse.of(VALIDATION_ERROR, "нарушен формат запроса", List.of(violation), requestId());
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse missingParameter(MissingServletRequestParameterException missing) {
        var violation = new ErrorResponse.Violation(missing.getParameterName(), "обязательный параметр");
        return ErrorResponse.of(VALIDATION_ERROR, "нарушен формат запроса", List.of(violation), requestId());
    }

    @ExceptionHandler(ReferenceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse referenceNotFound(ReferenceNotFoundException notFound) {
        return ErrorResponse.of(notFound.code(), notFound.getMessage(), requestId());
    }

    @ExceptionHandler(AmbiguousReferenceException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse ambiguousReference(AmbiguousReferenceException ambiguous) {
        log.warn("неоднозначные данные 1С: {} {}, request_id={}",
                ambiguous.code(), ambiguous.details(), requestId());
        return ErrorResponse.of(ambiguous.code(), ambiguous.getMessage(), ambiguous.details(), requestId());
    }

    @ExceptionHandler(IncompleteReferenceException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_CONTENT)
    public ErrorResponse incompleteReference(IncompleteReferenceException incomplete) {
        log.warn("данных 1С не хватает для ответа: {} {}, request_id={}",
                incomplete.code(), incomplete.details(), requestId());
        return ErrorResponse.of(incomplete.code(), incomplete.getMessage(), incomplete.details(), requestId());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse malformedArgument(MethodArgumentTypeMismatchException mismatch) {
        String expected = Optional.ofNullable(mismatch.getRequiredType())
                .map(Class::getSimpleName)
                .orElse("другой тип");
        var violation = new ErrorResponse.Violation(mismatch.getName(), "ожидается " + expected);
        return ErrorResponse.of(VALIDATION_ERROR, "нарушен формат запроса", List.of(violation), requestId());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse unknownPath(NoResourceFoundException noResource) {
        return ErrorResponse.of(NOT_FOUND, "ресурс не найден: " + noResource.getResourcePath(), requestId());
    }

    /**
     * В журнал — тип отказа, SQLSTATE драйвера (если отказ пришёл из базы: 55P03 — блокировка,
     * 57014 — отмена по таймауту, 08xxx — соединение) и идентификатор запроса. Текст исключения
     * туда не идёт: в нём SQL и сообщение драйвера, а с ними могут прийти значения строки.
     */
    @ExceptionHandler({
            CannotCreateTransactionException.class,
            TransactionTimedOutException.class,
            DataAccessResourceFailureException.class,
            RecoverableDataAccessException.class,
            TransientDataAccessException.class})
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ErrorResponse databaseUnavailable(RuntimeException outage) {
        log.warn("база недоступна или не ответила вовремя: {}, sqlstate={}, request_id={}",
                outage.getClass().getSimpleName(), sqlState(outage), requestId());
        return ErrorResponse.of(SERVICE_UNAVAILABLE, "сервис временно недоступен, повторите запрос позже", requestId());
    }

    /**
     * Истёкший лимит транзакции Hibernate — временный отказ; любая другая системная ошибка JPA — 500.
     * Ограничение: Spring переводит в {@link JpaSystemException} любой {@code org.hibernate.TransactionException}
     * без {@code SQLException} в причине, и отличить истёкший срок от прочих отказов транзакции можно
     * только по тексту сообщения — на это обработчик не опирается. Для чтений REST другие такие отказы —
     * дефект, а не данные; они тоже уйдут 503.
     */
    @ExceptionHandler(JpaSystemException.class)
    public ResponseEntity<ErrorResponse> jpaSystemFailure(JpaSystemException failure) {
        if (failure.getCause() instanceof org.hibernate.TransactionException) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(databaseUnavailable(failure));
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(unexpected(failure));
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse unexpected(Exception failure) {
        log.error("необработанная ошибка REST, request_id={}", requestId(), failure);
        return ErrorResponse.of(INTERNAL_ERROR, "внутренняя ошибка сервиса", requestId());
    }

    /** SQLSTATE самого глубокого {@link SQLException} в цепочке причин, если он есть. */
    private static String sqlState(Throwable outage) {
        return Optional.of(NestedExceptionUtils.getMostSpecificCause(outage))
                .filter(SQLException.class::isInstance)
                .map(cause -> ((SQLException) cause).getSQLState())
                .orElse("нет");
    }

    @Nullable
    private static String requestId() {
        return MDC.get(RequestIdFilter.MDC_KEY);
    }
}
