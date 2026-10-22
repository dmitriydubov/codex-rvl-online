package ru.projectx.contracts.exception;

import java.util.UUID;

public class DocumentVersionMismatchException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int currentVersion;

    public DocumentVersionMismatchException(UUID contractId, int currentVersion) {
        super("акцептуется не актуальная версия договора КМ " + contractId + ", актуальная — " + currentVersion);
        this.currentVersion = currentVersion;
    }

    public int currentVersion() {
        return currentVersion;
    }
}
