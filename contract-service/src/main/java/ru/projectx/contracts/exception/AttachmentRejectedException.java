package ru.projectx.contracts.exception;

public class AttachmentRejectedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final AttachmentDefect defect;

    public AttachmentRejectedException(AttachmentDefect defect) {
        super("файл справки отклонён: " + defect);
        this.defect = defect;
    }

    public AttachmentDefect defect() {
        return defect;
    }
}
