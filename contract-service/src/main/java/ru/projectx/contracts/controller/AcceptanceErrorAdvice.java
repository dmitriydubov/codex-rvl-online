package ru.projectx.contracts.controller;

import java.util.EnumMap;
import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import ru.projectx.contracts.dto.ErrorResponse;
import ru.projectx.contracts.exception.AcceptanceRefusal;
import ru.projectx.contracts.exception.AcceptanceRefusedException;
import ru.projectx.contracts.exception.DocumentVersionMismatchException;
import ru.projectx.contracts.exception.PayerEmailRequiredException;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AcceptanceErrorAdvice {

    static final String CONTRACT_VERSION_FIELD = "contract_version";
    static final String EMAIL_FIELD = "email";
    static final String CURRENT_VERSION = "актуальная версия договора — ";

    private static final Map<AcceptanceRefusal, Answer> ANSWERS = new EnumMap<>(Map.of(
            AcceptanceRefusal.CONTRACT_NOT_FOUND, new Answer(HttpStatus.NOT_FOUND, "CONTRACT_NOT_FOUND",
                    "договора КМ с таким идентификатором нет"),
            AcceptanceRefusal.ALREADY_ACCEPTED, new Answer(HttpStatus.CONFLICT, "ALREADY_ACCEPTED",
                    "договор КМ уже акцептован по другой версии"),
            AcceptanceRefusal.INVALID_STATE, new Answer(HttpStatus.CONFLICT, "INVALID_STATE",
                    "состояние договора КМ не допускает акцепт"),
            AcceptanceRefusal.CONTRACT_BUSY, new Answer(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE",
                    "договор КМ занят другой операцией, повторите запрос позже")));

    @ExceptionHandler
    public ResponseEntity<ErrorResponse> refused(AcceptanceRefusedException refused) {
        Answer answer = ANSWERS.get(refused.refusal());
        return ApiErrors.of(answer.status(), answer.code(), answer.message());
    }

    @ExceptionHandler
    public ResponseEntity<ErrorResponse> versionMismatch(DocumentVersionMismatchException mismatch) {
        return ApiErrors.conflictOnField("DOCUMENT_VERSION_MISMATCH", "акцептуется не актуальная версия договора КМ",
                CONTRACT_VERSION_FIELD, CURRENT_VERSION + mismatch.currentVersion());
    }

    @ExceptionHandler
    public ResponseEntity<ErrorResponse> payerEmailMissing(PayerEmailRequiredException missing) {
        return ApiErrors.invalidField(EMAIL_FIELD, RequestFormatAdvice.MISSING);
    }

    private record Answer(HttpStatus status, String code, String message) {
    }
}
