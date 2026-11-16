package ru.projectx.datareference.controller;

import static ru.projectx.datareference.controller.PolicyNumberKey.CONTRACT_EXTERNAL_ID;
import static ru.projectx.datareference.controller.PolicyNumberKey.EXTERNAL_ID;
import static ru.projectx.datareference.controller.PolicyNumberKey.NUMBER;

import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import ru.projectx.datareference.dto.InsuredRelativesResponse;
import ru.projectx.datareference.exception.InvalidRequestException;
import ru.projectx.datareference.service.policy.InsuredRelativesReads;

@InternalApi
public class InsuredRelativesController {

    private static final String RELATIVES = "/policies/relatives";

    private final InsuredRelativesReads relatives;

    public InsuredRelativesController(InsuredRelativesReads relatives) {
        this.relatives = relatives;
    }

    @GetMapping(path = RELATIVES, params = {EXTERNAL_ID, "!" + NUMBER, "!" + CONTRACT_EXTERNAL_ID})
    public InsuredRelativesResponse relativesByExternalId(@RequestParam(EXTERNAL_ID) UUID externalId) {
        return relatives.ofPolicy(externalId);
    }

    @GetMapping(path = RELATIVES, params = "!" + EXTERNAL_ID)
    public InsuredRelativesResponse relativesByNumber(
            @RequestParam(required = false) @Nullable String number,
            @RequestParam(name = CONTRACT_EXTERNAL_ID, required = false) @Nullable UUID contractExternalId) {

        PolicyNumberKey key = PolicyNumberKey.of(number, contractExternalId);
        return relatives.ofPolicyNumber(key.number(), key.contractExternalId());
    }

    @GetMapping(path = RELATIVES, params = EXTERNAL_ID)
    public InsuredRelativesResponse relativesByConflictingKeys() {
        throw new InvalidRequestException(EXTERNAL_ID, PolicyNumberKey.CONFLICTING_KEYS);
    }
}
