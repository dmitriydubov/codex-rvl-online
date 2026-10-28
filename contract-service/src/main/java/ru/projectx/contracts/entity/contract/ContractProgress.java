package ru.projectx.contracts.entity.contract;

import java.time.Instant;
import java.util.Optional;

public record ContractProgress(Optional<Instant> paidAt, Optional<ContractRegistration> registration) {
}
