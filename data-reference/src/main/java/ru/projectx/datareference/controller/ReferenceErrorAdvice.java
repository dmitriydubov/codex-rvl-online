package ru.projectx.datareference.controller;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import ru.projectx.datareference.dto.ErrorResponse;
import ru.projectx.datareference.exception.AmbiguousReferenceException;
import ru.projectx.datareference.exception.IncompleteReferenceException;
import ru.projectx.datareference.exception.ReferenceNotFoundException;

/**
 * Отказы чтения справочника в едином формате ошибки. Записи нет: 404 с кодом справочника. Данные 1С
 * неоднозначны: 409 с перечнем конфликтующих записей. Данных 1С не хватает для ответа: 422.
 *
 * <p>409 и 422 означают ошибку данных в 1С, а не вызывающего, поэтому они пишутся в журнал на уровне
 * WARN. По таким записям собирают очередь на исправление. В журнал идут код ошибки, идентификаторы
 * конфликтующих записей или ЛПУ без договора с ценами и идентификатор запроса. Персональных данных
 * там нет.
 */
@RestControllerAdvice
@Order(ErrorAdviceOrder.REFERENCE)
public class ReferenceErrorAdvice {

    private static final Logger log = LoggerFactory.getLogger(ReferenceErrorAdvice.class);

    @ExceptionHandler(ReferenceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse referenceNotFound(ReferenceNotFoundException notFound) {
        return ErrorResponse.of(notFound.code(), notFound.getMessage(), CurrentRequest.id());
    }

    @ExceptionHandler(AmbiguousReferenceException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse ambiguousReference(AmbiguousReferenceException ambiguous) {
        List<ErrorResponse.Detail> candidates = AmbiguityDetails.of(ambiguous);
        log.warn("неоднозначные данные 1С: {} {}, request_id={}", ambiguous.code(), candidates, CurrentRequest.id());
        return ErrorResponse.of(ambiguous.code(), ambiguous.getMessage(), candidates, CurrentRequest.id());
    }

    @ExceptionHandler(IncompleteReferenceException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_CONTENT)
    public ErrorResponse incompleteReference(IncompleteReferenceException incomplete) {
        log.warn("данных 1С не хватает для ответа: {}, lpu_id={}, request_id={}",
                incomplete.code(), incomplete.lpuId(), CurrentRequest.id());
        return ErrorResponse.of(incomplete.code(), incomplete.getMessage(), CurrentRequest.id());
    }
}
