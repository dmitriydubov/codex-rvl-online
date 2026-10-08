package ru.projectx.datareference.service;

import java.util.UUID;
import ru.projectx.datareference.dto.InsuranceContractResponse;

/** Чтение договора страхования по нашему {@code id} или по GUID 1С для контроллера договоров. */
public interface InsuranceContractReads {

    InsuranceContractResponse byId(UUID id);

    InsuranceContractResponse byExternalId(UUID externalId);
}
