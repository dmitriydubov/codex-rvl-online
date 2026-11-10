package ru.projectx.contracts.entity.payment;

import java.time.Instant;

public record InvoiceTerm(Instant startedAt, Instant deadline) {
}
