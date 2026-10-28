package ru.projectx.contracts.service;

import java.time.Instant;
import org.springframework.stereotype.Component;
import ru.projectx.contracts.config.ContractNumberingProperties;
import ru.projectx.contracts.repository.issuance.ContractNumberRepository;

@Component
public class ContractNumbers {

    private static final String FORMAT = "DKM-%d-%06d";

    private final ContractNumberRepository counters;
    private final ContractNumberingProperties properties;

    public ContractNumbers(ContractNumberRepository counters, ContractNumberingProperties properties) {
        this.counters = counters;
        this.properties = properties;
    }

    public String next(Instant now) {
        int year = now.atZone(properties.zone()).getYear();
        return String.format(FORMAT, year, counters.nextNumber(year));
    }
}
