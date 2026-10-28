package ru.projectx.contracts.entity.contract;

import java.math.BigDecimal;
import ru.projectx.contracts.entity.issuance.TripPeriod;

public record ContractTerms(TripPeriod period, BigDecimal premium) {
}
