package ru.projectx.datareference.repository;

import java.time.LocalDate;
import java.util.UUID;

public record PolicyScope(UUID contractExternalId, LocalDate today, int limit) {
}
