package ru.projectx.contracts.service;

import ru.projectx.contracts.entity.DocumentKind;
import ru.projectx.contracts.entity.IssuanceRecord;

public record ActiveIssuance(DocumentKind kind, IssuanceRecord record) {
}
