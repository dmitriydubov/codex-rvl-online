package ru.projectx.contracts.exception;

public class InvalidAttachmentIdException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InvalidAttachmentIdException() {
        super("неверный формат идентификатора вложения");
    }
}
