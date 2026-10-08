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
import ru.projectx.datareference.service.InsuranceContractReads;

/** Ручки договора страхования выбирают сервис чтения по ключу: {@code id}, {@code external_id} или отказ без ключа. */
@ExtendWith(MockitoExtension.class)
class InsuranceContractControllerTest {

    private static final String CONTRACTS = InternalApi.BASE_PATH + "/insurance-contracts";
    private static final UUID CONTRACT_ID = UUID.fromString("1c0d0000-0000-4000-8000-000000000001");
    private static final UUID CONTRACT_EXTERNAL_ID = UUID.fromString("1c0d0000-0000-4000-8000-000000000002");

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
    void contractWithoutKey_isValidationErrorNamingExternalId() {
        MvcTestResult result = mvc.get().uri(CONTRACTS).exchange();

        assertThat(result).hasStatus(400);
        assertThat(result).bodyJson().extractingPath("$.error.code").isEqualTo("VALIDATION_ERROR");
        assertThat(result).bodyJson().extractingPath("$.error.details[0].field").isEqualTo("external_id");
        assertThat(result).bodyJson().extractingPath("$.error.details[0].reason").isEqualTo("нужен external_id");
        verifyNoInteractions(insuranceContracts);
    }

    /** {@code produces} отсекает запрос ещё при выборе обработчика; тело 406 проверяет {@code RequestErrorAdviceTest}. */
    @Test
    void contractById_acceptingOnlyXml_isNotAcceptable_withoutAskingService() {
        MvcTestResult result = mvc.get().uri(CONTRACTS + "/{id}", CONTRACT_ID).accept(MediaType.APPLICATION_XML)
                .exchange();

        assertThat(result).hasStatus(406);
        verifyNoInteractions(insuranceContracts);
    }

    private static InsuranceContractResponse contract() {
        return new InsuranceContractResponse(
                CONTRACT_ID, CONTRACT_EXTERNAL_ID, "ДМС-0001", LocalDate.of(2026, 1, 1), null, false, null);
    }
}
