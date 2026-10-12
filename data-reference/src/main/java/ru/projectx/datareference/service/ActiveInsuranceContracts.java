package ru.projectx.datareference.service;

import java.time.LocalDate;
import java.util.UUID;
import org.springframework.stereotype.Component;
import ru.projectx.datareference.exception.ReferenceNotFoundException;
import ru.projectx.datareference.repository.InsuranceContractRepository;

@Component
class ActiveInsuranceContracts {

    private final InsuranceContractRepository insuranceContracts;

    ActiveInsuranceContracts(InsuranceContractRepository insuranceContracts) {
        this.insuranceContracts = insuranceContracts;
    }

    void requireActive(UUID contractExternalId, LocalDate today) {
        insuranceContracts.findByExternalId(contractExternalId)
                .filter(contract -> contract.isActiveOn(today))
                .orElseThrow(() -> ReferenceNotFoundException.insuranceContractByExternalId(contractExternalId));
    }
}
