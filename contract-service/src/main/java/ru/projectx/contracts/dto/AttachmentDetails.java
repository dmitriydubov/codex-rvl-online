package ru.projectx.contracts.dto;

import java.time.Instant;
import java.util.UUID;

public record AttachmentDetails(
        UUID documentId,
        UUID attachmentId,
        String contentType,
        long fileSize,
        String fileHash,
        Instant createdAt) {
}
