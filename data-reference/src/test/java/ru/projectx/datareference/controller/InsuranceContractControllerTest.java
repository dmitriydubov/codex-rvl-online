package ru.projectx.datareference.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import ru.projectx.datareference.dto.InsuranceContractResponse;
import ru.projectx.datareference.service.insurancecontract.InsuranceContractReads;

@ExtendWith(MockitoExtension.class)
class InsuranceContractControllerTest {

    private static final String CONTRACTS = InternalApi.BASE_PATH + "/insurance-contracts";
    private static final UUID CONTRACT_ID = UUID.fromString("1c0d0000-0000-4000-8000-000000000001");
    private static final UUID CONTRACT_EXTERNAL_ID = UUID.fromString("1c0d0000-0000-4000-8000-000000000002");
    private static final UUID INSURER_EXTERNAL_ID = UUID.fromString("1c0d0000-0000-4000-8000-000000000003");

    @Mock
    private InsuranceContractReads insuranceContracts;

    private MockMvcTester mvc;

    @BeforeEach
    void setUp() {
        mvc = StandaloneMvc.of(new InsuranceContractController(insuranceContracts));
    }

    @Test
    void contractById_returnsContractFoundByService_asJson() {
        when(insuranceContracts.byId(CONTRACT_ID)).thenReturn(contract());

        MvcTestResult result = mvc.get().uri(CONTRACTS + "/{id}", CONTRACT_ID).exchange();

        assertThat(result).hasStatusOk().hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON);
        assertThat(result).bodyJson().extractingPath("$.id").isEqualTo(CONTRACT_ID.toString());
    }

    @Test
    void contractByExternalId_returnsContractFoundByService() {
        when(insuranceContracts.byExternalId(CONTRACT_EXTERNAL_ID)).thenReturn(contract());

        MvcTestResult result = mvc.get().uri(CONTRACTS + "?external_id={external}", CONTRACT_EXTERNAL_ID).exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.id").isEqualTo(CONTRACT_ID.toString());
    }

    @Test
    void activeContractByInsurer_returnsContractFoundByService() {
        when(insuranceContracts.activeByInsurer(INSURER_EXTERNAL_ID)).thenReturn(contract());

        MvcTestResult result = mvc.get()
                .uri(CONTRACTS + "?insurer_external_id={insurer}", INSURER_EXTERNAL_ID).exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.id").isEqualTo(CONTRACT_ID.toString());
    }

    @Test
    void activeContractByInsurer_notCanonicalGuid_isValidationErrorNamingInsurerKey() {
        MvcTestResult result = mvc.get().uri(CONTRACTS + "?insurer_external_id=1-2-3-4-5").exchange();

        assertValidationError(result, "insurer_external_id", "ожидается UUID");
    }

    @Test
    void contractByBothKeys_isValidationErrorNamingExternalId() {
        MvcTestResult result = mvc.get().uri(CONTRACTS + "?external_id={external}&insurer_external_id={insurer}",
                CONTRACT_EXTERNAL_ID, INSURER_EXTERNAL_ID).exchange();

        assertValidationError(result, "external_id", "external_id не передаётся вместе с insurer_external_id");
    }

    @Test
    void contractByEmptyExternalIdWithInsurer_isStillBothKeysError() {
        MvcTestResult result = mvc.get()
                .uri(CONTRACTS + "?external_id=&insurer_external_id={insurer}", INSURER_EXTERNAL_ID).exchange();

        assertValidationError(result, "external_id", "external_id не передаётся вместе с insurer_external_id");
    }

    @Test
    void contractWithoutKey_isValidationErrorNamingBothKeys() {
        MvcTestResult result = mvc.get().uri(CONTRACTS).exchange();

        assertValidationError(result, "external_id", "нужен external_id либо insurer_external_id");
    }

    @Test
    void contractById_acceptingOnlyXml_isNotAcceptable_withoutAskingService() {
        MvcTestResult result = mvc.get().uri(CONTRACTS + "/{id}", CONTRACT_ID).accept(MediaType.APPLICATION_XML)
                .exchange();

        assertThat(result).hasStatus(406);
        verifyNoInteractions(insuranceContracts);
    }

    private void assertValidationError(MvcTestResult result, String field, String reason) {
        assertThat(result).hasStatus(400);
        assertThat(result).bodyJson().extractingPath("$.error.code").isEqualTo("VALIDATION_ERROR");
        assertThat(result).bodyJson().extractingPath("$.error.details[0].field").isEqualTo(field);
        assertThat(result).bodyJson().extractingPath("$.error.details[0].reason").isEqualTo(reason);
        verifyNoInteractions(insuranceContracts);
    }

    private static InsuranceContractResponse contract() {
        return new InsuranceContractResponse(
                CONTRACT_ID, CONTRACT_EXTERNAL_ID, "ДМС-0001", LocalDate.of(2026, 1, 1), null, false, null);
    }
}
