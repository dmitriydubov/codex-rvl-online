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
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import ru.projectx.datareference.dto.InsuredRelativesResponse;
import ru.projectx.datareference.dto.RelativePolicy;
import ru.projectx.datareference.service.policy.InsuredRelativesReads;

@ExtendWith(MockitoExtension.class)
class InsuredRelativesControllerTest {

    private static final String RELATIVES = InternalApi.BASE_PATH + "/policies/relatives";
    private static final UUID INSURED = UUID.fromString("9011c000-0000-4000-8000-000000000001");
    private static final UUID CONTRACT = UUID.fromString("9011c000-0000-4000-8000-000000000002");
    private static final UUID RELATIVE = UUID.fromString("9011c000-0000-4000-8000-000000000003");
    private static final UUID TYPE = UUID.fromString("797093be-2c51-11e8-8123-005056b43f21");
    private static final String NUMBER = "СТЕНД-0001";

    @Mock
    private InsuredRelativesReads relatives;

    private MockMvcTester mvc;

    @BeforeEach
    void setUp() {
        mvc = StandaloneMvc.of(new InsuredRelativesController(relatives));
    }

    @Test
    void byExternalId_returnsRelativesOfThatPolicy_asJson() {
        when(relatives.ofPolicy(INSURED)).thenReturn(oneRelative());

        MvcTestResult result = mvc.get().uri(RELATIVES + "?external_id={id}", INSURED).exchange();

        assertThat(result).hasStatusOk().hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON);
        assertThat(result).bodyJson().extractingPath("$.items[0]").asMap().containsValue(TYPE.toString());
        assertThat(result).bodyJson().extractingPath("$.items[0].policy.number").isEqualTo("СТЕНД-0002");
    }

    @Test
    void byNumberAndContract_returnsRelativesOfThatPolicy() {
        when(relatives.ofPolicyNumber(NUMBER, CONTRACT)).thenReturn(oneRelative());

        MvcTestResult result = mvc.get()
                .uri(RELATIVES + "?number={number}&contract_external_id={contract}", NUMBER, CONTRACT).exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.items[0].policy.number").isEqualTo("СТЕНД-0002");
    }

    @ParameterizedTest(name = "{0} — нарушение по {1}: {2}")
    @CsvSource(delimiter = '|', value = {
            "''                                                              | external_id          | нужен external_id либо number вместе с contract_external_id",
            "number=СТЕНД-0001                                               | contract_external_id | обязательный параметр",
            "number=СТЕНД-0001&contract_external_id=                         | contract_external_id | обязательный параметр",
            "contract_external_id=9011c000-0000-4000-8000-000000000002       | number               | обязательный параметр",
            "number=&contract_external_id=9011c000-0000-4000-8000-000000000002 | number            | обязательный параметр",
            "external_id=                                                    | external_id          | обязательный параметр",
            "external_id=9011c000-0000-4000-8000-000000000001&number=СТЕНД-0001 | external_id       | external_id не передаётся вместе с number или contract_external_id",
            "external_id=9011c000-0000-4000-8000-000000000001&contract_external_id=9011c000-0000-4000-8000-000000000002 | external_id | external_id не передаётся вместе с number или contract_external_id",
            "external_id=not-a-uuid                                          | external_id          | ожидается UUID",
            "number=СТЕНД-0001&contract_external_id=not-a-uuid               | contract_external_id | ожидается UUID"})
    void invalidKey_isValidationErrorNamingTheParameter_withoutAskingService(String query, String field, String reason) {
        MvcTestResult result = mvc.get().uri(RELATIVES + "?" + query).exchange();

        assertThat(result).hasStatus(400);
        assertThat(result).bodyJson().extractingPath("$.error.code").isEqualTo("VALIDATION_ERROR");
        assertThat(result).bodyJson().extractingPath("$.error.details").asArray().hasSize(1);
        assertThat(result).bodyJson().extractingPath("$.error.details[0].field").isEqualTo(field);
        assertThat(result).bodyJson().extractingPath("$.error.details[0].reason").isEqualTo(reason);
        verifyNoInteractions(relatives);
    }

    @Test
    void blankNumberWithContract_isMissingNumber_withoutAskingService() {
        MvcTestResult result = mvc.get().uri(RELATIVES).param("number", " ")
                .param("contract_external_id", CONTRACT.toString()).exchange();

        assertThat(result).hasStatus(400);
        assertThat(result).bodyJson().extractingPath("$.error.details[0].field").isEqualTo("number");
        verifyNoInteractions(relatives);
    }

    @Test
    void relativePolicy_hasNoEmailField() {
        when(relatives.ofPolicy(INSURED)).thenReturn(oneRelative());

        MvcTestResult result = mvc.get().uri(RELATIVES + "?external_id={id}", INSURED).exchange();

        assertThat(result).bodyJson().extractingPath("$.items[0].policy").asMap()
                .doesNotContainKey("email")
                .containsValues("Тестова", "2015-01-01");
    }

    private static InsuredRelativesResponse oneRelative() {
        RelativePolicy policy = new RelativePolicy(UUID.randomUUID(), RELATIVE, "СТЕНД-0002", "Тестова", "Проба", null,
                LocalDate.of(2015, 1, 1), "Ж", LocalDate.of(2026, 1, 1), null, true, false, false, null, List.of());
        return new InsuredRelativesResponse(List.of(new InsuredRelativesResponse.Relative(TYPE, policy)));
    }
}
