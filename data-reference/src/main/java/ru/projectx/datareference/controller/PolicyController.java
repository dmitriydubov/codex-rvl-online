package ru.projectx.datareference.controller;

import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import ru.projectx.datareference.dto.PolicyContractView;
import ru.projectx.datareference.dto.PolicyResponse;
import ru.projectx.datareference.dto.PolicySearchResponse;
import ru.projectx.datareference.exception.InvalidRequestException;
import ru.projectx.datareference.service.PolicyContractViews;
import ru.projectx.datareference.service.PolicySearchService;
import ru.projectx.datareference.service.ReferenceQueryService;

/**
 * Полисы: подсказка среди действующих полисов договора (R-04), полис целиком (R-05) —
 * по нашему {@code id}, по {@code external_id} или по номеру в договоре, — и полис для сервиса
 * Договоров без персональных данных ({@code contract-view}, решение Р20 (а)).
 *
 * <p>Единственное, что контроллер решает сам, — что запрос без ключа неверен: у Spring
 * на такой путь нашёлся бы только «не найдено», а вызывающему нужно «нет параметра».
 * Литерал {@code contract-view} Spring предпочитает переменной {@code {id}}, как и {@code search}:
 * путь без переменных специфичнее.
 */
@InternalApi
public class PolicyController {

    private final ReferenceQueryService references;
    private final PolicySearchService policySearch;
    private final PolicyContractViews contractViews;

    public PolicyController(
            ReferenceQueryService references, PolicySearchService policySearch, PolicyContractViews contractViews) {

        this.references = references;
        this.policySearch = policySearch;
        this.contractViews = contractViews;
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

    /** Полис для сервиса Договоров по нашему {@code id}: без ПДн. */
    @GetMapping("/policies/{id}/contract-view")
    public PolicyContractView contractView(@PathVariable UUID id) {
        return contractViews.byId(id);
    }

    /** Полис для сервиса Договоров по GUID 1С:УСК: без ПДн. */
    @GetMapping(path = "/policies/contract-view", params = "external_id")
    public PolicyContractView contractViewByExternalId(@RequestParam("external_id") UUID externalId) {
        return contractViews.byExternalId(externalId);
    }

    /** Ключ по GUID 1С у полиса для Договоров один, поэтому отказ называет только его. */
    @GetMapping("/policies/contract-view")
    public PolicyContractView contractViewWithoutKey() {
        throw new InvalidRequestException("external_id", "нужен external_id");
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
