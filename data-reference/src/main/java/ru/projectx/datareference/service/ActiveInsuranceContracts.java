package ru.projectx.datareference.service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import ru.projectx.datareference.entity.InsuranceContract;
import ru.projectx.datareference.exception.AmbiguousReferenceException;
import ru.projectx.datareference.exception.MissingReference;
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
                .orElseThrow(() -> MissingReference.INSURANCE_CONTRACT_BY_EXTERNAL_ID.notFound(contractExternalId));
    }

    InsuranceContract soleActiveOf(UUID insurerExternalId, LocalDate today) {
        List<InsuranceContract> active = insuranceContracts.findAllByExternalInsurerId(insurerExternalId).stream()
                .filter(contract -> contract.isActiveOn(today))
                .toList();
        if (active.size() > 1) {
            throw AmbiguousReferenceException.insuranceContract(insurerExternalId,
                    active.stream().map(InsuranceContract::id).toList());
        }
        return active.stream().findFirst()
                .orElseThrow(() -> MissingReference.ACTIVE_INSURANCE_CONTRACT_OF_INSURER.notFound(insurerExternalId));
    }
}
