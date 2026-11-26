package ru.projectx.contracts.dto;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import java.util.UUID;

public record AttachmentResponse(
        UUID individualRequestId,
        UUID participantId,
        @JsonUnwrapped AttachmentDetails details) implements AttachmentReceipt {
}
