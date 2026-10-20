package ru.projectx.datareference.repository.catalog;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record InsuranceContractKey(UUID id, UUID externalId, @Nullable UUID externalInsurerId) {
}
