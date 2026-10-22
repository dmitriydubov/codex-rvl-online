package ru.projectx.contracts.dto;

import java.time.Instant;
import java.util.UUID;

public record AcceptanceResponse(UUID contractId, DocumentState state, Instant acceptedAt) {
}
