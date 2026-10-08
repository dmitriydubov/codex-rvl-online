package ru.projectx.datareference.service;

import java.util.Optional;
import java.util.UUID;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import ru.projectx.datareference.config.ReferenceCaches;
import ru.projectx.datareference.dto.InsuranceContractResponse;
import ru.projectx.datareference.entity.InsuranceContract;
import ru.projectx.datareference.entity.Insurer;
import ru.projectx.datareference.exception.ReferenceNotFoundException;
import ru.projectx.datareference.repository.InsuranceContractRepository;
import ru.projectx.datareference.repository.InsurerRepository;

/**
 * Читает договор страхования по нашему {@code id} или по GUID 1С вместе со страхователем. Страхователь связан
 * логически, без внешнего ключа, и его может не быть в справочнике, если это физлицо: тогда {@code insurer} пуст,
 * а договор всё равно отдаётся. Ответы по двум ключам кэшируются раздельно.
 */
@Service
public class InsuranceContractQueryService implements InsuranceContractReads {

    private final InsuranceContractRepository insuranceContracts;
    private final InsurerRepository insurers;

    public InsuranceContractQueryService(InsuranceContractRepository insuranceContracts, InsurerRepository insurers) {
        this.insuranceContracts = insuranceContracts;
        this.insurers = insurers;
    }

    @Override
    @Cacheable(ReferenceCaches.INSURANCE_CONTRACTS_BY_ID)
    @RestReadTransaction
    public InsuranceContractResponse byId(UUID id) {
        InsuranceContract contract = insuranceContracts.findById(id)
                .orElseThrow(() -> ReferenceNotFoundException.insuranceContract(id));
        return withInsurer(contract);
    }

    @Override
    @Cacheable(ReferenceCaches.INSURANCE_CONTRACTS_BY_EXTERNAL_ID)
    @RestReadTransaction
    public InsuranceContractResponse byExternalId(UUID externalId) {
        InsuranceContract contract = insuranceContracts.findByExternalId(externalId)
                .orElseThrow(() -> ReferenceNotFoundException.insuranceContractByExternalId(externalId));
        return withInsurer(contract);
    }

    private InsuranceContractResponse withInsurer(InsuranceContract contract) {
        return InsuranceContractResponse.of(contract, insurerOf(contract).orElse(null));
    }

    private Optional<Insurer> insurerOf(InsuranceContract contract) {
        return Optional.ofNullable(contract.externalInsurerId()).flatMap(insurers::findByExternalId);
    }
}
