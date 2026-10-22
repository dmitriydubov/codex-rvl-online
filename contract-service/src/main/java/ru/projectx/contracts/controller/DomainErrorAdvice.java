package ru.projectx.contracts.controller;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import ru.projectx.contracts.dto.ErrorResponse;
import ru.projectx.contracts.exception.AttachmentConflictException;
import ru.projectx.contracts.exception.AttachmentDefect;
import ru.projectx.contracts.exception.AttachmentRejectedException;
import ru.projectx.contracts.exception.ParticipantNotFoundException;
import ru.projectx.contracts.exception.RequestCancelledException;
import ru.projectx.contracts.exception.RequestNotFoundException;
import ru.projectx.contracts.exception.StorageUnavailableException;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DomainErrorAdvice {

    static final String UNSUPPORTED_TYPE = "тип файла не допускается";
    static final String SIZE_OUT_OF_RANGE = "размер файла вне допустимого";

    @ExceptionHandler
    public ResponseEntity<ErrorResponse> requestNotFound(RequestNotFoundException notFound) {
        return ApiErrors.of(HttpStatus.NOT_FOUND, "REQUEST_NOT_FOUND", "задачи на выпуск по заявке нет");
    }

    @ExceptionHandler
    public ResponseEntity<ErrorResponse> participantNotFound(ParticipantNotFoundException notFound) {
        return ApiErrors.of(HttpStatus.NOT_FOUND, "PARTICIPANT_NOT_FOUND", "участника нет в составе заявки");
    }

    @ExceptionHandler
    public ResponseEntity<ErrorResponse> requestCancelled(RequestCancelledException cancelled) {
        return ApiErrors.of(HttpStatus.CONFLICT, "INVALID_STATE", "работа по заявке прекращена");
    }

    @ExceptionHandler
    public ResponseEntity<ErrorResponse> attachmentConflict(AttachmentConflictException conflict) {
        return ApiErrors.of(HttpStatus.CONFLICT, "ATTACHMENT_CONFLICT",
                "вложение уже принято с другим содержимым или по другому участнику");
    }

    @ExceptionHandler
    public ResponseEntity<ErrorResponse> attachmentRejected(AttachmentRejectedException rejected) {
        return ApiErrors.invalidField(ApiErrors.FILE_FIELD,
                rejected.defect() == AttachmentDefect.CONTENT_TYPE ? UNSUPPORTED_TYPE : SIZE_OUT_OF_RANGE);
    }

    @ExceptionHandler
    public ResponseEntity<ErrorResponse> storageUnavailable(StorageUnavailableException unavailable) {
        return ApiErrors.of(HttpStatus.SERVICE_UNAVAILABLE, "STORAGE_UNAVAILABLE", "хранилище файлов недоступно");
    }
}
