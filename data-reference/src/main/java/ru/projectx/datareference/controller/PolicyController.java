package ru.projectx.datareference.controller;

import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import ru.projectx.datareference.dto.PolicyResponse;
import ru.projectx.datareference.dto.PolicySearchResponse;
import ru.projectx.datareference.exception.InvalidRequestException;
import ru.projectx.datareference.service.PolicySearchService;
import ru.projectx.datareference.service.ReferenceQueryService;

/**
 * Полисы: подсказка среди действующих полисов договора (R-04) и полис целиком (R-05) —
 * по нашему {@code id}, по {@code external_id} или по номеру в договоре.
 *
 * <p>Единственное, что контроллер решает сам, — что запрос без ключа неверен: у Spring
 * на такой путь нашёлся бы только «не найдено», а вызывающему нужно «нет параметра».
 */
@InternalApi
public class PolicyController {

    private final ReferenceQueryService references;
    private final PolicySearchService policySearch;

    public PolicyController(ReferenceQueryService references, PolicySearchService policySearch) {
        this.references = references;
        this.policySearch = policySearch;
    }

    /** R-05: полис целиком. */
    @GetMapping("/policies/{id}")
    public PolicyResponse policy(@PathVariable UUID id) {
        return references.policy(id);
    }

    @GetMapping(path = "/policies", params = "external_id")
    public PolicyResponse policyByExternalId(@RequestParam("external_id") UUID externalId) {
        return references.policyByExternalId(externalId);
    }

    @GetMapping(path = "/policies", params = {"number", "contract_id"})
    public PolicyResponse policyByNumber(@RequestParam String number, @RequestParam("contract_id") UUID contractId) {
        return references.policyByNumber(number, contractId);
    }

    @GetMapping("/policies")
    public PolicyResponse policyWithoutKey() {
        throw new InvalidRequestException("external_id", "нужен external_id либо number вместе с contract_id");
    }

    /** R-04: подсказка по части ФИО или номера среди действующих полисов договора. */
    @GetMapping("/policies/search")
    public PolicySearchResponse searchPolicies(
            @RequestParam("contract_external_id") UUID contractExternalId,
            @RequestParam String query,
            @RequestParam(required = false) @Nullable Integer limit) {

        return policySearch.search(contractExternalId, query, limit);
    }
}
