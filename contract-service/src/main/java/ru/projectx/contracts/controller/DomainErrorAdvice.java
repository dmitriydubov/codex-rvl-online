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
import ru.projectx.contracts.exception.AttachmentBusyException;
import ru.projectx.contracts.exception.AttachmentConflictException;
import ru.projectx.contracts.exception.AttachmentDefect;
import ru.projectx.contracts.exception.AttachmentLockedException;
import ru.projectx.contracts.exception.AttachmentNotFoundException;
import ru.projectx.contracts.exception.AttachmentRejectedException;
import ru.projectx.contracts.exception.InvalidAttachmentIdException;
import ru.projectx.contracts.exception.RequestCancelledException;
import ru.projectx.contracts.exception.RequestNotFoundException;
import ru.projectx.contracts.exception.StorageUnavailableException;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DomainErrorAdvice {

    private static final Map<AttachmentDefect, String> DEFECT_REASONS = new EnumMap<>(Map.of(
            AttachmentDefect.CONTENT_TYPE, "тип файла не допускается",
            AttachmentDefect.SIZE, "размер файла вне допустимого"));
    private static final Map<Class<? extends RuntimeException>, AttachmentError> ATTACHMENT_ERRORS = Map.of(
            AttachmentNotFoundException.class, new AttachmentError(HttpStatus.NOT_FOUND, "ATTACHMENT_NOT_FOUND"),
            AttachmentLockedException.class, new AttachmentError(HttpStatus.CONFLICT, "ATTACHMENT_LOCKED"),
            AttachmentBusyException.class, new AttachmentError(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE"));

    @ExceptionHandler
    public ResponseEntity<ErrorResponse> requestNotFound(RequestNotFoundException notFound) {
        return ApiErrors.of(HttpStatus.NOT_FOUND, "REQUEST_NOT_FOUND", "задачи на выпуск по заявке нет");
    }

    @ExceptionHandler
    public ResponseEntity<ErrorResponse> requestCancelled(RequestCancelledException cancelled) {
        return ApiErrors.of(HttpStatus.CONFLICT, "INVALID_STATE", "работа по заявке прекращена");
    }

    @ExceptionHandler
    public ResponseEntity<ErrorResponse> attachmentConflict(AttachmentConflictException conflict) {
        return ApiErrors.of(HttpStatus.CONFLICT, "ATTACHMENT_CONFLICT",
                "вложение с таким идентификатором уже зарегистрировано с другими реквизитами или удалено");
    }

    @ExceptionHandler
    public ResponseEntity<ErrorResponse> attachmentRejected(AttachmentRejectedException rejected) {
        return ApiErrors.invalidField(ApiErrors.FILE_FIELD, DEFECT_REASONS.get(rejected.defect()));
    }

    @ExceptionHandler
    public ResponseEntity<ErrorResponse> storageUnavailable(StorageUnavailableException unavailable) {
        return ApiErrors.of(HttpStatus.SERVICE_UNAVAILABLE, "STORAGE_UNAVAILABLE", "хранилище файлов недоступно");
    }

    @ExceptionHandler
    public ResponseEntity<ErrorResponse> invalidAttachmentId(InvalidAttachmentIdException invalid) {
        return ApiErrors.invalidField(AttachmentContentController.ID_FIELD, RequestFormatAdvice.INVALID_FORMAT);
    }

    @ExceptionHandler({AttachmentNotFoundException.class, AttachmentLockedException.class, AttachmentBusyException.class})
    public ResponseEntity<ErrorResponse> attachmentAccess(RuntimeException refused) {
        AttachmentError error = ATTACHMENT_ERRORS.get(refused.getClass());
        return ApiErrors.of(error.status(), error.code(), refused.getMessage());
    }

    private record AttachmentError(HttpStatus status, String code) {
    }
}
