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
 * Полисы: подсказка среди действующих полисов договора, полис целиком и полис для сервиса Договоров без
 * персональных данных ({@code contract-view}). Полис целиком читается по нашему {@code id}, по GUID 1С:УСК
 * ({@code external_id}) или по номеру вместе с договором.
 *
 * <p>Контроллер сам решает только одно: каким ключом прочитан запрос. Ручка каждого ключа принимает запрос,
 * только если ключа другого способа в нём нет. Иначе Spring молча выбрал бы ручку с большим числом совпавших
 * параметров и прочитал полис по одному из ключей. Пустое значение ключа тоже считается переданным ключом.
 * Запрос без ключа и запрос, где рядом с {@code external_id} стоит номер или договор, отклоняются ошибкой
 * вызывающего с полем {@code external_id}, и у каждого своя ручка со своей причиной отказа. Без ручки на запрос
 * без ключа Spring отказал бы исключением о несовпавших параметрах, которого единый формат ошибки не знает.
 *
 * <p>У {@code /policies/contract-view} своя ручка без ключа по той же причине: без неё запрос ушёл бы в
 * {@code /policies/{id}} и получил бы отказ по полю {@code id}, а вызывающему нужно «нет параметра
 * {@code external_id}». Пути {@code /policies/contract-view} и {@code /policies/search} не читаются как
 * {@code /policies/{id}}: Spring сначала ищет ручку с точно таким путём без шаблонов и только без неё
 * перебирает шаблоны.
 */
@InternalApi
public class PolicyController {

    private static final String EXTERNAL_ID = "external_id";
    private static final String NUMBER = "number";
    private static final String CONTRACT_ID = "contract_id";

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

    @GetMapping(path = "/policies", params = {EXTERNAL_ID, "!" + NUMBER, "!" + CONTRACT_ID})
    public PolicyResponse policyByExternalId(@RequestParam(EXTERNAL_ID) UUID externalId) {
        return policies.byExternalId(externalId);
    }

    @GetMapping(path = "/policies", params = {NUMBER, CONTRACT_ID, "!" + EXTERNAL_ID})
    public PolicyResponse policyByNumber(@RequestParam String number, @RequestParam(CONTRACT_ID) UUID contractId) {
        return policies.byNumber(number, contractId);
    }

    /** Сюда попадает запрос с {@code external_id}, которого не приняла ручка этого ключа: рядом номер или договор. */
    @GetMapping(path = "/policies", params = EXTERNAL_ID)
    public PolicyResponse policyByConflictingKeys() {
        throw new InvalidRequestException(EXTERNAL_ID, "external_id не передаётся вместе с number или contract_id");
    }

    @GetMapping("/policies")
    public PolicyResponse policyWithoutKey() {
        throw new InvalidRequestException(EXTERNAL_ID, "нужен external_id либо number вместе с contract_id");
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

    /** Ключ по GUID 1С у полиса для Договоров один, поэтому отказ называет только его. */
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
