package ru.projectx.contracts.service.inbox;

import ru.projectx.contracts.service.inbox.parse.FieldPresence;

public record UskResultLayout(
        String issuedStatus,
        String uskIdField,
        String uskNumberField,
        FieldPresence uskNumber,
        FieldPresence documents) {
}
