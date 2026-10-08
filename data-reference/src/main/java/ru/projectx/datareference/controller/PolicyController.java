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
 * Полисы: подсказка по договору, полис целиком по {@code id}, GUID 1С:УСК или номеру с договором и полис без
 * персональных данных для сервиса Договоров. Ручка каждого ключа принимает запрос, только если других ключей нет, а
 * каждый неверный набор ключей получает свою ручку с отказом по нужному полю; новую ручку сверяйте со всеми наборами.
 */
@InternalApi
public class PolicyController {

    private static final String EXTERNAL_ID = "external_id";
    private static final String NUMBER = "number";
    private static final String CONTRACT_EXTERNAL_ID = "contract_external_id";
    private static final String LEGACY_CONTRACT_ID = "contract_id";

    private final PolicyReads policies;
    private final PolicySearchReads policySearch;
    private final PolicyContractViews contractViews;

    public PolicyController(
            PolicyReads policies, PolicySearchReads policySearch, PolicyContractViews contractViews) {

        this.policies = policies;
        this.policySearch = policySearch;
        this.contractViews = contractViews;
    }

    /** Полис целиком по нашему {@code id}. */
    @GetMapping("/policies/{id}")
    public PolicyResponse policy(@PathVariable UUID id) {
        return policies.byId(id);
    }

    @GetMapping(path = "/policies",
            params = {EXTERNAL_ID, "!" + NUMBER, "!" + CONTRACT_EXTERNAL_ID, "!" + LEGACY_CONTRACT_ID})
    public PolicyResponse policyByExternalId(@RequestParam(EXTERNAL_ID) UUID externalId) {
        return policies.byExternalId(externalId);
    }

    /**
     * Номер без договора тоже попадает сюда, и отказ называет обязательный {@code contract_external_id}. Пустой номер
     * отклоняется до поиска в базе, как и пустой GUID, а не превращается в «полиса нет».
     */
    @GetMapping(path = "/policies", params = {NUMBER, "!" + EXTERNAL_ID, "!" + LEGACY_CONTRACT_ID})
    public PolicyResponse policyByNumber(
            @RequestParam String number, @RequestParam(CONTRACT_EXTERNAL_ID) UUID contractExternalId) {

        if (number.isBlank()) {
            throw new InvalidRequestException(NUMBER, RequestErrorAdvice.REQUIRED_PARAMETER);
        }
        return policies.byNumber(number, contractExternalId);
    }

    @GetMapping(path = "/policies",
            params = {CONTRACT_EXTERNAL_ID, "!" + NUMBER, "!" + EXTERNAL_ID, "!" + LEGACY_CONTRACT_ID})
    public PolicyResponse policyByContractWithoutNumber() {
        throw new InvalidRequestException(NUMBER, RequestErrorAdvice.REQUIRED_PARAMETER);
    }

    /** Сюда попадает запрос с {@code external_id}, которого не приняла ручка этого ключа: рядом номер или договор. */
    @GetMapping(path = "/policies", params = EXTERNAL_ID)
    public PolicyResponse policyByConflictingKeys() {
        throw new InvalidRequestException(EXTERNAL_ID,
                "external_id не передаётся вместе с number или contract_external_id");
    }

    @GetMapping(path = "/policies", params = {LEGACY_CONTRACT_ID, "!" + EXTERNAL_ID})
    public PolicyResponse policyByLegacyContractId() {
        throw new InvalidRequestException(LEGACY_CONTRACT_ID,
                "не принимается: договор передаётся параметром contract_external_id");
    }

    @GetMapping("/policies")
    public PolicyResponse policyWithoutKey() {
        throw new InvalidRequestException(EXTERNAL_ID, "нужен external_id либо number вместе с contract_external_id");
    }

    /** Полис для сервиса Договоров по нашему {@code id}, без персональных данных. */
    @GetMapping("/policies/{id}/contract-view")
    public PolicyContractView contractView(@PathVariable UUID id) {
        return contractViews.byId(id);
    }

    /** Полис для сервиса Договоров по GUID 1С:УСК, без персональных данных. */
    @GetMapping(path = "/policies/contract-view", params = EXTERNAL_ID)
    public PolicyContractView contractViewByExternalId(@RequestParam(EXTERNAL_ID) UUID externalId) {
        return contractViews.byExternalId(externalId);
    }

    /** Без этой ручки запрос попал бы в {@code /policies/{id}} и получил отказ по полю {@code id}. */
    @GetMapping("/policies/contract-view")
    public PolicyContractView contractViewWithoutKey() {
        throw new InvalidRequestException(EXTERNAL_ID, "нужен external_id");
    }

    /** Подсказка по части ФИО или номера среди действующих полисов договора. */
    @GetMapping("/policies/search")
    public PolicySearchResponse searchPolicies(
            @RequestParam("contract_external_id") UUID contractExternalId,
            @RequestParam String query,
            @RequestParam(required = false) @Nullable Integer limit) {

        return policySearch.search(contractExternalId, query, limit);
    }
}
