package ru.projectx.datareference.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import ru.projectx.datareference.dto.ErrorResponse;
import ru.projectx.datareference.service.database.DatabaseOutages;

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
