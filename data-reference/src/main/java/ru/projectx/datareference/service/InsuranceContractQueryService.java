package ru.projectx.datareference.service;

import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import ru.projectx.datareference.dto.InsuranceContractResponse;
import ru.projectx.datareference.entity.InsuranceContract;
import ru.projectx.datareference.entity.Insurer;
import ru.projectx.datareference.exception.ReferenceNotFoundException;
import ru.projectx.datareference.repository.InsuranceContractRepository;
import ru.projectx.datareference.repository.InsurerRepository;

/**
 * Договор страхования по нашему {@code id} или по GUID 1С: номер, период,
 * признак архива и страхователь. Транзакция чтения здесь, ответ собирается внутри неё.
 *
 * <p>Страхователь подтягивается вторым запросом по {@code external_insurer_id}: связь
 * логическая, без FK (ТЗ-2), и страхователя в справочнике может не быть вовсе — им бывает
 * физлицо. Тогда {@code insurer} в ответе пуст, а договор всё равно отдаётся.
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
    @RestReadTransaction
    public InsuranceContractResponse byId(UUID id) {
        InsuranceContract contract = insuranceContracts.findById(id)
                .orElseThrow(() -> ReferenceNotFoundException.insuranceContract(id));
        return withInsurer(contract);
    }

    @Override
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
