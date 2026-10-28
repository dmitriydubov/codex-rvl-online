package ru.projectx.contracts.entity;

import java.math.BigDecimal;
import ru.projectx.contracts.entity.issuance.TripPeriod;

public record ContractTerms(TripPeriod period, BigDecimal premium) {
}
