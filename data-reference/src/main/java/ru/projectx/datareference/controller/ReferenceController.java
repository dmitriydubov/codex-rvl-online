package ru.projectx.datareference.controller;

import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.jspecify.annotations.Nullable;
import ru.projectx.datareference.dto.InsuranceContractResponse;
import ru.projectx.datareference.dto.LpuCardResponse;
import ru.projectx.datareference.dto.LpuCatalogResponse;
import ru.projectx.datareference.dto.LpuServicesResponse;
import ru.projectx.datareference.dto.Mkb10Response;
import ru.projectx.datareference.dto.PolicyResponse;
import ru.projectx.datareference.dto.PolicySearchResponse;
import ru.projectx.datareference.dto.ProgramResponse;
import ru.projectx.datareference.exception.InvalidRequestException;
import ru.projectx.datareference.service.LpuCatalogService;
import ru.projectx.datareference.service.Mkb10QueryService;
import ru.projectx.datareference.service.PolicySearchService;
import ru.projectx.datareference.service.ReferenceQueryService;

/**
 * REST-чтение справочников для сервисов Заявок и Договоров. Писать в справочники может
 * только 1С:УСК, поэтому ничего, кроме {@code GET}, здесь не будет.
 *
 * <p>Базовый путь {@code /internal/v1} — соглашение контура: внутренние ручки под
 * {@code /internal}, версия в пути, на внешнем периметре не публикуются. Аутентификации
 * нет: способ для контура не выбран ({@code ОВ-10}), доступ — сетевой политикой.
 * Ключи — по решению от 23.09: в пути всегда наш {@code id}, внешний ключ — параметром.
 *
 * <p>Логики в контроллере нет: он отдаёт то, что вернули сервисы, а отказы превращает
 * в единый формат {@link ApiExceptionHandler}. Единственное, что он решает сам, —
 * что запрос без ключа неверен: у Spring на такой путь нашёлся бы только «не найдено»,
 * а вызывающему нужно «нет параметра».
 */
@RestController
@RequestMapping(path = ReferenceController.BASE_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
public class ReferenceController {

    public static final String BASE_PATH = "/internal/v1";

    private final ReferenceQueryService references;
    private final PolicySearchService policySearch;
    private final Mkb10QueryService mkb10;
    private final LpuCatalogService lpuCatalog;

    public ReferenceController(
            ReferenceQueryService references,
            PolicySearchService policySearch,
            Mkb10QueryService mkb10,
            LpuCatalogService lpuCatalog) {

        this.references = references;
        this.policySearch = policySearch;
        this.mkb10 = mkb10;
        this.lpuCatalog = lpuCatalog;
    }

    /** R-01: каталог ЛПУ, доступных застрахованным договора страхования. */
    @GetMapping("/lpu")
    public LpuCatalogResponse lpuCatalog(@RequestParam("contract_external_id") UUID contractExternalId) {
        return lpuCatalog.catalog(contractExternalId);
    }

    /** R-02: карточка ЛПУ с прайсовым договором. */
    @GetMapping("/lpu/{id}")
    public LpuCardResponse lpu(@PathVariable UUID id) {
        return lpuCatalog.card(id);
    }

    /** R-03: услуги прайсового договора по видам обращения РВЛ, с ценами по запросу. */
    @GetMapping("/lpu/{id}/services")
    public LpuServicesResponse lpuServices(
            @PathVariable UUID id, @RequestParam(name = "need_prices", defaultValue = "false") boolean needPrices) {

        return lpuCatalog.services(id, needPrices);
    }

    /** R-09: программа с перечнем услуг, как в ТЗ сервиса Договоров. */
    @GetMapping("/programs/{id}")
    public ProgramResponse program(@PathVariable UUID id) {
        return references.program(id);
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

    /** R-07: подсказка по МКБ-10 — по коду или по наименованию. */
    @GetMapping(path = "/mkb10", params = "query")
    public Mkb10Response searchMkb10(
            @RequestParam String query, @RequestParam(required = false) @Nullable Integer limit) {

        return mkb10.search(query, limit);
    }

    /** R-07: раскрыть перечень кодов разом, вместе с архивными. */
    @GetMapping(path = "/mkb10", params = "codes")
    public Mkb10Response mkb10ByCodes(@RequestParam List<String> codes) {
        return mkb10.byCodes(codes);
    }

    @GetMapping("/mkb10")
    public Mkb10Response mkb10WithoutKey() {
        throw new InvalidRequestException("query", "нужен query либо codes");
    }
}
