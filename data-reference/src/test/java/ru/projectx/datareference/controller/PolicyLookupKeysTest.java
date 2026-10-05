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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import ru.projectx.datareference.dto.PolicyResponse;
import ru.projectx.datareference.service.PolicyContractViews;
import ru.projectx.datareference.service.PolicyReads;
import ru.projectx.datareference.service.PolicySearchReads;

/**
 * Полис читается ровно одним ключом: по GUID 1С:УСК ({@code external_id}) либо по номеру вместе с GUID договора
 * страхования ({@code contract_external_id}). Запрос, где рядом с {@code external_id} стоит номер или договор,
 * неоднозначен. Он отклоняется целиком с нарушением по полю {@code external_id}, а не читается молча по одному из
 * ключей. Ключ с пустым значением тоже считается переданным, и значения ключей при конфликте не разбираются.
 * Прежний ключ договора {@code contract_id} (наш id) сервис больше не принимает и называет его в отказе: иначе
 * вызывающий со старым ключом получил бы непонятный отказ по другому полю.
 */
@ExtendWith(MockitoExtension.class)
class PolicyLookupKeysTest {

    private static final String POLICIES = InternalApi.BASE_PATH + "/policies";
    private static final UUID POLICY_ID = UUID.fromString("9011c000-0000-4000-8000-000000000011");
    private static final UUID POLICY_EXTERNAL_ID = UUID.fromString("9011c000-0000-4000-8000-000000000012");
    private static final UUID CONTRACT_EXTERNAL_ID = UUID.fromString("9011c000-0000-4000-8000-000000000013");
    private static final String POLICY_NUMBER = "П-0001";
    private static final String CONFLICT_REASON = "external_id не передаётся вместе с number или contract_external_id";
    private static final String LEGACY_KEY_REASON = "не принимается: договор передаётся параметром contract_external_id";

    @Mock
    private PolicyReads policies;

    @Mock
    private PolicySearchReads policySearch;

    @Mock
    private PolicyContractViews contractViews;

    private MockMvcTester mvc;

    @BeforeEach
    void setUp() {
        mvc = StandaloneMvc.of(new PolicyController(policies, policySearch, contractViews));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "?external_id=9011c000-0000-4000-8000-000000000012&number=П-0001&contract_external_id=9011c000-0000-4000-8000-000000000013",
        "?external_id=9011c000-0000-4000-8000-000000000012&number=П-0001",
        "?external_id=9011c000-0000-4000-8000-000000000012&contract_external_id=9011c000-0000-4000-8000-000000000013",
        "?external_id=9011c000-0000-4000-8000-000000000012&contract_id=9011c000-0000-4000-8000-000000000013",
        "?external_id=&number=П-0001&contract_external_id=9011c000-0000-4000-8000-000000000013",
        "?external_id=9011c000-0000-4000-8000-000000000012&number=П-0001&contract_external_id=not-a-uuid"})
    void externalIdWithNumberOrContract_isValidationErrorNamingExternalId_withoutAskingService(String parameters) {
        MvcTestResult result = mvc.get().uri(POLICIES + parameters).exchange();

        assertThat(result).hasStatus(400);
        assertThat(result).bodyJson().extractingPath("$.error.code").isEqualTo("VALIDATION_ERROR");
        assertThat(result).bodyJson().extractingPath("$.error.details").asArray().hasSize(1);
        assertThat(result).bodyJson().extractingPath("$.error.details[0].field").isEqualTo("external_id");
        assertThat(result).bodyJson().extractingPath("$.error.details[0].reason").isEqualTo(CONFLICT_REASON);
        verifyNoInteractions(policies, policySearch, contractViews);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "?number=П-0001&contract_id=9011c000-0000-4000-8000-000000000013",
        "?contract_id=9011c000-0000-4000-8000-000000000013",
        "?number=П-0001&contract_id=9011c000-0000-4000-8000-000000000013&contract_external_id=9011c000-0000-4000-8000-000000000013",
        "?contract_id=9011c000-0000-4000-8000-000000000013&contract_external_id=9011c000-0000-4000-8000-000000000013",
        "?contract_id=not-a-uuid"})
    void legacyContractId_isValidationErrorNamingContractId_withoutAskingService(String parameters) {
        MvcTestResult result = mvc.get().uri(POLICIES + parameters).exchange();

        assertThat(result).hasStatus(400);
        assertThat(result).bodyJson().extractingPath("$.error.code").isEqualTo("VALIDATION_ERROR");
        assertThat(result).bodyJson().extractingPath("$.error.details").asArray().hasSize(1);
        assertThat(result).bodyJson().extractingPath("$.error.details[0].field").isEqualTo("contract_id");
        assertThat(result).bodyJson().extractingPath("$.error.details[0].reason").isEqualTo(LEGACY_KEY_REASON);
        verifyNoInteractions(policies, policySearch, contractViews);
    }

    /** Условие «ключа другого способа нет» смотрит только на ключи полиса: посторонний параметр ему не мешает. */
    @Test
    void externalIdWithUnrelatedParameter_isStillLookupByExternalId() {
        when(policies.byExternalId(POLICY_EXTERNAL_ID)).thenReturn(policy());

        MvcTestResult result = mvc.get()
                .uri(POLICIES + "?external_id={external}&limit=5", POLICY_EXTERNAL_ID)
                .exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.id").isEqualTo(POLICY_ID.toString());
    }

    @Test
    void numberWithContractAndUnrelatedParameter_isStillLookupByNumber() {
        when(policies.byNumber(POLICY_NUMBER, CONTRACT_EXTERNAL_ID)).thenReturn(policy());

        MvcTestResult result = mvc.get()
                .uri(POLICIES + "?number={number}&contract_external_id={contract}&query=x", POLICY_NUMBER,
                        CONTRACT_EXTERNAL_ID)
                .exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.id").isEqualTo(POLICY_ID.toString());
    }

    private static PolicyResponse policy() {
        return new PolicyResponse(POLICY_ID, POLICY_EXTERNAL_ID, POLICY_NUMBER, "Фамилия", "Имя", null,
                LocalDate.of(2000, 1, 1), null, null, LocalDate.of(2026, 1, 1), null, true, false, null, List.of());
    }
}
