package ru.projectx.contracts.entity;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record IssuedPolicy(
        UUID policyUskId,
        @Nullable String policyUskNumber,
        UUID issuedPolicyId,
        String issuedPolicyNumber) {
}
