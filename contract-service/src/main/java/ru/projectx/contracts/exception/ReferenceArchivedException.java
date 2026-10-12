package ru.projectx.contracts.exception;

import java.util.UUID;

public class ReferenceArchivedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final ReferenceKind kind;
    private final UUID id;

    public ReferenceArchivedException(ReferenceKind kind, UUID id) {
        super("запись справочника в архиве: " + kind + " " + id);
        this.kind = kind;
        this.id = id;
    }

    public ReferenceKind kind() {
        return kind;
    }

    public UUID id() {
        return id;
    }
}
