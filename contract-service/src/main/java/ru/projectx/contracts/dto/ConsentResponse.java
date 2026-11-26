package ru.projectx.contracts.dto;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import java.util.UUID;

public record ConsentResponse(
        UUID policyId,
        UUID familyMemberId,
        @JsonUnwrapped AttachmentDetails details) implements AttachmentReceipt {
}
