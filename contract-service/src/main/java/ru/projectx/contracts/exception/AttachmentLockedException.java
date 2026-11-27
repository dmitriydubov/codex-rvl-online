package ru.projectx.contracts.exception;

public final class AttachmentLockedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public AttachmentLockedException() {
        super("справка защищена от удаления передачей договора");
    }
}
