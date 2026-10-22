package ru.projectx.contracts.service.storage;

import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import ru.projectx.contracts.entity.ParticipantAttachment;

public record CertificateUpload(
        UUID individualRequestId,
        UUID participantId,
        UUID attachmentId,
        @Nullable String contentType,
        long size,
        ContentSource content) {

    public ParticipantAttachment owner() {
        return new ParticipantAttachment(individualRequestId, participantId, attachmentId);
    }

    public Optional<String> declaredType() {
        return Optional.ofNullable(contentType);
    }
}
