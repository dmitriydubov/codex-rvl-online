package ru.projectx.contracts.controller;

import java.util.List;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import ru.projectx.contracts.dto.ErrorResponse;
import ru.projectx.contracts.exception.ContractAlreadyAcceptedException;
import ru.projectx.contracts.exception.ContractNotFoundException;
import ru.projectx.contracts.exception.ContractStateException;
import ru.projectx.contracts.exception.DocumentVersionMismatchException;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AcceptanceErrorAdvice {

    static final String CONTRACT_VERSION_FIELD = "contract_version";
    static final String CURRENT_VERSION = "актуальная версия договора — ";

    private static final List<Refusal> REFUSALS = List.of(
            new Refusal(ContractNotFoundException.class, HttpStatus.NOT_FOUND, "CONTRACT_NOT_FOUND",
                    "договора КМ с таким идентификатором нет"),
            new Refusal(ContractAlreadyAcceptedException.class, HttpStatus.CONFLICT, "ALREADY_ACCEPTED",
                    "договор КМ уже акцептован по другой версии"),
            new Refusal(ContractStateException.class, HttpStatus.CONFLICT, "INVALID_STATE",
                    "состояние договора КМ не допускает акцепт"),
            new Refusal(PessimisticLockingFailureException.class, HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE",
                    "договор КМ занят другой операцией, повторите запрос позже"));

    @ExceptionHandler({ContractNotFoundException.class, ContractAlreadyAcceptedException.class,
        ContractStateException.class, PessimisticLockingFailureException.class})
    public ResponseEntity<ErrorResponse> refused(RuntimeException refusal) {
        Refusal matching = REFUSALS.stream().filter(candidate -> candidate.type().isInstance(refusal)).findFirst()
                .orElseThrow(() -> new IllegalStateException("отказ без ответа: " + refusal.getClass().getName()));
        return ApiErrors.of(matching.status(), matching.code(), matching.message());
    }

    @ExceptionHandler
    public ResponseEntity<ErrorResponse> versionMismatch(DocumentVersionMismatchException mismatch) {
        return ApiErrors.conflictOnField("DOCUMENT_VERSION_MISMATCH", "акцептуется не актуальная версия договора КМ",
                CONTRACT_VERSION_FIELD, CURRENT_VERSION + mismatch.currentVersion());
    }

    private record Refusal(Class<? extends RuntimeException> type, HttpStatus status, String code, String message) {
    }
}
