package ru.projectx.contracts.exception;

public final class AttachmentBusyException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public AttachmentBusyException() {
        super("договор занят другой операцией");
    }

    public AttachmentBusyException(Throwable cause) {
        super("договор занят другой операцией", cause);
    }
}
