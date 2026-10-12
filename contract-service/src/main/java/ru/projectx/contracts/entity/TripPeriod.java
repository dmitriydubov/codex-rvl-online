package ru.projectx.contracts.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.time.LocalDate;

@Embeddable
public record TripPeriod(
        @Column(name = "date_from", nullable = false) LocalDate dateFrom,
        @Column(name = "date_to", nullable = false) LocalDate dateTo) {
}
