package ru.projectx.contracts.message.usk;

import java.util.UUID;

public record CertificateDocument(
        UUID documentId,
        String docType,
        String fileName,
        String contentType,
        String link,
        String fileHash) {
}
