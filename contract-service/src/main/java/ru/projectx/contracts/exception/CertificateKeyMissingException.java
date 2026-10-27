package ru.projectx.contracts.exception;

import java.util.UUID;

public class CertificateKeyMissingException extends IllegalStateException {

    private static final long serialVersionUID = 1L;

    public CertificateKeyMissingException(UUID documentId) {
        super("у записи справки " + documentId + " нет объекта в хранилище");
    }
}
