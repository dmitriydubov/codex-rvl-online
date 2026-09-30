package ru.projectx.datareference.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import ru.projectx.datareference.dto.PolicyContractView;
import ru.projectx.datareference.dto.PolicyResponse;
import ru.projectx.datareference.dto.PolicySearchResponse;
import ru.projectx.datareference.service.PolicyContractViews;
import ru.projectx.datareference.service.PolicySearchService;
import ru.projectx.datareference.service.ReferenceQueryService;

/**
 * R-05: полис по нашему {@code id}, по {@code external_id}, по номеру в договоре и запрос
 * без ключа; R-04: подсказка среди полисов договора; полис для сервиса Договоров без ПДн
 * ({@code contract-view}) — по {@code id} и по {@code external_id}. ФИО и дата рождения
 * в фикстурах — заглушки, а не данные человека.
 */
@ExtendWith(MockitoExtension.class)
class PolicyControllerTest {

    private static final String POLICIES = InternalApi.BASE_PATH + "/policies";
    private static final UUID POLICY_ID = UUID.fromString("9011c000-0000-4000-8000-000000000001");
    private static final UUID POLICY_EXTERNAL_ID = UUID.fromString("9011c000-0000-4000-8000-000000000002");
    private static final UUID CONTRACT_ID = UUID.fromString("9011c000-0000-4000-8000-000000000003");
    private static final UUID CONTRACT_EXTERNAL_ID = UUID.fromString("9011c000-0000-4000-8000-000000000004");
    private static final String POLICY_NUMBER = "П-0001";
    /** Отказ самого контроллера называет оба ключа; у пропущенного параметра Spring причина другая. */
    private static final String NO_KEY_REASON = "нужен external_id либо number вместе с contract_id";
    /** У полиса для Договоров ключ по GUID 1С один — отказ называет только его. */
    private static final String NO_EXTERNAL_ID_REASON = "нужен external_id";
    /** Причина отказа Spring по пустому обязательному параметру ({@link ApiExceptionHandler}). */
    private static final String MISSING_VALUE_REASON = "обязательный параметр";

    @Mock
    private ReferenceQueryService references;

    @Mock
    private PolicySearchService policySearch;

    @Mock
    private PolicyContractViews contractViews;

    private MockMvcTester mvc;

    @BeforeEach
    void setUp() {
        mvc = StandaloneMvc.of(new PolicyController(references, policySearch, contractViews));
    }

    @Test
    void policyById_returnsPolicyFoundByService_asJson() {
        when(references.policy(POLICY_ID)).thenReturn(policy());

        MvcTestResult result = mvc.get().uri(POLICIES + "/{id}", POLICY_ID).exchange();

        assertThat(result).hasStatusOk().hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON);
        assertThat(result).bodyJson().extractingPath("$.id").isEqualTo(POLICY_ID.toString());
    }

    @Test
    void policyByExternalId_returnsPolicyFoundByService() {
        when(references.policyByExternalId(POLICY_EXTERNAL_ID)).thenReturn(policy());

        MvcTestResult result = mvc.get().uri(POLICIES + "?external_id={external}", POLICY_EXTERNAL_ID).exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.id").isEqualTo(POLICY_ID.toString());
    }

    @Test
    void policyByNumber_passesNumberAndContract() {
        when(references.policyByNumber(POLICY_NUMBER, CONTRACT_ID)).thenReturn(policy());

        MvcTestResult result = mvc.get()
                .uri(POLICIES + "?number={number}&contract_id={contract}", POLICY_NUMBER, CONTRACT_ID)
                .exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.id").isEqualTo(POLICY_ID.toString());
    }

    @Test
    void policyWithoutKey_isValidationErrorNamingExternalId() {
        MvcTestResult result = mvc.get().uri(POLICIES).exchange();

        assertThat(result).hasStatus(400);
        assertThat(result).bodyJson().extractingPath("$.error.code").isEqualTo("VALIDATION_ERROR");
        assertThat(result).bodyJson().extractingPath("$.error.details[0].field").isEqualTo("external_id");
        assertThat(result).bodyJson().extractingPath("$.error.details[0].reason").isEqualTo(NO_KEY_REASON);
        verifyNoInteractions(references, policySearch, contractViews);
    }

    @Test
    void numberWithoutContract_isNotALookupByNumber() {
        MvcTestResult result = mvc.get().uri(POLICIES + "?number={number}", POLICY_NUMBER).exchange();

        assertThat(result).hasStatus(400);
        assertThat(result).bodyJson().extractingPath("$.error.details[0].field").isEqualTo("external_id");
        assertThat(result).bodyJson().extractingPath("$.error.details[0].reason").isEqualTo(NO_KEY_REASON);
        verifyNoInteractions(references, policySearch, contractViews);
    }

    @Test
    void contractWithoutNumber_isNotALookupByNumber() {
        MvcTestResult result = mvc.get().uri(POLICIES + "?contract_id={contract}", CONTRACT_ID).exchange();

        assertThat(result).hasStatus(400);
        assertThat(result).bodyJson().extractingPath("$.error.details[0].field").isEqualTo("external_id");
        assertThat(result).bodyJson().extractingPath("$.error.details[0].reason").isEqualTo(NO_KEY_REASON);
        verifyNoInteractions(references, policySearch, contractViews);
    }

    @Test
    void search_passesContractQueryAndLimit_andReturnsFoundPolicies_asJson() {
        when(policySearch.search(CONTRACT_EXTERNAL_ID, "П-00", 5)).thenReturn(found());

        MvcTestResult result = mvc.get()
                .uri(POLICIES + "/search?contract_external_id={contract}&query={query}&limit={limit}",
                        CONTRACT_EXTERNAL_ID, "П-00", 5)
                .exchange();

        assertThat(result).hasStatusOk().hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON);
        assertThat(result).bodyJson().extractingPath("$.items[*].number").asArray().containsExactly(POLICY_NUMBER);
    }

    @Test
    void search_withoutLimit_leavesLimitToService() {
        when(policySearch.search(CONTRACT_EXTERNAL_ID, "П-00", null)).thenReturn(found());

        MvcTestResult result = mvc.get()
                .uri(POLICIES + "/search?contract_external_id={contract}&query={query}", CONTRACT_EXTERNAL_ID, "П-00")
                .exchange();

        assertThat(result).bodyJson().extractingPath("$.items[*].number").asArray().containsExactly(POLICY_NUMBER);
        verifyNoInteractions(references);
    }

    @Test
    void search_withoutContract_isValidationErrorNamingContract_withoutAskingService() {
        MvcTestResult result = mvc.get().uri(POLICIES + "/search?query={query}", "П-00").exchange();

        assertThat(result).hasStatus(400);
        assertThat(result).bodyJson().extractingPath("$.error.details[0].field").isEqualTo("contract_external_id");
        verifyNoInteractions(references, policySearch, contractViews);
    }

    @Test
    void search_withoutQuery_isValidationErrorNamingQuery_withoutAskingService() {
        MvcTestResult result = mvc.get()
                .uri(POLICIES + "/search?contract_external_id={contract}", CONTRACT_EXTERNAL_ID)
                .exchange();

        assertThat(result).hasStatus(400);
        assertThat(result).bodyJson().extractingPath("$.error.details[0].field").isEqualTo("query");
        verifyNoInteractions(references, policySearch, contractViews);
    }

    /** {@code produces} отсекает запрос ещё при выборе обработчика; тело 406 — забота Ш25. */
    @Test
    void policyById_acceptingOnlyXml_isNotAcceptable_withoutAskingService() {
        MvcTestResult result = mvc.get().uri(POLICIES + "/{id}", POLICY_ID).accept(MediaType.APPLICATION_XML)
                .exchange();

        assertThat(result).hasStatus(406);
        verifyNoInteractions(references, policySearch, contractViews);
    }

    @Test
    void contractViewById_returnsViewFoundByService_withoutAskingFullPolicy() {
        when(contractViews.byId(POLICY_ID)).thenReturn(contractView());

        MvcTestResult result = mvc.get().uri(POLICIES + "/{id}/contract-view", POLICY_ID).exchange();

        assertThat(result).hasStatusOk().hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON);
        assertThat(result).bodyJson().extractingPath("$.id").isEqualTo(POLICY_ID.toString());
        verifyNoInteractions(references, policySearch);
    }

    /** Литерал {@code contract-view} выигрывает у {@code /policies/{id}}: он не читается как id полиса R-05. */
    @Test
    void contractViewByExternalId_returnsViewFoundByService_withoutAskingFullPolicy() {
        when(contractViews.byExternalId(POLICY_EXTERNAL_ID)).thenReturn(contractView());

        MvcTestResult result = mvc.get()
                .uri(POLICIES + "/contract-view?external_id={external}", POLICY_EXTERNAL_ID)
                .exchange();

        assertThat(result).hasStatusOk().hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON);
        assertThat(result).bodyJson().extractingPath("$.id").isEqualTo(POLICY_ID.toString());
        verifyNoInteractions(references, policySearch);
    }

    @Test
    void contractViewWithoutKey_isValidationErrorNamingExternalId_withoutAskingService() {
        MvcTestResult result = mvc.get().uri(POLICIES + "/contract-view").exchange();

        assertThat(result).hasStatus(400);
        assertThat(result).bodyJson().extractingPath("$.error.code").isEqualTo("VALIDATION_ERROR");
        assertThat(result).bodyJson().extractingPath("$.error.details[0].field").isEqualTo("external_id");
        assertThat(result).bodyJson().extractingPath("$.error.details[0].reason").isEqualTo(NO_EXTERNAL_ID_REASON);
        verifyNoInteractions(references, policySearch, contractViews);
    }

    /**
     * Пустой {@code external_id} — это «ключ передан пустым», а не «ключа нет»: запрос выбирает ручку
     * с параметром, и отказ даёт Spring (пустая строка в UUID — {@code null}), а не ручка без ключа.
     */
    @Test
    void contractViewWithEmptyExternalId_isMissingValueOfExternalId_withoutAskingService() {
        MvcTestResult result = mvc.get().uri(POLICIES + "/contract-view?external_id=").exchange();

        assertThat(result).hasStatus(400);
        assertThat(result).bodyJson().extractingPath("$.error.details[0].field").isEqualTo("external_id");
        assertThat(result).bodyJson().extractingPath("$.error.details[0].reason").isEqualTo(MISSING_VALUE_REASON);
        verifyNoInteractions(references, policySearch, contractViews);
    }

    @Test
    void contractViewWithUnreadableId_isValidationErrorNamingId_withoutAskingService() {
        MvcTestResult result = mvc.get().uri(POLICIES + "/{id}/contract-view", "not-a-uuid").exchange();

        assertThat(result).hasStatus(400);
        assertThat(result).bodyJson().extractingPath("$.error.details[0].field").isEqualTo("id");
        verifyNoInteractions(references, policySearch, contractViews);
    }

    @Test
    void contractViewWithUnreadableExternalId_isValidationErrorNamingExternalId_withoutAskingService() {
        MvcTestResult result = mvc.get().uri(POLICIES + "/contract-view?external_id=not-a-uuid").exchange();

        assertThat(result).hasStatus(400);
        assertThat(result).bodyJson().extractingPath("$.error.details[0].field").isEqualTo("external_id");
        verifyNoInteractions(references, policySearch, contractViews);
    }

    private static PolicyContractView contractView() {
        return new PolicyContractView(POLICY_ID, POLICY_EXTERNAL_ID, POLICY_NUMBER, LocalDate.of(2026, 1, 1), null,
                false);
    }

    private static PolicyResponse policy() {
        return new PolicyResponse(POLICY_ID, POLICY_EXTERNAL_ID, POLICY_NUMBER, "Фамилия", "Имя", null,
                LocalDate.of(2000, 1, 1), null, null, LocalDate.of(2026, 1, 1), null, true, false, null, List.of());
    }

    private static PolicySearchResponse found() {
        return new PolicySearchResponse(List.of(
                new PolicySearchResponse.Item(POLICY_ID, POLICY_EXTERNAL_ID, POLICY_NUMBER, "Фамилия", "Имя", null)));
    }
}
