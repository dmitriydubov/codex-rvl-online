package ru.projectx.contracts.service;

import ru.projectx.contracts.entity.IssuanceRecord;

public record ActiveIssuance(IssuanceKind kind, IssuanceRecord record) {
}
