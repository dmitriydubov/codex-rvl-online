package ru.projectx.contracts.controller;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import ru.projectx.contracts.dto.ErrorResponse;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DatabindException;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class JsonBodyErrorAdvice {

    private static final String NOT_NULL = "NotNull";
    private static final String PATH_SEPARATOR = ".";

    @ExceptionHandler
    public ResponseEntity<ErrorResponse> unreadableBody(HttpMessageNotReadableException unreadable) {
        return mismatchedField(unreadable)
                .map(field -> ApiErrors.invalidField(field, RequestFormatAdvice.INVALID_FORMAT))
                .orElseGet(() -> ApiErrors.of(HttpStatus.BAD_REQUEST, ApiErrors.VALIDATION_ERROR,
                        "тело запроса не разобрано как JSON"));
    }

    @ExceptionHandler
    public ResponseEntity<ErrorResponse> invalidBody(MethodArgumentNotValidException invalid) {
        return ApiErrors.invalidFields(invalid.getBindingResult().getFieldErrors().stream()
                .map(JsonBodyErrorAdvice::violation).toList());
    }

    private static Optional<String> mismatchedField(HttpMessageNotReadableException unreadable) {
        return Optional.ofNullable(unreadable.getCause())
                .filter(DatabindException.class::isInstance)
                .map(DatabindException.class::cast)
                .map(JsonBodyErrorAdvice::path)
                .filter(path -> !path.isEmpty());
    }

    private static String path(DatabindException mismatch) {
        return mismatch.getPath().stream()
                .map(JacksonException.Reference::getPropertyName)
                .filter(Objects::nonNull)
                .collect(Collectors.joining(PATH_SEPARATOR));
    }

    private static ErrorResponse.FieldViolation violation(FieldError error) {
        String reason = NOT_NULL.equals(error.getCode()) ? RequestFormatAdvice.MISSING
                : RequestFormatAdvice.INVALID_FORMAT;
        return new ErrorResponse.FieldViolation(snakeCase(error.getField()), reason);
    }

    private static String snakeCase(String property) {
        return property.replaceAll("([A-Z])", "_$1").toLowerCase(Locale.ROOT);
    }
}
