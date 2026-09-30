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
import ru.projectx.datareference.dto.PolicyResponse;
import ru.projectx.datareference.dto.PolicySearchResponse;
import ru.projectx.datareference.service.PolicySearchService;
import ru.projectx.datareference.service.ReferenceQueryService;

/**
 * R-05: полис по нашему {@code id}, по {@code external_id}, по номеру в договоре и запрос
 * без ключа; R-04: подсказка среди полисов договора. ФИО и дата рождения в фикстурах —
 * заглушки, а не данные человека.
 */
@ExtendWith(MockitoExtension.class)
class PolicyControllerTest {

    private static final String POLICIES = InternalApi.BASE_PATH + "/policies";
    private static final UUID POLICY_ID = UUID.fromString("9011c000-0000-4000-8000-000000000001");
    private static final UUID POLICY_EXTERNAL_ID = UUID.fromString("9011c000-0000-4000-8000-000000000002");
    private static final UUID CONTRACT_ID = UUID.fromString("9011c000-0000-4000-8000-000000000003");
    private static final UUID CONTRACT_EXTERNAL_ID = UUID.fromString("9011c000-0000-4000-8000-000000000004");
    private static final String POLICY_NUMBER = "П-0001";

    @Mock
    private ReferenceQueryService references;

    @Mock
    private PolicySearchService policySearch;

    private MockMvcTester mvc;

    @BeforeEach
    void setUp() {
        mvc = StandaloneMvc.of(new PolicyController(references, policySearch));
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
        verifyNoInteractions(references, policySearch);
    }

    @Test
    void numberWithoutContract_isNotALookupByNumber() {
        MvcTestResult result = mvc.get().uri(POLICIES + "?number={number}", POLICY_NUMBER).exchange();

        assertThat(result).hasStatus(400);
        assertThat(result).bodyJson().extractingPath("$.error.details[0].field").isEqualTo("external_id");
        verifyNoInteractions(references, policySearch);
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

    private static PolicyResponse policy() {
        return new PolicyResponse(POLICY_ID, POLICY_EXTERNAL_ID, POLICY_NUMBER, "Фамилия", "Имя", null,
                LocalDate.of(2000, 1, 1), null, null, LocalDate.of(2026, 1, 1), null, true, false, null, List.of());
    }

    private static PolicySearchResponse found() {
        return new PolicySearchResponse(List.of(
                new PolicySearchResponse.Item(POLICY_ID, POLICY_EXTERNAL_ID, POLICY_NUMBER, "Фамилия", "Имя", null)));
    }
}
