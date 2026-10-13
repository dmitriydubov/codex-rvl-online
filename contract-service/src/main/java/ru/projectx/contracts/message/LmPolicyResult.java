package ru.projectx.contracts.message;

import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public sealed interface LmPolicyResult {

    UUID id();

    record Issued(
            UUID id,
            UUID policyUskId,
            @Nullable String policyUskNumber,
            UUID policyExternalId,
            String policyNumber,
            List<IssuedDocument> documents) implements LmPolicyResult {
    }

    record Failed(UUID id, String errorCode, String errorMessage) implements LmPolicyResult {
    }
}
