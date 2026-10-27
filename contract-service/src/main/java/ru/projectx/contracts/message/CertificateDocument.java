package ru.projectx.contracts.message;

import java.util.UUID;

public record CertificateDocument(
        UUID documentId,
        String docType,
        String fileName,
        String contentType,
        String link,
        String fileHash) {
}
