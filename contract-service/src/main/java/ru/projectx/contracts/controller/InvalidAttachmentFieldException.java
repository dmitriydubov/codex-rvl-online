package ru.projectx.contracts.controller;

class InvalidAttachmentFieldException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String field;

    InvalidAttachmentFieldException(String field) {
        this.field = field;
    }

    String field() {
        return field;
    }
}
