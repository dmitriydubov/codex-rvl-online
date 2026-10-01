package ru.projectx.datareference.service;

import java.util.UUID;
import ru.projectx.datareference.dto.InsuranceContractResponse;

/** Договор страхования по нашему {@code id} или по GUID 1С — то, что нужно контроллеру договоров. */
public interface InsuranceContractReads {

    InsuranceContractResponse byId(UUID id);

    InsuranceContractResponse byExternalId(UUID externalId);
}
