package ru.projectx.contracts.controller;

class InvalidAttachmentPartException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String field;

    InvalidAttachmentPartException(String field) {
        this.field = field;
    }

    String field() {
        return field;
    }
}
