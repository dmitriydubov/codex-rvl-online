package ru.projectx.datareference.controller;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.UnsatisfiedServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import ru.projectx.datareference.dto.ErrorResponse;
import ru.projectx.datareference.exception.InvalidRequestException;

/**
 * Ошибки вызывающего в едином формате ошибки. Запрос неверен по форме или по смыслу: 400 с именем поля.
 * Такого пути нет: 404. У ручки нет такого метода: 405 с перечнем методов в заголовке {@code Allow}.
 * Ответ нужен в формате, которого сервис не отдаёт: 406.
 *
 * <p>Это ошибки вызывающего, а не сервиса, поэтому в журнал они не пишутся. Ответы 405 и 406 уходят с
 * явным типом {@code application/json}. Иначе Spring подбирал бы формат тела по заголовку {@code Accept}
 * запроса, а при {@code Accept: text/xml} не нашёл бы подходящего и отдал бы ответ без тела.
 *
 * <p>В заголовке {@code Allow} к методу GET добавляется HEAD, и к любому набору добавляется OPTIONS:
 * сервис отвечает на них сам, и тот же набор он называет в ответе на OPTIONS.
 */
@RestControllerAdvice
@Order(ErrorAdviceOrder.REQUEST)
public class RequestErrorAdvice {

    private static final String VALIDATION_ERROR = "VALIDATION_ERROR";
    private static final String NOT_FOUND = "NOT_FOUND";
    private static final String METHOD_NOT_ALLOWED = "METHOD_NOT_ALLOWED";
    private static final String NOT_ACCEPTABLE = "NOT_ACCEPTABLE";
    private static final String MALFORMED_REQUEST = "нарушен формат запроса";

    @ExceptionHandler(InvalidRequestException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse invalidRequest(InvalidRequestException invalid) {
        return validationError(List.of(new ErrorResponse.Violation(invalid.field(), invalid.getMessage())));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse missingParameter(MissingServletRequestParameterException missing) {
        return validationError(List.of(new ErrorResponse.Violation(missing.getParameterName(), "обязательный параметр")));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse malformedArgument(MethodArgumentTypeMismatchException mismatch) {
        String expected = Optional.ofNullable(mismatch.getRequiredType())
                .map(Class::getSimpleName)
                .orElse("другой тип");
        return validationError(List.of(new ErrorResponse.Violation(mismatch.getName(), "ожидается " + expected)));
    }

    /**
     * Ни одна ручка с условием на параметры не подошла. Каждое условие называется в ответе так, как оно
     * записано у ручки: имена параметров через запятую.
     */
    @ExceptionHandler(UnsatisfiedServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse unsatisfiedParameters(UnsatisfiedServletRequestParameterException unsatisfied) {
        return validationError(unsatisfied.getParamConditionGroups().stream()
                .map(condition -> new ErrorResponse.Violation(String.join(",", condition), "нужен этот набор параметров"))
                .toList());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse unknownPath(NoResourceFoundException noResource) {
        return ErrorResponse.of(NOT_FOUND, "ресурс не найден: " + noResource.getResourcePath(), CurrentRequest.id());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> methodNotAllowed(HttpRequestMethodNotSupportedException unsupported) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .allow(allowedMethods(unsupported).toArray(HttpMethod[]::new))
                .contentType(MediaType.APPLICATION_JSON)
                .body(ErrorResponse.of(METHOD_NOT_ALLOWED,
                        "метод " + unsupported.getMethod() + " у этого ресурса не поддерживается", CurrentRequest.id()));
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<ErrorResponse> notAcceptable(HttpMediaTypeNotAcceptableException notAcceptable) {
        return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE)
                .contentType(MediaType.APPLICATION_JSON)
                .body(ErrorResponse.of(NOT_ACCEPTABLE, "сервис отдаёт ответ только в формате JSON", CurrentRequest.id()));
    }

    private static ErrorResponse validationError(List<ErrorResponse.Violation> violations) {
        return ErrorResponse.of(VALIDATION_ERROR, MALFORMED_REQUEST, violations, CurrentRequest.id());
    }

    private static Set<HttpMethod> allowedMethods(HttpRequestMethodNotSupportedException unsupported) {
        Set<HttpMethod> allowed = new LinkedHashSet<>(
                Optional.ofNullable(unsupported.getSupportedHttpMethods()).orElseGet(Set::of));
        if (allowed.contains(HttpMethod.GET)) {
            allowed.add(HttpMethod.HEAD);
        }
        allowed.add(HttpMethod.OPTIONS);
        return allowed;
    }
}
