package ru.projectx.contracts.entity.party;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record PersonOwner(@Nullable UUID contractId, @Nullable UUID lmPolicyId) {

    public static PersonOwner contract(UUID contractId) {
        return new PersonOwner(contractId, null);
    }

    public static PersonOwner lmPolicy(UUID lmPolicyId) {
        return new PersonOwner(null, lmPolicyId);
    }
}
