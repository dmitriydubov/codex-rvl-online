package ru.projectx.contracts.dto;

import java.util.List;

public record ErrorResponse(Body error) {

    public static ErrorResponse of(String code, String message, List<FieldViolation> details, String requestId) {
        return new ErrorResponse(new Body(code, message, List.copyOf(details), requestId));
    }

    public record Body(String code, String message, List<FieldViolation> details, String requestId) {
    }

    public record FieldViolation(String field, String reason) {
    }
}
