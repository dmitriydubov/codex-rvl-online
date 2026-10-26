package ru.projectx.contracts.service.inbox;

public record UskResultLayout(
        String issuedStatus,
        String uskIdField,
        String uskNumberField,
        FieldPresence uskNumber,
        FieldPresence documents) {
}
