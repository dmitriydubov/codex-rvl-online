package ru.projectx.datareference.service;

import java.util.UUID;
import ru.projectx.datareference.dto.InsuranceContractResponse;

public interface InsuranceContractReads {

    InsuranceContractResponse byId(UUID id);

    InsuranceContractResponse byExternalId(UUID externalId);
}
