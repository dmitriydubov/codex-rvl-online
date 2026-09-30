package ru.projectx.datareference.controller;

import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import ru.projectx.datareference.dto.InsuranceContractResponse;
import ru.projectx.datareference.exception.InvalidRequestException;
import ru.projectx.datareference.service.ReferenceQueryService;

/**
 * Договор страхования со страхователем (R-08) — по нашему {@code id} или по {@code external_id}.
 * Запрос без ключа — «нет параметра», а не «не найдено», как и у полисов.
 */
@InternalApi
public class InsuranceContractController {

    private final ReferenceQueryService references;

    public InsuranceContractController(ReferenceQueryService references) {
        this.references = references;
    }

    /** R-08: договор страхования со страхователем. */
    @GetMapping("/insurance-contracts/{id}")
    public InsuranceContractResponse insuranceContract(@PathVariable UUID id) {
        return references.insuranceContract(id);
    }

    @GetMapping(path = "/insurance-contracts", params = "external_id")
    public InsuranceContractResponse insuranceContractByExternalId(@RequestParam("external_id") UUID externalId) {
        return references.insuranceContractByExternalId(externalId);
    }

    @GetMapping("/insurance-contracts")
    public InsuranceContractResponse insuranceContractWithoutKey() {
        throw new InvalidRequestException("external_id", "нужен external_id");
    }
}
