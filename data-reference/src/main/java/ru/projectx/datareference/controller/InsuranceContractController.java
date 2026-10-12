package ru.projectx.datareference.controller;

import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import ru.projectx.datareference.dto.InsuranceContractResponse;
import ru.projectx.datareference.exception.InvalidRequestException;
import ru.projectx.datareference.service.InsuranceContractReads;

@InternalApi
public class InsuranceContractController {

    private final InsuranceContractReads insuranceContracts;

    public InsuranceContractController(InsuranceContractReads insuranceContracts) {
        this.insuranceContracts = insuranceContracts;
    }

    @GetMapping("/insurance-contracts/{id}")
    public InsuranceContractResponse insuranceContract(@PathVariable UUID id) {
        return insuranceContracts.byId(id);
    }

    @GetMapping(path = "/insurance-contracts", params = "external_id")
    public InsuranceContractResponse insuranceContractByExternalId(@RequestParam("external_id") UUID externalId) {
        return insuranceContracts.byExternalId(externalId);
    }

    @GetMapping("/insurance-contracts")
    public InsuranceContractResponse insuranceContractWithoutKey() {
        throw new InvalidRequestException("external_id", "нужен external_id");
    }
}
