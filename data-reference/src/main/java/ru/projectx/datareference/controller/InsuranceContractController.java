package ru.projectx.datareference.controller;

import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import ru.projectx.datareference.dto.InsuranceContractResponse;
import ru.projectx.datareference.exception.InvalidRequestException;
import ru.projectx.datareference.service.insurancecontract.InsuranceContractReads;

@InternalApi
public class InsuranceContractController {

    private static final String EXTERNAL_ID = "external_id";
    private static final String INSURER_EXTERNAL_ID = "insurer_external_id";

    private final InsuranceContractReads insuranceContracts;

    public InsuranceContractController(InsuranceContractReads insuranceContracts) {
        this.insuranceContracts = insuranceContracts;
    }

    @GetMapping("/insurance-contracts/{id}")
    public InsuranceContractResponse insuranceContract(@PathVariable UUID id) {
        return insuranceContracts.byId(id);
    }

    @GetMapping(path = "/insurance-contracts", params = {EXTERNAL_ID, "!" + INSURER_EXTERNAL_ID})
    public InsuranceContractResponse insuranceContractByExternalId(@RequestParam(EXTERNAL_ID) UUID externalId) {
        return insuranceContracts.byExternalId(externalId);
    }

    @GetMapping(path = "/insurance-contracts", params = {INSURER_EXTERNAL_ID, "!" + EXTERNAL_ID})
    public InsuranceContractResponse activeInsuranceContractOfInsurer(
            @RequestParam(INSURER_EXTERNAL_ID) UUID insurerExternalId) {

        return insuranceContracts.activeByInsurer(insurerExternalId);
    }

    @GetMapping(path = "/insurance-contracts", params = {EXTERNAL_ID, INSURER_EXTERNAL_ID})
    public InsuranceContractResponse insuranceContractByConflictingKeys() {
        throw new InvalidRequestException(EXTERNAL_ID, "external_id не передаётся вместе с insurer_external_id");
    }

    @GetMapping("/insurance-contracts")
    public InsuranceContractResponse insuranceContractWithoutKey() {
        throw new InvalidRequestException(EXTERNAL_ID, "нужен external_id либо insurer_external_id");
    }
}
