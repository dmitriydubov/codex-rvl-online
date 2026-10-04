package ru.projectx.datareference.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import ru.projectx.datareference.dto.ErrorResponse;
import ru.projectx.datareference.service.DatabaseOutages;

/**
 * Сбой сервиса в едином формате ошибки: база временно недоступна или не ответила вовремя — 503, любая
 * другая ошибка — 500. Обработчик стоит последним среди обработчиков ошибок REST и получает только то,
 * что не разобрали остальные.
 *
 * <p>503 с кодом {@code SERVICE_UNAVAILABLE} означает «повторите запрос». Сервисы Заявок и Договоров
 * повторяют такой запрос ограниченное число раз, а 500 не повторяют. Какой отказ считать временным,
 * решает {@link DatabaseOutages}. Ошибка попадает сюда уже после сервиса чтения, поэтому классификация
 * одна на все ручки и не зависит от того, в каком слое ошибка возникла.
 *
 * <p>Здесь перехватывается любое исключение. Это верхняя граница сервиса, и перехват не подавляет ошибку,
 * а классифицирует её. Наружу уходят код и общий текст. В журнал идут цепочка причин с местом каждой
 * ({@link FailureTrace}) и идентификатор запроса, но не тексты и не стек: в тексте чужого исключения
 * могут быть SQL и значения строки с персональными данными, а стек печатает текст каждой причины.
 */
@RestControllerAdvice
@Order(ErrorAdviceOrder.SERVER_FAILURE)
public class ServerFailureAdvice {

    private static final Logger log = LoggerFactory.getLogger(ServerFailureAdvice.class);

    private static final String INTERNAL_ERROR = "INTERNAL_ERROR";
    private static final String SERVICE_UNAVAILABLE = "SERVICE_UNAVAILABLE";

    private final DatabaseOutages outages;

    public ServerFailureAdvice(DatabaseOutages outages) {
        this.outages = outages;
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> unexpected(Exception failure) {
        return outages.recognize(failure)
                .map(outage -> ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(serviceUnavailable(outage)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(internalError(failure)));
    }

    /** В журнал идут тип отказа, код SQLSTATE драйвера и идентификатор запроса. */
    private ErrorResponse serviceUnavailable(DatabaseOutages.Outage outage) {
        log.warn("база недоступна или не ответила вовремя: {}, sqlstate={}, request_id={}",
                outage.type(), outage.sqlState(), CurrentRequest.id());
        return ErrorResponse.of(SERVICE_UNAVAILABLE, "сервис временно недоступен, повторите запрос позже",
                CurrentRequest.id());
    }

    private ErrorResponse internalError(Exception failure) {
        log.error("необработанная ошибка REST: {}, request_id={}", FailureTrace.of(failure), CurrentRequest.id());
        return ErrorResponse.of(INTERNAL_ERROR, "внутренняя ошибка сервиса", CurrentRequest.id());
    }
}
