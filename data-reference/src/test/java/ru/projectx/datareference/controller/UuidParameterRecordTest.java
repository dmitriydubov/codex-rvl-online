package ru.projectx.datareference.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.net.URI;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import ru.projectx.datareference.service.insurancecontract.InsuranceContractReads;
import ru.projectx.datareference.service.lpu.LpuCardReads;
import ru.projectx.datareference.service.lpu.LpuCatalogReads;
import ru.projectx.datareference.service.lpu.LpuServiceReads;
import ru.projectx.datareference.service.policy.PolicyContractViews;
import ru.projectx.datareference.service.policy.PolicyReads;
import ru.projectx.datareference.service.policy.PolicySearchReads;
import ru.projectx.datareference.service.program.ProgramReads;

class UuidParameterRecordTest {

    private static final String API = InternalApi.BASE_PATH;

    private final InsuranceContractReads insuranceContracts = mock(InsuranceContractReads.class);
    private final ProgramReads programs = mock(ProgramReads.class);
    private final PolicyReads policies = mock(PolicyReads.class);
    private final PolicySearchReads policySearch = mock(PolicySearchReads.class);
    private final PolicyContractViews contractViews = mock(PolicyContractViews.class);
    private final LpuCatalogReads catalogs = mock(LpuCatalogReads.class);
    private final LpuCardReads cards = mock(LpuCardReads.class);
    private final LpuServiceReads services = mock(LpuServiceReads.class);

    private MockMvcTester mvc;

    @BeforeEach
    void setUp() {
        mvc = StandaloneMvc.of(
                new InsuranceContractController(insuranceContracts),
                new ProgramController(programs),
                new PolicyController(policies, policySearch, contractViews),
                new LpuController(catalogs, cards, services));
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(delimiterString = " => ", value = {
            "/insurance-contracts/{v} => id",
            "/insurance-contracts?external_id={v} => external_id",
            "/programs/{v} => id",
            "/policies/{v} => id",
            "/policies?external_id={v} => external_id",
            "/policies?number=1&contract_external_id={v} => contract_external_id",
            "/policies/{v}/contract-view => id",
            "/policies/contract-view?external_id={v} => external_id",
            "/policies/search?query=a&contract_external_id={v} => contract_external_id",
            "/lpu?contract_external_id={v} => contract_external_id",
            "/lpu/{v} => id",
            "/lpu/{v}/services => id"})
    void nonCanonicalUuid_isValidationErrorNamingTheParameter_withoutAskingServices(String path, String field) {
        for (String value : new String[] {"1-2-3-4-5", " 77ff17b5-e659-4720-b57f-e8a19c00fcea "}) {
            MvcTestResult result = mvc.get().uri(API + path, value).exchange();

            assertThat(result).hasStatus(400);
            assertThat(result).bodyJson().extractingPath("$.error.code").isEqualTo("VALIDATION_ERROR");
            assertThat(result).bodyJson().extractingPath("$.error.details[0].field").isEqualTo(field);
        }
        verifyNoInteractions(
                insuranceContracts, programs, policies, policySearch, contractViews, catalogs, cards, services);
    }

    @ParameterizedTest
    @CsvSource(delimiterString = " => ", value = {
            "/insurance-contracts?external_id= => external_id",
            "/policies?external_id=%20%20 => external_id",
            "/policies?number=1&contract_external_id= => contract_external_id"})
    void blankUuidParameter_isTheMissingParameter_withoutAskingServices(String path, String field) {
        MvcTestResult result = mvc.get().uri(URI.create(API + path)).exchange();

        assertThat(result).hasStatus(400);
        assertThat(result).bodyJson().extractingPath("$.error.code").isEqualTo("VALIDATION_ERROR");
        assertThat(result).bodyJson().extractingPath("$.error.details[0].field").isEqualTo(field);
        assertThat(result).bodyJson().extractingPath("$.error.details[0].reason").isEqualTo("обязательный параметр");
        verifyNoInteractions(
                insuranceContracts, programs, policies, policySearch, contractViews, catalogs, cards, services);
    }

    @Test
    void canonicalUuidInUpperCaseInThePath_reachesTheServiceAsTheSameUuid() {
        mvc.get().uri(API + "/programs/{id}", "77FF17B5-E659-4720-B57F-E8A19C00FCEA").exchange();

        verify(programs).byId(UUID.fromString("77ff17b5-e659-4720-b57f-e8a19c00fcea"));
    }
}
