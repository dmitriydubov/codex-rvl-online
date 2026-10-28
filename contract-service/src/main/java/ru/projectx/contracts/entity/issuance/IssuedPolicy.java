package ru.projectx.contracts.entity.issuance;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record IssuedPolicy(
        UUID uskId,
        @Nullable String uskNumber,
        UUID issuedPolicyId,
        String issuedPolicyNumber) {
}
