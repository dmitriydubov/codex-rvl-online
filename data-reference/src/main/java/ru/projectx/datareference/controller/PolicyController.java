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
import ru.projectx.datareference.service.PolicySearchReads;
import ru.projectx.datareference.service.PolicyReads;

/**
 * Полисы: подсказка среди действующих полисов договора (R-04), полис целиком (R-05) —
 * по нашему {@code id}, по {@code external_id} или по номеру в договоре, — и полис для сервиса
 * Договоров без персональных данных ({@code contract-view}, решение Р20 (а)).
 *
 * <p>Единственное, что контроллер решает сам, — что запрос без ключа неверен, и говорит об этом
 * сам на обоих путях: без своей ручки на {@code /policies} Spring отказал бы исключением о несовпавших
 * параметрах ({@code UnsatisfiedServletRequestParameterException}), которого единый формат ошибки
 * не знает, — вышло бы 500; а {@code /policies/contract-view} ушёл бы в {@code /policies/{id}}
 * и получил бы отказ по полю {@code id}. Вызывающему же нужно «нет параметра {@code external_id}». Путь
 * {@code /policies/contract-view}, как и {@code search}, не читается как {@code /policies/{id}}:
 * Spring сначала ищет обработчик с точно таким путём без шаблонов и только без него перебирает шаблоны.
 */
@InternalApi
public class PolicyController {

    private final PolicyReads policies;
    private final PolicySearchReads policySearch;
    private final PolicyContractViews contractViews;

    public PolicyController(
            PolicyReads policies, PolicySearchReads policySearch, PolicyContractViews contractViews) {

        this.policies = policies;
        this.policySearch = policySearch;
        this.contractViews = contractViews;
    }

    /** R-05: полис целиком. */
    @GetMapping("/policies/{id}")
    public PolicyResponse policy(@PathVariable UUID id) {
        return policies.byId(id);
    }

    @GetMapping(path = "/policies", params = "external_id")
    public PolicyResponse policyByExternalId(@RequestParam("external_id") UUID externalId) {
        return policies.byExternalId(externalId);
    }

    @GetMapping(path = "/policies", params = {"number", "contract_id"})
    public PolicyResponse policyByNumber(@RequestParam String number, @RequestParam("contract_id") UUID contractId) {
        return policies.byNumber(number, contractId);
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
