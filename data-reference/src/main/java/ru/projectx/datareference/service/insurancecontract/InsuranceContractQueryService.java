package ru.projectx.datareference.service.insurancecontract;

import java.util.Optional;
import java.util.UUID;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import ru.projectx.datareference.config.ReferenceCaches;
import ru.projectx.datareference.dto.InsuranceContractResponse;
import ru.projectx.datareference.entity.InsuranceContract;
import ru.projectx.datareference.entity.Insurer;
import ru.projectx.datareference.exception.MissingReference;
import ru.projectx.datareference.repository.InsuranceContractRepository;
import ru.projectx.datareference.repository.InsurerRepository;
import ru.projectx.datareference.service.rest.ReferenceCalendar;
import ru.projectx.datareference.service.rest.RestReadTransaction;

@Service
public class InsuranceContractQueryService implements InsuranceContractReads {

    private final InsuranceContractRepository insuranceContracts;
    private final InsurerRepository insurers;
    private final ActiveInsuranceContracts activeContracts;
    private final ReferenceCalendar calendar;

    public InsuranceContractQueryService(
            InsuranceContractRepository insuranceContracts,
            InsurerRepository insurers,
            ActiveInsuranceContracts activeContracts,
            ReferenceCalendar calendar) {

        this.insuranceContracts = insuranceContracts;
        this.insurers = insurers;
        this.activeContracts = activeContracts;
        this.calendar = calendar;
    }

    @Override
    @Cacheable(ReferenceCaches.INSURANCE_CONTRACTS_BY_ID)
    @RestReadTransaction
    public InsuranceContractResponse byId(UUID id) {
        InsuranceContract contract = insuranceContracts.findById(id)
                .orElseThrow(() -> MissingReference.INSURANCE_CONTRACT.notFound(id));
        return withInsurer(contract);
    }

    @Override
    @Cacheable(ReferenceCaches.INSURANCE_CONTRACTS_BY_EXTERNAL_ID)
    @RestReadTransaction
    public InsuranceContractResponse byExternalId(UUID externalId) {
        InsuranceContract contract = insuranceContracts.findByExternalId(externalId)
                .orElseThrow(() -> MissingReference.INSURANCE_CONTRACT_BY_EXTERNAL_ID.notFound(externalId));
        return withInsurer(contract);
    }

    @Override
    @Cacheable(cacheNames = ReferenceCaches.ACTIVE_INSURANCE_CONTRACTS_BY_INSURER,
            keyGenerator = ReferenceCaches.TODAY_KEY)
    @RestReadTransaction
    public InsuranceContractResponse activeByInsurer(UUID insurerExternalId) {
        return withInsurer(activeContracts.soleActiveOf(insurerExternalId, calendar.today()));
    }

    private InsuranceContractResponse withInsurer(InsuranceContract contract) {
        return InsuranceContractResponse.of(contract, insurerOf(contract).orElse(null));
    }

    private Optional<Insurer> insurerOf(InsuranceContract contract) {
        return Optional.ofNullable(contract.externalInsurerId()).flatMap(insurers::findByExternalId);
    }
}
