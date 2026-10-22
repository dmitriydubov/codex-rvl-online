package ru.projectx.contracts.controller;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import ru.projectx.contracts.dto.AcceptanceRequest;
import ru.projectx.contracts.dto.AcceptanceResponse;
import ru.projectx.contracts.service.acceptance.OfferAcceptances;

@InternalApi
public class ContractAcceptanceController {

    private final OfferAcceptances acceptances;

    public ContractAcceptanceController(OfferAcceptances acceptances) {
        this.acceptances = acceptances;
    }

    @PostMapping(path = "/contracts/{contract_id}/acceptance", consumes = MediaType.APPLICATION_JSON_VALUE)
    public AcceptanceResponse accept(@PathVariable("contract_id") UUID contractId,
            @Valid @RequestBody AcceptanceRequest request) {
        return acceptances.accept(contractId, request);
    }
}
