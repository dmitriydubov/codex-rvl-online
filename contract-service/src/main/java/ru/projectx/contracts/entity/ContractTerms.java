package ru.projectx.contracts.entity;

import java.math.BigDecimal;

public record ContractTerms(TripPeriod period, BigDecimal premium) {
}
