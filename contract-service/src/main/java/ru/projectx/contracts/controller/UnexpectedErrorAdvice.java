package ru.projectx.contracts.controller;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import ru.projectx.contracts.dto.ErrorResponse;

@RestControllerAdvice(annotations = InternalApi.class)
@Order(Ordered.LOWEST_PRECEDENCE)
public class UnexpectedErrorAdvice {

    private static final Logger log = LoggerFactory.getLogger(UnexpectedErrorAdvice.class);

    @ExceptionHandler(IOException.class)
    public ResponseEntity<ErrorResponse> ioFailure(IOException failure, HttpServletResponse response) throws IOException {
        if (response.isCommitted()) {
            throw failure;
        }
        return unexpected(failure);
    }

    @ExceptionHandler
    public ResponseEntity<ErrorResponse> unexpected(Exception failure) {
        log.error("синхронный вызов не выполнен из-за непредвиденного отказа {}", failure.getClass().getName());
        return ApiErrors.of(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "внутренняя ошибка сервиса");
    }
}
