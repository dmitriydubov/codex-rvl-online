package ru.projectx.datareference.controller;

import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
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
 * такого пути нет (404), всё остальное — 500.
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

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse unexpected(Exception failure) {
        log.error("необработанная ошибка REST, request_id={}", requestId(), failure);
        return ErrorResponse.of(INTERNAL_ERROR, "внутренняя ошибка сервиса", requestId());
    }

    @Nullable
    private static String requestId() {
        return MDC.get(RequestIdFilter.MDC_KEY);
    }
}
