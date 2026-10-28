package ru.projectx.datareference.controller.error;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import ru.projectx.datareference.controller.request.CurrentRequest;
import ru.projectx.datareference.dto.ErrorResponse;

final class UnexpectedFailure {

    private static final Logger log = LoggerFactory.getLogger(ServerFailureAdvice.class);

    private static final String INTERNAL_ERROR = "INTERNAL_ERROR";

    private UnexpectedFailure() {
    }

    static ResponseEntity<ErrorResponse> answer(Exception failure) {
        log.error("необработанная ошибка REST: {}, request_id={}", FailureTrace.of(failure), CurrentRequest.id());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of(INTERNAL_ERROR, "внутренняя ошибка сервиса", CurrentRequest.id()));
    }
}
