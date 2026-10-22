package ru.projectx.contracts.dto;

import java.time.Instant;
import java.util.UUID;

public record AttachmentResponse(
        UUID documentId,
        UUID attachmentId,
        UUID individualRequestId,
        UUID participantId,
        String contentType,
        long fileSize,
        String fileHash,
        Instant createdAt) {
}
