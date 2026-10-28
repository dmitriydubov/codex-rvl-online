package ru.projectx.contracts.service.issuance;

import ru.projectx.contracts.entity.issuance.DocumentKind;
import ru.projectx.contracts.entity.issuance.IssuanceRecord;

public record ActiveIssuance(DocumentKind kind, String name, IssuanceRecord record) {

    public boolean issued() {
        return record.status().issued();
    }
}
