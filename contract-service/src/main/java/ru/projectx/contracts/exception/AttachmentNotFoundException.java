package ru.projectx.contracts.exception;

public final class AttachmentNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public AttachmentNotFoundException() {
        super("вложение не найдено");
    }
}
