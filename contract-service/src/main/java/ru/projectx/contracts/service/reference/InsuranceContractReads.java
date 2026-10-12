package ru.projectx.contracts.service.reference;

import java.util.UUID;
import ru.projectx.contracts.dto.ReferenceInsuranceContract;

public interface InsuranceContractReads {

    ReferenceInsuranceContract insuranceContract(UUID id);
}
