package ru.projectx.contracts.exception;

public class ReferenceNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final ReferenceKind kind;

    public ReferenceNotFoundException(ReferenceKind kind) {
        super("запись справочника не найдена: " + kind.notFoundCode());
        this.kind = kind;
    }

    public ReferenceKind kind() {
        return kind;
    }

    public String code() {
        return kind.notFoundCode();
    }
}
