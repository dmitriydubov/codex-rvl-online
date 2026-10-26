package ru.projectx.contracts.message;

import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public sealed interface UskResult {

    UUID id();

    record Issued(
            UUID id,
            UUID uskId,
            @Nullable String uskNumber,
            UUID policyExternalId,
            String policyNumber,
            List<IssuedDocument> documents) implements UskResult {
    }

    record Failed(UUID id, String errorCode, String errorMessage) implements UskResult {
    }
}
