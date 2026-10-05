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
import ru.projectx.datareference.exception.AmbiguousPriceException;
import ru.projectx.datareference.exception.AmbiguousReferenceException;
import ru.projectx.datareference.exception.IncompleteReferenceException;
import ru.projectx.datareference.exception.PriceNotCoveredException;
import ru.projectx.datareference.exception.ReferenceNotFoundException;

/**
 * Отказы чтения справочника в едином формате ошибки. Записи нет: 404 с кодом справочника. Данные 1С
 * неоднозначны: 409 с перечнем конфликтующих записей. Данных 1С не хватает для ответа: 422.
 *
 * <p>При расчёте стоимости услуги за период есть ещё два отказа по прайсу услуги: на одни сутки действует
 * больше одной строки прайса (409, в перечне — сроки этих строк) и есть сутки без строки прайса (422, в перечне —
 * непокрытые интервалы).
 *
 * <p>409 и 422 означают ошибку данных в 1С, а не вызывающего, поэтому они пишутся в журнал на уровне
 * WARN. По таким записям собирают очередь на исправление. В журнал идут код ошибки, идентификаторы
 * конфликтующих записей, ЛПУ без договора с ценами или услуги с ошибкой в прайсе и идентификатор запроса.
 * Персональных данных там нет.
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

    @ExceptionHandler(AmbiguousPriceException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse ambiguousPrice(AmbiguousPriceException ambiguous) {
        List<ErrorResponse.PriceRowCandidate> rows = ambiguous.rows().stream()
                .map(row -> new ErrorResponse.PriceRowCandidate(row.validFrom(), row.validTo()))
                .toList();
        log.warn("неоднозначный прайс услуги: {}, service_id={}, {}, request_id={}",
                ambiguous.code(), ambiguous.serviceId(), rows, CurrentRequest.id());
        return ErrorResponse.of(ambiguous.code(), ambiguous.getMessage(), rows, CurrentRequest.id());
    }

    @ExceptionHandler(PriceNotCoveredException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_CONTENT)
    public ErrorResponse priceNotCovered(PriceNotCoveredException notCovered) {
        List<ErrorResponse.UncoveredInterval> intervals = notCovered.intervals().stream()
                .map(interval -> new ErrorResponse.UncoveredInterval(interval.dateFrom(), interval.dateTo()))
                .toList();
        log.warn("прайс услуги не покрывает период: {}, service_id={}, {}, request_id={}",
                notCovered.code(), notCovered.serviceId(), intervals, CurrentRequest.id());
        return ErrorResponse.of(notCovered.code(), notCovered.getMessage(), intervals, CurrentRequest.id());
    }
}
